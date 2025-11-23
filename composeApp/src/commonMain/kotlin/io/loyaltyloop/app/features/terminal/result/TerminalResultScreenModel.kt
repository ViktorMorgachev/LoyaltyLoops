package io.loyaltyloop.app.features.terminal.result

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.ScanQrResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown
import loyaltyloop.composeapp.generated.resources.term_msg_success

class TerminalResultScreenModel(
    private val scanData: ScanQrResponse,
    private val repository: PartnerRepository
) : ScreenModel {

    data class State(
        val data: ScanQrResponse,
        val purchaseAmount: String = "",
        val pointsToAward: Double = 0.0,
        val isLoading: Boolean = false
    )

    sealed interface Action {
        data class OnAmountChanged(val value: String) : Action
        data object OnProcessClicked : Action
        data object OnBackClicked : Action
    }

    sealed interface Event {
        data object NavigateBack : Event // Вернуться к сканеру
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    private val _state = MutableStateFlow(State(data = scanData))
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            is Action.OnAmountChanged -> calculatePoints(action.value)
            is Action.OnProcessClicked -> processTransaction()
            is Action.OnBackClicked -> _events.trySend(Event.NavigateBack)
        }
    }

    private fun calculatePoints(amount: String) {
        // Разрешаем только цифры
        if (amount.all { it.isDigit() }) {
            val value = amount.toDoubleOrNull() ?: 0.0
            val points = value * (_state.value.data.cashbackPercent ?: 0.0)

            _state.value = _state.value.copy(
                purchaseAmount = amount,
                pointsToAward = points
            )
        }
    }

    private fun processTransaction() {
        val currentState = _state.value

        // Определяем, что слать: сумму или null (если визиты)
        val amountToSend = if (currentState.data.programType == LoyaltyProgramType.TIERED_LTV) {
            currentState.purchaseAmount.toDoubleOrNull()
        } else {
            null
        }

        screenModelScope.launch {
            _state.value = currentState.copy(isLoading = true)
            log.write("Processing transaction for card ${currentState.data.cardId}")

            repository.processTransaction(
                cardId = currentState.data.cardId,
                amount = amountToSend
            ).onSuccess { msg ->
                log.write("Transaction success: $msg")

                // Показываем успех и уходим
                _events.send(Event.ShowMessage(UiText.Resource(Res.string.term_msg_success), SnackbarType.Success))
                _events.send(Event.NavigateBack)

            }.onFailure { error ->
                log.write("Transaction failed", LogType.Error, error)

                val errorText = when(error) {
                    is ClientException -> UiText.DynamicString(error.errorMessage)
                    is NetworkException -> UiText.Resource(Res.string.error_network)
                    is ServerException -> UiText.Resource(Res.string.error_server)
                    else -> UiText.Resource(Res.string.error_unknown)
                }

                _events.send(Event.ShowMessage(errorText, SnackbarType.Error))
                _state.value = currentState.copy(isLoading = false)
            }
        }
    }
}