package io.loyaltyloop.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class ProfileScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        // Используем наш кастомный getScreenModel для совместимости с Wasm/JS в будущем
        val viewModel = koinScreenModel<ProfileScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow.parent ?: LocalNavigator.currentOrThrow

        // 1. Навигация
        LaunchedEffect(Unit) {
            // При первом входе грузим данные
            viewModel.onAction(ProfileAction.OnRefresh)

            viewModel.events.collect { event ->
                if (event is ProfileScreenModel.Event.NavigateToSplash) {
                    navigator.replaceAll(SplashScreen())
                }
                if (event is ProfileScreenModel.Event.NavigateToJoinCompany) {
                    navigator.push(io.loyaltyloop.app.features.join.JoinCompanyScreen())
                }
            }
        }

        // 2. Настройка Pull-to-Refresh
        val pullRefreshState = rememberPullToRefreshState()

        // Если юзер потянул вниз (state.isRefreshing стало true) -> вызываем загрузку
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(Unit) {
                viewModel.onAction(ProfileAction.OnRefresh)
            }
        }

        // Если загрузка закончилась (в ViewModel) -> сообщаем об этом UI, чтобы убрать крутилку
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                pullRefreshState.endRefresh()
            }
        }


        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            // Оборачиваем контент в Box с nestedScroll
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(pullRefreshState.nestedScrollConnection) // <-- Магия свайпа здесь
            ) {
                if (state.isLoading && state.phone.isBlank()) {
                    // Показываем большую крутилку только если данных НЕТ совсем
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Основной список
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 16.dp, start = 16.dp, end = 16.dp)
                    ) {
                        // 1. Header
                        item {
                            ProfileHeader(name = state.name.asString(), phone = state.phone)
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // 2. Рабочие места
                        if (state.workspaces.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(Res.string.profile_header_workspaces))
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(state.workspaces) { workspace ->
                                WorkspaceItem(workspace) {
                                    viewModel.onAction(ProfileAction.OnWorkspaceClicked(workspace))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }

                        // 3. Действия
                        item {
                            SectionTitle(stringResource(Res.string.profile_header_actions))

                            SettingsItem(
                                icon = Icons.Default.AddBusiness,
                                title = stringResource(Res.string.profile_btn_create_business),
                                subtitle = stringResource(Res.string.profile_desc_create_business),
                                onClick = { viewModel.onAction(ProfileAction.OnCreateBusinessClicked) }
                            )

                            SettingsItem(
                                icon = Icons.Default.Badge,
                                title = stringResource(Res.string.profile_btn_join_team),
                                subtitle = stringResource(Res.string.profile_desc_join_team),
                                onClick = { viewModel.onAction(ProfileAction.OnJoinTeamClicked) }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // 4. Настройки и Футер
                        item {
                            SectionTitle(stringResource(Res.string.profile_section_general))

                            SettingsItem(
                                icon = Icons.Default.Language,
                                title = stringResource(Res.string.profile_item_language),
                                value = "Русский",
                                onClick = { viewModel.onAction(ProfileAction.OnLanguageClicked) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                            SettingsItem(
                                icon = Icons.AutoMirrored.Filled.Help,
                                title = stringResource(Res.string.profile_item_support),
                                onClick = { viewModel.onAction(ProfileAction.OnSupportClicked) }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        item {
                            LogoutButton(onClick = { viewModel.onAction(ProfileAction.OnLogoutClicked) })
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "v1.0.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                // Индикатор обновления (поверх списка)
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// --- КОМПОНЕНТЫ ---

@Composable
fun ProfileHeader(name: String, phone: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватарка (Градиент или цвет)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = phone,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun WorkspaceItem(workspace: UserWorkspace, onClick: () -> Unit) {
    // Выбираем иконку и текст в зависимости от роли
    val (icon, roleRes) = when (workspace.role) {
        UserRole.PARTNER_ADMIN -> Pair(Icons.Default.Business, Res.string.profile_role_owner)
        UserRole.CASHIER -> Pair(Icons.Default.Store, Res.string.profile_role_cashier)

        // Для Админов платформы
        UserRole.PLATFORM_SUPER_ADMIN -> Pair(Icons.Default.Security, Res.string.profile_role_admin)
        UserRole.PLATFORM_MANAGER -> Pair(Icons.Default.SupervisorAccount, Res.string.profile_role_manager)

        // Клиент сюда попасть не должен, но на всякий случай
        UserRole.CLIENT -> Pair(Icons.Default.Person, Res.string.app_name)
    }

    SettingsItem(
        icon = icon,
        title = workspace.title,
        subtitle = stringResource(roleRes),
        onClick = onClick
    )
}

@Composable
fun LogoutButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.profile_btn_logout),
            color = MaterialTheme.colorScheme.error
        )
    }
}

// ... Компоненты ProfileHeader, SettingsItem, WorkspaceItem