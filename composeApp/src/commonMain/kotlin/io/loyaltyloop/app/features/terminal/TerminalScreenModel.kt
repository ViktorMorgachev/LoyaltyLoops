package io.loyaltyloop.app.features.terminal

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
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
        val manualInput: String = ""
    )

    sealed interface Action {
        data class OnManualInputChanged(val value: String) : Action
        data object OnScanClicked : Action
    }

    sealed interface Event {
        data class NavigateToResult(val scanData: ScanQrResponse) : Event
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
                    log.write("Scan success: ${response.userId}")
                    _events.send(Event.NavigateToResult(response))
                }
                .onFailure { error ->
                    log.write("Scan failed", LogType.Error, error)

                    val errorText = when(error) {
                        is ClientException -> UiText.DynamicString(error.errorMessage)
                        is NetworkException -> UiText.Resource(Res.string.error_network)
                        is ServerException -> UiText.Resource(Res.string.error_server)
                        else -> UiText.Resource(Res.string.term_err_scan_generic)
                    }

                    _events.send(Event.ShowMessage(errorText, SnackbarType.Error))
                }

            _state.value = _state.value.copy(isLoading = false)
        }
    }
}