package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String?, // Опционально
    val email: String?,
)