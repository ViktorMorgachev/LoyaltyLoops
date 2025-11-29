package io.loyaltyloop.app.platform.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun LoyaltyWebView(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
    reloadSignal: Int = 0,
    onPageLoading: (Boolean) -> Unit = {},
    onError: (String) -> Unit = {}
)


