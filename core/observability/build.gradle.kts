plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
}

android {
    namespace = "io.cloudcauldron.bocan.observability"
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

    lint {
        fatal += listOf("NewApi", "MissingPermission")
    }
}

kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

dependencies {
    api(libs.timber)

    testImplementation(libs.junit)
}
