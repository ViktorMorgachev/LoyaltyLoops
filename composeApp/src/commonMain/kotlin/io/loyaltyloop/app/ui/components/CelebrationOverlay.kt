package io.loyaltyloop.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.loyaltyloop.app.features.wallet.CelebrationState
import io.loyaltyloop.app.features.wallet.CelebrationType
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.celebration_close
import loyaltyloop.composeapp.generated.resources.celebration_reward_text
import loyaltyloop.composeapp.generated.resources.celebration_rules_hint
import loyaltyloop.composeapp.generated.resources.celebration_title_created
import loyaltyloop.composeapp.generated.resources.celebration_title_earn
import loyaltyloop.composeapp.generated.resources.celebration_title_reward
import loyaltyloop.composeapp.generated.resources.celebration_title_spend
import loyaltyloop.composeapp.generated.resources.celebration_title_visit
import loyaltyloop.composeapp.generated.resources.celebration_visit_logged
import loyaltyloop.composeapp.generated.resources.celebration_visit_reward_ready
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import loyaltyloop.composeapp.generated.resources.celebration_new_balance_prefix
import loyaltyloop.composeapp.generated.resources.celebration_title_tier_prefix
import loyaltyloop.composeapp.generated.resources.celebration_visit_increment_prefix
import loyaltyloop.composeapp.generated.resources.celebration_visit_increment_suffix
import loyaltyloop.composeapp.generated.resources.celebration_visit_remaining_prefix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CelebrationOverlay(
    state: CelebrationState,
    onDismiss: () -> Unit
) {
    val colors = celebrationColors(state.type)
    val visibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.first),
        contentAlignment = Alignment.Center
    ) {
        ConfettiLayer(state.type)
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.94f, animationSpec = tween(350, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.94f, animationSpec = tween(200))
        ) {
            CelebrationCard(state = state, onDismiss = onDismiss, colors = colors)
        }
    }
}

@Composable
private fun CelebrationCard(
    state: CelebrationState,
    onDismiss: () -> Unit,
    colors: Pair<Color, Color>
) {
    var showTooltip by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 32.dp)
            .clip(RoundedCornerShape(40.dp)),
        color = colors.first,
        contentColor = colors.second,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.cardName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = celebrationTitle(state),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                state.amount?.let {
                    Text(
                        text = formatAmount(state),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                }

                when (state.type) {
                    CelebrationType.Visit -> VisitProgressSection(state)
                    CelebrationType.Reward -> RewardSection(state)
                    CelebrationType.Spend,
                    CelebrationType.Earn,
                    CelebrationType.Tier,
                    CelebrationType.Created -> Unit
                }

                state.newBalance?.let {
                    Text(
                        text = stringResource(Res.string.celebration_new_balance_prefix) +
                            formatAmount(state.copy(type = CelebrationType.Earn, amount = it)),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = showTooltip) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.second.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = stringResource(Res.string.celebration_rules_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.second
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showTooltip = !showTooltip }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.second
                        )
                    }
                    TextButtonPill(onClick = onDismiss, contentColor = colors.second)
                }
            }
        }
    }
}

