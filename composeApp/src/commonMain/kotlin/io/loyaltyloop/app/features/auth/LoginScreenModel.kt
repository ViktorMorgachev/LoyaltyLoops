package io.loyaltyloop.app.features.auth


import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.shared.models.Country
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginScreenModel(
    private val repository: AuthRepository
) : ScreenModel {

    // Состояние UI
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    fun onPhoneChanged(newPhone: String) {
        _state.value = _state.value.copy(phoneInput = newPhone)
    }

    fun onCountrySelected(country: Country) {
        _state.value = _state.value.copy(selectedCountry = country)
    }

    fun onSendCodeClicked() {
        screenModelScope.launch {
            val country = state.value.selectedCountry
            val fullNumber = country.getFullNumber(state.value.phoneInput)

            println("Отправляем код на: $fullNumber")

            // Тут вызовем репозиторий (пока имитация)
            // repository.sendCode(fullNumber)

            // Переключаем состояние на ввод кода
            _state.value = _state.value.copy(step = LoginStep.EnterCode)
        }
    }
}

data class LoginState(
    val selectedCountry: Country = Country.default(),
    val phoneInput: String = "",
    val step: LoginStep = LoginStep.EnterPhone,
    val isLoading: Boolean = false
)

enum class LoginStep {
    EnterPhone, EnterCode
}