package io.loyaltyloop.app.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.main.MainScreen
import io.loyaltyloop.app.features.onboarding.OnboardingScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.OtpTextField
import io.loyaltyloop.app.ui.components.show
import io.loyaltyloop.app.utils.MaskVisualTransformation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import loyaltyloop.composeapp.generated.resources.*
import org.koin.core.parameter.parametersOf

class LoginScreen(val uuid: String? = null) : Screen {
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<LoginScreenModel>{ parametersOf(uuid)}
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val keyboardController = LocalSoftwareKeyboardController.current
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is LoginScreenModel.Event.HideKeyboard -> keyboardController?.hide()
                    is LoginScreenModel.Event.NavigateToHome -> navigator.replaceAll(MainScreen())
                    is LoginScreenModel.Event.NavigateToOnboarding -> navigator.replaceAll(OnboardingScreen())
                    is LoginScreenModel.Event.ShowMessage -> {
                        launch {
                            snackbarHostState.show(event.message, event.type)
                        }
                    }
                }
            }
        }

        LaunchedEffect(uuid){
            uuid?.let {
                viewModel.onAction(LoginScreenModel.Action.OnAutoStartTelegram(it))
            }
        }

        LoginScreenContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onAction = viewModel::onAction
        )
    }
}

@Composable
fun LoginScreenContent(
    state: LoginScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onAction: (LoginScreenModel.Action) -> Unit
) {

    LoyaltyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            if (state.step == LoginScreenModel.Step.EnterCode) {
                IconButton(onClick = { onAction(LoginScreenModel.Action.OnBackClicked) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(Res.string.auth_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (state.step == LoginScreenModel.Step.EnterPhone) {
                if (state.telegramMode || state.isTelegramStarting) {
                    TelegramAuthCard(state, onAction)
                } else {
                    PhoneInputCard(
                        state = state,
                        onAction = onAction
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { onAction(LoginScreenModel.Action.OnTelegramClicked) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.auth_telegram_btn))
                    }
                }
            } else {
                Text(stringResource(Res.string.auth_enter_code), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(32.dp))

                OtpTextField(
                    otpText = state.otpInput,
                    onOtpTextChange = { onAction(LoginScreenModel.Action.OnOtpChanged(it)) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    if (state.isResendEnabled) {
                        TextButton(
                            onClick = { onAction(LoginScreenModel.Action.OnResendClicked) }
                        ) {
                            Text(
                                text = stringResource(Res.string.auth_resend_btn),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        val timerValue = "00:${state.timerSeconds.toString().padStart(2, '0')}"
                        val fullText = "${stringResource(Res.string.auth_resend_timer)} $timerValue"

                        Text(
                            text = fullText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelegramAuthCard(
    state: LoginScreenModel.State,
    onAction: (LoginScreenModel.Action) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp) // Чуть светлее фона
        ),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 32.dp, horizontal = 16.dp)
                .fillMaxWidth(), // Растягиваем на всю ширину
            horizontalAlignment = Alignment.CenterHorizontally // Центрируем контент по горизонтали
        ) {

            if (state.isTelegramStarting) {
                TelegramConnectingAnimation()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color(0xFF2AABEE),
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.auth_telegram_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.auth_telegram_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        state.telegramBot?.let { bot ->
                            state.telegramUuid?.let { uuid ->
                                uriHandler.openUri("https://t.me/$bot?start=login_$uuid")
                            }
                        }
                        onAction.invoke(LoginScreenModel.Action.OnTelegramClicked)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2AABEE),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        stringResource(Res.string.auth_telegram_open),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.telegramStatus == "PENDING" && state.authTelegramButtonClicked) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.auth_telegram_pending),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { onAction(LoginScreenModel.Action.OnBackToPhoneClicked) }
                ) {
                    Text(
                        stringResource(Res.string.auth_telegram_back),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun PhoneInputCard(
    state: LoginScreenModel.State,
    onAction: (LoginScreenModel.Action) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.auth_phone_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.phoneInput,
                maxLines = 1,
                onValueChange = { onAction(LoginScreenModel.Action.OnPhoneChanged(it)) },
                leadingIcon = {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .padding(start = 8.dp, end = 8.dp)
                            .clickable { onAction(LoginScreenModel.Action.OnCountryClicked) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.selectedCountry.flagEmoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.selectedCountry.phonePrefix,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                },
                placeholder = {
                    Text(
                        text = state.selectedCountry.mask.replace("#", "0"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                },
                visualTransformation = MaskVisualTransformation(state.selectedCountry.mask),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading,
                textStyle = MaterialTheme.typography.titleMedium,
                isError = state.phoneError != null,
                supportingText = {
                    state.phoneError?.let { error ->
                        Text(
                            text = error.asString(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                trailingIcon = {
                    if (state.phoneError != null) {
                        Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error)
                    }
                },
                shape = MaterialTheme.shapes.medium
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    LoyaltyButton(
        text = stringResource(Res.string.auth_btn_next),
        isLoading = state.isLoading,
        onClick = { onAction(LoginScreenModel.Action.OnSubmitClicked) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TelegramConnectingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    // Анимация 1: Волна (масштаб + исчезновение)
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    // Анимация 2: Пульсация самой иконки (дыхание)
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Расходящийся круг (Эхо)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(Color(0xFF2AABEE).copy(alpha = 0.5f), CircleShape)
            )

            // Основной круг с иконкой
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .background(Color(0xFF2AABEE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send, // Или вектор Telegram
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = (-2).dp, y = 1.dp) // Чуть поправить оптический центр треугольника
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.auth_telegram_pending),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.auth_telegram_connecting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
