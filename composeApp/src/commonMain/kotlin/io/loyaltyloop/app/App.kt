package io.loyaltyloop.app

import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.app.navigation.NavigatorHolder
import io.loyaltyloop.app.platform.AppRestarter
import io.loyaltyloop.app.ui.theme.LoyaltyTheme
import org.koin.compose.koinInject

@Composable
fun App() {

    val appRestarter = koinInject<AppRestarter>()
    val sessionManager = koinInject<SessionManager>()

    LaunchedEffect(Unit) {
        sessionManager.logoutEvent.collect {
            appRestarter.restartApp()
        }
    }

    LoyaltyTheme {
        Navigator(SplashScreen()) { navigator ->
            NavigatorHolder.lastNavigator = navigator
            SlideTransition(navigator)
        }
    }
}