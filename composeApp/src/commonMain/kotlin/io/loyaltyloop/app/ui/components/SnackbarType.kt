package io.loyaltyloop.app.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.loyaltyloop.app.ui.theme.LoyaltyCoral
import io.loyaltyloop.app.ui.theme.LoyaltyEmerald
import io.loyaltyloop.app.utils.UiText

enum class SnackbarType {
    Success, Error, Info
}

/**
 * Кастомные данные для снекбара, чтобы передать тип.
 */
class LoyaltySnackbarVisuals(
    override val message: String,
    val type: SnackbarType,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

/**
 * Функция-расширение для удобного вызова.
 * Автоматически превращает UiText в строку.
 */
suspend fun SnackbarHostState.show(
    message: UiText,
    type: SnackbarType = SnackbarType.Info
) {
    // В KMP getString должен вызываться в корутине или Composable. 
    // Здесь мы предполагаем, что UiText.asStringSuspend() доступен (мы его писали ранее).
    // Если нет - передавай String.
    val text = message.asStringSuspend()
    
    this.showSnackbar(
        LoyaltySnackbarVisuals(
            message = text,
            type = type
        )
    )
}

/**
 * Сам UI компонент Снекбара
 */
@Composable
fun LoyaltySnackbar(snackbarData: SnackbarData) {
    val visuals = snackbarData.visuals as? LoyaltySnackbarVisuals
    val type = visuals?.type ?: SnackbarType.Info

    val backgroundColor = when (type) {
        SnackbarType.Success -> LoyaltyEmerald
        SnackbarType.Error -> LoyaltyCoral
        SnackbarType.Info -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (type) {
        SnackbarType.Info -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.White
    }

    Snackbar(
        snackbarData = snackbarData,
        containerColor = backgroundColor,
        contentColor = contentColor,
        actionColor = contentColor
    )
}