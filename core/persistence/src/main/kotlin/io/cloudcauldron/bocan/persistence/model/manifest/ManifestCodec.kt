package io.cloudcauldron.bocan.persistence.model.manifest

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration for manifest documents. Unknown keys are ignored
 * per protocol section 10; nulls are omitted on encode so round-trips stay
 * byte-lean.
 */
object ManifestCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun decode(text: String): Manifest = json.decodeFromString(text)

    fun encode(manifest: Manifest): String = json.encodeToString(manifest)
}
