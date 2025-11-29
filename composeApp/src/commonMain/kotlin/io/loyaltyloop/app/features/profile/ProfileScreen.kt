package io.loyaltyloop.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.features.profile.components.LogoutButton
import io.loyaltyloop.app.features.profile.components.ProfileHeader
import io.loyaltyloop.app.features.profile.components.SectionTitle
import io.loyaltyloop.app.features.profile.components.SettingsItem
import io.loyaltyloop.app.features.profile.components.WorkspaceItem
import io.loyaltyloop.app.features.splash.SplashScreen
import io.loyaltyloop.app.features.web.WebPortalScreen
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class ProfileScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<ProfileScreenModel>()
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow.parent ?: LocalNavigator.currentOrThrow

        // Состояние снекбара
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            // При первом входе грузим данные (можно убрать, если init блока достаточно)
             viewModel.onAction(ProfileScreenModel.Action.OnRefresh)

            viewModel.events.collect { event ->
                when (event) {
                    is ProfileScreenModel.Event.NavigateToSplash -> {
                        navigator.replaceAll(SplashScreen())
                    }
                    is ProfileScreenModel.Event.NavigateToJoinCompany -> {
                        navigator.push(io.loyaltyloop.app.features.join.JoinCompanyScreen())
                    }
                    // ОБРАБОТКА ОШИБОК
                    is ProfileScreenModel.Event.ShowMessage -> {
                        launch { snackbarHostState.show(event.message, event.type) }
                    }
                    is ProfileScreenModel.Event.NavigateToWeb -> {
                        navigator.push(WebPortalScreen(event.url, event.headers))
                    }
                }
            }
        }

        // 2. Настройка Pull-to-Refresh
        val pullRefreshState = rememberPullToRefreshState()

        // Если юзер потянул вниз (state.isRefreshing стало true) -> вызываем загрузку
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(Unit) {
                viewModel.onAction(ProfileScreenModel.Action.OnRefresh)
            }
        }

        // Если загрузка закончилась (в ViewModel) -> сообщаем об этом UI, чтобы убрать крутилку
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                pullRefreshState.endRefresh()
            }
        }


        LoyaltyScaffold(
            snackbarHostState = snackbarHostState,
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
                        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp, start = 16.dp, end = 16.dp)
                    ) {
                        // 1. Header
                        item {
                            ProfileHeader(name = state.name.asString(), phone = state.phone)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 2. Рабочие места
                        if (state.workspaces.isNotEmpty()) {
                            item {
                                SectionTitle(stringResource(Res.string.profile_header_workspaces))
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            items(state.workspaces) { workspace ->
                                WorkspaceItem(workspace) {
                                    viewModel.onAction(ProfileScreenModel.Action.OnWorkspaceClicked(workspace))
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
                                onClick = { viewModel.onAction(ProfileScreenModel.Action.OnCreateBusinessClicked) }
                            )

                            SettingsItem(
                                icon = Icons.Default.Badge,
                                title = stringResource(Res.string.profile_btn_join_team),
                                subtitle = stringResource(Res.string.profile_desc_join_team),
                                onClick = { viewModel.onAction(ProfileScreenModel.Action.OnJoinTeamClicked) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 4. Настройки и Футер
                        item {
                            SectionTitle(stringResource(Res.string.profile_section_general))

                            SettingsItem(
                                icon = Icons.Default.Language,
                                title = stringResource(Res.string.profile_item_language),
                                value = "Русский",
                                onClick = { viewModel.onAction(ProfileScreenModel.Action.OnLanguageClicked) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                            SettingsItem(
                                icon = Icons.AutoMirrored.Filled.Help,
                                title = stringResource(Res.string.profile_item_support),
                                onClick = { viewModel.onAction(ProfileScreenModel.Action.OnSupportClicked) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        item {
                            LogoutButton(onClick = { viewModel.onAction(ProfileScreenModel.Action.OnLogoutClicked) })
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