package io.loyaltyloop.app.platform

import android.content.Context
import co.touchlab.kermit.Logger
import com.yandex.mapkit.MapKitFactory

actual class MapInitializer(private val context: Context) {
    private var isInitialized = false

    actual fun initialize(apiKey: String) {
        if (isInitialized) return

        try {
            MapKitFactory.setApiKey(apiKey)
            MapKitFactory.initialize(context)
            isInitialized = true
            Logger.d("MapInitializer") { "Yandex MapKit initialized with key: $apiKey" }
        } catch (e: Exception) {
            Logger.e("MapInitializer", e) { "Failed to initialize MapKit" }
        }
    }
}