package io.cloudcauldron.bocan.app.widget

import android.content.Context
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Persists the latest [WidgetState] as JSON in an app-private file so the Glance widget
 * can render from it on a cold process, with the app not running (phase 10 gotcha). Small
 * and whole-file, so no DataStore ceremony is needed.
 */
class WidgetStateStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    fun write(state: WidgetState) {
        file.writeText(json.encodeToString(WidgetState.serializer(), state))
    }

    fun read(): WidgetState = runCatching {
        if (!file.exists()) WidgetState.EMPTY else json.decodeFromString(WidgetState.serializer(), file.readText())
    }.getOrElse { error -> if (error is SerializationException) WidgetState.EMPTY else throw error }

    private companion object {
        const val FILE_NAME = "widget_state.json"
    }
}
