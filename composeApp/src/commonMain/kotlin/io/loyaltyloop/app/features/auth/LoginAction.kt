package io.loyaltyloop.app.features.auth


import io.loyaltyloop.shared.models.Country

sealed interface LoginAction {
    // Ввод данных
    data class OnPhoneChanged(val phone: String) : LoginAction
    data class OnOtpChanged(val code: String) : LoginAction
    data object OnResendClicked : LoginAction

    // Клики
    data object OnCountryClicked : LoginAction
    data object OnSubmitClicked : LoginAction // Общая кнопка "Далее"
    data object OnBackClicked : LoginAction

}