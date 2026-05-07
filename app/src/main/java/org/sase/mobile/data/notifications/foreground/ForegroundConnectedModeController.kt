package org.sase.mobile.data.notifications.foreground

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface ForegroundConnectedServiceCommands {
    fun start()
    fun stop()
}

class ForegroundConnectedModeController(
    private val store: ForegroundConnectedModeStore,
    private val commands: ForegroundConnectedServiceCommands,
    scope: CoroutineScope,
) {
    private val controllerScope = scope
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    init {
        controllerScope.launch {
            store.enabled.collect { enabled ->
                mutableEnabled.value = enabled
            }
        }
    }

    fun startUntilStopped() {
        mutableEnabled.value = true
        commands.start()
        controllerScope.launch {
            store.setEnabled(true)
        }
    }

    fun stop() {
        mutableEnabled.value = false
        commands.stop()
        controllerScope.launch {
            store.setEnabled(false)
        }
    }

    fun stopAfterHostUnavailable() {
        stop()
    }
}

data class ForegroundConnectedModeUiState(
    val enabled: Boolean,
    val canStart: Boolean,
    val hostLabel: String?,
    val connectionLabel: String,
    val lastRefreshAt: String?,
)
