package io.loyaltyloop.app

import androidx.compose.ui.window.ComposeUIViewController
import cocoapods.YandexMapsMobile.YMKMapKit
import cocoapods.YandexMapsMobile.setApiKey
import cocoapods.YandexMapsMobile.sharedInstance
import io.loyaltyloop.app.config.AppConfig
import io.loyaltyloop.app.di.appModule
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

private var isMapInitialized = false
private var isKoinStarted = false

@Suppress("unused")
fun MainViewController(): UIViewController {
    // 1. Запускаем DI для iOS (аналог Android Application.onCreate)
    if (!isKoinStarted) {
        startKoin {
            modules(appModule)
        }
        isKoinStarted = true
    }

    // 2. Инициализируем Яндекс Карты ПЕРЕД запуском UI
    if (!isMapInitialized) {
        initializeYandexMaps()
        isMapInitialized = true
    }

    // 3. Запускаем Compose App
    return ComposeUIViewController {
        App()
    }
}

// Отдельная функция, чтобы было чисто
@OptIn(ExperimentalForeignApi::class)
private fun initializeYandexMaps() {
    // Устанавливаем ключ.
    YMKMapKit.setApiKey(AppConfig.MAP_API_KEY)
    
    // В iOS Lite версии YMKMapKit нужно инициализировать, чтобы не упасть при первом обращении
    YMKMapKit.sharedInstance().onStart()
}
