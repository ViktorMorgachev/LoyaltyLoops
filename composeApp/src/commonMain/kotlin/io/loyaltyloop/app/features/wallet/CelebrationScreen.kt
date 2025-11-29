package io.loyaltyloop.app.features.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.loyaltyloop.app.ui.components.CelebrationOverlay
import kotlinx.coroutines.delay

data class CelebrationScreen(
    private val celebration: CelebrationState
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(celebration.id) {
            delay(celebration.dismissAfterMs)
            if (navigator.canPop) {
                navigator.pop()
            }
        }

        CelebrationOverlay(
            state = celebration,
            onDismiss = {
                if (navigator.canPop) {
                    navigator.pop()
                }
            }
        )
    }
}


