package io.loyaltyloop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Настройка Светлой темы (наш основной корпоративный стиль)
private val LightColors = lightColorScheme(
    primary = LoyaltyIndigo,
    onPrimary = White,

    secondary = LoyaltyEmerald,
    onSecondary = White,

    background = Gray50,
    onBackground = Gray900,

    surface = White,
    onSurface = Gray900,

    error = LoyaltyCoral,
    onError = White,

    outline = Gray300 // Цвет рамок полей ввода
)

// Настройка Темной темы (для любителей ночного режима)
private val DarkColors = darkColorScheme(
    primary = LoyaltyIndigo, // В темной теме часто делают светлее, но оставим бренд
    onPrimary = White,

    secondary = LoyaltyEmerald,
    onSecondary = White,

    background = DarkBackground,
    onBackground = DarkTextPrimary,

    surface = DarkSurface,
    onSurface = DarkTextPrimary,

    error = LoyaltyCoral,
    onError = White,

    outline = Gray500
)

@Composable
fun LoyaltyTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(), // Автоматически берет настройки системы
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        // typography = Typography, // Подключим позже, если будем менять шрифты
        content = content
    )
}
