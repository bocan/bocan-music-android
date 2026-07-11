package io.cloudcauldron.bocan.playback.queue

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.withContext

/**
 * Persists the queue snapshot to a single JSON file and restores it on service
 * start. The write is atomic (temp file plus rename) so a process killed mid-write
 * never leaves a torn file: the reader sees either the old snapshot or the new one.
 *
 * QueueController drives the cadence: every five seconds while playing and on every
 * item transition, matching the Mac's write-back cadence. This class only owns the
 * file, not the timer, so its read and write are directly testable.
 */
class QueuePersistence(private val directory: File, private val dispatchers: CoroutineDispatchers, private val log: AppLog) {
    private val file: File get() = File(directory, FILE_NAME)
    private val tempFile: File get() = File(directory, "$FILE_NAME.tmp")

    /**
     * Write [snapshot] atomically. A failure is logged and swallowed: losing the
     * last few seconds of queue state is not worth crashing playback.
     */
    suspend fun save(snapshot: QueueSnapshot): Unit = withContext(dispatchers.io) {
        try {
            directory.mkdirs()
            tempFile.writeText(QueueSnapshotCodec.encode(snapshot))
            atomicReplace(tempFile, file)
            log.debug("queue.persist", mapOf("count" to snapshot.mediaIds.size, "index" to snapshot.index))
        } catch (io: IOException) {
            log.warning("queue.persist.failed", mapOf("error" to io.toString()))
        }
    }

    /** Restore the saved snapshot, or null if there is none or it is unreadable or corrupt. */
    suspend fun load(): QueueSnapshot? = withContext(dispatchers.io) {
        val target = file
        if (!target.isFile) return@withContext null
        val text = try {
            target.readText()
        } catch (io: IOException) {
            log.warning("queue.restore.failed", mapOf("error" to io.toString()))
            return@withContext null
        }
        QueueSnapshotCodec.decode(text)
    }

    private fun atomicReplace(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (unsupported: AtomicMoveNotSupportedException) {
            // Some filesystems cannot move atomically across the temp and target;
            // fall back to a plain replacing move, which is still a single rename.
            log.debug("queue.persist.nonatomic", mapOf("error" to unsupported.toString()))
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val FILE_NAME = "queue.json"
    }
}
