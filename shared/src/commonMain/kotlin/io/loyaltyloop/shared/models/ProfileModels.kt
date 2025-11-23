package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String? = null,
    val email: String? = null
)

@Serializable
data class UpdateLanguageRequest(
    val language: String
)