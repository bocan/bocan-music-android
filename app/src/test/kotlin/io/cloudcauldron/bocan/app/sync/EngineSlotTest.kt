package io.cloudcauldron.bocan.app.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class EngineSlotTest {
    /** A stand-in engine: identity is all the slot cares about. */
    private class Engine(val key: String)

    @Test
    fun `callers racing on one key share a single build`() = runTest {
        // The regression: pairing's forced sync and discovery's sync both found no engine
        // and built one each, so two runs downloaded the same artwork beside each other.
        var builds = 0
        val slot = EngineSlot<Engine>()

        val engines = List(CALLERS) {
            async {
                slot.obtain("fp") {
                    builds++
                    // Hold the build open so an unguarded check-then-build would interleave.
                    delay(BUILD_MILLIS)
                    Engine("fp")
                }
            }
        }.awaitAll()

        assertEquals(1, builds)
        assertTrue(engines.all { it === engines.first() })
    }

    @Test
    fun `the same key reuses the held instance`() = runTest {
        val slot = EngineSlot<Engine>()
        val first = slot.obtain("fp") { Engine("fp") }

        val second = slot.obtain("fp") { error("must not rebuild for the same key") }

        assertSame(first, second)
        assertSame(first, slot.current)
    }

    @Test
    fun `a new key builds afresh and hands the old instance to onReplaced`() = runTest {
        val replaced = mutableListOf<Engine>()
        val slot = EngineSlot<Engine>(onReplaced = replaced::add)
        val old = slot.obtain("old") { Engine("old") }

        val fresh = slot.obtain("new") { Engine("new") }

        assertNotSame(old, fresh)
        assertEquals(listOf(old), replaced)
        assertSame(fresh, slot.current)
    }

    @Test
    fun `clear empties the slot without calling onReplaced`() = runTest {
        val replaced = mutableListOf<Engine>()
        val slot = EngineSlot<Engine>(onReplaced = replaced::add)
        val old = slot.obtain("fp") { Engine("fp") }

        slot.clear()

        assertNull(slot.current)
        val rebuilt = slot.obtain("fp") { Engine("fp") }
        assertNotSame(old, rebuilt)
        assertTrue(replaced.isEmpty())
    }

    private companion object {
        const val CALLERS = 8
        const val BUILD_MILLIS = 1_000L
    }
}
