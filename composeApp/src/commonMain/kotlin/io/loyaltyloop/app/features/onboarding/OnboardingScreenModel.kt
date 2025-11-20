package io.loyaltyloop.app.features.onboarding

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.ValidationUtils
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.err_field_required
import loyaltyloop.composeapp.generated.resources.err_invalid_email
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown

class OnboardingScreenModel(
    private val repository: AuthRepository
) : ScreenModel {

    data class State(
        val firstName: String = "",
        val isLoading: Boolean = false,
        val firstNameError: UiText? = null,
    )

    sealed interface Event {
        data object NavigateToHome : Event

        data object NavigateToRoleSelection : Event
        data class ShowError(val message: UiText) : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onFirstNameChanged(value: String) { _state.value = _state.value.copy(firstName = value, firstNameError = null) }

    fun onSaveClicked() {
        val currentState = _state.value

        // Валидация Имени (через Ресурс)
        if (currentState.firstName.isBlank()) {
            _state.value = _state.value.copy(
                firstNameError = UiText.Resource(Res.string.err_field_required)
            )
            return
        }

        screenModelScope.launch {
            _state.value = currentState.copy(isLoading = true, firstNameError = null)
            
            log.write("Updating profile...")
            
            val result = repository.updateProfile(
                firstName = currentState.firstName,
            )

            result.onSuccess {
                log.write("Profile updated!")
                _events.send(Event.NavigateToRoleSelection)
            }.onFailure { error ->
                log.write("Update failed", LogType.Error, error)
                val errorText = when(error) {
                    is ClientException -> UiText.DynamicString(error.errorMessage)
                    is NetworkException -> UiText.Resource(Res.string.error_network)
                    is ServerException -> UiText.Resource(Res.string.error_server)
                    else -> UiText.Resource(Res.string.error_unknown)
                }
                _events.send(Event.ShowError(errorText))
                _state.value = currentState.copy(isLoading = false)
            }
        }
    }
}