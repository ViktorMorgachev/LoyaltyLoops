package io.loyaltyloop.app.features.terminal.rating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import io.loyaltyloop.app.features.terminal.TerminalScreen
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.app.ui.components.LoyaltyScaffold
import io.loyaltyloop.app.ui.components.show
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.shared.models.ClientRatingTag
import io.loyaltyloop.shared.models.CreateClientRatingDto
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import io.loyaltyloop.shared.models.onError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import io.loyaltyloop.app.ui.components.SnackbarType
import kotlinx.coroutines.delay
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.rate_client_title
import loyaltyloop.composeapp.generated.resources.rate_client_skip
import loyaltyloop.composeapp.generated.resources.rate_client_submit
import loyaltyloop.composeapp.generated.resources.rate_client_tag_rude
import loyaltyloop.composeapp.generated.resources.rate_client_tag_no_payment
import loyaltyloop.composeapp.generated.resources.rate_client_tag_fraud
import loyaltyloop.composeapp.generated.resources.rate_client_tag_tip
import loyaltyloop.composeapp.generated.resources.rate_client_success
import loyaltyloop.composeapp.generated.resources.error_rate_limit_exceeded
import loyaltyloop.composeapp.generated.resources.rate_client_select_tags
import loyaltyloop.composeapp.generated.resources.rate_client_how_was
import org.jetbrains.compose.resources.stringResource
import io.loyaltyloop.app.utils.toResource
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.app.data.ConfigStore
import org.koin.compose.koinInject
import kotlinx.coroutines.Job
import loyaltyloop.composeapp.generated.resources.rate_client_tag_abuse
import loyaltyloop.composeapp.generated.resources.rate_client_tag_friendly
import loyaltyloop.composeapp.generated.resources.rate_client_tag_late
import loyaltyloop.composeapp.generated.resources.rate_client_tag_no_show
import loyaltyloop.composeapp.generated.resources.rate_client_tag_polite
import io.loyaltyloop.app.data.SessionManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ColorScheme

data class RateClientScreen(val userId: String, val tradingPointId: String) : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<RateClientScreenModel> { parametersOf(userId, tradingPointId) }
        val state by viewModel.state.collectAsState()
        val navigator = LocalNavigator.current
        val snackbarHostState = remember { SnackbarHostState() }
        val configStore = koinInject<ConfigStore>()
        val sessionManager = koinInject<SessionManager>()
        val workspace by sessionManager.currentWorkspace.collectAsState()
        val workspaceName = workspace?.title ?: stringResource(Res.string.rate_client_title)


        val tagList = remember {
            val tagsFromConfig = configStore.clientTagsList()
                .mapNotNull { runCatching { ClientRatingTag.valueOf(it.code) }.getOrNull() }
                .filter { it != ClientRatingTag.NONE }
            tagsFromConfig.ifEmpty { ClientRatingTag.entries.filter { it != ClientRatingTag.NONE } }
        }
        val scope = rememberCoroutineScope()
        var autoCloseJob by remember { mutableStateOf<Job?>(null) }

        // Auto-close after 5 seconds unless user interacts
        LaunchedEffect(Unit) {
            autoCloseJob = launch {
                delay(5000)
                viewModel.onAction(RateClientScreenModel.Action.OnSkipClicked)
            }
        }

        fun cancelAutoClose() {
            autoCloseJob?.cancel()
            autoCloseJob = null
        }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when(event) {
                    is RateClientScreenModel.Event.NavigateHome -> {
                        navigator?.popUntil { it is TerminalScreen }
                    }
                    is RateClientScreenModel.Event.ShowMessage -> {
                        launch { snackbarHostState.show(event.message, event.type) }
                    }
                }
            }
        }

        LoyaltyScaffold(
            snackbarHostState = snackbarHostState,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Workspace name and title
                Text(
                    text = workspaceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.rate_client_how_was),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Stars
                Row(horizontalArrangement = Arrangement.Center) {
                    (1..5).forEach { index ->
                        val isSelected = index <= state.rating
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    cancelAutoClose()
                                    viewModel.onAction(RateClientScreenModel.Action.OnRatingChanged(index))
                                }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                Text(stringResource(Res.string.rate_client_select_tags), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                // Tags
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tagList.forEach { tag ->
                        val isSelected = state.selectedTags.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                cancelAutoClose()
                                viewModel.onAction(RateClientScreenModel.Action.OnTagToggled(tag))
                            },
                            label = { Text(getTagLabel(tag)) },
                            colors = chipColorsFor(tag.penalty, MaterialTheme.colorScheme),
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        cancelAutoClose()
                        viewModel.onAction(RateClientScreenModel.Action.OnSkipClicked)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(Res.string.rate_client_skip))
                }

                LoyaltyButton(
                    text = stringResource(Res.string.rate_client_submit),
                    onClick = {
                        cancelAutoClose()
                        viewModel.onAction(RateClientScreenModel.Action.OnSubmitClicked)
                    },
                    isLoading = state.isLoading,
                    enabled = state.rating > 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun getTagLabel(tag: ClientRatingTag): String {
        return when(tag) {
            ClientRatingTag.AGGRESSION -> stringResource(Res.string.rate_client_tag_rude)
            ClientRatingTag.NO_PAYMENT -> stringResource(Res.string.rate_client_tag_no_payment)
            ClientRatingTag.TIP -> stringResource(Res.string.rate_client_tag_tip)
            ClientRatingTag.FRAUD -> stringResource(Res.string.rate_client_tag_fraud)
            ClientRatingTag.POLITE -> stringResource(Res.string.rate_client_tag_polite)
            ClientRatingTag.FRIENDLY -> stringResource(Res.string.rate_client_tag_friendly)
            ClientRatingTag.ABUSE -> stringResource(Res.string.rate_client_tag_abuse)
            ClientRatingTag.NONE -> ""
        }
    }

    @Composable
    private fun chipColorsFor(penalty: Double, colors: ColorScheme): SelectableChipColors {
        return when {
            penalty > 0 -> FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.tertiaryContainer,
                selectedLabelColor = colors.onTertiaryContainer
            )
            penalty < 0 -> FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.errorContainer,
                selectedLabelColor = colors.onErrorContainer
            )
            else -> FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer
            )
        }
    }
}

