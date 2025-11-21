package io.loyaltyloop.app

import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.features.auth.LoginScreen
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.app.ui.theme.LoyaltyTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    LoyaltyTheme {
        val sessionManager = koinInject<SessionManager>()
        Navigator(SplashScreen()) { navigator ->

            // Слушаем событие глобального выхода
            LaunchedEffect(Unit) {
                sessionManager.logoutEvent.collect {
                    // Если пришло событие -> Жестко переходим на Логин, очищая стек
                    navigator.replaceAll(LoginScreen())
                }
            }

            // Анимация переходов (для красоты)
            SlideTransition(navigator)
        }
    }
}