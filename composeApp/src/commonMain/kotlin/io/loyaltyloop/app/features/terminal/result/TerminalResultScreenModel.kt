package io.loyaltyloop.app.features.terminal.result

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
import io.loyaltyloop.shared.models.Currency
import io.loyaltyloop.shared.models.NetworkResult
import io.loyaltyloop.shared.models.ScanQrResponse
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
import loyaltyloop.composeapp.generated.resources.error_server

class TerminalResultScreenModel(
    private val scanData: ScanQrResponse,
    private val initialStrategy: TransactionStrategy,
    private val repository: PartnerRepository
) : ScreenModel {

    data class State(
        val data: ScanQrResponse,
        val strategy: TransactionStrategy,
        val purchaseAmount: String = "",
        val isSpendingPoints: Boolean = false,
        val isLoading: Boolean = false
    )

    sealed interface Action {
        data class OnAmountChanged(val value: String) : Action
        data class OnToggleSpend(val isEnabled: Boolean) : Action
        data object OnNextClicked : Action
        data object OnBackClicked : Action
    }

    sealed interface Event {
        data object NavigateBack : Event
        data class NavigateToConfirmation(
            val calculation: TransactionCalculationDto,
            val cardId: String,
            val userId: String,
            val strategy: TransactionStrategy,
        ) : Event
        data class NavigateToRating(val userId: String) : Event
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    private val _state = MutableStateFlow(State(data = scanData, strategy = initialStrategy))
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            is Action.OnAmountChanged -> {
                _state.update { it.copy(purchaseAmount = action.value) }
            }
            is Action.OnToggleSpend -> {
                val newStrategy = if (action.isEnabled) TransactionStrategy.SPEND else TransactionStrategy.CHARGE
                _state.update { it.copy(isSpendingPoints = action.isEnabled, strategy = newStrategy) }
            }
            is Action.OnNextClicked -> {
                if (_state.value.strategy == TransactionStrategy.VISIT) {
                    processVisitTransaction()
                } else {
                    calculateAndNavigate()
                }
            }
            is Action.OnBackClicked -> _events.trySend(Event.NavigateBack)
        }
    }

    private fun processVisitTransaction() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            repository.processTransaction(
                cardId = scanData.cardId,
                amount = 0.0,
                strategy = TransactionStrategy.VISIT
            )
                .onSuccess { result ->
                    log.write("Visit processed successfully")
                    _events.send(Event.ShowMessage(TransactionResultMapper.getMessage(result), SnackbarType.Success))
                     delay(2000)
                    _events.send(Event.NavigateToRating(scanData.userId))
                }
                .onFailure { exception ->
                    log.write("Visit failed", LogType.Error, exception)
                    handleError(UiText.Resource(Res.string.error_network))
                }
                .onError { code, msg ->
                    handleError(UiText.Resource(code.toResource(msg)))
                }
        }
    }

    private fun calculateAndNavigate() {
        val amountStr = _state.value.purchaseAmount.replace(',', '.')
        val amount = amountStr.toDoubleOrNull()

        if (amount == null) return

        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            repository.calculateTransaction(
                cardId = scanData.cardId,
                amount = amount,
                strategy = _state.value.strategy
            )
                .onSuccess { calculation ->
                    _state.update { it.copy(isLoading = false) }
                    _events.send(
                        Event.NavigateToConfirmation(
                            calculation = calculation,
                            cardId = scanData.cardId,
                            userId = scanData.userId,
                            strategy = _state.value.strategy,
                        )
                    )
                }
                .onFailure { exception ->
                    log.write("Calc failed", LogType.Error, exception)
                    handleError(UiText.Resource(Res.string.error_network))
                }
                .onError { code, msg ->
                    handleError(UiText.Resource(code.toResource(msg)))
                }
        }
    }

    private fun handleError(errorText: UiText) {
        screenModelScope.launch {
            _events.send(Event.ShowMessage(errorText, SnackbarType.Error))
            _state.update { it.copy(isLoading = false) }
        }
    }
}
