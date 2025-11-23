package io.loyaltyloop.app.features.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
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
                    sessionManager.switchWorkspace(null)
                }
                is MainScreenAction.SwitchWorkspace -> {
                    sessionManager.switchWorkspace(action.workspace)
                }
            }
        }

        // Роутинг
        when (currentWorkspace?.role) {
            null -> ClientUi() // Режим Клиента (по умолчанию)
            UserRole.CASHIER -> CashierUi(onAction = onAction)
            UserRole.PARTNER_ADMIN -> PartnerUi(onAction = onAction)
            // Для админов платформы тоже можно сделать заглушку или перекинуть на PartnerUi
            UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_MANAGER -> PartnerUi(onAction = onAction)
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
        onAction: (MainScreenAction) -> Unit
    ) {
        // Запускаем Навигатор специально для флоу Кассира
        Navigator(TerminalScreen()) { navigator ->
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(Res.string.workspace_cashier_title)) },
                        actions = {
                            TextButton(onClick = { onAction(MainScreenAction.LogoutToClientMode) }) {
                                Text(stringResource(Res.string.btn_exit_short))
                            }
                        }
                    )
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    SlideTransition(navigator)
                }
            }
        }
    }

    @Composable
    fun PartnerUi(
        onAction: (MainScreenAction) -> Unit
    ) {
        // Экран-заглушка для Партнера (так как управление в Вебе)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Web,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.workspace_partner_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.workspace_partner_stub),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = { onAction(MainScreenAction.LogoutToClientMode) }) {
                    Text(stringResource(Res.string.btn_exit_mode))
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