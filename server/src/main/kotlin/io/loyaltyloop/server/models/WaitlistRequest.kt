package io.loyaltyloop.server.models

import kotlinx.serialization.Serializable

@Serializable
data class WaitlistRequest(val email: String)
