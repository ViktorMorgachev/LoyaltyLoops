package io.loyaltyloop.app.features.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.auth.LoginScreen
import io.loyaltyloop.app.features.forceupdate.ForceUpdateScreen
import io.loyaltyloop.app.features.main.MainScreen
import io.loyaltyloop.app.features.onboarding.OnboardingScreen
import io.loyaltyloop.app.features.whatsnew.WhatsNewScreen
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.btn_retry
import org.jetbrains.compose.resources.stringResource

class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinScreenModel<SplashScreenModel>()
        val state by viewModel.state.collectAsState()

        LaunchedEffect(Unit){
            viewModel.checkSession()
        }

        // Навигация через Events (одноразовые события)
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is SplashScreenModel.Event.NavigateToHome -> navigator.replaceAll(MainScreen())
                    is SplashScreenModel.Event.NavigateToLogin -> navigator.replaceAll(LoginScreen())
                    is SplashScreenModel.Event.NavigateToOnboarding -> navigator.replaceAll(OnboardingScreen())
                    is SplashScreenModel.Event.NavigateToForceUpdate -> navigator.replaceAll(ForceUpdateScreen(event.url))
                    is SplashScreenModel.Event.NavigateToWhatsNew -> navigator.replaceAll(WhatsNewScreen {
                        navigator.replaceAll(MainScreen())
                    })
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
                    val gradient = Brush.linearGradient(
                        listOf(
                            Color(0xFF4565E8),
                            Color(0xFF8C52D6)
                        )
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    brush = gradient,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("LoyaltyLoop")
                            }
                        },
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Unspecified
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