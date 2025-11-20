package io.loyaltyloop.app.features.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.Composable // <-- Важный импорт
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.loyaltyloop.app.features.wallet.WalletScreen

object WalletTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val title = "Кошелек"
            val icon = rememberVectorPainter(Icons.Default.QrCode)

            return remember {
                TabOptions(
                    index = 0u,
                    title = title,
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        // Внутри таба показываем наш WalletScreen
        // (Voyager позволяет вкладывать экраны, но для простоты вызовем контент напрямую или через Navigator)
        WalletScreen().Content() 
    }
}