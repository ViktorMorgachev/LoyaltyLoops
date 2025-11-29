package io.loyaltyloop.app.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write

class TokenStorage(private val settings: Settings) {

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_QR_SECRET = "qr_secret"
        private const val KEY_CURRENT_WORKSPACE_ID = "current_workspace_id"
        private const val KEY_IS_ROLE_SELECTED = "is_role_selected"
        private const val KEY_APP_LANGUAGE = "app_language"
    }

    // --- IN-MEMORY CACHE (Мгновенный доступ) ---
    // Инициализируем значениями с диска при старте приложения
    private var cachedAccessToken: String? = settings.getStringOrNull(KEY_ACCESS_TOKEN)
    private var cachedRefreshToken: String? = settings.getStringOrNull(KEY_REFRESH_TOKEN)

    fun saveAuthData(accessToken: String, refreshToken: String, userId: String, qrSecret: String) {
        log.write("💾 STORAGE: Writing to Memory & Disk...")

        // 1. Обновляем память (МГНОВЕННО)
        cachedAccessToken = accessToken
        cachedRefreshToken = refreshToken

        // 2. Обновляем диск (Асинхронно/Синхронно в зависимости от платформы)
        settings[KEY_ACCESS_TOKEN] = accessToken
        settings[KEY_REFRESH_TOKEN] = refreshToken
        settings[KEY_USER_ID] = userId
        settings[KEY_QR_SECRET] = qrSecret

        log.write("💾 STORAGE: Saved. Memory AccessToken: ${cachedAccessToken?.take(6)}...${cachedAccessToken?.takeLast(5)}")
    }

    fun getAccessToken(): String? {
        // Читаем из памяти!
        return cachedAccessToken
    }

    fun getRefreshToken(): String? {
        // Читаем из памяти!
        return cachedRefreshToken
    }

    fun getUserId(): String? {
        return settings.getStringOrNull(KEY_USER_ID)
    }

    fun getQrSecret(): String? {
        return settings.getStringOrNull(KEY_QR_SECRET)
    }

    fun saveCurrentWorkspaceId(id: String?) {
        if (id == null) {
            settings.remove(KEY_CURRENT_WORKSPACE_ID)
        } else {
            settings.putString(KEY_CURRENT_WORKSPACE_ID, id)
        }
    }

    fun getCurrentWorkspaceId(): String? {
        return settings.getStringOrNull(KEY_CURRENT_WORKSPACE_ID)
    }

    fun setRoleSelected(isSelected: Boolean) {
        settings.putBoolean(KEY_IS_ROLE_SELECTED, isSelected)
    }

    fun isRoleSelected(): Boolean {
        return settings.getBoolean(KEY_IS_ROLE_SELECTED, false)
    }

    fun setAppLanguageCode(code: String) {
        settings.putString(KEY_APP_LANGUAGE, code)
    }

    fun getAppLanguageCode(): String? = settings.getStringOrNull(KEY_APP_LANGUAGE)

    fun clear() {
        log.write("💾 STORAGE: Clearing all data")
        // Чистим память
        cachedAccessToken = null
        cachedRefreshToken = null
        settings.clear()
        log.write("💾 STORAGE: accessToken: ${getAccessToken()?.takeLast(8)}:${ settings.getStringOrNull(KEY_ACCESS_TOKEN)} in memory: ${cachedAccessToken}")
    }
}