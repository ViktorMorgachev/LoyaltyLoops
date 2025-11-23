package io.loyaltyloop.app.features.terminal.confirmation

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.TransactionResultMapper
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.toResource
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.NetworkResult
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.shared.models.onError
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.error_network

class TransactionConfirmationScreenModel(
    private val calculation: TransactionCalculationDto,
    private val tradingPointId: String,
    private val cardId: String,
    private val strategy: TransactionStrategy,
    private val repository: PartnerRepository
) : ScreenModel {

    data class State(
        val isLoading: Boolean = false
    )

    sealed interface Event {
        data object NavigateBack : Event
        data object NavigateToScan : Event // Вернуться на сканирование после успеха
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onConfirm() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }


            repository.processTransaction(
                tradingPointId = tradingPointId,
                cardId = cardId,
                amount = calculation.purchaseAmount,
                strategy = strategy
            )
                .onSuccess { result ->
                    _events.send(Event.ShowMessage(TransactionResultMapper.getMessage(result), SnackbarType.Success))
                    delay(1000)
                    _events.send(Event.NavigateToScan)
                }
                .onFailure { exception ->
                    log.write("Transaction failed", LogType.Error, exception)
                    _events.send(Event.ShowMessage(UiText.Resource(Res.string.error_network), SnackbarType.Error))
                    _state.update { it.copy(isLoading = false) }
                }
                .onError { code, _ ->
                    _events.send(Event.ShowMessage(UiText.Resource(code.toResource()), SnackbarType.Error))
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    fun onBack() {
        _events.trySend(Event.NavigateBack)
    }
}
