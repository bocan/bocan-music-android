package io.cloudcauldron.bocan.app

import android.app.Application
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory

/**
 * The single manual dependency-injection wiring point. Later phases extend
 * this class with their object graphs (database, sync engine, player, ...):
 * every collaborator is constructed here and handed down via constructors.
 */
class AppGraph(val application: Application) {
    val appLog: AppLog = AppLog.forCategory(LogCategory.App)
}
