package io.cloudcauldron.bocan.sync.net

import io.cloudcauldron.bocan.persistence.daos.SyncDao
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The write seam the pairing ceremony needs: persist a newly pinned server.
 * Narrow so [io.cloudcauldron.bocan.sync.pairing.PairingClient] can be tested
 * without a database.
 */
interface PairedServerStore {
    suspend fun save(server: SyncServerEntity)
}

/**
 * The persisted pinned relationship with the one paired Mac, over [SyncDao]'s
 * single-row sync_server table. The Room suspend queries run on the database's
 * own coroutine context, so these APIs are already main-safe.
 */
class TrustStore(private val syncDao: SyncDao) : PairedServerStore {
    /** True whenever a paired server row exists. */
    val isPaired: Flow<Boolean> = syncDao.observeServer().map { it != null }.distinctUntilChanged()

    /** The paired server, or null if this phone is not paired. */
    suspend fun current(): SyncServerEntity? = syncDao.server()

    /** Persist the paired server, replacing any previous pairing (v1 pairs one Mac). */
    override suspend fun save(server: SyncServerEntity) = syncDao.replaceServer(server)

    /** Forget the paired Mac. */
    suspend fun clear() = syncDao.clearServer()
}