class RateClientScreenModel(
    private val userId: String,
    private val tradingPointId: String,
    private val repository: PartnerRepository
) : ScreenModel {

    data class State(
        val rating: Int = 0,
        val selectedTags: Set<ClientRatingTag> = emptySet(),
        val isLoading: Boolean = false
    )

    sealed interface Action {
        data class OnRatingChanged(val rating: Int) : Action
        data class OnTagToggled(val tag: ClientRatingTag) : Action
        data object OnSubmitClicked : Action
        data object OnSkipClicked : Action
    }

    sealed interface Event {
        data object NavigateHome : Event
        data class ShowMessage(val message: UiText, val type: SnackbarType) : Event
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    fun onAction(action: Action) {
        when(action) {
            is Action.OnRatingChanged -> _state.update { it.copy(rating = action.rating) }
            is Action.OnTagToggled -> {
                _state.update { 
                    val newTags = if (it.selectedTags.contains(action.tag)) {
                        it.selectedTags - action.tag
                    } else {
                        it.selectedTags + action.tag
                    }
                    it.copy(selectedTags = newTags)
                }
            }
            is Action.OnSubmitClicked -> submitRating()
            is Action.OnSkipClicked -> screenModelScope.launch { _events.send(Event.NavigateHome) }
        }
    }

    private fun submitRating() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            repository.rateClient(
                CreateClientRatingDto(
                    userId = userId,
                    tradingPointId = tradingPointId,
                    rating = _state.value.rating,
                    tags = _state.value.selectedTags.toList()
                )
            ).onSuccess {
                _events.send(Event.ShowMessage(UiText.Resource(Res.string.rate_client_success), SnackbarType.Success))
                _events.send(Event.NavigateHome)
            }.onError { code, message ->
                if (code == AppErrorCode.RATE_LIMIT_EXCEEDEG) {
                _events.send(Event.ShowMessage(UiText.Resource(Res.string.error_rate_limit_exceeded), SnackbarType.Info))
                    _events.send(Event.NavigateHome)
                } else {
                    _events.send(Event.ShowMessage(UiText.Resource(code.toResource(message)), SnackbarType.Error))
                    _state.update { it.copy(isLoading = false) }
                }
            }.onFailure {
                _events.send(Event.ShowMessage(UiText.DynamicString(it.message ?: "Failed to submit rating"), SnackbarType.Error))
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}

