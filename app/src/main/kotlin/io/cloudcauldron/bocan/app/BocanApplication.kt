package io.cloudcauldron.bocan.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.work.Configuration
import io.cloudcauldron.bocan.observability.ReleaseLogTree
import io.cloudcauldron.bocan.playback.PlaybackComponents
import io.cloudcauldron.bocan.playback.PlaybackHost
import io.cloudcauldron.bocan.sync.SyncHost
import io.cloudcauldron.bocan.sync.engine.SyncState
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * The Application also implements [SyncHost] so the sync foreground service and
 * periodic worker (in :core:sync) can reach the app's sync graph without an
 * upward module dependency. Every call delegates to the single [SyncCoordinator].
 *
 * It is a [Configuration.Provider] so WorkManager initializes on demand (the
 * default startup initializer is removed in the manifest), which keeps the
 * periodic sync worker configurable and testable.
 */
@OptIn(UnstableApi::class)
class BocanApplication :
    Application(),
    SyncHost,
    PlaybackHost,
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        plantLogging()
        appGraph = AppGraph(this)
        appGraph.syncCoordinator.start()
        appGraph.appLog.info("app.start", mapOf("debug" to BuildConfig.DEBUG))
    }

    override val playbackComponents: PlaybackComponents get() = appGraph.playbackComponents

    override val syncState: StateFlow<SyncState> get() = appGraph.syncCoordinator.syncState

    override fun launchSync(force: Boolean) = appGraph.syncCoordinator.launchSync(force)

    override fun cancelSync() = appGraph.syncCoordinator.cancelSync()

    override suspend fun runScheduledSync() = appGraph.syncCoordinator.runScheduledSync()

    private fun plantLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseLogTree())
        }
    }
}
