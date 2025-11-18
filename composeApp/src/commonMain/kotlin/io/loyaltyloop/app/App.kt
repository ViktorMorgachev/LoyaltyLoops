package io.loyaltyloop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.UserRole
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.random.Random

@Composable
fun App() {
    MaterialTheme {
        // Получаем репозиторий через Koin
        val repository = koinInject<AuthRepository>()

        // Состояние UI
        var statusText by remember { mutableStateOf("Нажми кнопку для теста") }
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = statusText)

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                statusText = "Отправка..."

                scope.launch {
                    // Создаем тестового юзера
                    val testUser = UserDto(
                        id = "mobile_user_${Random.nextInt()}", // Уникальный ID
                        phoneNumber = "+996555777888",
                        role = UserRole.CLIENT,
                        countryCode = "KG"
                    )

                    // Вызываем сервер
                    val result = repository.register(testUser)
                    statusText = result
                }
            }) {
                Text("Проверить Сервер")
            }
        }
    }
}