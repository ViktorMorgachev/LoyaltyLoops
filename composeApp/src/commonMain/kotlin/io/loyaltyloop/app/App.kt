package io.loyaltyloop.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import cafe.adriel.voyager.navigator.Navigator
import io.loyaltyloop.app.features.auth.LoginScreen

@Composable
fun App() {
    MaterialTheme {
        Navigator(LoginScreen())
    }
}