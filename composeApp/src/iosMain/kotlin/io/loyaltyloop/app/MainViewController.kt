package io.loyaltyloop.app

import androidx.compose.ui.window.ComposeUIViewController
import cocoapods.YandexMapsMobile.YMKMapKit
import cocoapods.YandexMapsMobile.setApiKey
import cocoapods.YandexMapsMobile.sharedInstance
import io.loyaltyloop.app.config.AppConfig
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

private var isMapInitialized = false

@Suppress("unused")
fun MainViewController(): UIViewController {
    // 1. Инициализируем Яндекс Карты ПЕРЕД запуском UI
    if (!isMapInitialized) {
        initializeYandexMaps()
        isMapInitialized = true
    }

    // 2. Запускаем Compose App
    return ComposeUIViewController {
        App()
    }
}

// Отдельная функция, чтобы было чисто
@OptIn(ExperimentalForeignApi::class)
private fun initializeYandexMaps() {
    // Устанавливаем ключ.
    YMKMapKit.setApiKey(AppConfig.MAP_API_KEY)
    
    // YMKMapKit.sharedInstance().onStart() 
    // В Lite версии или новой версии MapKit for iOS этот метод может быть не нужен или недоступен через cinterop
    // Оставляем только setApiKey, обычно этого достаточно для старта
}
