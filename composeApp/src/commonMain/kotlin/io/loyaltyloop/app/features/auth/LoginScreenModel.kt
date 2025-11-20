package io.loyaltyloop.app.features.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.shared.models.Country
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginScreenModel(
    private val repository: AuthRepository,
    private val tokenStorage: TokenStorage
) : ScreenModel {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private var timerJob: Job? = null

    fun onPhoneChanged(newPhone: String) {
        // Разрешаем вводить только цифры
        if (newPhone.all { it.isDigit() }) {
            _state.value = _state.value.copy(phoneInput = newPhone)
        }
    }

    fun onCountrySelected(country: Country) {
        _state.value = _state.value.copy(selectedCountry = country)
    }

    // Шаг 1: Отправка СМС
    fun onSendCodeClicked() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val country = state.value.selectedCountry
            val fullNumber = country.phonePrefix + state.value.phoneInput

            println("Запрос кода на: $fullNumber")

            // --- РЕАЛЬНЫЙ ВЫЗОВ ---
            val result = repository.sendCode(fullNumber)

            result.onSuccess { debugCode ->
                println("Код получен: $debugCode")

                _state.value = _state.value.copy(
                    isLoading = false,
                    step = LoginStep.EnterCode,
                    timerSeconds = 60
                )
                startTimer()

            }.onFailure { error ->
                println("Ошибка отправки СМС: ${error.message}")
                _state.value = _state.value.copy(isLoading = false)
                //TODO Тут хорошо бы показать Snackber с ошибкой
            }
        }
    }

    // Шаг 2: Ввод кода (цифра за цифрой)
    fun onCodeChanged(newCode: String) {
        if (newCode.length <= 4 && newCode.all { it.isDigit() }) {
            _state.value = _state.value.copy(otpInput = newCode)

            // Если ввели 4 цифры — сразу проверяем
            if (newCode.length == 4) {
                verifyCode(newCode)
            }
        }
    }

    private fun verifyCode(code: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val fullNumber = state.value.selectedCountry.phonePrefix + state.value.phoneInput

            val result = repository.login(fullNumber, code)

            result.onSuccess {
                println("LOGIN SUCCESS! Токен получен.")
                tokenStorage.saveAuthData(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    userId = it.userId
                )
                // Тут будет навигация на Главный экран
            }.onFailure {
                println("LOGIN FAILED: ${it.message}")
                _state.value = _state.value.copy(
                    isLoading = false,
                    otpInput = ""
                )
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
            while (_state.value.timerSeconds > 0) {
                delay(1000)
                _state.value = _state.value.copy(timerSeconds = _state.value.timerSeconds - 1)
            }
        }
    }

    fun onBackClicked() {
        _state.value = _state.value.copy(step = LoginStep.EnterPhone, otpInput = "")
        timerJob?.cancel()
    }
}

// Обновленный State
data class LoginState(
    val selectedCountry: Country = Country.default(),
    val phoneInput: String = "",
    val otpInput: String = "",
    val step: LoginStep = LoginStep.EnterPhone,
    val isLoading: Boolean = false,
    val timerSeconds: Int = 0
)

enum class LoginStep {
    EnterPhone, EnterCode
}