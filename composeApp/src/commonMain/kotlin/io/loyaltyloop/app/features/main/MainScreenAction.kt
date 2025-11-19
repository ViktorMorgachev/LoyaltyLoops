package io.loyaltyloop.app.features.main

import io.loyaltyloop.shared.models.UserWorkspace

sealed interface MainScreenAction {
    // Выход из рабочего режима (возврат к роли Клиента)
    data object LogoutToClientMode : MainScreenAction
    
    // Переключение на другой воркспейс (на будущее, для меню в профиле)
    data class SwitchWorkspace(val workspace: UserWorkspace) : MainScreenAction
}