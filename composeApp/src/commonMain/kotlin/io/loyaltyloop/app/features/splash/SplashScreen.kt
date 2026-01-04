package io.loyaltyloop.app.features.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.auth.LoginScreen
import io.loyaltyloop.app.features.update.ForceUpdateScreen
import io.loyaltyloop.app.features.main.MainScreen
import io.loyaltyloop.app.features.onboarding.OnboardingScreen
import io.loyaltyloop.app.features.update.NeedUpdateScreen
import io.loyaltyloop.app.features.whatsnew.WhatsNewScreen
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.btn_retry
import loyaltyloop.composeapp.generated.resources.splash_subtitle
import loyaltyloop.composeapp.generated.resources.splash_title
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

                    is SplashScreenModel.Event.NavigateToShowNeedUpdate -> navigator.replaceAll(
                        NeedUpdateScreen(
                            storeUrl = event.storeUrl,
                            whatsNew = event.whatsNew,
                            next = event.next,
                            onContinue = { target ->
                                when (target) {
                                    SplashScreenModel.NavigationTarget.Home -> navigator.replaceAll(MainScreen())
                                    SplashScreenModel.NavigationTarget.Login -> navigator.replaceAll(LoginScreen())
                                    SplashScreenModel.NavigationTarget.Onboarding -> navigator.replaceAll(OnboardingScreen())
                                    SplashScreenModel.NavigationTarget.WhatsNew -> navigator.replaceAll(
                                        WhatsNewScreen {
                                            navigator.replaceAll(MainScreen())
                                        }
                                    )
                                }
                            }
                        )
                    )
                }
            }
        }

        val isDark = isSystemInDarkTheme()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (state.error != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = state.error!!.asString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.onAction(SplashScreenModel.Action.OnRetryClicked) }) {
                        Text(stringResource(Res.string.btn_retry))
                    }
                }
            } else {
                // --- ЛОГОТИП (Загрузка) ---
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    val titleGradient = Brush.linearGradient(
                        listOf(
                            Color(0xFF4565E8),
                            Color(0xFF8C52D6)
                        )
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    brush = titleGradient,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) {
                                append("LoyaltyLoops")
                            }
                        },
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.Unspecified,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(Res.string.splash_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Color(0xFFE5E7EB) else Color(0xFF2C3E66),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(Res.string.splash_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFFAEB5C3) else Color(0xFF4B5563),
                        textAlign = TextAlign.Center
                    )
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(24.dp),
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
            }
        }
    }
}