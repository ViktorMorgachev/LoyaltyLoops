package io.loyaltyloop.app.utils

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

// Мы передаем Context через Koin
actual class LocationService(private val context: Context) {

    @SuppressLint("MissingPermission") // Предполагаем, что пермишн запрошен в UI перед вызовом
    actual suspend fun getCurrentLocation(): GeoLocation? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        return try {
            // Пытаемся получить последнюю известную позицию (быстро)
            var location = fusedLocationClient.lastLocation.await()

            // Если пусто, пробуем запросить актуальную (более тяжелая операция)
            if (location == null) {
                location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).await()
            }

            location?.let { GeoLocation(it.latitude, it.longitude) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}