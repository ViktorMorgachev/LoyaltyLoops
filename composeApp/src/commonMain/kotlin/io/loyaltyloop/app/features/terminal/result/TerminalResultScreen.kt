package io.loyaltyloop.app.features.terminal.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import io.loyaltyloop.app.features.terminal.confirmation.TransactionConfirmationScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TransactionStrategy
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

data class TerminalResultScreen(val scanData: ScanQrResponse, val tradingPointId: String, val strategy: TransactionStrategy) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<TerminalResultScreenModel> { parametersOf(scanData, tradingPointId, strategy) }
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.current
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when(event) {
                    is TerminalResultScreenModel.Event.ShowMessage -> {
                        launch { snackbarHostState.show(event.message, event.type) }
                    }
                    is TerminalResultScreenModel.Event.NavigateBack -> {
                        navigator?.pop()
                    }
                    is TerminalResultScreenModel.Event.NavigateToConfirmation -> {
                        navigator?.push(TransactionConfirmationScreen(
                            calculation = event.calculation,
                            tradingPointId = event.tradingPointId,
                            cardId = event.cardId,
                            strategy = event.strategy
                        ))
                    }
                }
            }
        }

        LoyaltyScaffold(
            snackbarHostState = snackbarHostState,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.term_res_title)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onAction(TerminalResultScreenModel.Action.OnBackClicked) }) {
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
                CustomerHeader(state.data)

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(32.dp))

                if (state.data.programType == LoyaltyProgramType.VISIT_COUNTER || state.strategy == TransactionStrategy.VISIT) {
                    VisitsContent(state.data)
                } else {
                    TieredContent(
                        state = state,
                        onAmountChange = { viewModel.onAction(TerminalResultScreenModel.Action.OnAmountChanged(it)) },
                        onToggleSpend = { viewModel.onAction(TerminalResultScreenModel.Action.OnToggleSpend(it)) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // --- КНОПКА ДЕЙСТВИЯ ---
                // Для визитов кнопка может сразу быть "Add Visit" и вести на подтверждение с нулевой суммой
                val btnText = if (state.data.programType == LoyaltyProgramType.VISIT_COUNTER) {
                    stringResource(Res.string.term_res_btn_add_visit)
                } else {
                    stringResource(Res.string.term_btn_next)
                }

                LoyaltyButton(
                    text = btnText,
                    onClick = { viewModel.onAction(TerminalResultScreenModel.Action.OnNextClicked) },
                    isLoading = state.isLoading,
                    enabled = state.strategy == TransactionStrategy.VISIT || state.purchaseAmount.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CustomerHeader(data: ScanQrResponse) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = data.firstName.takeIf { !it.isNullOrBlank() } ?: stringResource(Res.string.term_res_client_default),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = data.userPhone,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        if (data.isNewCard) {
            Spacer(modifier = Modifier.height(8.dp))
            SuggestionChip(
                onClick = {},
                label = { Text(stringResource(Res.string.term_res_new_client_chip)) }
            )
        }
    }
}

@Composable
fun VisitsContent(data: ScanQrResponse) {
    val target = data.visitsTarget ?: 10
    val current = data.visitsCount
    val actionText = "${stringResource(Res.string.term_res_visit_action_prefix)}$target${stringResource(Res.string.term_res_visit_action_suffix)}"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(actionText, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.Center) {
            Text(
                text = "$current / $target",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (current >= target - 1) {
            Text(
                stringResource(Res.string.term_res_next_free),
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun TieredContent(
    state: TerminalResultScreenModel.State, 
    onAmountChange: (String) -> Unit,
    onToggleSpend: (Boolean) -> Unit
) {
    Column {
        Text(stringResource(Res.string.term_res_tiered_title), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.purchaseAmount,
            onValueChange = onAmountChange,
            label = { Text(stringResource(Res.string.term_res_amount_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.headlineSmall,
            suffix = { Text(state.data.currency, style = MaterialTheme.typography.headlineSmall) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.data.currentBalance > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Switch(
                    checked = state.isSpendingPoints,
                    onCheckedChange = onToggleSpend
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(stringResource(Res.string.term_res_spend_title), style = MaterialTheme.typography.bodyLarge)
                    val balanceText = "${stringResource(Res.string.term_res_spend_available)} ${state.data.currentBalance.toInt()}"
                    if (state.isSpendingPoints) {
                        // Можно показать подсказку "Будут списаны доступные баллы"
                        Text(balanceText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(balanceText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
