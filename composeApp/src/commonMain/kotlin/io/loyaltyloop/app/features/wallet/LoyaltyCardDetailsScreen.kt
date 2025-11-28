package io.loyaltyloop.app.features.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import org.koin.core.parameter.parametersOf

class LoyaltyCardDetailsScreen(
    private val cardId: String
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = getScreenModel<LoyaltyCardDetailsScreenModel> { parametersOf(cardId) }
        val state = viewModel.state.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.value.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = state.value.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

