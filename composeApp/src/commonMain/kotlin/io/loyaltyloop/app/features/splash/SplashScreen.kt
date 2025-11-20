package io.loyaltyloop.app.features.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.auth.LoginScreen
import io.loyaltyloop.app.features.home.HomeScreen
import io.loyaltyloop.app.features.onboarding.OnboardingScreen
import io.loyaltyloop.app.features.role.RoleSelectionScreen
import org.jetbrains.compose.resources.stringResource
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.btn_retry

class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<SplashScreenModel>()
        val state by viewModel.state.collectAsState()

        // Авто-запуск при открытии экрана
        LaunchedEffect(Unit) {
            viewModel.checkSession()
        }

        // Обработка навигации
        LaunchedEffect(state) {
            when (state) {
                is SplashScreenModel.SplashState.NavigateToHome -> navigator.replaceAll(HomeScreen())
                is SplashScreenModel.SplashState.NavigateToLogin -> navigator.replaceAll(LoginScreen())
                is SplashScreenModel.SplashState.NavigateToOnboarding -> {
                    navigator.replaceAll(OnboardingScreen())
                }
                is SplashScreenModel.SplashState.NavigateToRoleSelection -> {
                    navigator.replaceAll(RoleSelectionScreen())
                }
                else -> Unit
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is SplashScreenModel.SplashState.Loading -> {
                    // Логотип
                    Text(
                        text = "LoyaltyLoop",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is SplashScreenModel.SplashState.Error -> {
                    // Экран ошибки с кнопкой Повторить
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(currentState.messageRes), // <-- Локализованный текст
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.checkSession() }) {
                            Text(stringResource(Res.string.btn_retry))
                        }
                    }
                }
                else -> {}
            }
        }
    }
}