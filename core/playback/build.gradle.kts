plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "io.cloudcauldron.bocan.playback"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk {
            version = release(29)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        fatal += listOf("NewApi", "MissingPermission")
    }
}

kover {
    reports {
        filters {
            excludes {
                // Platform and Media3 glue with no behaviour to unit test off-device.
                // The player, session, controller, and file IO they own are exercised
                // on a real device; the logic they delegate to (shuffle, stats rules,
                // gain math, media id and queue codecs, item mapping) lives in plain
                // classes that are covered directly and carry this module's floor.
                classes(
                    "io.cloudcauldron.bocan.playback.PlaybackService",
                    "io.cloudcauldron.bocan.playback.PlaybackService$*",
                    "io.cloudcauldron.bocan.playback.PlayerFactory",
                    "io.cloudcauldron.bocan.playback.PlayerFactory$*",
                    "io.cloudcauldron.bocan.playback.queue.QueueController",
                    "io.cloudcauldron.bocan.playback.queue.QueueController$*",
                    "io.cloudcauldron.bocan.playback.stats.PlayStatsRecorder",
                    "io.cloudcauldron.bocan.playback.stats.PlayStatsRecorder$*",
                    "io.cloudcauldron.bocan.playback.podcast.EpisodeProgressRecorder",
                    "io.cloudcauldron.bocan.playback.podcast.EpisodeProgressRecorder$*"
                )
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:persistence"))
    api(project(":core:observability"))

    api(libs.media3.exoplayer)
    api(libs.media3.session)
    api(libs.media3.common)
    implementation(libs.media3.datasource)
    // Prebuilt FFmpeg audio renderer for formats the platform cannot decode
    // (APE, WavPack, and similar). The native .so version must match Media3 exactly.
    runtimeOnly(libs.media3.ffmpeg.decoder)

    implementation(libs.kotlinx.coroutines.core)
    // Provides Dispatchers.Main, the required thread for all MediaController and
    // Player calls.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.media3.test.utils)
    testImplementation(libs.media3.test.utils.robolectric)
}
