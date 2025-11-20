package io.loyaltyloop.app.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class TokenStorage(private val settings: Settings) {

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
    }

    // Сохранить все данные сразу
    fun saveAuthData(accessToken: String, refreshToken: String, userId: String) {
        settings[KEY_ACCESS_TOKEN] = accessToken
        settings[KEY_REFRESH_TOKEN] = refreshToken
        settings[KEY_USER_ID] = userId
    }

    // Получить Access Token
    fun getAccessToken(): String? {
        return settings.getStringOrNull(KEY_ACCESS_TOKEN)
    }

    // Получить Refresh Token
    fun getRefreshToken(): String? {
        return settings.getStringOrNull(KEY_REFRESH_TOKEN)
    }

    // Очистить (при выходе)
    fun clear() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
        settings.remove(KEY_USER_ID)
    }
}