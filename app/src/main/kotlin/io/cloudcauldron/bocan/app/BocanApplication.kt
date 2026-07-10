package io.cloudcauldron.bocan.app

import android.app.Application
import io.cloudcauldron.bocan.observability.ReleaseLogTree
import timber.log.Timber

class BocanApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        plantLogging()
        appGraph = AppGraph(this)
        appGraph.appLog.info("app.start", mapOf("debug" to BuildConfig.DEBUG))
    }

    private fun plantLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseLogTree())
        }
    }
}
