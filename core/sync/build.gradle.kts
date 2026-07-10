plugins {
    alias(libs.plugins.android.library)
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        fatal += listOf("NewApi", "MissingPermission")
    }
}

dependencies {
    api(project(":core:persistence"))

    testImplementation(libs.junit)
}
