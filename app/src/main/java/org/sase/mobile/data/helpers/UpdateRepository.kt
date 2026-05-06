package org.sase.mobile.data.helpers

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayApiResult
import org.sase.mobile.data.api.dto.ApiErrorCodeWire
import org.sase.mobile.data.api.dto.HelpersChangedEventPayloadWire
import org.sase.mobile.data.api.dto.MobileHelperResultWire
import org.sase.mobile.data.api.dto.MobileUpdateJobStatusWire
import org.sase.mobile.data.api.dto.MobileUpdateJobWire
import org.sase.mobile.data.api.dto.MobileUpdateStartRequestWire
import org.sase.mobile.data.session.HostSessionStorage
import org.sase.mobile.data.session.TokenVault

interface UpdateController {
    val state: StateFlow<UpdateUiState>
    suspend fun loadCachedState()
    suspend fun refreshCurrentJob()
    fun startUpdateAndPoll()
    fun cancelPolling()
    suspend fun handleHelpersChanged(payload: HelpersChangedEventPayloadWire)
}

class UpdateRepository(
    private val sessionStorage: HostSessionStorage,
    private val tokenVault: TokenVault,
    private val cache: UpdateJobCache,
    private val clientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewayApiClient,
    private val scope: CoroutineScope,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val pollIntervalMillis: Long = 2_000,
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) : UpdateController {
    private val mutableState = MutableStateFlow(UpdateUiState())
    override val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var pollJob: Job? = null

    override suspend fun loadCachedState() {
        val job = cache.read()
        mutableState.value = mutableState.value.copy(
            job = job,
            helperResult = null,
            status = if (job?.status?.isTerminal() == false) {
                UpdateStatus.Polling
            } else {
                UpdateStatus.Idle
            },
            error = null,
        )
        if (job?.status?.isTerminal() == false) {
            val shouldContinue = refreshJob(job.jobId)
            val refreshedJob = mutableState.value.job
            if (shouldContinue && refreshedJob?.status?.isTerminal() == false) {
                startPolling(job.jobId)
            } else {
                mutableState.value = mutableState.value.copy(status = UpdateStatus.Idle)
            }
        }
    }

    override fun startUpdateAndPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            mutableState.value = mutableState.value.copy(status = UpdateStatus.Starting, error = null)
            withClient(
                onMissingSession = {
                    mutableState.value = mutableState.value.copy(
                        status = UpdateStatus.Idle,
                        error = UpdateError.NotPaired,
                    )
                },
            ) { client, deviceId ->
                when (
                    val result = client.startUpdate(
                        MobileUpdateStartRequestWire(
                            requestId = requestIdFactory(),
                            deviceId = deviceId,
                        ),
                    )
                ) {
                    is GatewayApiResult.Success -> {
                        rememberJob(result.value.job, result.value.result)
                        if (result.value.job.status.isTerminal()) {
                            mutableState.value = mutableState.value.copy(status = UpdateStatus.Idle)
                        } else {
                            pollUntilTerminal(result.value.job.jobId)
                        }
                    }

                    is GatewayApiResult.Failure -> {
                        mutableState.value = mutableState.value.copy(
                            status = UpdateStatus.Idle,
                            error = result.error.toUpdateError(),
                        )
                    }
                }
            }
        }
    }

    override suspend fun refreshCurrentJob() {
        val jobId = mutableState.value.job?.jobId ?: cache.read()?.jobId
        if (jobId == null) {
            mutableState.value = mutableState.value.copy(error = UpdateError.JobNotFound)
            return
        }
        refreshJob(jobId)
    }

    override fun cancelPolling() {
        pollJob?.cancel()
        pollJob = null
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Idle)
    }

    override suspend fun handleHelpersChanged(payload: HelpersChangedEventPayloadWire) {
        val currentJobId = mutableState.value.job?.jobId ?: cache.read()?.jobId
        val eventJobId = payload.jobId ?: return
        if (payload.helper == "update" && eventJobId == currentJobId) {
            refreshJob(eventJobId)
        }
    }

    private fun startPolling(jobId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            mutableState.value = mutableState.value.copy(status = UpdateStatus.Polling, error = null)
            pollUntilTerminal(jobId)
        }
    }

    private suspend fun pollUntilTerminal(jobId: String) {
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Polling, error = null)
        while (true) {
            val shouldContinue = refreshJob(jobId)
            val job = mutableState.value.job
            if (!shouldContinue || job?.status?.isTerminal() != false) {
                mutableState.value = mutableState.value.copy(status = UpdateStatus.Idle)
                return
            }
            delayProvider(pollIntervalMillis)
        }
    }

    private suspend fun refreshJob(jobId: String): Boolean {
        return withClient(onMissingSession = {
            mutableState.value = mutableState.value.copy(
                status = UpdateStatus.Idle,
                error = UpdateError.NotPaired,
            )
            false
        }) { client, _ ->
            when (val result = client.updateStatus(jobId)) {
                is GatewayApiResult.Success -> {
                    rememberJob(result.value.job, result.value.result)
                    true
                }

                is GatewayApiResult.Failure -> {
                    mutableState.value = mutableState.value.copy(
                        status = UpdateStatus.Idle,
                        error = result.error.toUpdateError(),
                    )
                    false
                }
            }
        }
    }

    private suspend fun rememberJob(job: MobileUpdateJobWire, result: MobileHelperResultWire?) {
        cache.write(job)
        mutableState.value = mutableState.value.copy(
            job = job,
            helperResult = result,
            error = null,
        )
    }

    private suspend fun <T> withClient(
        onMissingSession: suspend () -> T,
        block: suspend (GatewayApiClient, String?) -> T,
    ): T {
        val session = sessionStorage.read() ?: return onMissingSession()
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(session.baseUrl) { token }
        return block(client, session.deviceId)
    }
}

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val job: MobileUpdateJobWire? = null,
    val helperResult: MobileHelperResultWire? = null,
    val error: UpdateError? = null,
) {
    val isBusy: Boolean
        get() = status == UpdateStatus.Starting || status == UpdateStatus.Polling
}

