package io.cloudcauldron.bocan.app.player

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression cover for a blank ambient wash on Now Playing: the artwork Uri in the
 * session metadata is a `content://` Uri whose path segment (`/artwork/<hash>`) is not
 * a file path. The extractor must open it through the content resolver, like every
 * other reader of that Uri. A tiny provider stands in for the FileProvider here
 * because Robolectric roots the provider's external-files path differently from
 * the app's own external files dir.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmbientBackgroundTests {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun registerProvider() {
        Robolectric.setupContentProvider(ArtworkProvider::class.java, AUTHORITY)
    }

    @Test
    fun `a content uri yields an ambient colour`() = runTest {
        val file = File(context.cacheDir, "cover.png")
        file.outputStream().use { out ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(RED) }.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        ArtworkProvider.files["present"] = file

        assertNotNull(extractAmbient(context, "content://$AUTHORITY/artwork/present"))
    }

    @Test
    fun `a content uri whose file is missing yields nothing rather than throwing`() = runTest {
        assertNull(extractAmbient(context, "content://$AUTHORITY/artwork/absent"))
    }

    /** Serves whatever file is registered under the last path segment, like the real FileProvider. */
    class ArtworkProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = files[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor? = null

        override fun getType(uri: Uri): String = "image/png"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0

        companion object {
            val files = mutableMapOf<String, File>()
        }
    }

    private companion object {
        const val AUTHORITY = "io.cloudcauldron.bocan.test.artwork"
        const val RED = 0xFFB00020.toInt()
    }
}
