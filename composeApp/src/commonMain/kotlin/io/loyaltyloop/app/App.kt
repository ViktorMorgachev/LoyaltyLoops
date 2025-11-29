package io.loyaltyloop.app

import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.app.navigation.NavigatorHolder
import io.loyaltyloop.app.ui.theme.LoyaltyTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    LoyaltyTheme {
        val sessionManager = koinInject<SessionManager>()

        // Ключ для перезапуска всего графа навигации
        var restartKey by remember { mutableStateOf(0) }

        // Слушаем логаут глобально
        LaunchedEffect(Unit) {
            sessionManager.logoutEvent.collect {
                // Увеличиваем ключ -> Компоновка Navigator пересоздастся с нуля!
                restartKey++
            }
        }
        // Оборачиваем Navigator в key
        key(restartKey) {
            Navigator(SplashScreen()) { navigator ->
                NavigatorHolder.lastNavigator = navigator
                SlideTransition(navigator)
            }
        }
    }
}