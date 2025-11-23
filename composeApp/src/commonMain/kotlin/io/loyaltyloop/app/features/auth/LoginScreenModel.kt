package io.loyaltyloop.app.features.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.data.network.*
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.ui.components.SnackbarType
import io.loyaltyloop.app.utils.*
import io.loyaltyloop.shared.models.Country
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.auth_success
import loyaltyloop.composeapp.generated.resources.err_invalid_phone_format
import loyaltyloop.composeapp.generated.resources.error_network
import loyaltyloop.composeapp.generated.resources.error_server
import loyaltyloop.composeapp.generated.resources.error_unknown

class LoginScreenModel(
    private val repository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val sessionManager: SessionManager
) : ScreenModel {

    // --- ВЛОЖЕННЫЕ КЛАССЫ (Контракт) ---

    data class State(
        val selectedCountry: Country = Country.default(),
        val phoneInput: String = "",
        val otpInput: String = "",
        val isResendEnabled: Boolean = false,
        val step: Step = Step.EnterPhone,
        val phoneError: UiText? = null,
        val isLoading: Boolean = false,
        val timerSeconds: Int = 0,
    )

    enum class Step {
        EnterPhone, EnterCode
    }

    sealed interface Action {
        data class OnPhoneChanged(val phone: String) : Action
        data class OnOtpChanged(val code: String) : Action
        object OnCountryClicked : Action
        object OnSubmitClicked : Action
        object OnBackClicked : Action
        object OnResendClicked : Action
    }

    sealed interface Event {
        object NavigateToHome : Event
        object NavigateToOnboarding : Event
        object HideKeyboard : Event
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    // -----------------------------------

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private var timerJob: Job? = null

    fun onAction(action: Action) {
        when (action) {
            is Action.OnPhoneChanged -> {
                val digitsOnly = action.phone.filter { it.isDigit() }
                val currentCountry = _state.value.selectedCountry
                val maxLen = currentCountry.mask.count { it == '#' }

                if (digitsOnly.length <= maxLen) {
                    _state.value = _state.value.copy(
                        phoneInput = digitsOnly,
                        phoneError = null
                    )
                }
            }

            is Action.OnOtpChanged -> onCodeChanged(action.code)

            is Action.OnCountryClicked -> {
                val current = _state.value.selectedCountry
                val nextIndex = (current.ordinal + 1) % Country.values().size
                _state.value = _state.value.copy(selectedCountry = Country.values()[nextIndex])
            }

            is Action.OnSubmitClicked -> {
                if (_state.value.step == Step.EnterPhone) {
                    onSendCodeClicked()
                }
            }

            is Action.OnBackClicked -> {
                _state.value = _state.value.copy(step = Step.EnterPhone, otpInput = "")
                timerJob?.cancel()
            }

            is Action.OnResendClicked -> {
                onSendCodeClicked(isResend = true)
            }
        }
    }

    private fun onSendCodeClicked(isResend: Boolean = false) {
        val country = state.value.selectedCountry
        if (!country.isValidNumber(state.value.phoneInput)) {
            _state.value = _state.value.copy(
                phoneError = UiText.Resource(Res.string.err_invalid_phone_format)
            )
            return
        }
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, step = Step.EnterCode)

            val fullNumber = country.phonePrefix + state.value.phoneInput
            val result = repository.sendCode(fullNumber)

            result.onSuccess { debugCode ->
                log.write("Code received: $debugCode")

                _state.value = _state.value.copy(
                    isLoading = false,
                    otpInput = "",
                    timerSeconds = 60,
                    isResendEnabled = false
                )
                startTimer()

            }.onFailure { error ->
                log.write("Failed", LogType.Error, error)

                _events.send(
                    Event.ShowMessage(
                        message = mapError(error),
                        type = SnackbarType.Error
                    )
                )

                _state.value = _state.value.copy(
                    isLoading = false,
                    otpInput = ""
                )
            }
        }
    }

    private fun onCodeChanged(newCode: String) {
        if (newCode.length <= 4 && newCode.all { it.isDigit() }) {
            _state.value = _state.value.copy(otpInput = newCode)
            if (newCode.length == 4) {
                verifyCode(newCode)
            }
        }
    }

    private fun verifyCode(code: String) {
        screenModelScope.launch {
            _events.send(Event.HideKeyboard)
            _state.value = _state.value.copy(isLoading = true)

            val fullNumber = state.value.selectedCountry.phonePrefix + state.value.phoneInput
            log.write("Verifying code for $fullNumber...")

            repository.login(fullNumber, code).onSuccess { authResponse ->
                tokenStorage.saveAuthData(
                    accessToken = authResponse.accessToken,
                    refreshToken = authResponse.refreshToken,
                    userId = authResponse.userId,
                    qrSecret = authResponse.qrSecret
                )

                repository.getProfile().onSuccess { userProfile ->

                    _events.send(Event.ShowMessage(UiText.Resource(Res.string.auth_success), SnackbarType.Success))
                    sessionManager.updateWorkspaces(userProfile.workspaces)

                    if (authResponse.isNewUser || userProfile.firstName.isNullOrBlank()) {
                        _events.send(Event.NavigateToOnboarding)
                    } else {
                        _events.send(Event.NavigateToHome)
                    }
                }.onFailure {
                    _events.send(Event.NavigateToHome)
                }

                _state.value = _state.value.copy(isLoading = false)

            }.onFailure { error ->
                _events.send(
                    Event.ShowMessage(
                        message = mapError(error),
                        type = SnackbarType.Error
                    )
                )
                _state.value = _state.value.copy(isLoading = false, otpInput = "")
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
            _state.value = _state.value.copy(isResendEnabled = true)
        }
    }

    private fun mapError(error: Throwable): UiText {
        return when (error) {
            is ClientException -> UiText.DynamicString(error.errorMessage)
            is NetworkException -> UiText.Resource(Res.string.error_network)
            is ServerException -> UiText.Resource(Res.string.error_server)
            else -> UiText.Resource(Res.string.error_unknown)
        }
    }
}