package io.loyaltyloop.app.platform

import cocoapods.YandexMapsMobile.YMKMapKit
import cocoapods.YandexMapsMobile.setApiKey
import kotlinx.cinterop.ExperimentalForeignApi

actual class MapInitializer {
    @OptIn(ExperimentalForeignApi::class)
    actual fun initialize(apiKey: String) {
        YMKMapKit.setApiKey(apiKey)
        // On iOS, usually we just set the key. Initialization is often implicit or handled by AppDelegate.
        // But if we need explicit start:
        // YMKMapKit.sharedInstance().onStart() is handled in YandexMap composable or lifecycle.
    }
}