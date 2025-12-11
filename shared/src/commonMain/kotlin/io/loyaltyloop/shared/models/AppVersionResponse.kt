package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class AppVersionResponse(
    val platform: String,
    val latestVersionCode: Int,
    val storeUrl: String,
    val force: Boolean = false
)


