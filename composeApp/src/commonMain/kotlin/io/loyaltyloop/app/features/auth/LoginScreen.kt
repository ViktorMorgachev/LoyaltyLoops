package io.loyaltyloop.app.features.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import io.loyaltyloop.app.ui.theme.LoyaltyTheme
import io.loyaltyloop.shared.models.Country
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import loyaltyloop.composeapp.generated.resources.*

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = getScreenModel<LoginScreenModel>()
        val state by viewModel.state.collectAsState()

        // Передаем состояние и действия в чистую UI функцию
        LoginScreenContent(
            state = state,
            onPhoneChanged = viewModel::onPhoneChanged,
            onCountryClicked = {
                val nextIndex = (state.selectedCountry.ordinal + 1) % Country.entries.size
                viewModel.onCountrySelected(Country.entries[nextIndex])
            },
            onNextClicked = viewModel::onSendCodeClicked
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
    onPhoneChanged: (String) -> Unit,
    onCountryClicked: () -> Unit,
    onNextClicked: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Используем ресурсы!
            Text(
                text = stringResource(Res.string.auth_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (state.step == LoginStep.EnterPhone) {
                // Встраиваем блок ввода
                PhoneInputCard(
                    state = state,
                    onPhoneChanged = onPhoneChanged,
                    onCountryClicked = onCountryClicked,
                    onNextClicked = onNextClicked
                )
            } else {
                Text(stringResource(Res.string.auth_enter_code))
            }
        }
    }
}

@Composable
fun PhoneInputCard(
    state: LoginState,
    onPhoneChanged: (String) -> Unit,
    onCountryClicked: () -> Unit,
    onNextClicked: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.auth_phone_label), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.selectedCountry.flagEmoji + " " + state.selectedCountry.phonePrefix,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clickable { onCountryClicked() }
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.phoneInput,
                    onValueChange = onPhoneChanged,
                    placeholder = { Text(state.selectedCountry.mask) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onNextClicked,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(stringResource(Res.string.auth_btn_next), style = MaterialTheme.typography.titleMedium)
    }
}

// --- PREVIEWS ---

@Preview
@Composable
fun LoginScreenPreview() {
    LoyaltyTheme {
        LoginScreenContent(
            state = LoginState(
                phoneInput = "555 123",
                selectedCountry = Country.KYRGYZSTAN
            ),
            onPhoneChanged = {},
            onCountryClicked = {},
            onNextClicked = {}
        )
    }
}

@Preview
@Composable
fun DarkLoginScreenPreview() {
    LoyaltyTheme(useDarkTheme = true) {
        LoginScreenContent(
            state = LoginState(
                phoneInput = "777 000",
                selectedCountry = Country.KAZAKHSTAN
            ),
            onPhoneChanged = {},
            onCountryClicked = {},
            onNextClicked = {}
        )
    }
}