package io.cloudcauldron.bocan.observability

/**
 * The fixed set of logging categories. Each category becomes the Timber tag,
 * lowercased, so logcat filtering stays predictable across the app.
 */
enum class LogCategory {
    App,
    Sync,
    Pairing,
    Persistence,
    Playback,
    Podcast,
    Scrobble,
    Ui,
    Network
}
