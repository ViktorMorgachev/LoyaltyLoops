package io.loyaltyloop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltyTierDto.LoyaltyLevel
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@Preview
fun LoyaltyCardItemPreview() {
    LoyaltyCardItem(card = LoyaltyCardDto(id = "id", userId = "userID",
        partnerId = "partnerID",
        balance = 345.0,
        totalSpent = 1344.0,
        visitsCount = 4,
        partnerName = "Frunze",
        tierLevel = 1,
        isBlocked = false)) {

    }
}

@Composable
fun LoyaltyCardItem(
    card: LoyaltyCardDto, onClick: () -> Unit
) {
    // Парсим цвет из HEX (приходит с сервера), если кривой - берем синий
    val cardColor = try {
        // Убираем # если есть и парсим
        val hex = card.cardColor.removePrefix("#")
        Color("FF$hex".toLong(16)) // Добавляем Alpha FF
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
            .height(180.dp) // Фиксированная высота как у банковской карты
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                    brush = Brush.linearGradient(
                        colors = listOf(cardColor, cardColor.copy(alpha = 0.6f))
                    )
                ).padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PartnerLogo(
                        logoUrl = card.logoUrl, partnerName = card.partnerName
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
                            text = "Карта лояльности",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    ContainerLvl(
                        level = card.tierLevel, modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column {
                    Text(
                        text = "Баланс",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${formatBalance(card.balance)} Б",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ContainerLvl(
    level: Int, modifier: Modifier = Modifier // <-- Добавили параметр
) {
    val safeIndex = (level - 1).coerceIn(0, LoyaltyLevel.entries.lastIndex)
    Box(
        modifier = modifier // <-- Применяем его здесь
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
    logoUrl: String?, partnerName: String
) {
    val initials = remember(partnerName) {
        partnerName.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { partnerName.take(2).uppercase() }.ifBlank { "LL" }
    }

    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrBlank()) {
            KamelImage(
                resource = asyncPainterResource(logoUrl),
                contentDescription = partnerName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White
                    )
                },
                onFailure = { LogoFallback(initials) })
        } else {
            LogoFallback(initials)
        }
    }
}

@Composable
private fun LogoFallback(initials: String) {
    Text(
        text = initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
    )
}

private fun formatBalance(balance: Double): String {
    val scaled = (balance * 10).roundToInt()
    val integerPart = scaled / 10
    val fraction = abs(scaled % 10)
    return if (fraction == 0) {
        integerPart.toString()
    } else {
        "$integerPart.$fraction"
    }
}