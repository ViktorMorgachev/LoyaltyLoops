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
import loyaltyloop.composeapp.generated.resources.error_profile_loading
import loyaltyloop.composeapp.generated.resources.profile_error_load
import loyaltyloop.composeapp.generated.resources.profile_loading
import org.jetbrains.compose.resources.getString

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
        data object NavigateToSplash : Event // После логаута
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    init {
        loadProfile()
    }

    fun onCreateBusinessClicked() {
        // TODO: Переход на флоу регистрации Партнера (Этап 4)
        log.write("Click: Create Business")
    }

    fun onJoinTeamClicked() {
        // TODO: Диалог ввода инвайт-кода (Этап 3)
        log.write("Click: Join Team")
    }

    fun onLanguageClicked() {
        // TODO: BottomSheet с выбором языка
        log.write("Click: Change Language")
    }

    fun onSupportClicked() {
        // TODO: Открыть Telegram/WhatsApp
    }

    fun loadProfile() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            repository.getProfile()
                .onSuccess { profile ->
                    _state.value = _state.value.copy(
                        name =  UiText.DynamicString(profile.firstName?.trim() ?: ""),
                        phone = profile.phone,
                        workspaces = profile.workspaces,
                        isLoading = false
                    )

                    // ИСПОЛЬЗУЕМ ЕДИНЫЙ МЕТОД
                    sessionManager.updateWorkspaces(profile.workspaces)
                }
                .onFailure { error ->
                    log.write("Failed to load profile", LogType.Error, error)

                    // ИСПОЛЬЗУЕМ РЕСУРСЫ (асинхронно)
                    val errorText = UiText.Resource(Res.string.profile_error_load)

                    _state.value = _state.value.copy(
                        isLoading = false,
                        name = errorText
                    )
                }
        }
    }

    fun onWorkspaceClicked(workspace: UserWorkspace) {
        // TODO: Тут потом добавим проверку PIN-кода для Владельца
        log.write("Switching to workspace: ${workspace.title}")
        sessionManager.switchWorkspace(workspace)
    }

    fun onLogoutClicked() {
        screenModelScope.launch {
            // 1. Чистим хранилище
            tokenStorage.clear()
            // 2. Сбрасываем сессию
            sessionManager.switchWorkspace(null)
            // 3. Отправляем на Сплеш (который кинет на Логин)
            _events.send(Event.NavigateToSplash)
        }
    }
}