enum class UpdateStatus {
    Idle,
    Starting,
    Polling,
}

sealed interface UpdateError {
    data object NotPaired : UpdateError
    data object AuthExpired : UpdateError
    data object AlreadyRunning : UpdateError
    data object JobNotFound : UpdateError
    data object LaunchFailed : UpdateError
    data object BridgeUnavailable : UpdateError
    data object Disconnected : UpdateError
    data class Unexpected(val message: String) : UpdateError
}

fun MobileUpdateJobStatusWire.isTerminal(): Boolean {
    return this == MobileUpdateJobStatusWire.Succeeded || this == MobileUpdateJobStatusWire.Failed
}

private fun GatewayApiError.toUpdateError(): UpdateError {
    return when (this) {
        is GatewayApiError.Http -> when {
            statusCode == 401 || apiError?.code == ApiErrorCodeWire.Unauthorized -> UpdateError.AuthExpired
            apiError?.code == ApiErrorCodeWire.UpdateAlreadyRunning -> UpdateError.AlreadyRunning
            apiError?.code == ApiErrorCodeWire.UpdateJobNotFound -> UpdateError.JobNotFound
            apiError?.code == ApiErrorCodeWire.LaunchFailed -> UpdateError.LaunchFailed
            apiError?.code == ApiErrorCodeWire.BridgeUnavailable -> UpdateError.BridgeUnavailable
            else -> UpdateError.Unexpected(apiError?.message ?: "HTTP $statusCode")
        }

        is GatewayApiError.Transport -> UpdateError.Disconnected
        is GatewayApiError.InvalidJson -> UpdateError.Unexpected(message.ifBlank { "Invalid gateway response" })
    }
}
