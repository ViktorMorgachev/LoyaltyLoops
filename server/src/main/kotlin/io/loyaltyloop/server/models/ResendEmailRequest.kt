package io.loyaltyloop.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String
)
