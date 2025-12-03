package io.loyaltyloop.app

import androidx.compose.ui.window.ComposeUIViewController
import cocoapods.YandexMapsMobile.YMKMapKit
import cocoapods.YandexMapsMobile.setApiKey
import cocoapods.YandexMapsMobile.sharedInstance
import io.loyaltyloop.app.config.AppConfig
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    // 1. Инициализируем Яндекс Карты ПЕРЕД запуском UI
    // Используем remember или side-effect не обязательно,
    // так как это точка входа, она выполняется один раз.
    initializeYandexMaps()

    // 2. Запускаем Compose App
    return ComposeUIViewController {
        App()
    }
}

// Отдельная функция, чтобы было чисто
@OptIn(ExperimentalForeignApi::class)
private fun initializeYandexMaps() {
    // Устанавливаем ключ.
    // Важно: на iOS это статический метод класса YMKMapKit
    YMKMapKit.setApiKey(AppConfig.MAP_API_KEY)

    // В Android мы вызывали initialize(), в iOS это обычно делает setApiKey + старт инстанса
    YMKMapKit.sharedInstance().onStart()
}