package io.loyaltyloop.app.features.join

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.toResource
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.onError
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.cashier_join_success
import loyaltyloop.composeapp.generated.resources.error_network

class JoinCompanyScreenModel(
    private val repository: PartnerRepository
) : ScreenModel {

    // --- MVI Contract ---
    data class State(
        val code: String = "",
        val isLoading: Boolean = false,
        val error: UiText? = null
    )

    sealed interface Action {
        data class OnCodeChanged(val value: String) : Action
        data object OnSubmitClicked : Action
        data object OnBackClicked : Action
    }

    sealed interface Event {
        data object NavigateBack : Event
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }
    // --------------------

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            is Action.OnCodeChanged -> {
                // Можно добавить uppercase, так как коды обычно капсом
                _state.value = _state.value.copy(
                    code = action.value.uppercase(),
                    error = null
                )
            }
            is Action.OnBackClicked -> {
                _events.trySend(Event.NavigateBack)
            }
            is Action.OnSubmitClicked -> {
                joinCompany()
            }
        }
    }

    private fun joinCompany() {
        val code = _state.value.code
        if (code.isBlank()) return

        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            log.write("Joining company with invite code: $code")

            repository.joinCompany(code)
                .onSuccess { result ->
                    log.write("Successfully joined: ${result.message}")
                    _events.send(Event.ShowMessage(
                        UiText.Resource(Res.string.cashier_join_success),
                        SnackbarType.Success
                    ))
                    delay(500)
                    _events.send(Event.NavigateBack)
                }
                .onFailure { exception ->
                    log.write("Join failed", LogType.Error, exception)
                    _events.send(Event.ShowMessage(
                        message = UiText.Resource(Res.string.error_network),
                        type = SnackbarType.Error
                    ))
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = null
                    )
                }
                .onError { appCode, msg ->
                    log.write("Join failed: $appCode", LogType.Error)
                    _events.send(Event.ShowMessage(
                        message = UiText.Resource(appCode.toResource(msg)),
                        type = SnackbarType.Error
                    ))
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = null
                    )
                }
        }
    }
}
