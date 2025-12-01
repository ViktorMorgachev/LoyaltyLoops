package io.loyaltyloop.app.utils

data class GeoLocation(val lat: Double, val lon: Double)

expect class LocationService {
    suspend fun getCurrentLocation(): GeoLocation?
}