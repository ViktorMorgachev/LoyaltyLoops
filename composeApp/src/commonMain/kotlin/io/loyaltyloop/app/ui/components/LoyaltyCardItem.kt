package io.loyaltyloop.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.loyaltyloop.app.features.wallet.CardAnimationEvent
import io.loyaltyloop.app.features.wallet.CardAnimationMessage
import io.loyaltyloop.app.utils.UiText
import io.loyaltyloop.app.ui.theme.TierColors
import io.loyaltyloop.shared.models.CardBlockStatus
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltyTierDto.LoyaltyLevel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.Flow
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.wallet_card_back_goal_prefix
import loyaltyloop.composeapp.generated.resources.wallet_card_back_goal_suffix
import loyaltyloop.composeapp.generated.resources.wallet_card_back_progress_title
import loyaltyloop.composeapp.generated.resources.wallet_card_back_remaining_prefix
import loyaltyloop.composeapp.generated.resources.wallet_card_back_remaining_suffix
import loyaltyloop.composeapp.generated.resources.wallet_card_back_reward_ready
import loyaltyloop.composeapp.generated.resources.wallet_card_back_total_prefix
import loyaltyloop.composeapp.generated.resources.wallet_card_balance_label
import loyaltyloop.composeapp.generated.resources.wallet_card_blocked_until
import loyaltyloop.composeapp.generated.resources.wallet_card_closed_default
import loyaltyloop.composeapp.generated.resources.wallet_card_closed_reason
import loyaltyloop.composeapp.generated.resources.wallet_card_new_level
import loyaltyloop.composeapp.generated.resources.wallet_card_points_suffix
import loyaltyloop.composeapp.generated.resources.wallet_card_reward_badge
import loyaltyloop.composeapp.generated.resources.wallet_card_type
import loyaltyloop.composeapp.generated.resources.wallet_card_visit_suffix
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@Preview
fun LoyaltyCardItemPreview() {
    LoyaltyCardItem(
        card = LoyaltyCardDto(
            id = "demo",
            userId = "user",
            partnerId = "partner",
            balance = 345.5,
            totalSpent = 1200.0,
            visitsCount = 4,
            tierLevel = 2,
            partnerName = "Hybrid Coffee",
            cardColor = "#5C6AC4",
            logoUrl = null,
            block = null,
            pause = null
        ),
        isFlipped = false,
        onFlipToggle = {},
        eventFlow = null
    )
}

