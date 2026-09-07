package io.cloudcauldron.bocan.app.demo

import java.io.File
import java.io.IOException
import java.io.InputStream

/** The checked-in demo assets on disk; Gradle runs unit tests with the module dir as the working dir. */
val DEMO_ASSET_DIR: File = File("src/main/assets/demo")

/** [DemoAssets] over a directory, with hooks to corrupt or fail one path for the failure-path tests. */
class DirectoryDemoAssets(
    private val root: File = DEMO_ASSET_DIR,
    private val failOn: String? = null,
    private val corruptOn: String? = null
) : DemoAssets {
    override fun open(path: String): InputStream {
        if (path == failOn) throw IOException("test failure opening $path")
        val file = File(root, path)
        if (!file.isFile) throw IOException("missing demo asset $path")
        if (path == corruptOn) return (file.readBytes() + byteArrayOf(0)).inputStream()
        return file.inputStream()
    }
}
