package io.loyaltyloop.app.features.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyCardItem
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.QrCard
import io.loyaltyloop.app.ui.components.show
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class WalletScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<WalletScreenModel>()
        val state by viewModel.state.collectAsState()

        var showQrSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        // Снекбар для ошибок
        val snackbarHostState = remember { SnackbarHostState() }

        // Показываем ошибку, если есть
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when(event) {
                    is WalletScreenModel.Event.ShowMessage -> {
                        launch { snackbarHostState.show(event.message, event.type) }
                    }
                }
            }
        }

        // Pull To Refresh
        val pullRefreshState = rememberPullToRefreshState()
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(Unit) { viewModel.onAction(WalletScreenModel.Action.OnRefresh) }
        }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) pullRefreshState.endRefresh()
        }

        LoyaltyScaffold(
            snackbarHostState = snackbarHostState,
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (!state.isLoading && state.cards.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            viewModel.onAction(WalletScreenModel.Action.OnQrCodeClicked)
                            showQrSheet = true
                        },
                        icon = { Icon(Icons.Default.QrCode, null) },
                        text = { Text(stringResource(Res.string.wallet_my_qr)) },
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
                    EmptyWalletView(onShowQrClicked = { showQrSheet = true })
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(Res.string.wallet_my_cards),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        items(state.cards) { card ->
                            LoyaltyCardItem(card) { /* Детали */ }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }

                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            if (showQrSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showQrSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 48.dp)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(Res.string.qr_sheet_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Вызов компонента
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
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
            text = stringResource(Res.string.wallet_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.wallet_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        LoyaltyButton(
            text = stringResource(Res.string.wallet_show_qr_btn),
            onClick = onShowQrClicked,
            modifier = Modifier.fillMaxWidth()
        )
    }
}