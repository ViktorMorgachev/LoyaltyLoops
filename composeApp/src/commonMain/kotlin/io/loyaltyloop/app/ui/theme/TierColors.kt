package io.loyaltyloop.app.ui.theme

import androidx.compose.ui.graphics.Color

object TierColors {

    fun forTier(tier: Int): Color? = when (tier) {
        1 -> TierBronze
        2 -> TierSilver
        3 -> TierGold
        4 -> TierPlatinum
        else -> null
    }
}

