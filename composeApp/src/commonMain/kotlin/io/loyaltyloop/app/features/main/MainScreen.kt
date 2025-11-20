package io.loyaltyloop.app.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope // <--- ВАЖНЫЙ ИМПОРТ
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import io.loyaltyloop.app.features.tabs.ProfileTab
import io.loyaltyloop.app.features.tabs.WalletTab

class MainScreen : Screen {
    @Composable
    override fun Content() {
        TabNavigator(WalletTab) {
            Scaffold(
                content = { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        CurrentTab()
                    }
                },
                bottomBar = {
                    NavigationBar {
                        // Теперь эти вызовы сработают, так как мы внутри RowScope
                        TabNavigationItem(WalletTab)
                        TabNavigationItem(ProfileTab)
                    }
                }
            )
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val options = tab.options

    NavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        icon = {
            options.icon?.let { icon ->
                Icon(painter = icon, contentDescription = options.title)
            }
        },
        label = {
            Text(text = options.title)
        }
    )
}