package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class Employer(
    val userId: String,
    val name: String, // Имя (берем из User)
    val phone: String, // Телефон (берем из User)
    val active: Boolean,
    val tradingPointId: String? = null,
    val pointName: String? = null,
    val revenue: Double = 0.0,
    val transactionsCount: Int = 0
)
