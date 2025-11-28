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
    data class Concat(val parts: List<UiText>) : UiText {
        constructor(vararg elements: UiText) : this(elements.toList())
    }

    // Для использования в UI (Composable)
    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is Resource -> stringResource(res, *args)
            is Concat -> {
                val builder = StringBuilder()
                parts.forEach { builder.append(it.asString()) }
                builder.toString()
            }
        }
    }

    // Для использования в ViewModel или LaunchedEffect (Suspend)
    suspend fun asStringSuspend(): String {
        return when (this) {
            is DynamicString -> value
            is Resource -> getString(res, *args)
            is Concat -> buildString {
                parts.forEach { append(it.asStringSuspend()) }
            }
        }
    }

    companion object {
        fun concat(vararg parts: UiText): UiText = Concat(parts.toList())
    }
}