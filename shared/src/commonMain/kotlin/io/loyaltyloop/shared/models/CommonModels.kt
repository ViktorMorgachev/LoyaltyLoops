package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiMessage(
    val message: String
)