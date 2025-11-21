package io.loyaltyloop.app.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.log
import io.loyaltyloop.app.utils.write

class TokenStorage(private val settings: Settings) {

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_ROLE_SELECTED = "is_role_selected"
        private const val KEY_QR_SECRET = "qr_secret"
        private const val KEY_CURRENT_WORKSPACE_ID = "current_workspace_id"
    }

    // Сохранить все данные сразу
    fun saveAuthData(accessToken: String, refreshToken: String, userId: String, qrSecret: String) {
        log.write("💾 STORAGE: Saving tokens... Access[${accessToken.take(5)}...]")
        settings[KEY_ACCESS_TOKEN] = accessToken
        settings[KEY_REFRESH_TOKEN] = refreshToken
        settings[KEY_USER_ID] = userId
        settings[KEY_QR_SECRET] = qrSecret
    }

    fun getQrSecret(): String? = settings.getStringOrNull(KEY_QR_SECRET)

    // Получить Access Token
    fun getAccessToken(): String? {
        val token = settings.getStringOrNull(KEY_ACCESS_TOKEN)
        log.write("💾 STORAGE: Reading Access Token: ${if (token != null) "FOUND" else "NULL"}", LogType.Debug)
        return token
    }

    // Получить Refresh Token
    fun getRefreshToken(): String? {
        return settings.getStringOrNull(KEY_REFRESH_TOKEN)
    }

    fun setRoleSelected(isSelected: Boolean) {
        settings.putBoolean(KEY_IS_ROLE_SELECTED, isSelected)
    }

    fun isRoleSelected(): Boolean {
        return settings.getBoolean(KEY_IS_ROLE_SELECTED, false)
    }

    fun getUserId(): String? {
        return settings.getStringOrNull(KEY_USER_ID)
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

    // Очистить (при выходе)
    fun clear() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
        settings.remove(KEY_IS_ROLE_SELECTED)
        settings.remove(KEY_USER_ID)
    }
}