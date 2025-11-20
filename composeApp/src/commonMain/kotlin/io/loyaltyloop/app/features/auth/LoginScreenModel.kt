package io.loyaltyloop.app.features.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.data.network.ClientException
import io.loyaltyloop.app.data.network.NetworkException
import io.loyaltyloop.app.data.network.ServerException
import io.loyaltyloop.app.data.network.UnauthorizedException
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.Country
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown

class LoginScreenModel(
    private val repository: AuthRepository,
    private val tokenStorage: TokenStorage
) : ScreenModel {

    private val _state = MutableStateFlow(LoginState(isLoading = false))
    val state = _state.asStateFlow()

    // Канал для разовых событий (Навигация, Клавиатура)
    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()

    private var timerJob: Job? = null

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnPhoneChanged -> {
                if (action.phone.all { it.isDigit() }) {
                    _state.value = _state.value.copy(phoneInput = action.phone)
                }
            }
            is LoginAction.OnOtpChanged -> onCodeChanged(action.code)

            is LoginAction.OnCountryClicked -> {
                // Логика переключения стран
                val current = _state.value.selectedCountry
                val nextIndex = (current.ordinal + 1) % Country.values().size
                _state.value = _state.value.copy(selectedCountry = Country.values()[nextIndex])
            }

            is LoginAction.OnSubmitClicked -> {
                // Решаем, что делать, в зависимости от шага
                // Для OTP у нас авто-отправка при вводе 4 цифр,
                if (_state.value.step == LoginStep.EnterPhone) {
                    onSendCodeClicked()
                }
            }

            is LoginAction.OnBackClicked -> {
                _state.value = _state.value.copy(step = LoginStep.EnterPhone, otpInput = "")
                timerJob?.cancel()
            }

            is LoginAction.OnResendClicked -> {
                // Повторно отправляем код
                onSendCodeClicked(isResend = true)
            }
        }
    }

    private fun onSendCodeClicked(isResend: Boolean = false) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, step = LoginStep.EnterCode)

            val country = state.value.selectedCountry
            val fullNumber = country.phonePrefix + state.value.phoneInput

            val result = repository.sendCode(fullNumber)

            result.onSuccess { debugCode ->
                log.write("Code received: $debugCode")

                _state.value = _state.value.copy(
                    isLoading = false,
                    otpInput = "", // Очищаем поле ввода при повторной отправке
                    timerSeconds = 60,
                    isResendEnabled = false // Блокируем кнопку снова
                )
                startTimer() // Перезапускаем таймер

            }.onFailure { error ->
                log.write("Failed", LogType.Error, error)

                val errorText = when(error) {
                    is ClientException -> UiText.DynamicString(error.errorMessage)
                    is NetworkException -> UiText.Resource(Res.string.error_network)
                    is ServerException -> UiText.Resource(Res.string.error_server)
                    else -> UiText.Resource(Res.string.error_unknown)
                }

                _events.send(LoginEvent.ShowError(errorText))

                _state.value = _state.value.copy(
                    isLoading = false,
                    otpInput = ""
                )
            }
        }
    }


    fun onCountrySelected(country: Country) {
        _state.value = _state.value.copy(selectedCountry = country)
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
            _events.send(LoginEvent.HideKeyboard)
            _state.value = _state.value.copy(isLoading = true)
            val fullNumber = state.value.selectedCountry.phonePrefix + state.value.phoneInput

            val result = repository.login(fullNumber, code)

            result.onSuccess {response ->
                println("LOGIN SUCCESS! Токен получен.")
                tokenStorage.saveAuthData(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    userId = response.userId
                )
                if (response.isNewUser) {
                    _events.send(LoginEvent.NavigateToOnboarding)
                } else {
                    _events.send(LoginEvent.NavigateToHome)
                }
                _state.value = _state.value.copy(isLoading = false)
            }.onFailure { error ->
                log.write("Login Failed", LogType.Error, error)
                val errorText = when(error) {
                    is UnauthorizedException -> UiText.DynamicString("Неверный код")
                    is ClientException -> UiText.DynamicString("Неверный код") // Упростим для MVP
                    else -> UiText.Resource(Res.string.error_unknown)
                }
                _events.send(LoginEvent.ShowError(errorText))

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
                // Уменьшаем таймер
                _state.value = _state.value.copy(timerSeconds = _state.value.timerSeconds - 1)
            }
            // Таймер дошел до 0 -> Включаем кнопку
            _state.value = _state.value.copy(isResendEnabled = true)
        }
    }

}

// Обновленный State
data class LoginState(
    val selectedCountry: Country = Country.default(),
    val phoneInput: String = "",
    val otpInput: String = "",
    val isResendEnabled: Boolean = false,
    val step: LoginStep = LoginStep.EnterPhone,
    val isLoading: Boolean = false,
    val timerSeconds: Int = 0,
)

enum class LoginStep {
    EnterPhone, EnterCode
}