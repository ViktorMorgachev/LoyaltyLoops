package io.loyaltyloop.app.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope // <--- ВАЖНЫЙ ИМПОРТ
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.transitions.SlideTransition
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.features.tabs.LocationsTab
import io.loyaltyloop.app.features.tabs.ProfileTab
import io.loyaltyloop.app.features.tabs.WalletTab
import io.loyaltyloop.app.features.terminal.TerminalScreen
import io.loyaltyloop.shared.models.UserRole
import org.koin.compose.koinInject

class MainScreen : Screen {

    @Composable
    override fun Content() {
        val sessionManager = koinInject<SessionManager>()
        val currentWorkspace by sessionManager.currentWorkspace.collectAsState()

        // Единый обработчик действий
        val onAction: (MainScreenAction) -> Unit = { action ->
            when (action) {
                is MainScreenAction.LogoutToClientMode -> {
                    sessionManager.switchWorkspace(null) // Сброс в null = Клиент
                }
                is MainScreenAction.SwitchWorkspace -> {
                    sessionManager.switchWorkspace(action.workspace)
                }
            }
        }

        // Роутинг
        when (currentWorkspace?.role) {
            null -> ClientUi() // Клиенту пока action не нужен (у него свои табы)
            UserRole.CASHIER -> CashierUi(onAction = onAction)
            UserRole.PARTNER_ADMIN -> PartnerUi(onAction = onAction)
            else -> ClientUi()
        }

    }

    @Composable
    fun ClientUi() {
        // Наши табы: Кошелек, Места, Профиль
        TabNavigator(WalletTab) {
            Scaffold(
                content = { Box(Modifier.padding(it)) { CurrentTab() } },
                bottomBar = {
                    NavigationBar {
                        TabNavigationItem(WalletTab)
                        TabNavigationItem(LocationsTab)
                        TabNavigationItem(ProfileTab)
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CashierUi(
        onAction: (MainScreenAction) -> Unit // <-- Принимаем лямбду
    ) {
        // Запускаем Навигатор специально для флоу Кассира
        // Это позволяет внутри терминала ходить вперед-назад,
        // не ломая глобальную навигацию.
        Navigator(TerminalScreen()) { navigator ->
            Scaffold(
                topBar = {
                    // Добавим кнопку выхода из режима
                    CenterAlignedTopAppBar(
                        title = { Text("Рабочее место") },
                        actions = {
                            TextButton(onClick = { onAction(MainScreenAction.LogoutToClientMode) }) {
                                Text("Выйти")
                            }
                        }
                    )
                }
            ) { padding ->
                // Показываем текущий экран внутри Навигатора с отступами
                Box(Modifier.padding(padding)) {
                    SlideTransition(navigator)
                }
            }
        }
    }

    @Composable
    fun PartnerUi(
        onAction: (MainScreenAction) -> Unit // <-- Принимаем лямбду
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Режим Партнера (Дашборд)")
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { onAction(MainScreenAction.LogoutToClientMode) }) {
                    Text("Выйти в режим клиента")
                }
            }
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