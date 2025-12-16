package io.loyaltyloop.app.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
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
                if (state.telegramMode) {
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
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.auth_telegram_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        state.telegramBot?.let { bot ->
                            state.telegramUuid?.let { uuid ->
                                uriHandler.openUri("https://t.me/$bot?start=login_$uuid")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.auth_telegram_open))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (state.telegramStatus == "PENDING" && !state.isLoading) {
                 Text(
                    text = stringResource(Res.string.auth_telegram_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                 Spacer(modifier = Modifier.height(16.dp))
            }

            TextButton(
                onClick = { onAction(LoginScreenModel.Action.OnBackToPhoneClicked) }
            ) {
                Text(stringResource(Res.string.auth_telegram_back))
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
