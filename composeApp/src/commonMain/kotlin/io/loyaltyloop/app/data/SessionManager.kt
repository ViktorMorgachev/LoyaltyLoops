package io.loyaltyloop.app.data

import io.loyaltyloop.app.data.network.AuthWatcher
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow


class SessionManager(
    private val tokenStorage: TokenStorage
) {
    // Текущий активный режим
    private val _currentWorkspace = MutableStateFlow<UserWorkspace?>(null)
    val currentWorkspace = _currentWorkspace.asStateFlow()
    private val _logoutEvent = Channel<Unit>()
    val logoutEvent = _logoutEvent.receiveAsFlow()

    // Список всех доступных ролей (загружается с сервера)
    private val _availableWorkspaces = MutableStateFlow<List<UserWorkspace>>(emptyList())
    val availableWorkspaces = _availableWorkspaces.asStateFlow()

    init {
        AuthWatcher.register(this)
    }

    suspend fun logout() {
        tokenStorage.clear()
        _currentWorkspace.value = null
        _availableWorkspaces.value = emptyList() // <-- ВАЖНО: Очищаем список
        _logoutEvent.send(Unit)
    }

    // Вызывается при старте приложения (в Splash)
    fun updateWorkspaces(newList: List<UserWorkspace>) {
        _availableWorkspaces.value = newList

        // 1. Пытаемся восстановить сохраненный выбор
        val savedId = tokenStorage.getCurrentWorkspaceId()

        // 2. Проверяем, есть ли сохраненный ID в новом списке
        // (Это важно: если меня уволили, я не должен остаться в режиме кассира)
        val validWorkspace = newList.find { it.id == savedId }

        // 3. Обновляем текущий режим (если не нашли - сбросится в null/Client)
        _currentWorkspace.value = validWorkspace

        // Если выбор сбросился, а был сохранен какой-то ID - стоит почистить сторадж
        if (savedId != null && validWorkspace == null) {
            tokenStorage.saveCurrentWorkspaceId(null)
        }
    }

    // Переключение режима
    fun switchWorkspace(workspace: UserWorkspace?) {
        _currentWorkspace.value = workspace
        tokenStorage.saveCurrentWorkspaceId(workspace?.id) // Сохраняем выбор
    }

    fun getWorkspaces() = availableWorkspaces
}