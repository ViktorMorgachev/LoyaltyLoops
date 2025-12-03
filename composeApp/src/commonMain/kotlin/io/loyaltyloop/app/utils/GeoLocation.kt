package io.loyaltyloop.app.utils

import io.loyaltyloop.shared.models.GeoLocation


expect class LocationService {
    suspend fun getCurrentLocation(): GeoLocation?
}