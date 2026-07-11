package io.cloudcauldron.bocan.scrobble.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.cloudcauldron.bocan.scrobble.CoroutineDispatchers
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Credential storage encrypted with an AES-GCM key held non-exportably in the Android
 * Keystore. Jetpack Security's EncryptedSharedPreferences and MasterKey are deprecated
 * (confirmed via the phase 09 Context7 check), so this is the standards-sanctioned
 * "Keystore-wrapped equivalent": ciphertext (iv prefixed) is base64ed into an
 * app-private SharedPreferences file; the key never leaves the Keystore.
 *
 * Excluded from coverage: Robolectric provides no AndroidKeyStore, so there is nothing to
 * unit test off-device. Providers and the service are tested against an in-memory
 * [TokenStore] fake.
 */
class KeystoreTokenStore(context: Context, private val dispatchers: CoroutineDispatchers) : TokenStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val flows = ConcurrentHashMap<String, MutableStateFlow<String?>>()

    override fun observe(key: String): Flow<String?> = flowFor(key).asStateFlow()

    override suspend fun get(key: String): String? = withContext(dispatchers.io) { readDecrypted(key) }

    override suspend fun set(key: String, value: String) = withContext(dispatchers.io) {
        prefs.edit().putString(key, encrypt(value)).apply()
        flowFor(key).value = value
    }

    override suspend fun clear(key: String) = withContext(dispatchers.io) {
        prefs.edit().remove(key).apply()
        flowFor(key).value = null
    }

    private fun flowFor(key: String): MutableStateFlow<String?> = flows.getOrPut(key) { MutableStateFlow(readDecrypted(key)) }

    private fun readDecrypted(key: String): String? = prefs.getString(key, null)?.let(::decrypt)

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(combined)
        ciphertext.copyInto(combined, iv.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "scrobble_secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bocan.scrobble.credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
