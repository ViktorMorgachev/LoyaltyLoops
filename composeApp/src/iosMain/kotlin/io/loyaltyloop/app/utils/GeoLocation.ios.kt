package io.loyaltyloop.app.utils


import io.loyaltyloop.shared.models.GeoLocation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class LocationService {

    private val locationManager = CLLocationManager()

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun getCurrentLocation(): GeoLocation? = suspendCoroutine { continuation ->
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation
                if (location != null) {
                    location.coordinate.useContents {
                        continuation.resume(GeoLocation(latitude, longitude))
                    }
                    manager.stopUpdatingLocation()
                } else {
                    continuation.resume(null)
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                continuation.resume(null)
            }
        }

        locationManager.delegate = delegate
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.requestWhenInUseAuthorization() // Запрашиваем права
        locationManager.startUpdatingLocation()
    }
}