package io.loyaltyloop.app.features.terminal.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.loyaltyloop.shared.models.RiskLevel
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TransactionStrategy
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch
import io.loyaltyloop.shared.utils.getCurrencySymbol
import io.loyaltyloop.app.features.terminal.rating.RateClientScreen

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
                    is TerminalResultScreenModel.Event.NavigateToRating -> {
                        navigator?.replace(RateClientScreen(event.userId, event.tradingPointId))
                    }
                    is TerminalResultScreenModel.Event.NavigateToConfirmation -> {
                        navigator?.push(
                            TransactionConfirmationScreen(
                                calculation = event.calculation,
                                tradingPointId = event.tradingPointId,
                                cardId = event.cardId,
                                userId = event.userId,
                                strategy = event.strategy,
                                currency = event.currency
                            )
                        )
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
    // Clamp BLACK to RED on UI — no explicit fraud status shown
    val displayLevel = if (data.riskLevel == RiskLevel.BLACK) RiskLevel.RED else data.riskLevel

    val riskColor = when (displayLevel) {
        RiskLevel.GREEN -> Color(0xFF4CAF50)
        RiskLevel.YELLOW -> Color(0xFFFFC107)
        RiskLevel.ORANGE -> Color(0xFFFF9800)
        RiskLevel.RED -> Color(0xFFF44336)
        RiskLevel.BLACK -> Color(0xFFF44336)
    }
    
    val riskText = when (displayLevel) {
        RiskLevel.GREEN -> stringResource(Res.string.risk_level_green)
        RiskLevel.YELLOW -> stringResource(Res.string.risk_level_yellow)
        RiskLevel.ORANGE -> stringResource(Res.string.risk_level_orange)
        RiskLevel.RED -> stringResource(Res.string.risk_level_red)
        RiskLevel.BLACK -> stringResource(Res.string.risk_level_red)
    }

    val riskDesc = when (displayLevel) {
        RiskLevel.GREEN -> stringResource(Res.string.risk_desc_green)
        RiskLevel.YELLOW -> stringResource(Res.string.risk_desc_yellow)
        RiskLevel.ORANGE -> stringResource(Res.string.risk_desc_orange)
        RiskLevel.RED -> stringResource(Res.string.risk_desc_red)
        RiskLevel.BLACK -> stringResource(Res.string.risk_desc_red)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            
            // Risk Indicator Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(riskColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = data.trustScore.toString().take(3), 
                        style = MaterialTheme.typography.labelSmall, 
                        color = if (displayLevel == RiskLevel.YELLOW) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
        
        // Risk Label
        Spacer(modifier = Modifier.height(4.dp))
        AssistChip(
            onClick = {},
            label = { Text(riskText) },
            leadingIcon = {
                Icon(Icons.Default.Shield, null, tint = riskColor, modifier = Modifier.size(16.dp))
            },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = riskColor
            )
        )

        if (data.isNewCard) {
            Spacer(modifier = Modifier.height(8.dp))
            SuggestionChip(
                onClick = {},
                label = { Text(stringResource(Res.string.term_res_new_client_chip)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
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
            suffix = { Text(getCurrencySymbol(state.data.currency), style = MaterialTheme.typography.headlineSmall) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.term_res_amount_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            AnimatedVisibility(
                visible = state.isSpendingPoints,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.term_res_spend_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            if (state.data.awardOnMixedPayment && state.isSpendingPoints) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.term_res_award_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}
