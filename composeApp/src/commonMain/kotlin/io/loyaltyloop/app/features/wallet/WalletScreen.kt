package io.loyaltyloop.app.features.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

class WalletScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<WalletScreenModel>()
        val state by viewModel.state.collectAsState()

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Мой Кошелек",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(40.dp))

                // --- КАРТОЧКА С QR ---
                QrCard(
                    qrContent = state.qrContent,
                    secondsRemaining = state.secondsRemaining
                )

                Spacer(modifier = Modifier.height(40.dp))

                Text("Покажите этот код кассиру для начисления баллов")
            }
        }
    }
}

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