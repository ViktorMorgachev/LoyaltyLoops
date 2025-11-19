package io.loyaltyloop.app.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Обертка для текста, который может быть либо строкой из ресурсов,
 * либо динамической строкой (пришедшей с сервера).
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class Resource(val res: StringResource, vararg val args: Any) : UiText

    // Для использования в UI (Composable)
    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is Resource -> stringResource(res, *args)
        }
    }

    // Для использования в ViewModel или LaunchedEffect (Suspend)
    suspend fun asStringSuspend(): String {
        return when (this) {
            is DynamicString -> value
            // getString - это нативная KMP функция
            is Resource -> getString(res, *args)
        }
    }
}