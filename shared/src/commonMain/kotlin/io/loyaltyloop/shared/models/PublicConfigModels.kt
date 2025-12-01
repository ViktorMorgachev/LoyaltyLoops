package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class MapProvider {
    YANDEX
}

@Serializable
data class FeatureToggleDto(
    val realtimeEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val mapEnabled: Boolean = true,
    val testLabEnabled: Boolean = false
)

@Serializable
data class MapSettingsDto(
    val defaultLat: Double,
    val defaultLon: Double,
    val provider: MapProvider = MapProvider.YANDEX,
    val minRadiusMeters: Int = 50,
    val defaultRadiusMeters: Int = 2000,
    val maxRadiusMeters: Int = 15000,
    val clusterRadiusMeters: Int = 80,
    val searchDebounceMs: Long = 350,
    val showFilters: Boolean = true,
    val showRatings: Boolean = true,
    val showWorkingHours: Boolean = true,
    val yandexAndroidKey: String? = null,
    val yandexIosKey: String? = null,
    val yandexWebKey: String? = null,
)

@Serializable
data class PublicConfigResponse(
    val features: FeatureToggleDto,
    val map: MapSettingsDto
)

