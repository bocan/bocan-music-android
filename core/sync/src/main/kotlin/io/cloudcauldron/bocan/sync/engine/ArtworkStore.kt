package io.cloudcauldron.bocan.sync.engine

import java.io.File

/**
 * Content-addressed artwork on disk. Artwork is identified purely by its SHA-256
 * hash (the manifest's `artworkHash`), so a file is stored under
 * `media/artwork/<hash>` with no extension: the hash is the identity and the
 * image decoder sniffs the format. This keeps the download path uniform, since
 * the target path is known before the response's Content-Type is.
 */
class ArtworkStore(private val mediaLayout: MediaLayout) {
    /** Where the artwork for [hash] should be written and later read from. */
    fun fileFor(hash: String): File = File(mediaLayout.artworkDir(), hash)

    /** The on-disk artwork file if it is present, else null (for the player and UI). */
    fun existing(hash: String): File? = fileFor(hash).takeIf { it.isFile }
}
