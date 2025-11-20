package io.loyaltyloop.app.features.splash

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.data.network.* // Импортируем наши исключения
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.* // Импорт ресурсов
import org.jetbrains.compose.resources.StringResource
import co.touchlab.kermit.Logger as KermitLogger

class SplashScreenModel(
    private val repository: AuthRepository,
    private val tokenStorage: TokenStorage
) : ScreenModel {

     val log = KermitLogger.withTag("SplashScreenModel")

    sealed class SplashState {
        data object Loading : SplashState()
        data object NavigateToLogin : SplashState()
        data object NavigateToHome : SplashState()

        // Состояние ошибки (не переходим никуда, просим повторить)
        data class Error(val messageRes: StringResource) : SplashState()
    }

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state = _state.asStateFlow()

    fun checkSession() {
        screenModelScope.launch {
            _state.value = SplashState.Loading
            delay(1000)

            val hasToken = tokenStorage.getAccessToken() != null

            // Логируем отладку

            log.write("Check Session: Token present? $hasToken", LogType.Debug)

            if (!hasToken) {
                log.write("No token found -> Navigate to Login")
                _state.value = SplashState.NavigateToLogin
                return@launch
            }

            val result = repository.getProfile()

            result.onSuccess {
                log.write("Profile loaded -> Navigate to Home")
                _state.value = SplashState.NavigateToHome
            }.onFailure { error ->
                // Логируем ошибку
                log.write("Profile check failed", LogType.Error, error)
                handleError(error)
            }
        }
    }

    private fun handleError(error: Throwable) {
        when (error) {
            is UnauthorizedException -> {
                // Токен умер -> Чистим и на Логин
                tokenStorage.clear()
                _state.value = SplashState.NavigateToLogin
            }
            is NetworkException -> {
                _state.value = SplashState.Error(Res.string.error_network)
            }
            is ServerException -> {
                _state.value = SplashState.Error(Res.string.error_server)
            }
            else -> {
                _state.value = SplashState.Error(Res.string.error_unknown)
            }
        }
    }
}