package io.cloudcauldron.bocan.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import io.cloudcauldron.bocan.sync.engine.SyncState
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncStatusViewModelTest {
    private val syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    private val server = MutableStateFlow<SyncServerEntity?>(null)
    private val counts = MutableStateFlow(DownloadCounts(0, 0, 0))
    private val autoSync = MutableStateFlow(true)
    private val chargingOnly = MutableStateFlow(false)
    private var syncNowCalls = 0
    private var cancelCalls = 0
    private val autoSets = mutableListOf<Boolean>()
    private val chargingSets = mutableListOf<Boolean>()

    private fun viewModel() = SyncStatusViewModel(
        sources = SyncStatusViewModel.Sources(
            syncState = syncState,
            server = server,
            counts = counts,
            autoSync = autoSync,
            chargingOnly = chargingOnly,
            storageBytes = { 2_048L }
        ),
        actions = SyncStatusViewModel.Actions(
            syncNow = { syncNowCalls++ },
            cancel = { cancelCalls++ },
            setAutoSync = { autoSets.add(it) },
            setChargingOnly = { chargingSets.add(it) }
        ),
        dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = UnconfinedTestDispatcher())
    )

    @Test
    fun `state folds server counts storage and settings`() = runTest {
        server.value = SyncServerEntity(
            serverId = "s1",
            serverName = "Chris's Mac",
            certFingerprint = "ab".repeat(32),
            certDer = byteArrayOf(1),
            lastAppliedGeneration = 42,
            lastSyncAt = Instant.parse("2026-07-10T12:00:00Z"),
            pairedAt = Instant.EPOCH
        )
        counts.value = DownloadCounts(pending = 3, downloaded = 10, failed = 1)
        syncState.value = SyncState.Transferring(2, 5, 100, 500, "Track")
        val vm = viewModel()

        vm.state.test {
            var state = awaitItem()
            while (!state.paired || state.storageBytes == 0L) {
                state = awaitItem()
            }
            assertTrue(state.paired)
            assertEquals("Chris's Mac", state.serverName)
            assertEquals(42L, state.generation)
            assertEquals(DownloadCounts(3, 10, 1), state.counts)
            assertEquals(2_048L, state.storageBytes)
            assertTrue(state.sync is SyncState.Transferring)
            assertTrue(state.autoSyncEnabled)
            assertFalse(state.chargingOnly)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }

    @Test
    fun `events delegate to the injected actions`() = runTest {
        val vm = viewModel()

        vm.syncNow()
        vm.cancel()
        vm.setAutoSync(false)
        vm.setChargingOnly(true)

        assertEquals(1, syncNowCalls)
        assertEquals(1, cancelCalls)
        assertEquals(listOf(false), autoSets)
        assertEquals(listOf(true), chargingSets)
        vm.dispose()
    }
}
