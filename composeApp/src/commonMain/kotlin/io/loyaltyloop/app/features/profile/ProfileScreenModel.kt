package io.loyaltyloop.app.features.profile

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.UserWorkspace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown
import loyaltyloop.composeapp.generated.resources.profile_error_load
import loyaltyloop.composeapp.generated.resources.profile_loading

class ProfileScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    private val tokenStorage: TokenStorage
) : ScreenModel {

    data class State(
        val name: UiText = UiText.Resource(Res.string.profile_loading),
        val phone: String = "",
        val workspaces: List<UserWorkspace> = emptyList(),
        val isLoading: Boolean = false
    )

    sealed interface Action {
        data object OnRefresh : Action
        data object OnLogoutClicked : Action
        data class OnWorkspaceClicked(val workspace: UserWorkspace) : Action
        data object OnJoinTeamClicked : Action
        data object OnCreateBusinessClicked : Action
        data object OnLanguageClicked : Action
        data object OnSupportClicked : Action
    }

    sealed interface Event {
        data object NavigateToSplash : Event
        data object NavigateToJoinCompany : Event
        // Добавляем событие показа сообщения
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    init {
        loadProfile()
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnRefresh -> loadProfile()
            is Action.OnLogoutClicked -> logout()

            is Action.OnWorkspaceClicked -> {
                log.write("Switching to workspace: ${action.workspace.title}")
                sessionManager.switchWorkspace(action.workspace)
            }

            is Action.OnJoinTeamClicked -> {
                _events.trySend(Event.NavigateToJoinCompany)
            }

            is Action.OnCreateBusinessClicked -> {
                log.write("Create Business clicked")
                // TODO: Показать тост "Используйте веб-версию"
            }

            is Action.OnLanguageClicked -> log.write("Click: Change Language")
            is Action.OnSupportClicked -> log.write("Click: Support")
        }
    }

    private fun loadProfile() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            repository.getProfile()
                .onSuccess { profile ->
                    _state.value = _state.value.copy(
                        name = UiText.DynamicString("${profile.firstName ?: ""} ${profile.lastName ?: ""}".trim()),
                        phone = profile.phone,
                        workspaces = profile.workspaces,
                        isLoading = false
                    )
                    sessionManager.updateWorkspaces(profile.workspaces)
                }
                .onFailure { error ->
                    log.write("Failed to load profile", LogType.Error, error)

                    val errorText = when (error) {
                        is ClientException -> UiText.DynamicString(error.errorMessage)
                        is NetworkException -> UiText.Resource(Res.string.error_network)
                        is ServerException -> UiText.Resource(Res.string.error_server)
                        else -> UiText.Resource(Res.string.error_unknown)
                    }

                    // Шлем ошибку в UI
                    _events.send(Event.ShowMessage(errorText, SnackbarType.Error))

                    _state.value = _state.value.copy(
                        isLoading = false,
                        name = UiText.Resource(Res.string.profile_error_load)
                    )
                }
        }
    }

    private fun logout() {
        screenModelScope.launch {
            tokenStorage.clear()
            sessionManager.logout()
            _events.send(Event.NavigateToSplash)
        }
    }
}