package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class FeatureToggleDto(
    val pushEnabled: Boolean = true,
    val enableTestSupport: Boolean = false
)

@Serializable
data class MapSettingsDto(
    val basePoints: Map<CountryCode, GeoLocation>,
    val minRadiusMeters: Int = 50,
    val defaultRadiusMeters: Int = 2000,
    val maxRadiusMeters: Int = 15000,
    val clusterRadiusMeters: Int = 80,
    val searchDebounceMs: Long = 350,
    val showRatings: Boolean = true,
    val showWorkingHours: Boolean = true,
)

@Serializable
data class PublicConfigResponse(
    val features: FeatureToggleDto,
    val map: MapSettingsDto
)

