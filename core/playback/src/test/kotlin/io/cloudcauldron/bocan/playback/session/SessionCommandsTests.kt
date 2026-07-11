package io.cloudcauldron.bocan.playback.session

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SessionCommandsTests {
    @Test
    fun `every command is namespaced and distinct`() {
        assertTrue(SessionCommands.ALL.all { it.startsWith(SessionCommands.NAMESPACE) })
        assertEquals(SessionCommands.ALL.size, SessionCommands.ALL.toSet().size)
    }

    @Test
    fun `all builds one session command per action, preserving the action string`() {
        val commands = SessionCommands.all()
        assertEquals(SessionCommands.ALL.size, commands.size)
        assertEquals(SessionCommands.ALL, commands.map { it.customAction })
    }
}
