package io.cloudcauldron.bocan.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import androidx.test.services.storage.TestStorage
import io.cloudcauldron.bocan.app.demo.DemoLibrary
import io.cloudcauldron.bocan.app.fixtures.AndroidFixtureAssets
import io.cloudcauldron.bocan.app.fixtures.FixtureLibrary
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.persistence.model.manifest.Manifest
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestCodec
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import io.cloudcauldron.bocan.sync.engine.ArtworkStore
import io.cloudcauldron.bocan.sync.engine.MediaLayout
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * The base of every end-to-end class (phase 17). Rules run outermost first:
 * notification permission granted, then the fixture seeded for [profile], then the
 * activity launched, so the app never sees a half-seeded library.
 *
 * Wiping is the Test Orchestrator's job (`clearPackageData`, see app/build.gradle.kts):
 * every test starts in a fresh process on a cleared install. The fixture rule checks that
 * and fails at once if it is not so, rather than seeding on top of an old library.
 */
abstract class E2ETest(private val profile: FixtureProfile, private val completeOnboarding: Boolean = true) {
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(*runtimePermissions()))
        .around(FixtureRule(profile, completeOnboarding))
        .around(composeRule)

    protected val app: BocanApplication get() = ApplicationProvider.getApplicationContext()
    protected val graph: AppGraph get() = app.appGraph
    protected val ui: Ui by lazy { Ui(composeRule) }

    /** The player state the transport publishes; tests wait on it, never on time. */
    protected val player: PlayerUiState get() = graph.queueController.state.value

    /** Wait until the player reaches [condition], or fail naming [what] and the last state seen. */
    protected fun awaitPlayer(what: String, timeoutMs: Long = PLAYER_TIMEOUT_MS, condition: (PlayerUiState) -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMs) { condition(player) }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("player never reached: $what within $timeoutMs ms; last state: $player", timeout)
        }
    }

    /** Wait until [condition] holds, or fail naming [what]. For state outside the semantics tree (the database). */
    protected fun awaitState(what: String, timeoutMs: Long = PLAYER_TIMEOUT_MS, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMs, condition)
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("never reached: $what within $timeoutMs ms", timeout)
        }
    }

    /**
     * Turn the app to landscape, the way rotating a phone does: the activity is recreated in the
     * new configuration (navigation rail, side-by-side Now Playing). App-local, so the next
     * test's fresh process starts in the device's own orientation again.
     */
    protected fun rotateToLandscape() {
        composeRule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        awaitState("the activity recreated in landscape", UI_TIMEOUT_MS) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
    }

    /**
     * A PNG of the whole screen for a person to look at, never a pass or fail. Written through
     * the Test Storage service, which survives the Orchestrator's data clearing; Gradle pulls
     * it to app/build/outputs/connected_android_test_additional_output/ and run.sh copies it on.
     */
    protected fun screenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("e2e/${javaClass.simpleName}-$name.png").use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        }
    }

    companion object {
        const val PLAYER_TIMEOUT_MS = 15_000L
        const val UI_TIMEOUT_MS = 10_000L
        private const val PNG_QUALITY = 100

        /** The fixture manifest as the test sees it: ids and titles come from here, never from guesses. */
        val fixture: Manifest by lazy {
            val context = ApplicationProvider.getApplicationContext<BocanApplication>()
            ManifestCodec.decode(context.assets.open("fixture/manifest.json").use { it.readBytes().decodeToString() })
        }

        fun trackMediaId(title: String): String = "track:" + fixture.tracks.single { it.title == title }.id

        fun episodeMediaId(title: String): String = "episode:" + fixture.episodes.single { it.title == title }.id

        private fun runtimePermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }
}

/**
 * Seeds the fixture before the activity launches. Profiles other than [FixtureProfile.UnpairedDemo]
 * mark the demo's one automatic seed as spent first, so the phase 14 demo can never land on top
 * of the fixture; onboarding is marked complete unless the journey wants to walk it.
 */
class FixtureRule(private val profile: FixtureProfile, private val completeOnboarding: Boolean) : ExternalResource() {
    override fun before() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<BocanApplication>()
        val graph = app.appGraph
        val syncDao = graph.database.syncDao()
        check(syncDao.server() == null && syncDao.allTracks().isEmpty()) {
            "the install is not clean: run the suite through the Test Orchestrator (make end-to-end), which clears app data per test"
        }
        if (profile != FixtureProfile.UnpairedDemo) graph.demoPreferences.setAutoSeeded()
        if (completeOnboarding) graph.onboardingPreferences.setCompleted()
        val mediaLayout = MediaLayout(app)
        val store = DemoLibrary.Store(graph.database, graph.syncApplier, mediaLayout, ArtworkStore(mediaLayout))
        FixtureLibrary(AndroidFixtureAssets(app), store, graph.dispatchers).seed(profile)
    }
}
