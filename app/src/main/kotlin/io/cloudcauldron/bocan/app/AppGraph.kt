package io.cloudcauldron.bocan.app

import android.app.Application
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.persistence.BocanDatabase
import io.cloudcauldron.bocan.persistence.SyncApplier
import kotlinx.coroutines.Dispatchers

/**
 * The single manual dependency-injection wiring point. Later phases extend
 * this class with their object graphs (sync engine, player, ...): every
 * collaborator is constructed here and handed down via constructors.
 */
class AppGraph(val application: Application) {
    val appLog: AppLog = AppLog.forCategory(LogCategory.App)

    /** Lazy so a cold launch does not open the database before first use. */
    val database: BocanDatabase by lazy {
        BocanDatabase.create(application, Dispatchers.IO)
    }

    val syncApplier: SyncApplier by lazy { SyncApplier(database) }
}
