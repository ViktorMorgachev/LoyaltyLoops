package io.loyaltyloop.app.features.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

object LocationsTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val title = "Места" // В ресурсы!
            val icon = rememberVectorPainter(Icons.Default.Place) // Или Store
            return remember { TabOptions(1u, title, icon) }
        }

    @Composable
    override fun Content() {
        // Тут будет список всех заведений
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Список заведений")
        }
    }
}