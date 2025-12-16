package io.loyaltyloop.app.features.terminal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.terminal.result.TerminalResultScreen
import io.loyaltyloop.app.features.terminal.stats.CashierStatsScreen
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import io.loyaltyloop.shared.models.TransactionStrategy
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.QrShapes
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

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
                when (event) {
                    is TerminalScreenModel.Event.ShowMessage -> {
                        launch { snackbarHostState.show(event.message, event.type) }
                    }

                    is TerminalScreenModel.Event.NavigateToResult -> {
                        navigator.push(
                            TerminalResultScreen(
                                event.scanData,
                                event.tradingPointId,
                                event.strategy
                            )
                        )
                    }

                    is TerminalScreenModel.Event.NavigateToStats -> {
                        navigator.push(CashierStatsScreen())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalContent(
    state: TerminalScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onAction: (TerminalScreenModel.Action) -> Unit
) {
    val playStoreUrl = "https://play.google.com/store/apps/details?id=io.loyaltyloop.app"
    var showDownloadSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showDownloadSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.terminal_download_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.terminal_download_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                val painter = rememberQrCodePainter(
                    data = playStoreUrl,
                    shapes = QrShapes(
                        ball = QrBallShape.roundCorners(.25f),
                        frame = QrFrameShape.roundCorners(.25f),
                        darkPixel = QrPixelShape.roundCorners(.5f)
                    )
                )
                Image(
                    painter = painter,
                    contentDescription = "Play Market QR",
                    modifier = Modifier.size(220.dp)
                )
                Button(
                    onClick = { showDownloadSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.terminal_download_hide_qr))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (state.showHybridDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss to force choice */ },
            title = { Text(stringResource(Res.string.hybrid_dialog_title)) },
            text = { Text(stringResource(Res.string.hybrid_dialog_text)) },
            confirmButton = {
                Button(onClick = {
                    onAction(
                        TerminalScreenModel.Action.OnHybridStrategySelected(
                            TransactionStrategy.CHARGE
                        )
                    )
                }) {
                    Text(stringResource(Res.string.hybrid_action_bonus))
                }
            },
            dismissButton = {
                Button(onClick = {
                    onAction(
                        TerminalScreenModel.Action.OnHybridStrategySelected(
                            TransactionStrategy.VISIT
                        )
                    )
                }) {
                    Text(stringResource(Res.string.hybrid_action_visit))
                }
            }
        )
    }

    var selectedTab by remember { mutableStateOf(0) }

    LoyaltyScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            Column {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(Res.string.terminal_tab_scanner)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(Res.string.terminal_tab_stats)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedTab == 0) {
                // SCANNER TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // QR Scanner inline with overlay control
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            var localScannerActive by remember { mutableStateOf(false) }

                            LaunchedEffect(state.manualInput) {
                                // no-op, keep composition aware
                            }

                            if (localScannerActive) {
                                CameraScannerView(
                                    modifier = Modifier.fillMaxSize(),
                                    onScan = { result ->
                                        onAction(TerminalScreenModel.Action.OnQrScanned(result))
                                        localScannerActive = false
                                    }
                                )
                            }

                            if (!localScannerActive) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(Res.string.terminal_camera_instruction),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(onClick = {
                                        localScannerActive = true
                                    }) {
                                        Text(stringResource(Res.string.terminal_btn_scan))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- DOWNLOAD QR ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.terminal_download_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(Res.string.terminal_download_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showDownloadSheet = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(Res.string.terminal_download_show_qr))
                            }
                        }
                    }
                }
            } else {
                // STATS TAB
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // CashierStatsScreen().Content() // Temporarily disabled
                    ComingSoonPlaceholder()
                }
            }

            // LOADER OVERLAY
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {}, // Block clicks
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun ComingSoonPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.coming_soon_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.coming_soon_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
