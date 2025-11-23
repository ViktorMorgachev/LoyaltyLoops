package io.loyaltyloop.app.features.terminal.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.ScanQrResponse
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import kotlinx.coroutines.launch

data class TerminalResultScreen(val scanData: ScanQrResponse) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        // Передаем параметры в Koin для создания ViewModel
        val viewModel = koinScreenModel<TerminalResultScreenModel> { parametersOf(scanData) }
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
                }
            }
        }

        LoyaltyScaffold(
            snackbarHostState = snackbarHostState,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.term_res_title)) }, // Локализовано
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
                // --- ШАПКА КЛИЕНТА ---
                CustomerHeader(state.data)

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(32.dp))

                // --- АДАПТИВНЫЙ КОНТЕНТ ---
                if (state.data.programType == LoyaltyProgramType.VISIT_COUNTER) {
                    VisitsContent(state.data)
                } else {
                    TieredContent(
                        state = state,
                        onAmountChange = { viewModel.onAction(TerminalResultScreenModel.Action.OnAmountChanged(it)) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // --- КНОПКА ДЕЙСТВИЯ ---
                val btnText = if (state.data.programType == LoyaltyProgramType.VISIT_COUNTER) {
                    stringResource(Res.string.term_res_btn_add_visit)
                } else {
                    stringResource(Res.string.term_res_btn_add_points)
                }

                LoyaltyButton(
                    text = btnText,
                    onClick = { viewModel.onAction(TerminalResultScreenModel.Action.OnProcessClicked) },
                    isLoading = state.isLoading,
                    enabled = state.data.programType == LoyaltyProgramType.VISIT_COUNTER || state.purchaseAmount.isNotEmpty(),
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
            text = data.firstName ?: stringResource(Res.string.term_res_client_default), // Локализовано
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
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

    // Склеиваем строку в коде (безопасно для KMP)
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
fun TieredContent(state: TerminalResultScreenModel.State, onAmountChange: (String) -> Unit) {
    Column {
        Text(stringResource(Res.string.term_res_tiered_title), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.purchaseAmount,
            onValueChange = onAmountChange,
            label = { Text(stringResource(Res.string.term_res_amount_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.pointsToAward > 0) {
            val accrualText = "${stringResource(Res.string.term_res_calc_prefix)} ${state.pointsToAward.toInt()} ${stringResource(Res.string.term_res_points_suffix)}"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = accrualText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val balanceText = "${stringResource(Res.string.term_res_balance_prefix)} ${state.data.currentBalance.toInt()}"
        Text(balanceText, style = MaterialTheme.typography.bodyMedium)
    }
}