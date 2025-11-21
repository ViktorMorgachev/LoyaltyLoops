package io.loyaltyloop.app.features.role

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.main.MainScreen
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class RoleSelectionScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinScreenModel<RoleSelectionScreenModel>()

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(Res.string.role_select_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // 1. КЛИЕНТ -> Сразу на главную
                RoleCard(
                    title = stringResource(Res.string.role_client_title),
                    desc = stringResource(Res.string.role_client_desc),
                    icon = Icons.Default.Person,
                    onClick = {
                        viewModel.onRoleSelected()
                        navigator.replaceAll(MainScreen())
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. ВЛАДЕЛЕЦ -> Флоу регистрации бизнеса
                RoleCard(
                    title = stringResource(Res.string.role_partner_title),
                    desc = stringResource(Res.string.role_partner_desc),
                    icon = Icons.Default.Business,
                    onClick = {
                        viewModel.onRoleSelected()
                        // Для теста можно тоже кинуть на Home
                        navigator.replaceAll(MainScreen())
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. СОТРУДНИК -> Ввод инвайта
                RoleCard(
                    title = stringResource(Res.string.role_cashier_title),
                    desc = stringResource(Res.string.role_cashier_desc),
                    icon = Icons.Default.Work,
                    onClick = {
                        viewModel.onRoleSelected()
                        // Для теста можно тоже кинуть на Home
                        navigator.replaceAll(MainScreen())
                    }
                )
            }
        }
    }

    @Composable
    fun RoleCard(title: String, desc: String, icon: ImageVector, onClick: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}