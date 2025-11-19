package io.loyaltyloop.app.features.join

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.ui.components.LoyaltyButton
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.* // (Если используем ресурсы)

class JoinCompanyScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinScreenModel<JoinCompanyScreenModel>()
        val state by viewModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        // Обработка событий (Успех -> Назад, Ошибка -> Снекбар)
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is JoinCompanyScreenModel.Event.NavigateBack -> {
                        navigator.pop()
                    }
                    is JoinCompanyScreenModel.Event.ShowMessage -> {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Стать сотрудником") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Введите код приглашения, который вам дал владелец заведения.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChanged,
                    label = { Text("Инвайт-код (например: 123456)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters, // Обычно коды капсом
                        imeAction = ImeAction.Done
                    ),
                    // Отображение ошибки
                    isError = state.error != null,
                    supportingText = {
                        state.error?.let {
                            Text(text = it.asString(), color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                LoyaltyButton(
                    text = "Присоединиться",
                    onClick = viewModel::onSubmit,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.code.isNotBlank()
                )
            }
        }
    }
}