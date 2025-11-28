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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.QrShapes
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.loyaltyloop.shared.config.SecurityDefaults
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.qr_sec
import loyaltyloop.composeapp.generated.resources.qr_timer_update
import org.jetbrains.compose.resources.stringResource

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
            if (qrContent.isNotEmpty()) {
                val painter = rememberQrCodePainter(
                    data = qrContent,
                    shapes = QrShapes(
                        ball = QrBallShape.roundCorners(.25f),
                        frame = QrFrameShape.roundCorners(.25f),
                        darkPixel = QrPixelShape.roundCorners(.5f)
                    )
                )
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = "QR",
                    modifier = Modifier.size(250.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val secondsLabel = stringResource(Res.string.qr_sec)
            val formattedTime = remember(secondsRemaining, secondsLabel) {
                formatCountdown(secondsRemaining, secondsLabel)
            }
            val timerText = "${stringResource(Res.string.qr_timer_update)} $formattedTime"

            Text(
                text = timerText,
                style = MaterialTheme.typography.labelLarge,
                color = if (secondsRemaining < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            val maxSeconds = SecurityDefaults.QR_TOKEN_TTL_SECONDS.toFloat()

            LinearProgressIndicator(
                progress = { secondsRemaining / maxSeconds },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

private fun formatCountdown(seconds: Int, secondsLabel: String): String {
    if (seconds >= 60) {
        val minutes = seconds / 60
        val secPart = seconds % 60
        return "$minutes:${secPart.toString().padStart(2, '0')}"
    }
    return "$seconds $secondsLabel"
}