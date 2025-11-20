package io.loyaltyloop.app

import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import io.loyaltyloop.app.features.auth.LoginScreen
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.app.ui.theme.LoyaltyTheme

@Composable
fun App() {
    LoyaltyTheme {
        Navigator(SplashScreen())
    }
}