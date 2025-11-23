package io.loyaltyloop.app.features.onboarding

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.err_field_required
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown
import loyaltyloop.composeapp.generated.resources.onboarding_success

class OnboardingScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager // Чтобы обновить сессию после сохранения имени
) : ScreenModel {

    // --- КОНТРАКТ ---
    data class State(
        val firstName: String = "",
        val isLoading: Boolean = false,
        val firstNameError: UiText? = null
    )

    sealed interface Action {
        data class OnFirstNameChanged(val value: String) : Action
        data object OnSubmitClicked : Action
    }

    sealed interface Event {
        data object NavigateToHome : Event
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }
    // ----------------

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            is Action.OnFirstNameChanged -> {
                _state.value = _state.value.copy(
                    firstName = action.value,
                    firstNameError = null
                )
            }
            is Action.OnSubmitClicked -> saveData()
        }
    }

    private fun saveData() {
        val currentState = _state.value

        // Валидация
        if (currentState.firstName.isBlank()) {
            _state.value = _state.value.copy(
                firstNameError = UiText.Resource(Res.string.err_field_required)
            )
            return
        }

        screenModelScope.launch {
            _state.value = currentState.copy(isLoading = true)
            log.write("Updating profile: ${currentState.firstName}")

            repository.updateProfile(firstName = currentState.firstName)
                .onSuccess {
                    log.write("Profile updated successfully")

                    repository.getProfile().onSuccess { profile ->
                        sessionManager.updateWorkspaces(profile.workspaces)
                    }

                    _events.send(Event.ShowMessage(
                        UiText.Resource(Res.string.onboarding_success),
                        SnackbarType.Success
                    ))

                    // Переходим домой
                    _events.send(Event.NavigateToHome)
                }
                .onFailure { error ->
                    log.write("Update failed", LogType.Error, error)

                    val errorText = when (error) {
                        is ClientException -> UiText.DynamicString(error.errorMessage)
                        is NetworkException -> UiText.Resource(Res.string.error_network)
                        is ServerException -> UiText.Resource(Res.string.error_server)
                        else -> UiText.Resource(Res.string.error_unknown)
                    }

                    // Показываем красный снекбар
                    _events.send(Event.ShowMessage(errorText, SnackbarType.Error))

                    _state.value = currentState.copy(isLoading = false)
                }
        }
    }
}