@Composable
private fun VisitProgressSection(state: CelebrationState) {
    val target = (state.visitsTarget ?: 5).coerceAtLeast(1)
    val visits = state.newVisits ?: 0
    val increment = state.visitsIncrement ?: 1
    val providedRemaining = state.remainingVisits
    val remaining = providedRemaining ?: calculateRemaining(visits, target)

    val filled = when {
        providedRemaining == null -> (visits % target).takeIf { it != 0 || visits == 0 } ?: target
        remaining <= 0 -> target
        else -> (target - remaining).coerceIn(0, target)
    }

    StampRow(count = target, filled = filled)

    val info = when {
        providedRemaining == null -> stringResource(Res.string.celebration_visit_logged)
        remaining <= 0 -> stringResource(Res.string.celebration_visit_reward_ready)
        else -> stringResource(Res.string.celebration_visit_remaining_prefix) + remaining
    }

    Text(
        text = info,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Text(
        text = stringResource(Res.string.celebration_visit_increment_prefix) +
            increment + stringResource(Res.string.celebration_visit_increment_suffix),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RewardSection(state: CelebrationState) {
    val target = (state.visitsTarget ?: 5).coerceAtLeast(1)
    StampRow(count = target, filled = target)
    Text(
        text = stringResource(Res.string.celebration_reward_text),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
}

private fun calculateRemaining(visits: Int, target: Int): Int {
    if (target <= 0) return 0
    if (visits <= 0) return target
    val mod = visits % target
    return if (mod == 0) 0 else target - mod
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StampRow(count: Int, filled: Int) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(count.coerceAtLeast(1)) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFilled) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isFilled) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfettiLayer(type: CelebrationType) {
    val colors = listOf(
        Color(0xFFFDE047),
        Color(0xFFBDEBFF),
        Color(0xFFF9A8D4),
        Color(0xFFA7F3D0)
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val particles = if (type == CelebrationType.Reward) 70 else 30
        repeat(particles) {
            val randomX = Random.nextFloat() * size.width
            val randomY = Random.nextFloat() * size.height
            val radius = Random.nextDouble(4.0, 10.0).toFloat()
            drawCircle(
                color = colors.random(),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(randomX, randomY),
                alpha = when (type) {
                    CelebrationType.Spend -> 0.2f
                    CelebrationType.Earn -> 0.35f
                    CelebrationType.Reward -> 0.7f
                    else -> 0.5f
                },
                style = Stroke(width = radius / 1.5f, cap = StrokeCap.Round)
            )
        }

        if (type == CelebrationType.Reward) {
            val centerY = size.height * 0.2f
            val centerX = size.width / 2f
            repeat(8) { idx ->
                val angle = (idx * (360f / 8)) * (PI.toFloat() / 180f)
                val length = size.minDimension * 0.25f + Random.nextFloat() * 40f
                val endX = centerX + cos(angle) * length
                val endY = centerY + sin(angle) * length
                drawLine(
                    color = colors[idx % colors.size],
                    start = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun TextButtonPill(onClick: () -> Unit, contentColor: Color) {
    Surface(
        color = contentColor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            text = stringResource(Res.string.celebration_close),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
private fun celebrationTitle(state: CelebrationState): String {
    return when (state.type) {
        CelebrationType.Earn -> stringResource(Res.string.celebration_title_earn)
        CelebrationType.Spend -> stringResource(Res.string.celebration_title_spend)
        CelebrationType.Visit -> stringResource(Res.string.celebration_title_visit)
        CelebrationType.Reward -> stringResource(Res.string.celebration_title_reward)
        CelebrationType.Tier -> stringResource(Res.string.celebration_title_tier_prefix) +
            (state.tierLevel ?: 0)
        CelebrationType.Created -> stringResource(Res.string.celebration_title_created)
    }
}

private fun formatAmount(state: CelebrationState): String {
    val amount = state.amount ?: 0.0
    val rounded = (amount * 10).roundToInt() / 10.0
    val prefix = if (state.type == CelebrationType.Spend) "-" else "+"
    return "$prefix$rounded Б"
}

private fun celebrationColors(type: CelebrationType): Pair<Color, Color> {
    return when (type) {
        CelebrationType.Earn -> Color(0xFF14532D) to Color(0xFFE2FFD8)
        CelebrationType.Spend -> Color(0xFF1E3A8A) to Color(0xFFDDE4FF)
        CelebrationType.Visit -> Color(0xFF7C3AED) to Color(0xFFF3E8FF)
        CelebrationType.Reward -> Color(0xFFB45309) to Color(0xFFFFF7ED)
        CelebrationType.Tier -> Color(0xFF047857) to Color(0xFFD1FAE5)
        CelebrationType.Created -> Color(0xFF0F172A) to Color(0xFFE2E8F0)
    }
}

