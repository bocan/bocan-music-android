package io.cloudcauldron.bocan.app.persistence

import android.content.Context
import io.cloudcauldron.bocan.app.AppGraph
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Every setting a person can change, as the app reads it back from storage, written as text
 * so two processes can compare: the write phase records what it left, the read phase (a new
 * process after `am force-stop`) reads the same keys and must find the same values.
 */
object StoredState {
    fun read(graph: AppGraph): Map<String, String> = runBlocking {
        val eq = graph.eqPreferences.state.first()
        val appearance = graph.appearancePreferences.settings.first()
        val library = graph.libraryPreferences
        val podcasts = graph.podcastPreferences
        mapOf(
            "playback.resumeOnReconnect" to graph.playbackPreferences.resumeOnReconnect.first(),
            "podcasts.defaultSpeed" to podcasts.appDefaultSpeed.first(),
            "podcasts.skipBack" to podcasts.skipBackSeconds.first(),
            "podcasts.skipForward" to podcasts.skipForwardSeconds.first(),
            "scrobble.toggles" to graph.scrobbleSettings.toggles.first(),
            "appearance.theme" to appearance.themeMode,
            "appearance.dynamicColor" to appearance.dynamicColor,
            "appearance.pureBlack" to appearance.pureBlack,
            "eq.enabled" to eq.enabled,
            "eq.activePreset" to eq.activePresetId,
            "eq.bands" to eq.bandGainsDb,
            "eq.bassBoost" to eq.bassBoostDb,
            "eq.replayGain" to eq.replayGainMode,
            "eq.preamp" to eq.preampDb,
            "eq.skipSilence" to eq.skipSilence,
            "eq.fade" to eq.fadeSeconds,
            "eq.userPresets" to eq.userPresets.map { it.name },
            "library.albumSort" to library.albumSort.first(),
            "library.trackSort" to library.trackSort.first(),
            "library.lastTab" to library.lastTab.first(),
            "library.recentSearches" to library.recentSearches.first()
        ).mapValues { (_, value) -> value.toString() }
    }

    /** What the write phase left, in the app's own files: kept across the kill, gone with the uninstall. */
    fun recordFile(context: Context): File = File(context.filesDir, "e2e/persistence-expected.json")

    fun save(context: Context, state: Map<String, String>) {
        val file = recordFile(context)
        file.parentFile?.mkdirs()
        file.writeText(JSONObject(state).toString(2))
    }

    fun load(context: Context): Map<String, String>? {
        val file = recordFile(context).takeIf { it.isFile } ?: return null
        val json = JSONObject(file.readText())
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
}
