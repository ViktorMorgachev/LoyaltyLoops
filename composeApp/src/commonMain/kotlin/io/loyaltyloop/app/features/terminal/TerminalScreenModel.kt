package io.loyaltyloop.app.features.terminal

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.toResource
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.NetworkResult
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.shared.models.onError
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.*

class TerminalScreenModel(
    private val repository: PartnerRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    data class State(
        val isLoading: Boolean = false,
        val manualInput: String = "",
        val showHybridDialog: Boolean = false,
        val pendingScanData: ScanQrResponse? = null
    )

    sealed interface Action {
        data class OnManualInputChanged(val value: String) : Action
        data object OnScanClicked : Action
        data class OnQrScanned(val content: String) : Action
        data class OnHybridStrategySelected(val strategy: TransactionStrategy) : Action
        data object OnStatsClicked : Action
    }

    sealed interface Event {
        data class NavigateToResult(val scanData: ScanQrResponse, val tradingPointId: String, val strategy: TransactionStrategy) : Event
        data object NavigateToStats : Event
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            is Action.OnManualInputChanged -> {
                _state.value = _state.value.copy(manualInput = action.value)
            }
            is Action.OnScanClicked -> {
                processScan(_state.value.manualInput)
            }
            is Action.OnQrScanned -> {
                processScan(action.content)
            }
            is Action.OnHybridStrategySelected -> {
                val data = _state.value.pendingScanData ?: return
                val workspaceId = sessionManager.currentWorkspace.value?.id ?: return
                _state.value = _state.value.copy(showHybridDialog = false, pendingScanData = null)
                _events.trySend(Event.NavigateToResult(data, workspaceId, action.strategy))
            }
            is Action.OnStatsClicked -> {
                 _events.trySend(Event.NavigateToStats)
            }
        }
    }

    private fun processScan(qrContent: String) {
        if (qrContent.isBlank()) return

        val currentWorkspace = sessionManager.currentWorkspace.value
        if (currentWorkspace == null) {
            log.write("No workspace selected!", LogType.Error)
            _events.trySend(Event.ShowMessage(UiText.Resource(Res.string.term_err_no_workspace), SnackbarType.Error))
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            log.write("Scanning QR in point: ${currentWorkspace.id}")

            val request = ScanQrRequest(
                qrContent = qrContent,
                tradingPointId = currentWorkspace.id
            )

            repository.scanQr(request)
                .onSuccess { response ->
                    log.write("Scan success: $response")

                    if (response.programType == LoyaltyProgramType.HYBRID) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            showHybridDialog = true,
                            pendingScanData = response
                        )
                    } else {
                        val strategy = if (response.programType == LoyaltyProgramType.VISIT_COUNTER) {
                            TransactionStrategy.VISIT
                        } else {
                            TransactionStrategy.CHARGE
                        }
                        _events.send(Event.NavigateToResult(response, currentWorkspace.id, strategy))
                        _state.value = _state.value.copy(isLoading = false)
                    }
                }
                .onFailure { exception ->
                    log.write("Scan failed", LogType.Error, exception)
                    _events.send(Event.ShowMessage(UiText.Resource(Res.string.term_err_scan_generic), SnackbarType.Error))
                    _state.value = _state.value.copy(isLoading = false)
                }
                .onError { code, msg ->
                    log.write("Scan failed: $code", LogType.Error)
                    _events.send(Event.ShowMessage(UiText.Resource(code.toResource(msg)), SnackbarType.Error))
                    _state.value = _state.value.copy(isLoading = false)
                }
        }
    }
}
