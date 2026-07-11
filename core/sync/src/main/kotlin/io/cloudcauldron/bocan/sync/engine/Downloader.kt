package io.cloudcauldron.bocan.sync.engine

import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.identity.Fingerprints
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Downloads one file resumably and verifies it byte for byte before it ever
 * appears at its final path (sync-protocol.md sections 6 and 9 step 4).
 *
 * The flow:
 *  1. If the target already exists and hashes to the expected SHA-256, it is
 *     [Result.AlreadyPresent] and no request is made. This is what lets an
 *     interrupted sync re-run without re-downloading completed files.
 *  2. Otherwise stream to `<target>.part`, resuming with `Range: bytes=<n>-`
 *     when a partial `.part` is present and re-hashing its prefix first (digest
 *     state is never persisted). `If-Match: <sha256>` guards against the file
 *     changing under us: a `412` throws [SyncError.ManifestStale].
 *  3. On a SHA-256 mismatch the `.part` is discarded and the whole file is
 *     retried once from scratch, then recorded as [Result.Failed].
 *  4. A verified file is moved into place with an atomic rename, so a reader
 *     never sees a half-written file.
 *
 * The client's read timeout is the stall detector: if no bytes arrive for the
 * timeout window the socket read throws and the `.part` is left intact for a
 * later resume (the sync pauses rather than failing the item).
 */
class Downloader(private val client: OkHttpClient, private val dispatchers: CoroutineDispatchers) {
    private val log = AppLog.forCategory(LogCategory.Sync)

    /** The outcome of a single file download. */
    sealed interface Result {
        /** Bytes were fetched, verified, and atomically moved into place. */
        data object Downloaded : Result

        /** The verified file was already on disk; nothing was transferred. */
        data object AlreadyPresent : Result

        /** The file could not be obtained (bad digest twice, or a per-item HTTP error). */
        data class Failed(val reason: String) : Result
    }

    /**
     * Fetch [url] into [target], verifying it hashes to [expectedSha256].
     * [onProgress] receives the byte count of each chunk written, for the engine's
     * transfer progress. Throws [SyncError.ManifestStale] on a 412 and
     * [SyncError.Network] on a transport failure (both resumable at the engine).
     */
    suspend fun download(url: HttpUrl, expectedSha256: String, target: File, onProgress: (bytesWritten: Long) -> Unit = {}): Result =
        withContext(dispatchers.io) {
            target.parentFile?.mkdirs()
            if (target.isFile && sha256Of(target) == expectedSha256) {
                return@withContext Result.AlreadyPresent
            }
            val part = File(target.parentFile, target.name + PART_SUFFIX)
            when (val first = streamToPart(url, expectedSha256, part, allowResume = true, onProgress)) {
                is Attempt.Verified -> return@withContext finish(part, target)
                is Attempt.Failed -> return@withContext Result.Failed(first.reason)
                Attempt.DigestMismatch -> Unit // fall through to a single clean retry
            }
            part.delete()
            when (val second = streamToPart(url, expectedSha256, part, allowResume = false, onProgress)) {
                is Attempt.Verified -> finish(part, target)
                is Attempt.Failed -> {
                    part.delete()
                    Result.Failed(second.reason)
                }
                Attempt.DigestMismatch -> {
                    part.delete()
                    log.warning("download.digestMismatch", mapOf("url" to url.encodedPath))
                    Result.Failed("sha256 mismatch after retry")
                }
            }
        }

    private fun finish(part: File, target: File): Result {
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return Result.Downloaded
    }

    private sealed interface Attempt {
        data object Verified : Attempt
        data object DigestMismatch : Attempt
        data class Failed(val reason: String) : Attempt
    }

    private suspend fun streamToPart(
        url: HttpUrl,
        expectedSha256: String,
        part: File,
        allowResume: Boolean,
        onProgress: (Long) -> Unit
    ): Attempt {
        val digest = MessageDigest.getInstance(SHA_256)
        var existing = if (allowResume && part.isFile) part.length() else 0L
        if (existing > 0 && !rehashPrefix(part, digest)) {
            part.delete()
            existing = 0
            digest.reset()
        }
        val request = Request.Builder()
            .url(url)
            .header(HEADER_IF_MATCH, expectedSha256)
            .apply { if (existing > 0) header(HEADER_RANGE, "bytes=$existing-") }
            .build()
        val sink = Sink(part, digest, onProgress)
        try {
            client.newCall(request).execute().use { response ->
                return consume(response, sink, existing, expectedSha256)
            }
        } catch (e: IOException) {
            // A stall (read timeout) or a dropped socket: the .part stays for a
            // later resume; the engine treats this as a pause, not a failure.
            throw SyncError.Network(url.toString(), e)
        }
    }

    private suspend fun consume(response: Response, sink: Sink, existing: Long, expectedSha256: String): Attempt {
        if (response.code == HTTP_PRECONDITION_FAILED) {
            throw SyncError.ManifestStale(response.request.url.toString())
        }
        val partial = response.code == HTTP_PARTIAL
        val full = response.code == HTTP_OK
        if (!partial && !full) return Attempt.Failed("http ${response.code}")
        // The server ignored our Range (answered 200): restart the digest and truncate.
        val append = partial && existing > 0
        if (!append) sink.digest.reset()
        streamBody(response, sink, append)
        val actual = Fingerprints.toHex(sink.digest.digest())
        return if (actual == expectedSha256) Attempt.Verified else Attempt.DigestMismatch
    }

    private suspend fun streamBody(response: Response, sink: Sink, append: Boolean) {
        FileOutputStream(sink.part, append).use { out ->
            response.body.byteStream().use { input -> pump(input, out, sink) }
        }
    }

    private suspend fun pump(input: InputStream, out: FileOutputStream, sink: Sink) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            // Cooperate with cancellation: a cancelled sync stops mid-stream.
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            sink.digest.update(buffer, 0, read)
            sink.onProgress(read.toLong())
        }
    }

    private fun rehashPrefix(part: File, digest: MessageDigest): Boolean = try {
        part.inputStream().use { input -> digestAll(input, digest) }
        true
    } catch (e: IOException) {
        log.debug("download.rehashFailed", mapOf("error" to e.toString()))
        false
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance(SHA_256)
        file.inputStream().use { input -> digestAll(input, digest) }
        return Fingerprints.toHex(digest.digest())
    }

    private fun digestAll(input: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }

    private class Sink(val part: File, val digest: MessageDigest, val onProgress: (Long) -> Unit)

    private companion object {
        const val PART_SUFFIX = ".part"
        const val SHA_256 = "SHA-256"
        const val BUFFER_BYTES = 64 * 1024
        const val HEADER_IF_MATCH = "If-Match"
        const val HEADER_RANGE = "Range"
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
        const val HTTP_PRECONDITION_FAILED = 412
    }
}
