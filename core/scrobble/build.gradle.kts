plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "io.cloudcauldron.bocan.scrobble"
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
                // Thin platform glue over the Android Keystore. Robolectric does not
                // implement the AndroidKeyStore provider, so there is no behaviour to
                // unit test off-device; the token store is exercised through an
                // in-memory fake, and every class it delegates to (rules, retry math,
                // signing, queue state machine, providers, service) is covered directly.
                classes(
                    "io.cloudcauldron.bocan.scrobble.auth.KeystoreTokenStore",
                    "io.cloudcauldron.bocan.scrobble.auth.KeystoreTokenStore$*"
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

    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.sqlite.bundled.jvm)
}
