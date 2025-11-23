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
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import io.loyaltyloop.shared.models.TransactionStrategy
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
                    is TerminalScreenModel.Event.ShowMessage -> {
                        launch { snackbarHostState.show(event.message, event.type) }
                    }
                    is TerminalScreenModel.Event.NavigateToResult -> {
                        navigator.push(TerminalResultScreen(event.scanData, event.tradingPointId, event.strategy))
                    }
                }
            }
        }

        TerminalContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onAction = viewModel::onAction
        )
    }
}

@Composable
fun TerminalContent(
    state: TerminalScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onAction: (TerminalScreenModel.Action) -> Unit
) {
    if (state.showHybridDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss to force choice */ },
            title = { Text(stringResource(Res.string.hybrid_dialog_title)) },
            text = { Text(stringResource(Res.string.hybrid_dialog_text)) },
            confirmButton = {
                Button(onClick = { onAction(TerminalScreenModel.Action.OnHybridStrategySelected(TransactionStrategy.CHARGE)) }) {
                    Text(stringResource(Res.string.hybrid_action_bonus))
                }
            },
            dismissButton = {
                Button(onClick = { onAction(TerminalScreenModel.Action.OnHybridStrategySelected(TransactionStrategy.VISIT)) }) {
                    Text(stringResource(Res.string.hybrid_action_visit))
                }
            }
        )
    }

    LoyaltyScaffold(
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- CAMERA PLACEHOLDER ---
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
                    Text(
                        text = stringResource(Res.string.terminal_camera_hint),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- MANUAL INPUT (DEBUG) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.terminal_debug_title),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.manualInput,
                        onValueChange = { onAction(TerminalScreenModel.Action.OnManualInputChanged(it)) },
                        label = { Text(stringResource(Res.string.terminal_input_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LoyaltyButton(
                        text = stringResource(Res.string.terminal_btn_scan),
                        onClick = { onAction(TerminalScreenModel.Action.OnScanClicked) },
                        isLoading = state.isLoading,
                        enabled = state.manualInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
