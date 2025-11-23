package io.loyaltyloop.app.features.terminal.confirmation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import io.loyaltyloop.app.features.terminal.TerminalScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import io.loyaltyloop.app.utils.TransactionCalculationMessageMapper
import io.loyaltyloop.app.utils.formatAmount
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

data class TransactionConfirmationScreen(
    val calculation: TransactionCalculationDto,
    val tradingPointId: String,
    val cardId: String,
    val strategy: TransactionStrategy,
    val currency: String
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<TransactionConfirmationScreenModel> { parametersOf(calculation, tradingPointId, cardId, strategy) }
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.current
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when(event) {
                    is TransactionConfirmationScreenModel.Event.ShowMessage -> {
                        launch { snackbarHostState.show(event.message, event.type) }
                    }
                    is TransactionConfirmationScreenModel.Event.NavigateBack -> {
                        navigator?.pop()
                    }
                    is TransactionConfirmationScreenModel.Event.NavigateToScan -> {
                        // Возвращаемся на экран сканирования (TerminalScreen)
                        // popUntil { it is TerminalScreen }
                        navigator?.popUntil { it is TerminalScreen }
                    }
                }
            }
        }

        LoyaltyScaffold(
            snackbarHostState = snackbarHostState,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.term_confirm_title)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(TransactionCalculationMessageMapper.map(calculation.message).asString()) },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                val formatCurrency: (Double) -> String = { value ->
                    val formatted = formatAmount(value)
                    if (currency.isNotBlank()) "$formatted $currency" else formatted
                }
                val pointsSuffix = stringResource(Res.string.term_res_points_suffix)
                val formatPoints: (Double) -> String = { value ->
                    "${formatAmount(value)} $pointsSuffix"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            stringResource(Res.string.term_confirm_purchase),
                            formatCurrency(calculation.purchaseAmount)
                        )
                        
                        if (calculation.pointsSpent > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow(
                                stringResource(Res.string.term_confirm_spent),
                                "-${formatPoints(calculation.pointsSpent)}",
                                MaterialTheme.colorScheme.error
                            )
                        }
                        
                        if (calculation.pointsToAward > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow(
                                stringResource(Res.string.term_confirm_earned),
                                "+${formatPoints(calculation.pointsToAward)}",
                                MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(Res.string.term_confirm_total_pay), style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = formatCurrency(calculation.moneyPaid),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Balance Forecast
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(stringResource(Res.string.term_confirm_balance), formatPoints(calculation.newBalance))
                        if (calculation.newVisits > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            InfoRow(stringResource(Res.string.term_confirm_visits), "${calculation.newVisits}")
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                LoyaltyButton(
                    text = stringResource(Res.string.term_btn_confirm),
                    onClick = { viewModel.onConfirm() },
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun InfoRow(title: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

