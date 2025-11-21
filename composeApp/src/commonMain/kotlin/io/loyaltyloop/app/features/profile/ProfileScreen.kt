package io.loyaltyloop.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
        val viewModel = koinScreenModel<ProfileScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow.parent ?: LocalNavigator.currentOrThrow
        val pullRefreshState = rememberPullToRefreshState()


        LaunchedEffect(Unit) {
            viewModel.loadProfile()
            viewModel.events.collect { event ->
                if (event is ProfileScreenModel.Event.NavigateToSplash) {
                    navigator.replaceAll(SplashScreen())
                }
            }
        }

        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(Unit) {
                viewModel.loadProfile()
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Подключаем скролл-жесты к контейнеру
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                if (state.isLoading && state.phone.isBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // 1. Header (Красивая карточка)
                        item {
                            ProfileHeaderCard(name = state.name.asString(), phone = state.phone)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // --- ГРУППА 1: ТЕКУЩИЕ РАБОЧИЕ МЕСТА ---
                        if (state.workspaces.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(Res.string.profile_header_workspaces))
                            }

                            items(state.workspaces) { workspace ->
                                WorkspaceItem(workspace) {
                                    viewModel.onWorkspaceClicked(workspace)
                                }
                                // Тонкий разделитель между элементами (опционально)
                                if (state.workspaces.last() != workspace) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        thickness = DividerDefaults.Thickness,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }

                        // --- ГРУППА 2: ДЕЙСТВИЯ (Создать / Вступить) ---
                        item {
                            // Отступ, если список выше был
                            Spacer(modifier = Modifier.height(24.dp))

                            SectionTitle(stringResource(Res.string.profile_header_actions))

                            // Создать бизнес
                            SettingsItem(
                                icon = Icons.Default.AddBusiness,
                                title = stringResource(Res.string.profile_btn_create_business),
                                subtitle = stringResource(Res.string.profile_desc_create_business),
                                onClick = viewModel::onCreateBusinessClicked
                            )

                            // Стать сотрудником
                            SettingsItem(
                                icon = Icons.Default.Badge,
                                title = stringResource(Res.string.profile_btn_join_team),
                                subtitle = stringResource(Res.string.profile_desc_join_team),
                                onClick = viewModel::onJoinTeamClicked
                            )
                        }
                        // 3. Секция НАСТРОЙКИ
                        item {
                            SectionTitle(stringResource(Res.string.profile_section_general))

                            SettingsItem(
                                icon = Icons.Default.Language,
                                title = stringResource(Res.string.profile_item_language),
                                value = "Русский", // TODO: Брать из настроек
                                onClick = viewModel::onLanguageClicked
                            )
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            SettingsItem(
                                icon = Icons.AutoMirrored.Filled.Help,
                                title = stringResource(Res.string.profile_item_support),
                                onClick = viewModel::onSupportClicked
                            )
                        }

                        // 4. Футер (Выход и Версия)
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            LogoutButton(onClick = viewModel::onLogoutClicked)

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "v1.0.0 (Build 10)", // TODO: BuildConfig
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }

                    }
                }

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
fun ProfileHeaderCard(name: String, phone: String) {
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