import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Last.fm API key and shared secret come from local.properties (never committed) or, on
// CI, from environment variables of the same name. A missing value leaves them empty,
// which hides the Last.fm provider rather than crashing. ListenBrainz and Rocksky are
// per-user token providers and need no app-level key, so Last.fm is the only build-time
// secret the player carries.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// local.properties wins locally; the environment fills in on CI. Returns "" when unset so
// buildConfigField never emits a null and the provider hides itself rather than crashing.
fun secret(name: String): String = localProperties.getProperty(name) ?: System.getenv(name) ?: ""

// Release signing. Local builds read keystore.properties (git-ignored); CI decodes the
// keystore from a secret and passes the same field names through the environment. An
// absent keystore path leaves the release build unsigned, which is exactly what the
// PR-time assembleRelease check and local R8 smoke builds want.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun signing(name: String): String? = keystoreProperties.getProperty(name) ?: System.getenv(name)?.takeIf { it.isNotEmpty() }

val releaseKeystoreFile = signing("BOCAN_KEYSTORE_FILE")

// release-please maintains the version string on the line below (see
// release-please-config.json); versionCode is derived from it monotonically by
// versionCodeOf (buildSrc), so a 2.0.0 release always outranks a 1.99.x hotfix.
val appVersionName = "0.3.0" // x-release-please-version

android {
    namespace = "io.cloudcauldron.bocan.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.cloudcauldron.bocan.android"
        minSdk {
            version = release(29)
        }
        targetSdk {
            version = release(37)
        }
        versionName = appVersionName
        versionCode = versionCodeOf(appVersionName)

        buildConfigField("String", "LASTFM_API_KEY", "\"${secret("BOCAN_LASTFM_API_KEY")}\"")
        buildConfigField("String", "LASTFM_SHARED_SECRET", "\"${secret("BOCAN_LASTFM_SHARED_SECRET")}\"")
    }

    signingConfigs {
        create("release") {
            // Populated only when a keystore is configured; otherwise the release build is
            // left unsigned so PR R8 checks and local smoke builds still succeed.
            releaseKeystoreFile?.let { path ->
                storeFile = file(path)
                storePassword = signing("BOCAN_KEYSTORE_PASSWORD")
                keyAlias = signing("BOCAN_KEY_ALIAS")
                keyPassword = signing("BOCAN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseKeystoreFile != null) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
        // The Auto MediaBrowserService is the Media3 MediaLibraryService declared in
        // :core:playback; it is present in the merged manifest with both the
        // MediaLibraryService and MediaBrowserService actions. This check does not see the
        // library-declared service, so it misfires here.
        disable += "MissingMediaBrowserServiceIntentFilter"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:observability"))
    implementation(project(":core:persistence"))
    implementation(project(":core:sync"))
    implementation(project(":core:playback"))
    implementation(project(":core:scrobble"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    // Host SQLite natives for the in-memory database a view model test wires up.
    testImplementation(libs.sqlite.bundled.jvm)
}
