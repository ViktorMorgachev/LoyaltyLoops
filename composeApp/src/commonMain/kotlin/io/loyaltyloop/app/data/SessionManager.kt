package io.loyaltyloop.app.data

import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// composeApp/src/commonMain/kotlin/io/loyaltyloop/app/data/SessionManager.kt

class SessionManager(
    private val tokenStorage: TokenStorage
) {
    // Текущий активный режим
    private val _currentWorkspace = MutableStateFlow<UserWorkspace?>(null)
    val currentWorkspace = _currentWorkspace.asStateFlow()

    // Список всех доступных ролей (загружается с сервера)
    private var allWorkspaces: List<UserWorkspace> = emptyList()

    // Вызывается при старте приложения (в Splash)
    fun initSession(workspaces: List<UserWorkspace>) {
        this.allWorkspaces = workspaces

        // Восстанавливаем последний выбор
        val savedId = tokenStorage.getCurrentWorkspaceId()
        val savedWorkspace = workspaces.find { it.id == savedId }

        _currentWorkspace.value = savedWorkspace
    }

    // Переключение режима
    fun switchWorkspace(workspace: UserWorkspace?) {
        _currentWorkspace.value = workspace
        tokenStorage.saveCurrentWorkspaceId(workspace?.id) // Сохраняем выбор
    }

    // Геттеры для UI
    fun isClientMode() = _currentWorkspace.value == null
    fun isCashierMode() = _currentWorkspace.value?.role == UserRole.CASHIER
    fun isPartnerMode() = _currentWorkspace.value?.role == UserRole.PARTNER_ADMIN

    fun getAvailableWorkspaces() = allWorkspaces
}