package io.cloudcauldron.bocan.app.demo

import io.cloudcauldron.bocan.observability.AppLog
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The file plumbing under [DemoLibrary]: verified copies out of the assets and quiet deletes. */
internal object DemoFiles {
    private const val PART_SUFFIX = ".part"
    private const val SHA_256 = "SHA-256"
    private const val COPY_BUFFER = 64 * 1024

    /**
     * Stream one asset to `<target>.part` while hashing, then rename into place.
     * A hash mismatch discards the part and throws [DemoAssetCorruptException].
     */
    suspend fun copyVerified(assets: DemoAssets, assetPath: String, target: File, expectedSha256: String) {
        target.parentFile?.mkdirs()
        val part = File(target.path + PART_SUFFIX)
        val actual = assets.open(assetPath).use { input -> part.outputStream().use { output -> hashWhileCopying(input, output) } }
        if (actual != expectedSha256) {
            part.delete()
            throw DemoAssetCorruptException(assetPath)
        }
        moveIntoPlace(part, target)
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance(SHA_256).digest(bytes).toHex()

    fun deleteQuietly(file: File, log: AppLog) {
        if (file.exists() && !file.delete()) log.warning("demo.deleteFailed", mapOf("path" to file.path))
    }

    private suspend fun hashWhileCopying(input: InputStream, output: OutputStream): String {
        val digest = MessageDigest.getInstance(SHA_256)
        val buffer = ByteArray(COPY_BUFFER)
        var read = input.read(buffer)
        while (read >= 0) {
            currentCoroutineContext().ensureActive()
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            read = input.read(buffer)
        }
        return digest.digest().toHex()
    }

    private fun moveIntoPlace(part: File, target: File) {
        if (target.exists() && !target.delete()) throw IOException("could not replace ${target.path}")
        if (!part.renameTo(target)) throw IOException("could not move ${part.path} into place")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
