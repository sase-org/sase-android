package org.sase.mobile.data.notifications.foreground

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ForegroundConnectedModeControllerTest {
    @Test
    fun startUntilStoppedPersistsEnabledAndStartsService() = runTest {
        val store = InMemoryForegroundConnectedModeStore()
        val commands = RecordingServiceCommands()
        val controller = ForegroundConnectedModeController(store, commands, backgroundScope)

        controller.startUntilStopped()

        assertThat(controller.enabled.value).isTrue()
        assertThat(commands.commands).containsExactly("start")
    }

    @Test
    fun stopPersistsDisabledAndStopsService() = runTest {
        val store = InMemoryForegroundConnectedModeStore(initialEnabled = true)
        val commands = RecordingServiceCommands()
        val controller = ForegroundConnectedModeController(store, commands, backgroundScope)

        controller.stop()

        assertThat(controller.enabled.value).isFalse()
        assertThat(commands.commands).containsExactly("stop")
    }

    @Test
    fun stopAfterHostUnavailableUsesSameStopPath() = runTest {
        val store = InMemoryForegroundConnectedModeStore(initialEnabled = true)
        val commands = RecordingServiceCommands()
        val controller = ForegroundConnectedModeController(store, commands, backgroundScope)

        controller.stopAfterHostUnavailable()

        assertThat(controller.enabled.value).isFalse()
        assertThat(commands.commands).containsExactly("stop")
    }
}

private class RecordingServiceCommands : ForegroundConnectedServiceCommands {
    val commands = mutableListOf<String>()

    override fun start() {
        commands += "start"
    }

    override fun stop() {
        commands += "stop"
    }
}