@Composable
fun LoyaltyCardItem(
    card: LoyaltyCardDto,
    isFlipped: Boolean = false,
    onFlipToggle: () -> Unit = {},
    eventFlow: Flow<CardAnimationMessage>? = null
) {
    val fallbackColor = MaterialTheme.colorScheme.primary
    val cardColor = remember(card.cardColor, fallbackColor) {
        parseCardColor(card.cardColor, fallbackColor)
    }
    val highlight = remember { Animatable(0f) }

    val pointsSuffix = stringResource(Res.string.wallet_card_points_suffix)
    val visitSuffix = stringResource(Res.string.wallet_card_visit_suffix)
    val newLevelLabel = stringResource(Res.string.wallet_card_new_level)
    val rewardLabel = stringResource(Res.string.wallet_card_reward_badge)
    val activeBlock = card.block?.takeIf { it.until > Clock.System.now().toEpochMilliseconds() }
    val statusLabel = when {
        activeBlock != null -> {
            val formatted = formatBlockedUntil(activeBlock.until)
            val base = stringResource(Res.string.wallet_card_blocked_until) + formatted
            activeBlock.reason?.takeIf { it.isNotBlank() }?.let { "$base • $it" } ?: base
        }
        card.pause != null -> {
            card.pause?.reason?.takeIf { it.isNotBlank() }?.let {
                stringResource(Res.string.wallet_card_closed_reason) + it
            } ?: stringResource(Res.string.wallet_card_closed_default)
        }
        else -> null
    }
    val tierBorderColor = remember(card.tierLevel) { TierColors.forTier(card.tierLevel) }
    val cornerShape = RoundedCornerShape(20.dp)
    val animatedBalance by animateFloatAsState(
        targetValue = card.balance.toFloat(),
        animationSpec = tween(durationMillis = 450),
        label = "balanceAnim"
    )

    LaunchedEffect(card.id, eventFlow, pointsSuffix, newLevelLabel, visitSuffix, rewardLabel) {
        eventFlow?.collect { message ->
            if (message.cardId != card.id) return@collect
            when (val event = message.event) {
                is CardAnimationEvent.BalanceEarned -> {
                    pulseHighlight(highlight, emphasis = 0.7f)
                }

                is CardAnimationEvent.BalanceSpent -> {
                    pulseHighlight(highlight, emphasis = 0.5f)
                }

                is CardAnimationEvent.VisitProgress -> {
                    pulseHighlight(highlight, emphasis = 0.45f)
                }

                is CardAnimationEvent.TierUpgrade -> {
                    pulseHighlight(highlight, emphasis = 1f)
                }

                CardAnimationEvent.RewardUnlocked -> {
                    pulseHighlight(highlight, emphasis = 1.2f)
                }

                CardAnimationEvent.CardCreated -> {
                    pulseHighlight(highlight, emphasis = 0.3f)
                }

                CardAnimationEvent.CardSynced -> {
                    pulseHighlight(highlight, emphasis = 0.2f)
                }

                CardAnimationEvent.CardDeleted -> {
                }
            }
        }
    }

    val safeHighlight = highlight.value.coerceIn(0f, 1f)

    val flipRotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "cardFlip"
    )
    val density = LocalDensity.current
    val cameraDistance = with(density) { 36.dp.toPx() }
    val showingBack = flipRotation > 90f

    Card(
        shape = cornerShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(195.dp)
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .clip(shape = cornerShape)
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = true, color = Color.White.copy(alpha = 0.35f)),
                    onClick = onFlipToggle
                )
                .graphicsLayer {
                    rotationY = flipRotation
                    this.cameraDistance = cameraDistance
                }
        ) {
            if (!showingBack) {
                LoyaltyCardFront(
                    card = card,
                    cardColor = cardColor,
                    pointsSuffix = pointsSuffix,
                    activeBlock = activeBlock,
                    statusLabel = statusLabel,
                    tierBorderColor = tierBorderColor,
                    cornerShape = cornerShape,
                    animatedBalance = animatedBalance,
                    safeHighlight = safeHighlight
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(shape = cornerShape)
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    LoyaltyCardBackContent(card = card, cardColor = cardColor)
                }
            }
        }
    }
}

