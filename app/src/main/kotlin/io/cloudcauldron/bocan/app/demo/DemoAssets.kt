package io.cloudcauldron.bocan.app.demo

import android.content.Context
import java.io.InputStream

/**
 * Opens one file from the bundled demo library. The app reads the APK's
 * `assets/demo/` tree; tests read a directory, so nothing in [DemoLibrary]
 * needs an AssetManager.
 */
fun interface DemoAssets {
    /** Open [path], relative to the demo root (for example `library/Demo/01 Demo Audio 1.mp3`). */
    fun open(path: String): InputStream
}

/** The APK's `assets/demo/` tree. */
class AndroidDemoAssets(private val context: Context) : DemoAssets {
    override fun open(path: String): InputStream = context.assets.open("$ROOT/$path")

    private companion object {
        const val ROOT = "demo"
    }
}
