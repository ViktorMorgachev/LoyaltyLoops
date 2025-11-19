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
import io.loyaltyloop.shared.models.Country

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        // Получаем ViewModel автоматически через Koin
        val viewModel = getScreenModel<LoginScreenModel>()
        val state by viewModel.state.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Вход в LoyaltyLoop",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (state.step == LoginStep.EnterPhone) {
                PhoneInputBlock(state, viewModel)
            } else {
                Text("Введите код из СМС (введи 1111)")
                // Тут будет поле для кода
            }
        }
    }

    @Composable
    fun PhoneInputBlock(state: LoginState, viewModel: LoginScreenModel) {
        // Выбор страны (упрощенный, потом сделаем BottomSheet)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.selectedCountry.flagEmoji + " " + state.selectedCountry.phonePrefix,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable {
                    // Тут можно сделать переключение стран.
                    // Для теста: переключим на следующую по кругу
                    val nextIndex = (state.selectedCountry.ordinal + 1) % Country.values().size
                    viewModel.onCountrySelected(Country.values()[nextIndex])
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedTextField(
                value = state.phoneInput,
                onValueChange = { viewModel.onPhoneChanged(it) },
                label = { Text("Номер телефона") },
                placeholder = { Text(state.selectedCountry.mask) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.onSendCodeClicked() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Получить код")
        }
    }
}