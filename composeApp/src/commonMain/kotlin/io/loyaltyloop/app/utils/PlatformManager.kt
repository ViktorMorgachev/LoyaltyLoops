package io.loyaltyloop.app.utils

interface PlatformManager {
    fun applyLanguage(languageCode: String)
    fun reloadUI()
}