package io.loyaltyloop.app.features.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.terminal.result.TerminalResultScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton

class TerminalScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<TerminalScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow.parent ?: LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when(event) {
                    is TerminalScreenModel.Event.ShowError -> {
                        snackbarHostState.showSnackbar(event.message)
                    }
                    is TerminalScreenModel.Event.NavigateToResult -> {
                        navigator.push(TerminalResultScreen(event.scanData))
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- БЛОК КАМЕРЫ (Заглушка) ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black, shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QrCodeScanner, 
                            contentDescription = null, 
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Наведите камеру на QR-код", color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // --- БЛОК РУЧНОГО ВВОДА (Для тестов) ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Режим отладки (Эмулятор)", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = state.manualInput,
                            onValueChange = viewModel::onManualInputChanged,
                            label = { Text("Вставьте строку QR") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LoyaltyButton(
                            text = "Имитировать сканирование",
                            onClick = { viewModel.onScan(state.manualInput) },
                            isLoading = state.isLoading,
                            enabled = state.manualInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}