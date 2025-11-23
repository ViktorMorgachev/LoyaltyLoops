package io.loyaltyloop.app.features.splash

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.NetworkResult
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
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown

class SplashScreenModel(
    private val repository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager,
) : ScreenModel {

    // --- КОНТРАКТ ---
    data class State(
        val isLoading: Boolean = true,
        val error: UiText? = null // Если ошибка есть - показываем кнопку Retry
    )

    sealed interface Action {
        data object OnRetryClicked : Action
    }

    sealed interface Event {
        data object NavigateToLogin : Event
        data object NavigateToHome : Event
        data object NavigateToOnboarding : Event
    }
    // ----------------

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    init {
        checkSession()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnRetryClicked -> checkSession()
        }
    }

    private fun checkSession() {
        screenModelScope.launch {
            _state.value = State(isLoading = true, error = null)

            // Минимальная задержка, чтобы логотип не мигал
            delay(1000)

            val hasToken = tokenStorage.getAccessToken() != null
            log.write("Check Session: Token present? $hasToken", LogType.Debug)

            if (!hasToken) {
                log.write("No token found -> Navigate to Login")
                _events.send(Event.NavigateToLogin)
                return@launch
            }

            repository.getProfile()
                .onSuccess { profile ->
                    sessionManager.updateWorkspaces(profile.workspaces)

                    if (profile.firstName.isNullOrBlank()) {
                        log.write("Profile incomplete -> Go to Onboarding")
                        _events.send(Event.NavigateToOnboarding)
                    } else {
                        log.write("Session valid -> Go to Home")
                        _events.send(Event.NavigateToHome)
                    }
                }
                .onFailure { exception ->
                    log.write("Profile check failed", LogType.Error, exception)
                    _state.value = State(isLoading = false, error = UiText.Resource(Res.string.error_network))
                }
                .onError { code, _ ->
                    log.write("Profile check failed: $code", LogType.Error)
                    _state.value = State(isLoading = false, error = UiText.Resource(Res.string.error_server))
                }
        }
    }
}
