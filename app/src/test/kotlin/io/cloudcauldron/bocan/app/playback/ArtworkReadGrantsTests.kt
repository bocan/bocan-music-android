package io.cloudcauldron.bocan.app.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression cover for blank artwork in Android Auto: Media3 never grants a
 * controller read on artwork content Uris, so this grant is the only thing
 * standing between Auto and a SecurityException. The Uri here must match what
 * the FileProvider serves (authority from the manifest, "artwork" path name
 * from file_paths.xml); if either side is renamed this pins the breakage.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ArtworkReadGrantsTests {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `grants prefix read on the artwork root to the connecting package`() {
        var granted: Triple<String, Uri, Int>? = null
        val grants = ArtworkReadGrants(context) { pkg, uri, flags -> granted = Triple(pkg, uri, flags) }

        grants.grantReadTo("com.google.android.projection.gearhead")

        val (pkg, uri, flags) = assertNotNull(granted)
        assertEquals("com.google.android.projection.gearhead", pkg)
        assertEquals("content://${context.packageName}.fileprovider/artwork", uri.toString())
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, flags)
    }

    @Test
    fun `a refused grant is contained, never thrown into the session callback`() {
        val refusing = ArtworkReadGrants(context) { _, _, _ -> throw SecurityException("refused") }
        refusing.grantReadTo("com.example.browser")

        val rejecting = ArtworkReadGrants(context) { _, _, _ -> throw IllegalArgumentException("unknown package") }
        rejecting.grantReadTo("com.example.browser")
    }
}