@Composable
private fun LoyaltyCardFront(
    card: LoyaltyCardDto,
    cardColor: Color,
    pointsSuffix: String,
    activeBlock: CardBlockStatus?,
    statusLabel: String?,
    tierBorderColor: Color?,
    cornerShape: RoundedCornerShape,
    animatedBalance: Float,
    safeHighlight: Float
) {
    val gradientColors = remember(cardColor, safeHighlight) {
        listOf(
            lerp(cardColor, Color.White, 0.12f * safeHighlight),
            lerp(cardColor.copy(alpha = 0.65f), Color.White, 0.3f * safeHighlight)
        )
    }
    val scale = 1f + (safeHighlight * 0.03f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(brush = Brush.linearGradient(gradientColors))
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .fillMaxSize()
                .padding(20.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PartnerLogo(
                        logoUrl = card.logoUrl,
                        partnerName = card.partnerName
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = card.partnerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(Res.string.wallet_card_type),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    ContainerLvl(level = card.tierLevel)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.weight(1f))

                Column {
                    Text(
                        text = stringResource(Res.string.wallet_card_balance_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${formatBalance(animatedBalance.toDouble())} $pointsSuffix",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
        }


        if (tierBorderColor != null) {
            val borderAlpha = (0.7f + (safeHighlight * 0.3f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        border = BorderStroke(6.dp, tierBorderColor.copy(alpha = borderAlpha)),
                        shape = cornerShape
                    )
            )
        }

        statusLabel?.let { label ->
            val statusColor = if (activeBlock != null) Color(0xFFB91C1C) else Color(0xFF1F2933)
            val statusAlpha = if (activeBlock != null) 0.7f else 0.55f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(cornerShape)
                    .background(statusColor.copy(alpha = statusAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LoyaltyCardBackContent(card: LoyaltyCardDto, cardColor: Color) {
    val goal = card.visitsTarget.coerceAtLeast(1)
    val rawProgress = card.visitsCount % goal
    val filled = when {
        card.visitsCount == 0 -> 0
        rawProgress == 0 -> goal
        else -> rawProgress
    }
    val remaining = (goal - filled).coerceAtLeast(0)
    val progressFraction = (filled.toFloat() / goal).coerceIn(0f, 1f)

    val progressTitle = stringResource(Res.string.wallet_card_back_progress_title)
    val totalVisitsText = UiText.concat(
        UiText.Resource(Res.string.wallet_card_back_total_prefix),
        UiText.DynamicString(card.visitsCount.toString())
    ).asString()
    val remainingText = if (remaining > 0) {
        UiText.concat(
            UiText.Resource(Res.string.wallet_card_back_remaining_prefix),
            UiText.DynamicString(remaining.toString()),
            UiText.Resource(Res.string.wallet_card_back_remaining_suffix)
        ).asString()
    } else {
        stringResource(Res.string.wallet_card_back_reward_ready)
    }
    val goalText = UiText.concat(
        UiText.Resource(Res.string.wallet_card_back_goal_prefix),
        UiText.DynamicString(goal.toString()),
        UiText.Resource(Res.string.wallet_card_back_goal_suffix)
    ).asString()

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(cardColor, cardColor.copy(alpha = 0.85f))))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = progressTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = totalVisitsText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = remainingText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.95f)
            )
            Text(
                text = goalText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        StampIndicatorRow(totalSlots = goal, filled = filled.coerceAtMost(goal))

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun StampIndicatorRow(totalSlots: Int, filled: Int) {
    val safeTotal = totalSlots.coerceAtLeast(1)
    val needsScroll = safeTotal > 10

    val dot: @Composable (Boolean) -> Unit = { active ->
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (needsScroll) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = true
        ) {
            items(safeTotal) { index ->
                dot(index < filled)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(safeTotal) { index ->
                dot(index < filled)
            }
        }
    }
}

@Composable
private fun ContainerLvl(level: Int, modifier: Modifier = Modifier) {
    val safeIndex = (level - 1).coerceIn(0, LoyaltyLevel.entries.lastIndex)
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = LoyaltyLevel.entries[safeIndex].name,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PartnerLogo(
    logoUrl: String?,
    partnerName: String
) {
    val initials = remember(partnerName) {
        partnerName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { partnerName.take(2).uppercase() }
            .ifBlank { "LL" }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrBlank()) {
            KamelImage(
                resource = asyncPainterResource(logoUrl),
                contentDescription = partnerName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                },
                onFailure = { LogoFallback(initials) }
            )
        } else {
            LogoFallback(initials)
        }
    }
}

@Composable
private fun LogoFallback(initials: String) {
    Text(
        text = initials,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

private suspend fun pulseHighlight(
    animatable: Animatable<Float, AnimationVector1D>,
    emphasis: Float = 1f
) {
    animatable.snapTo(0f)
    animatable.animateTo(0.9f * emphasis, animationSpec = tween(360))
    animatable.animateTo(0f, animationSpec = tween(1200))
}

private fun parseCardColor(hexColor: String, fallback: Color): Color =
    try {
        val hex = hexColor.removePrefix("#")
        Color("FF$hex".toLong(16))
    } catch (_: Exception) {
        fallback
    }

private fun formatBalance(balance: Double): String {
    val scaled = (balance * 10).roundToInt()
    val integerPart = scaled / 10
    val fraction = abs(scaled % 10)
    return if (fraction == 0) integerPart.toString() else "$integerPart.$fraction"
}

private fun formatBlockedUntil(epochMillis: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dateTime.dayOfMonth.toString().padStart(2, '0')
    val month = dateTime.monthNumber.toString().padStart(2, '0')
    val year = dateTime.year.toString()
    return "$day.$month.$year"
}

private data class CardBubble(
    val text: String,
    val color: Color,
    val caption: String? = null
)
