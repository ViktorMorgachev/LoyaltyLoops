package io.loyaltyloop.app.features.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.Composable // <-- Важный импорт
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.loyaltyloop.app.features.wallet.WalletScreen
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.tab_wallet
import org.jetbrains.compose.resources.stringResource

object WalletTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val title = stringResource(Res.string.tab_wallet)
            val icon = rememberVectorPainter(Icons.Default.QrCode)

            return remember(title) {
                TabOptions(
                    index = 0u,
                    title = title,
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        // Делегируем отрисовку экрану
        WalletScreen().Content()
    }
}