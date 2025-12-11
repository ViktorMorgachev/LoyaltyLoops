package io.loyaltyloop.app.features.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CameraScannerView(
    modifier: Modifier = Modifier,
    onScan: (String) -> Unit
)

