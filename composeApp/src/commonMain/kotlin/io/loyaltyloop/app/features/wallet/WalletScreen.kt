package io.loyaltyloop.app.features.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyCardItem
import io.loyaltyloop.app.ui.components.QrCard

class WalletScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<WalletScreenModel>()
        val state by viewModel.state.collectAsState()

        // Состояние для BottomSheet
        var showQrSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        // Pull To Refresh
        val pullRefreshState = rememberPullToRefreshState()
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(Unit) { viewModel.loadCards() }
        }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) pullRefreshState.endRefresh()
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // ПЛАВАЮЩАЯ КНОПКА (FAB) ДЛЯ QR
            floatingActionButton = {
                if (!state.isLoading && state.cards.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { showQrSheet = true },
                        icon = { Icon(Icons.Default.QrCode, "QR") },
                        text = { Text("Мой код") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                if (state.cards.isEmpty() && !state.isLoading) {
                    // --- EMPTY STATE ---
                    EmptyWalletView(onShowQrClicked = { showQrSheet = true })
                } else {
                    // --- СПИСОК КАРТ ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            Text(
                                "Мои карты",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        items(state.cards) { card ->
                            LoyaltyCardItem(card) {
                                // TODO: Детали карты
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        // Отступ снизу под FAB
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }

                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // --- BOTTOM SHEET С QR-КОДОМ ---
            if (showQrSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showQrSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    // Контент шторки
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 48.dp) // Отступ для safe area
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Покажите кассиру",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Наша существующая карточка
                        QrCard(
                            qrContent = state.qrContent,
                            secondsRemaining = state.secondsRemaining
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyWalletView(onShowQrClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "У вас пока нет карт лояльности",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Покажите свой QR-код кассиру при первой покупке, чтобы получить карту.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        LoyaltyButton(
            text = "Показать мой QR",
            onClick = onShowQrClicked,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// QrCard остается без изменений (в том же файле или отдельно)