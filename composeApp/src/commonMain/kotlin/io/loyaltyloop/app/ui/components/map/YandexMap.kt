package io.loyaltyloop.app.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.loyaltyloop.shared.models.TradingPointType
import io.loyaltyloop.shared.models.GeoLocation

data class MapMarker(
    val id: String,
    val lat: Double,
    val lon: Double,
    val title: String,
    val type: TradingPointType,
    val isSelected: Boolean = false,
    val logoUrl: String? = null
)

data class CameraPosition(
    val lat: Double,
    val lon: Double,
    val zoom: Float
)

@Composable
expect fun YandexMap(
    modifier: Modifier = Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    userLocation: GeoLocation? = null,
    searchAreaCenter: GeoLocation? = null,
    searchRadius: Int? = null,
    onMapClick: () -> Unit,
    onMarkerClick: (String) -> Unit
)