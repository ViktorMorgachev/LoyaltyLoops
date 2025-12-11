package io.loyaltyloop.app.features.terminal.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.shared.utils.formatCurrency
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.stats_title
import loyaltyloop.composeapp.generated.resources.stats_transactions
import loyaltyloop.composeapp.generated.resources.stats_revenue
import loyaltyloop.composeapp.generated.resources.stats_points_awarded
import loyaltyloop.composeapp.generated.resources.stats_points_spent
import loyaltyloop.composeapp.generated.resources.stats_visits
import loyaltyloop.composeapp.generated.resources.btn_retry
import org.jetbrains.compose.resources.stringResource

class CashierStatsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<CashierStatsScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow


        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.stats_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (state.error != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        LoyaltyButton(text = stringResource(Res.string.btn_retry), onClick = { viewModel.loadStats() })
                    }
                } else {
                    val stats = state.stats
                    if (stats != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            StatCard(
                                title = stringResource(Res.string.stats_transactions),
                                value = stats.transactionsCount.toString()
                            )
                            StatCard(
                                title = stringResource(Res.string.stats_revenue),
                                value = formatCurrency(stats.totalRevenue, stats.currency)
                            )
                            StatCard(
                                title = stringResource(Res.string.stats_points_awarded),
                                value = "${stats.pointsAwarded.toInt()} B"
                            )
                            StatCard(
                                title = stringResource(Res.string.stats_points_spent),
                                value = "${stats.pointsSpent.toInt()} B"
                            )
                            StatCard(
                                title = stringResource(Res.string.stats_visits),
                                value = stats.visitsRecorded.toString()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
