package io.loyaltyloop.app.platform.web

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

@Composable
actual fun LoyaltyWebView(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    reloadSignal: Int,
    onPageLoading: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    Text("Web view не поддерживается на iOS пока что", modifier = modifier)
}


