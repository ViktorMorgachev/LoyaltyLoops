package io.loyaltyloop.app.features.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.ui.components.CelebrationOverlay
import io.loyaltyloop.app.ui.components.LoyaltyButton
import io.loyaltyloop.shared.models.CreateServiceReviewDto
import io.loyaltyloop.shared.models.ServiceReviewTag
import io.loyaltyloop.shared.models.onFailure
import io.loyaltyloop.shared.models.onSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.rate_service_comment_hint
import loyaltyloop.composeapp.generated.resources.rate_service_skip
import loyaltyloop.composeapp.generated.resources.rate_service_submit
import loyaltyloop.composeapp.generated.resources.rate_service_tag_attentive
import loyaltyloop.composeapp.generated.resources.rate_service_tag_clean
import loyaltyloop.composeapp.generated.resources.rate_service_tag_comfort
import loyaltyloop.composeapp.generated.resources.rate_service_tag_dirty
import loyaltyloop.composeapp.generated.resources.rate_service_tag_fast
import loyaltyloop.composeapp.generated.resources.rate_service_tag_friendly
import loyaltyloop.composeapp.generated.resources.rate_service_tag_pricey
import loyaltyloop.composeapp.generated.resources.rate_service_tag_rude_staff
import loyaltyloop.composeapp.generated.resources.rate_service_tag_slow
import loyaltyloop.composeapp.generated.resources.rate_service_tag_tasty
import loyaltyloop.composeapp.generated.resources.rate_service_tag_wait_time
import loyaltyloop.composeapp.generated.resources.rate_service_title
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.loyaltyloop.app.data.ConfigStore
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

data class CelebrationScreen(
    private val celebration: CelebrationState
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var isRating by remember { mutableStateOf(false) }

        // Auto-dismiss only if not rating
        LaunchedEffect(celebration.id, isRating) {
            if (!isRating) {
            delay(celebration.dismissAfterMs)
            if (navigator.canPop) {
                navigator.pop()
                }
            }
        }

        CelebrationOverlay(
            state = celebration,
            onDismiss = {
                if (navigator.canPop) {
                    navigator.pop()
                }
            },
            onRateClick = if (celebration.tradingPointId != null) { { isRating = true } } else null
        )
        
        if (isRating && celebration.tradingPointId != null) {
            RateServiceDialog(
                tradingPointId = celebration.tradingPointId,
                onDismiss = { 
                    isRating = false 
                    if (navigator.canPop) navigator.pop() 
                },
                onSubmit = {
                    if (navigator.canPop) navigator.pop()
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RateServiceDialog(
    tradingPointId: String,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val repository = koinInject<PartnerRepository>()
    val scope = rememberCoroutineScope()
    val configStore = koinInject<ConfigStore>()
    val serviceTags = remember {
        val fromConfig = configStore.serviceTagsList()
            .mapNotNull { runCatching { ServiceReviewTag.valueOf(it.code) }.getOrNull() }
        fromConfig.ifEmpty { ServiceReviewTag.entries }
    }
    
    var rating by remember { mutableStateOf(0) }
    var selectedTags by remember { mutableStateOf(emptySet<ServiceReviewTag>()) }
    var comment by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    @Composable
    fun chipColorsFor(penalty: Double): SelectableChipColors {
        return when {
            penalty > 0 -> FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            penalty < 0 -> FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
            )
            else -> FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                selectedLabelColor = MaterialTheme.colorScheme.primary
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.rate_service_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Stars row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { index ->
                            val isSelected = index <= rating
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .clickable { rating = index }
                                    .padding(2.dp)
                                    .heightIn(min = 36.dp)
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        serviceTags.forEach { tag ->
                            val isSelected = selectedTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                                },
                                label = { Text(serviceTagLabel(tag), style = MaterialTheme.typography.labelLarge) },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = chipColorsFor(tag.penalty),
                                modifier = Modifier.heightIn(min = 40.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        placeholder = { Text(stringResource(Res.string.rate_service_comment_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(Res.string.rate_service_skip))
                        }
                        LoyaltyButton(
                            text = stringResource(Res.string.rate_service_submit),
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    repository.rateService(
                                        CreateServiceReviewDto(
                                            tradingPointId = tradingPointId,
                                            rating = rating,
                                            tags = selectedTags.toList(),
                                            comment = comment.takeIf { it.isNotBlank() }
                                        )
                                    ).onSuccess {
                                        onSubmit()
                                    }.onFailure {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = rating > 0 && !isLoading,
                            isLoading = isLoading,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun serviceTagLabel(tag: ServiceReviewTag): String {
    return when (tag) {
        ServiceReviewTag.SLOW -> stringResource(Res.string.rate_service_tag_slow)
        ServiceReviewTag.DIRTY -> stringResource(Res.string.rate_service_tag_dirty)
        ServiceReviewTag.RUDE_STAFF -> stringResource(Res.string.rate_service_tag_rude_staff)
        ServiceReviewTag.TASTY -> stringResource(Res.string.rate_service_tag_tasty)
        ServiceReviewTag.FAST -> stringResource(Res.string.rate_service_tag_fast)
        ServiceReviewTag.FRIENDLY -> stringResource(Res.string.rate_service_tag_friendly)
        ServiceReviewTag.CLEAN -> stringResource(Res.string.rate_service_tag_clean)
        ServiceReviewTag.COMFORT -> stringResource(Res.string.rate_service_tag_comfort)
        ServiceReviewTag.PRICEY -> stringResource(Res.string.rate_service_tag_pricey)
        ServiceReviewTag.ATTENTIVE -> stringResource(Res.string.rate_service_tag_attentive)
        ServiceReviewTag.WAIT_TIME -> stringResource(Res.string.rate_service_tag_wait_time)
    }
}


