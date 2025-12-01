package io.loyaltyloop.app

import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.app.navigation.NavigatorHolder
import io.loyaltyloop.app.platform.AppRestarter
import io.loyaltyloop.app.ui.theme.LoyaltyTheme
import io.loyaltyloop.shared.config.AppConfig
import org.koin.compose.koinInject

@Composable
fun App() {

    val appRestarter = koinInject<AppRestarter>()
    val sessionManager = koinInject<SessionManager>()
    val factory = rememberPermissionsControllerFactory()
    val controller = remember(factory) { factory.createPermissionsController() }

    BindEffect(controller)

    LaunchedEffect(Unit) {
        sessionManager.logoutEvent.collect {
            appRestarter.restartApp()
        }
        if (AppConfig.featureFlags.pushEnabled) {
            try {
                // Запрашиваем разрешение.
                // Moko сам проверит версию Android. Если < 13, он ничего не сделает (считает granted).
                // На iOS покажет системный алерт.
                controller.providePermission(Permission.REMOTE_NOTIFICATION)
            } catch (e: Exception) {
                println("Push permission denied: $e")
            }
        }
    }

    LoyaltyTheme {
        Navigator(SplashScreen()) { navigator ->
            NavigatorHolder.lastNavigator = navigator
            SlideTransition(navigator)
        }
    }
}