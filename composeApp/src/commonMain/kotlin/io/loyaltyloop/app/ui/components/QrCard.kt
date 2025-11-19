package io.loyaltyloop.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

@Composable
fun QrCard(qrContent: String, secondsRemaining: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Рисуем QR
            if (qrContent.isNotEmpty()) {
                val painter = rememberQrCodePainter(
                    data = qrContent,
                    shapes = io.github.alexzhirkevich.qrose.options.QrShapes(
                        ball = QrBallShape.roundCorners(.25f),
                        frame = QrFrameShape.roundCorners(.25f),
                        darkPixel = QrPixelShape.roundCorners(.5f)
                    )
                )

                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = "QR Code",
                    modifier = Modifier.size(250.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Таймер жизни кода
            Text(
                text = "Обновится через $secondsRemaining сек",
                style = MaterialTheme.typography.labelLarge,
                color = if (secondsRemaining < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )

            // Полоска прогресса таймера
            LinearProgressIndicator(
                progress = { secondsRemaining / 30f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }
}