package io.loyaltyloop.app.features.terminal.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.ScanQrResponse
import org.koin.core.parameter.parametersOf

data class TerminalResultScreen(val scanData: ScanQrResponse) : Screen {
    @Composable
    override fun Content() {
        // Передаем параметры в Koin для создания ViewModel
        val viewModel = koinScreenModel<TerminalResultScreenModel> { parametersOf(scanData) }
        val state by viewModel.state.collectAsState()

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
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
                Divider()
                Spacer(modifier = Modifier.height(32.dp))

                // --- АДАПТИВНЫЙ КОНТЕНТ ---
                if (state.data.programType == LoyaltyProgramType.VISIT_COUNTER) {
                    VisitsContent(state.data)
                } else {
                    TieredContent(state, viewModel::onAmountChanged)
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // --- КНОПКА ДЕЙСТВИЯ ---
                val btnText = if (state.data.programType == LoyaltyProgramType.VISIT_COUNTER) {
                    "Засчитать визит (+1)"
                } else {
                    "Начислить бонусы"
                }
                
                LoyaltyButton(
                    text = btnText,
                    onClick = viewModel::onProcessTransaction,
                    // Блокируем, если сумма пустая (для TIERED)
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
            Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = data.firstName ?: "Клиент",
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
            SuggestionChip(onClick = {}, label = { Text("Новый клиент! 🎉") })
        }
    }
}

@Composable
fun VisitsContent(data: ScanQrResponse) {
    val target = data.visitsTarget ?: 10
    val current = data.visitsCount
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("АКЦИЯ: $target-й товар в подарок", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Визуализация кружочков
        Row(horizontalArrangement = Arrangement.Center) {
            Text(
                text = "$current / $target",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        if (current >= target - 1) {
             Text("СЛЕДУЮЩИЙ ВИЗИТ - ПРИЗОВОЙ! 🎁", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TieredContent(state: TerminalResultScreenModel.State, onAmountChange: (String) -> Unit) {
    Column {
        Text("Накопительная система", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = state.purchaseAmount,
            onValueChange = onAmountChange,
            label = { Text("Сумма чека") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (state.pointsToAward > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Будет начислено: ${state.pointsToAward.toInt()} Б",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("Текущий баланс: ${state.data.currentBalance.toInt()}", style = MaterialTheme.typography.bodyMedium)
    }
}