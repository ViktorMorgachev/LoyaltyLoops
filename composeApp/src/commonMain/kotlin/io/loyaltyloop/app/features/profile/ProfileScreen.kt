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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = getScreenModel<ProfileScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow.parent ?: LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                if (event is ProfileScreenModel.Event.NavigateToSplash) {
                    navigator.replaceAll(SplashScreen())
                }
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
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
                        ProfileHeaderCard(state.name.asString(), state.phone)
                    }

                    // 2. Секция БИЗНЕС (Workspaces + Actions)
                    item {
                        SectionTitle(stringResource(Res.string.profile_section_business))
                    }

                    // Если есть рабочие места - показываем их
                    if (state.workspaces.isNotEmpty()) {
                        items(state.workspaces) { workspace ->
                            WorkspaceItem(workspace) { viewModel.onWorkspaceClicked(workspace) }
                        }
                    }

                    // Кнопки действий (Всегда видны!)
                    item {
                        // Создать бизнес
                        SettingsItem(
                            icon = Icons.Default.AddBusiness,
                            title = stringResource(Res.string.profile_btn_create_business),
                            subtitle = stringResource(Res.string.profile_desc_create_business),
                            onClick = viewModel::onCreateBusinessClicked
                        )
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Стать сотрудником
                        SettingsItem(
                            icon = Icons.Default.Badge, // Или Work
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
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
    // Переиспользуем стиль SettingsItem, но с выделением
    val icon = if (workspace.role == UserRole.PARTNER_ADMIN) Icons.Default.Business else Icons.Default.Store
    val roleRes = if (workspace.role == UserRole.PARTNER_ADMIN) Res.string.profile_role_owner else Res.string.profile_role_cashier

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