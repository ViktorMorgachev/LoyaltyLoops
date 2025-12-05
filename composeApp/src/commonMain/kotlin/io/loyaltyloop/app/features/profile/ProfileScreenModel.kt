package io.loyaltyloop.app.features.profile

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.config.AppConfig.WEB_URL
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.services.PushService
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.PlatformManager
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.toResource
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.config.AppConfig
import io.loyaltyloop.shared.models.UserWorkspace
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
import loyaltyloop.composeapp.generated.resources.profile_error_load
import loyaltyloop.composeapp.generated.resources.profile_language_updated
import loyaltyloop.composeapp.generated.resources.profile_loading
import loyaltyloop.composeapp.generated.resources.profile_web_error_token

class ProfileScreenModel(
    private val repository: AuthRepository,
    private val sessionManager: SessionManager,
    private val tokenStorage: TokenStorage,
    private val pushService: PushService,
    private val platformManager: PlatformManager
) : ScreenModel {

    data class State(
        val name: UiText = UiText.Resource(Res.string.profile_loading),
        val phone: String = "",
        val workspaces: List<UserWorkspace> = emptyList(),
        val isLoading: Boolean = false,
        val languageCode: String = "ru",
        val appVersion: String = AppConfig.appVersion
    )

    sealed interface Action {
        data object OnRefresh : Action
        data object OnLogoutClicked : Action
        data class OnWorkspaceClicked(val workspace: UserWorkspace) : Action
        data object OnJoinTeamClicked : Action
        data object OnCreateBusinessClicked : Action
        data object OnLanguageClicked : Action
        data class OnLanguageSelected(val code: String) : Action
        data object OnSupportClicked : Action
    }

    sealed interface Event {
        data object NavigateToSplash : Event

        data object ShowLanguageDialog : Event
        data object NavigateToJoinCompany : Event
        data object NavigateToSupport : Event

        data object ShowAboutDialog: Event
        data class NavigateToWeb(val url: String, val headers: Map<String, String>) : Event
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    private val _state = MutableStateFlow(
        State(
            languageCode = tokenStorage.getAppLanguageCode() ?: "ru",
            appVersion = AppConfig.appVersion
        )
    )
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when (action) {
            is Action.OnRefresh -> loadProfile()
            is Action.OnLogoutClicked -> logout()

            is Action.OnWorkspaceClicked -> {
                log.write("Switching to workspace: ${action.workspace.title}")
                sessionManager.switchWorkspace(action.workspace)
                screenModelScope.launch {
                    pushService.unregister()
                    pushService.register()
                }
            }

            is Action.OnJoinTeamClicked -> {
                _events.trySend(Event.NavigateToJoinCompany)
            }

            is Action.OnCreateBusinessClicked -> openBusinessPortal()

            is Action.OnLanguageClicked -> _events.trySend(Event.ShowLanguageDialog)
            is Action.OnLanguageSelected -> updateLanguage(action.code)
            is Action.OnSupportClicked -> _events.trySend(Event.NavigateToSupport)
        }
    }
    private fun loadProfile() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            repository.getProfile()
                .onSuccess { profile ->
                    val resolvedLanguage = resolveLanguage(profile.language)
                    _state.value = _state.value.copy(
                        name = UiText.DynamicString("${profile.firstName ?: ""} ${profile.lastName ?: ""}".trim()),
                        phone = profile.phone,
                        workspaces = profile.workspaces,
                        isLoading = false,
                        languageCode = resolvedLanguage
                    )
                    sessionManager.updateWorkspaces(profile.workspaces)
                   pushService.register()
                }
                .onFailure { exception ->
                    log.write("Failed to load profile", LogType.Error, exception)
                    _events.send(Event.ShowMessage(UiText.Resource(Res.string.error_network), SnackbarType.Error))
                    _state.value = _state.value.copy(
                        isLoading = false,
                        name = UiText.Resource(Res.string.profile_error_load)
                    )
                }
                .onError { code, _ ->
                    log.write("Failed to load profile: $code", LogType.Error)
                    _events.send(Event.ShowMessage(UiText.Resource(code.toResource()), SnackbarType.Error))
                    _state.value = _state.value.copy(
                        isLoading = false,
                        name = UiText.Resource(Res.string.profile_error_load)
                    )
                }
        }
    }
    private fun logout() {
        screenModelScope.launch {
            pushService.unregister()
            sessionManager.logout()
        }
    }
    private fun openBusinessPortal() {
        val access = tokenStorage.getAccessToken()
        val refresh = tokenStorage.getRefreshToken()
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            _events.trySend(
                Event.ShowMessage(
                    UiText.Resource(Res.string.profile_web_error_token),
                    SnackbarType.Error
                )
            )
            return
        }
        val headers = mapOf(
            "Authorization" to "Bearer $access",
            "X-Refresh-Token" to refresh
        )
        _events.trySend(Event.NavigateToWeb(WEB_URL, headers))
    }
    private fun resolveLanguage(serverLanguage: String?): String {
        val current = tokenStorage.getAppLanguageCode()
        return when {
            current != null -> current
            !serverLanguage.isNullOrBlank() -> {
                tokenStorage.setAppLanguageCode(serverLanguage)
                serverLanguage
            }
            else -> "ru"
        }
    }

    private fun updateLanguage(code: String) {
        if (_state.value.languageCode == code) return
        screenModelScope.launch {
            repository.updateLanguage(code)
                .onSuccess {
                    tokenStorage.setAppLanguageCode(code)
                    _state.value = _state.value.copy(languageCode = code)
                    _events.send(
                        Event.ShowMessage(
                            UiText.Resource(Res.string.profile_language_updated),
                            SnackbarType.Success
                        )
                    )
                }
                .onError { errorCode, _ ->
                    log.write("Failed to update language: $errorCode", LogType.Warning)
                    _events.send(
                        Event.ShowMessage(UiText.Resource(errorCode.toResource()), SnackbarType.Error)
                    )
                }
                .onFailure { throwable ->
                    log.write("Failed to update language", LogType.Error, throwable)
                    _events.send(
                        Event.ShowMessage(UiText.Resource(Res.string.error_network), SnackbarType.Error)
                    )
                }
            platformManager.applyLanguage(code)

            // 4. Обновляем стейт UI (на всякий случай)
            _state.value = _state.value.copy(languageCode = code)

            delay(300)
            platformManager.reloadUI()
        }
    }
}
