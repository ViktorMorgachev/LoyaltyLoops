package io.loyaltyloop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.loyaltyloop.shared.models.LoyaltyCardDto

@Composable
fun LoyaltyCardItem(
    card: LoyaltyCardDto,
    onClick: () -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp) // Фиксированная высота как у банковской карты
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(cardColor, cardColor.copy(alpha = 0.6f))
                    )
                )
                .padding(20.dp)
        ) {
            // Название Партнера (Сверху слева)
            Text(
                text = card.partnerName,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Уровень (Сверху справа)
            // Пока просто цифра, потом привяжем названия (Start, Gold)
            ContainerLvl(level = card.tierLevel,   modifier = Modifier.align(Alignment.TopEnd))
            
            // Баланс (Снизу)
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = "Баланс",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "${card.balance.toInt()} Б", // Округляем до целого
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ContainerLvl(
    level: Int,
    modifier: Modifier = Modifier // <-- Добавили параметр
) {
    Box(
        modifier = modifier // <-- Применяем его здесь
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "LVL $level",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}