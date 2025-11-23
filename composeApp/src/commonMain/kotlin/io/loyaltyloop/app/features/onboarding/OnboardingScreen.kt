package io.loyaltyloop.app.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.main.MainScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold // Наш скаффолд
import io.loyaltyloop.app.ui.components.show // Наша функция
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<OnboardingScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        // Создаем хост для снекбара
        val snackbarHostState = remember { SnackbarHostState() }
        val keyboardController = LocalSoftwareKeyboardController.current

        // Обработка событий
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is OnboardingScreenModel.Event.NavigateToHome -> {
                        keyboardController?.hide()
                        navigator.replaceAll(MainScreen())
                    }
                    is OnboardingScreenModel.Event.ShowMessage -> {
                        launch {
                            snackbarHostState.show(event.message, event.type)
                        }
                    }
                }
            }
        }

        OnboardingContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onAction = viewModel::onAction
        )
    }
}

@Composable
fun OnboardingContent(
    state: OnboardingScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onAction: (OnboardingScreenModel.Action) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LoyaltyScaffold(
        snackbarHostState = snackbarHostState,
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding() // Учитываем клавиатуру
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(Res.string.onboarding_why_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Поле ввода Имени
            OutlinedTextField(
                value = state.firstName,
                onValueChange = { onAction(OnboardingScreenModel.Action.OnFirstNameChanged(it)) },
                label = { Text(stringResource(Res.string.field_firstname)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isLoading,

                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onAction(OnboardingScreenModel.Action.OnSubmitClicked)
                    }
                ),

                // Валидация
                isError = state.firstNameError != null,
                supportingText = {
                    state.firstNameError?.let { error ->
                        Text(
                            text = error.asString(), // UiText -> String
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                trailingIcon = {
                    if (state.firstNameError != null) {
                        Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            LoyaltyButton(
                text = stringResource(Res.string.btn_save),
                onClick = { onAction(OnboardingScreenModel.Action.OnSubmitClicked) },
                isLoading = state.isLoading,
                // Блокируем кнопку визуально, если поле пустое (дубль валидации для UX)
                enabled = state.firstName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}