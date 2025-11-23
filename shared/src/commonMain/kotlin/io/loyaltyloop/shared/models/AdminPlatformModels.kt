package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class JoinPlatformAdminRequest(
    val inviteCode: String
)