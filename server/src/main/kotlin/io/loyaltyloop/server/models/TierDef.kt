package io.loyaltyloop.server.models

import java.math.BigDecimal

data class TierDef(
    val index: Int,
    val name: String,
    val threshold: BigDecimal,
    val cashback: BigDecimal
)

val getDefaultTiers = listOf<TierDef>( TierDef(1, "Start", BigDecimal.ZERO, BigDecimal.valueOf(1.0)),
    TierDef(2, "Silver", BigDecimal.valueOf(5000), BigDecimal.valueOf(3.0)),
    TierDef(3, "Gold", BigDecimal.valueOf(15000), BigDecimal.valueOf(5.0)))
