package io.cloudcauldron.bocan.sync.engine

import io.cloudcauldron.bocan.persistence.SyncApplier
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl

/**
 * Turns a [SyncApplier.Plan] into an ordered download queue and streams it: three
 * files in parallel, artwork first, then tracks by album, then episodes, so the
 * library never lists audio before its bytes exist. Progress is reported as
 * deltas so the engine can render it without knowing about HTTP or files.
 *
 * Path validation happens while the queue is built (via [MediaLayout]), so a
 * hostile relPath fails before any network request and never escapes the media
 * root.
 */
class FileTransferrer(
    private val downloader: Downloader,
    private val mediaLayout: MediaLayout,
    private val artworkStore: ArtworkStore,
    private val dispatchers: CoroutineDispatchers,
    private val maxParallelDownloads: Int = DEFAULT_PARALLELISM
) {
    /** A resolved, ordered set of files to fetch, with the byte totals for progress and the space check. */
    data class Queue(val items: List<Item>, val bytesTotal: Long) {
        val filesTotal: Int get() = items.size
    }

    /** Transfer progress, emitted as the queue drains. */
    data class Progress(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long, val label: String)

    /** The ids to flip to downloaded, the count actually fetched, and any per-item failures. */
    data class Outcome(val trackIds: List<Long>, val episodeIds: List<String>, val downloadedCount: Int, val failures: List<ItemFailure>)

    /** One queued file. Artwork carries no id and no byte total; tracks and episodes do. */
    data class Item(
        val kind: String,
        val displayId: String,
        val urlPath: String,
        val expectedSha: String,
        val target: File,
        val label: String,
        val sizeBytes: Long,
        val countsBytes: Boolean,
        val trackId: Long? = null,
        val episodeId: String? = null
    )

    /** Build the ordered queue. May throw [io.cloudcauldron.bocan.sync.SyncError.UnsafePath] for a hostile relPath. */
    fun buildQueue(plan: SyncApplier.Plan): Queue {
        val artwork = plan.artworkHashesNeeded.map(::artworkItem)
        val tracks = plan.tracksToDownload
            .sortedWith(compareBy({ it.albumName }, { it.discNumber ?: 1 }, { it.trackNumber ?: Int.MAX_VALUE }, { it.id }))
            .map(::trackItem)
        val episodes = plan.episodesToDownload.map(::episodeItem)
        val items = artwork + tracks + episodes
        return Queue(items, items.filter { it.countsBytes }.sumOf { it.sizeBytes })
    }

    /** Stream the queue, reporting [onProgress] as it drains. */
    suspend fun transfer(queue: Queue, base: HttpUrl, onProgress: (Progress) -> Unit): Outcome {
        val run = Run(queue, base, onProgress)
        queue.items.groupedInOrder().forEach { group -> transferGroup(group, run) }
        return run.counters.toOutcome()
    }

    private suspend fun transferGroup(group: List<Item>, run: Run) {
        val semaphore = Semaphore(maxParallelDownloads)
        coroutineScope {
            group.forEach { item ->
                launch(dispatchers.io) { semaphore.withPermit { downloadOne(item, run) } }
            }
        }
    }

    private suspend fun downloadOne(item: Item, run: Run) {
        emit(item.label, run)
        val result = downloader.download(run.base.resolvePath(item.urlPath), item.expectedSha, item.target) { delta ->
            run.counters.bytesDone.addAndGet(delta)
            emit(item.label, run)
        }
        record(result, item, run.counters)
        run.counters.filesDone.incrementAndGet()
        emit(item.label, run)
    }

    private fun record(result: Downloader.Result, item: Item, counters: Counters) {
        when (result) {
            is Downloader.Result.Failed -> counters.failures.add(ItemFailure(item.displayId, item.kind, result.reason))
            is Downloader.Result.Downloaded -> {
                counters.downloadedCount.incrementAndGet()
                item.trackId?.let(counters.trackIds::add)
                item.episodeId?.let(counters.episodeIds::add)
            }
            is Downloader.Result.AlreadyPresent -> {
                item.trackId?.let(counters.trackIds::add)
                item.episodeId?.let(counters.episodeIds::add)
            }
        }
    }

    private fun emit(label: String, run: Run) {
        val c = run.counters
        run.onProgress(Progress(c.filesDone.get(), run.queue.filesTotal, c.bytesDone.get(), run.queue.bytesTotal, label))
    }

    private fun artworkItem(hash: String): Item = Item(
        kind = KIND_ARTWORK,
        displayId = hash,
        urlPath = "v1/artwork/$hash",
        expectedSha = hash,
        target = artworkStore.fileFor(hash),
        label = LABEL_ARTWORK,
        sizeBytes = 0,
        countsBytes = false
    )

    private fun trackItem(track: TrackEntity): Item = Item(
        kind = KIND_TRACK,
        displayId = track.id.toString(),
        trackId = track.id,
        urlPath = "v1/file/track/${track.id}",
        expectedSha = track.sha256,
        target = mediaLayout.trackFile(track.relPath),
        label = track.title,
        sizeBytes = track.size,
        countsBytes = true
    )

    private fun episodeItem(episode: EpisodeEntity): Item = Item(
        kind = KIND_EPISODE,
        displayId = episode.id,
        episodeId = episode.id,
        urlPath = "v1/file/episode/${episode.id}",
        expectedSha = episode.sha256,
        target = mediaLayout.episodeFile(episode.relPath),
        label = episode.title,
        sizeBytes = episode.size,
        countsBytes = true
    )

    private fun List<Item>.groupedInOrder(): List<List<Item>> = listOf(
        filter { it.kind == KIND_ARTWORK },
        filter { it.kind == KIND_TRACK },
        filter { it.kind == KIND_EPISODE }
    ).filter { it.isNotEmpty() }

    private class Run(val queue: Queue, val base: HttpUrl, val onProgress: (Progress) -> Unit) {
        val counters = Counters()
    }

    private class Counters {
        val bytesDone = AtomicLong(0)
        val filesDone = AtomicInteger(0)
        val downloadedCount = AtomicInteger(0)
        val trackIds = ConcurrentLinkedQueue<Long>()
        val episodeIds = ConcurrentLinkedQueue<String>()
        val failures = ConcurrentLinkedQueue<ItemFailure>()

        fun toOutcome() = Outcome(trackIds.toList(), episodeIds.toList(), downloadedCount.get(), failures.toList())
    }

    private companion object {
        const val DEFAULT_PARALLELISM = 3
        const val KIND_ARTWORK = "artwork"
        const val KIND_TRACK = "track"
        const val KIND_EPISODE = "episode"
        const val LABEL_ARTWORK = "artwork"
    }
}
