plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "io.cloudcauldron.bocan.sync"
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
                // Thin platform glue over the Android Keystore, NsdManager, and
                // WifiManager multicast lock. There is no behaviour to unit test
                // off-device (Robolectric does not implement the AndroidKeyStore
                // provider, and NsdManager needs a real network stack). The
                // logic they delegate to (fingerprinting, TXT parsing, resolve
                // serialisation, cert pinning) lives in plain classes that are
                // covered directly.
                classes(
                    "io.cloudcauldron.bocan.sync.identity.KeystoreDeviceIdentity",
                    "io.cloudcauldron.bocan.sync.identity.KeystoreDeviceIdentity$*",
                    "io.cloudcauldron.bocan.sync.discovery.NsdServiceBrowserImpl",
                    "io.cloudcauldron.bocan.sync.discovery.NsdServiceBrowserImpl$*",
                    "io.cloudcauldron.bocan.sync.discovery.WifiMulticastLeaseImpl",
                    "io.cloudcauldron.bocan.sync.discovery.WifiMulticastLeaseImpl$*",
                    // Platform glue over the foreground service, WorkManager, and
                    // the NotificationManager. The engine, triggers, downloader,
                    // and layout logic they delegate to are covered directly;
                    // driving a real Service or WorkManager off-device is not
                    // meaningful under Robolectric.
                    "io.cloudcauldron.bocan.sync.service.SyncForegroundService",
                    "io.cloudcauldron.bocan.sync.service.SyncForegroundService$*",
                    "io.cloudcauldron.bocan.sync.service.SyncNotifications",
                    "io.cloudcauldron.bocan.sync.service.SyncNotifications$*",
                    "io.cloudcauldron.bocan.sync.auto.SyncWorker",
                    "io.cloudcauldron.bocan.sync.auto.SyncWorker$*",
                    "io.cloudcauldron.bocan.sync.auto.SyncWorkScheduler",
                    "io.cloudcauldron.bocan.sync.auto.SyncWorkScheduler$*"
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

dependencies {
    api(project(":core:persistence"))

    api(libs.okhttp)
    api(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.okhttp.mockwebserver3)
    // The bundled SQLite Android artifact ships device .so files only; the jvm
    // artifact carries the host natives Robolectric database tests need.
    testImplementation(libs.sqlite.bundled.jvm)
}
