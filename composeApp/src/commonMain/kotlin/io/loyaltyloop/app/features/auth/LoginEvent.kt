package io.loyaltyloop.app.features.auth

import io.loyaltyloop.app.utils.UiText

sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
    data object HideKeyboard : LoginEvent
    data class ShowError(val message: UiText) : LoginEvent
}