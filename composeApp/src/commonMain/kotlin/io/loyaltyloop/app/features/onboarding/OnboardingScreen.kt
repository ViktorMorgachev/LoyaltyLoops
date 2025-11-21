package io.loyaltyloop.app.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.main.MainScreen
import io.loyaltyloop.app.features.role.RoleSelectionScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.utils.UiText
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = getScreenModel<OnboardingScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is OnboardingScreenModel.Event.NavigateToHome ->
                        navigator.replaceAll(MainScreen())
                    is OnboardingScreenModel.Event.ShowError -> {
                         val msg = event.message.asStringSuspend()
                         launch { snackbarHostState.showSnackbar(msg) }
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
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

                // Поля ввода
                OutlinedTextField(
                    value = state.firstName,
                    onValueChange = viewModel::onFirstNameChanged,
                    label = { Text(stringResource(Res.string.field_firstname)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),

                    // --- ВАЛИДАЦИЯ ---
                    isError = state.firstNameError != null,
                    supportingText = {
                        state.firstNameError?.let { error ->
                            Text(
                                text = error.asString(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    // ----------------
                )


                Spacer(modifier = Modifier.height(32.dp))

                LoyaltyButton(
                    text = stringResource(Res.string.btn_save),
                    onClick = viewModel::onSaveClicked,
                    isLoading = state.isLoading,
                    enabled = state.firstName.isNotBlank(), // Кнопка активна только если есть имя
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}