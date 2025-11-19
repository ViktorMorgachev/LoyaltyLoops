package io.loyaltyloop.app.features.profile

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.repository.AuthRepository
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

    sealed interface Event {
        data object NavigateToSplash : Event
        data object NavigateToJoinCompany : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    init {
        loadProfile()
    }

    // --- ЕДИНАЯ ТОЧКА ВХОДА ---
    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.OnRefresh -> loadProfile()

            is ProfileAction.OnLogoutClicked -> logout()

            is ProfileAction.OnWorkspaceClicked -> {
                log.write("Switching to workspace: ${action.workspace.title}")
                sessionManager.switchWorkspace(action.workspace)
            }

            is ProfileAction.OnJoinTeamClicked -> {
                _events.trySend(Event.NavigateToJoinCompany)
            }

            is ProfileAction.OnCreateBusinessClicked -> {
                log.write("Create Business clicked (Mobile not implemented)")
                // TODO: Можно показать тост "Используйте веб-версию"
            }

            is ProfileAction.OnLanguageClicked -> {
                log.write("Click: Change Language")
            }
            is ProfileAction.OnSupportClicked -> {
                log.write("Click: Support")
            }
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