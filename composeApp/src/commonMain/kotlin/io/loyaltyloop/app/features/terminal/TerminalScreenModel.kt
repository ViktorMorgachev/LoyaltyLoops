package io.loyaltyloop.app.features.terminal

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.utils.*
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TerminalScreenModel(
    private val repository: PartnerRepository,
    private val sessionManager: SessionManager
) : ScreenModel {

    data class State(
        val isLoading: Boolean = false,
        // Поле для ручного ввода (для тестов)
        val manualInput: String = "",
        val error: UiText? = null
    )

    sealed interface Event {
        data class ShowError(val message: String) : Event
        data class NavigateToResult(val scanData: ScanQrResponse) : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onManualInputChanged(value: String) {
        _state.value = _state.value.copy(manualInput = value)
    }

    // Вызывается при нажатии "Отправить" или при сканировании камерой
    fun onScan(qrContent: String) {
        val currentWorkspace = sessionManager.currentWorkspace.value

        if (currentWorkspace == null) {
            log.write("No workspace selected!", LogType.Error)
            return
        }

        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val request = ScanQrRequest(
                qrContent = qrContent,
                tradingPointId = currentWorkspace.id // <-- ID Точки, где мы кассир
            )

            repository.scanQr(request)
                .onSuccess { response ->
                    _events.send(Event.NavigateToResult(response))
                }
                .onFailure { error ->
                    log.write("Scan failed", LogType.Error, error)
                    _state.value = _state.value.copy(
                        error = UiText.DynamicString(error.message ?: "Ошибка")
                    )
                }

            _state.value = _state.value.copy(isLoading = false)
        }
    }
}