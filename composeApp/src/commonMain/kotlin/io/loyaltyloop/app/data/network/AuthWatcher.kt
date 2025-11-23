package io.loyaltyloop.app.data.network

import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.shared.models.AppErrorCode

/**
 * Централизованный обработчик auth-ошибок.
 * Регистрируем SessionManager, а потом из NetworkHelper дергаем logout.
 */
object AuthWatcher {
    private var logoutCallback: (suspend () -> Unit)? = null

    private val authErrorCodes = setOf(
        AppErrorCode.UNAUTHORIZED,
        AppErrorCode.USER_NOT_FOUND,
        AppErrorCode.INVALID_CODE,
        AppErrorCode.CODE_EXPIRED
    )

    fun register(sessionManager: SessionManager) {
        logoutCallback = { sessionManager.logout() }
    }

    suspend fun onAuthError(code: AppErrorCode?) {
        if (code != null && authErrorCodes.contains(code)) {
            logoutCallback?.invoke()
        }
    }

    suspend fun onUnauthorizedFallback() {
        logoutCallback?.invoke()
    }
}

