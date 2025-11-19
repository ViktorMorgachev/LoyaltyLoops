package io.loyaltyloop.app.features.profile

import io.loyaltyloop.shared.models.UserWorkspace

sealed interface ProfileAction {
    // Клики по кнопкам
    data object OnLogoutClicked : ProfileAction
    data object OnCreateBusinessClicked : ProfileAction
    data object OnJoinTeamClicked : ProfileAction
    
    // Клик по элементу списка
    data class OnWorkspaceClicked(val workspace: UserWorkspace) : ProfileAction
    
    // Настройки
    data object OnLanguageClicked : ProfileAction
    data object OnSupportClicked : ProfileAction
    
    // Системные (например, обновление свайпом)
    data object OnRefresh : ProfileAction
}