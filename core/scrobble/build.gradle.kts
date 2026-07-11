plugins {
    alias(libs.plugins.android.library)
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

    lint {
        fatal += listOf("NewApi", "MissingPermission")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:persistence"))

    testImplementation(libs.junit)
}
