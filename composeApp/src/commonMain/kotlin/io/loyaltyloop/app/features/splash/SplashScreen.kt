package io.loyaltyloop.app.features.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.auth.LoginScreen
import io.loyaltyloop.app.features.main.MainScreen
import io.loyaltyloop.app.features.onboarding.OnboardingScreen
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.btn_retry
import org.jetbrains.compose.resources.stringResource

class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinScreenModel<SplashScreenModel>()
        val state by viewModel.state.collectAsState()

        // Навигация через Events (одноразовые события)
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is SplashScreenModel.Event.NavigateToHome -> navigator.replaceAll(MainScreen())
                    is SplashScreenModel.Event.NavigateToLogin -> navigator.replaceAll(LoginScreen())
                    is SplashScreenModel.Event.NavigateToOnboarding -> navigator.replaceAll(OnboardingScreen())
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (state.error != null) {
                // --- ЭКРАН ОШИБКИ ---
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = state.error!!.asString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.onAction(SplashScreenModel.Action.OnRetryClicked) }) {
                        Text(stringResource(Res.string.btn_retry))
                    }
                }
            } else {
                // --- ЛОГОТИП (Загрузка) ---
                // Можно добавить CircularProgressIndicator, если долго грузится
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LoyaltyLoop",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}