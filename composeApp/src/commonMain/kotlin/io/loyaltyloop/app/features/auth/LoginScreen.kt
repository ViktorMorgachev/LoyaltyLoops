package io.loyaltyloop.app.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.main.MainScreen
import io.loyaltyloop.app.features.onboarding.OnboardingScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.OtpTextField
import io.loyaltyloop.app.utils.MaskVisualTransformation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import loyaltyloop.composeapp.generated.resources.*

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = getScreenModel<LoginScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val keyboardController = LocalSoftwareKeyboardController.current
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is LoginEvent.HideKeyboard -> {
                        keyboardController?.hide()
                    }
                    is LoginEvent.NavigateToHome -> {
                        navigator.replaceAll(MainScreen())
                    }
                    is LoginEvent.ShowError -> {
                        // 1. Получаем строку (асинхронно)
                        val message = event.message.asStringSuspend()
                        // 2. Показываем снекбар (этот вызов приостановит корутину, пока снекбар не исчезнет)
                        // Но так как мы в collect, это может заблокировать обработку следующих событий.
                        // Поэтому лучше запустить отдельную дочернюю корутину для показа.
                        launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                    is LoginEvent.NavigateToOnboarding -> navigator.replaceAll(OnboardingScreen())
                }
            }
        }

        LoginScreenContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onAction = viewModel::onAction
        )
    }
}

/**
 * Чистая UI функция. Никаких ViewModel, только данные.
 * Её легко переиспользовать и тестировать.
 */
@Composable
fun LoginScreenContent(
    state: LoginState,
    snackbarHostState: SnackbarHostState,
    onAction: (LoginAction) -> Unit
) {

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState)},
        topBar = {
            if (state.step == LoginStep.EnterCode) {
                IconButton(onClick = { onAction(LoginAction.OnBackClicked) }) {
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

            if (state.step == LoginStep.EnterPhone) {
                PhoneInputCard(
                    state = state,
                    onAction = onAction
                )
            } else {
                // --- ЭКРАН ВВОДА КОДА ---
                Text(stringResource(Res.string.auth_enter_code), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(32.dp))

                OtpTextField(
                    otpText = state.otpInput,
                    onOtpTextChange = { onAction(LoginAction.OnOtpChanged(it)) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    if (state.isResendEnabled) {
                        // Кнопка активна
                        TextButton(
                            onClick = { onAction(LoginAction.OnResendClicked) }
                        ) {
                            Text(
                                text = stringResource(Res.string.auth_resend_btn),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Таймер тикает (Кнопка неактивна)
                        val timerValue = "00:${state.timerSeconds.toString().padStart(2, '0')}"

                        // 2. Склеиваем текст ресурса и время
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
fun PhoneInputCard(
    state: LoginState,
    onAction: (LoginAction) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Заголовок над полем (аккуратно, серым)
            Text(
                text = stringResource(Res.string.auth_phone_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.phoneInput,
                onValueChange = { onAction(LoginAction.OnPhoneChanged(it)) },

                // --- САМОЕ ВАЖНОЕ: Флаг и Код ВНУТРИ поля ---
                leadingIcon = {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min) // Чтобы разделитель был во всю высоту
                            .padding(start = 12.dp, end = 8.dp)
                            .clickable { onAction(LoginAction.OnCountryClicked) }, // Кликаем только по флагу/коду
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Флаг
                        Text(
                            text = state.selectedCountry.flagEmoji,
                            style = MaterialTheme.typography.headlineSmall // Крупный эмодзи
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // 2. Код страны (+996)
                        Text(
                            text = state.selectedCountry.phonePrefix,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // 3. Вертикальная черта-разделитель
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                },
                // --------------------------------------------

                // Маска ввода (000 000)
                placeholder = {
                    Text(
                        text = state.selectedCountry.mask.replace("#", "0"),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                },

                visualTransformation = MaskVisualTransformation(state.selectedCountry.mask),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading,

                // Стиль вводимых цифр (Крупный, как и код страны)
                textStyle = MaterialTheme.typography.titleLarge,

                // Обработка ошибок (Красная рамка)
                isError = state.phoneError != null,
                supportingText = {
                    state.phoneError?.let { error ->
                        Text(
                            text = error.asString(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                shape = MaterialTheme.shapes.medium // Скругленные углы поля
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    LoyaltyButton(
        text = stringResource(Res.string.auth_btn_next),
        isLoading = state.isLoading,
        onClick = { onAction(LoginAction.OnSubmitClicked) },
        modifier = Modifier.fillMaxWidth()
    )
}

