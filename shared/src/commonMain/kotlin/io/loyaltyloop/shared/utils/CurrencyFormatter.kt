package io.loyaltyloop.shared.utils

import kotlin.math.round

object LoyaltyFormatter {

    /**
     * Округляет число до 2 знаков после запятой.
     * Используется для всех денежных расчетов.
     */
    fun round(value: Double): Double {
        return kotlin.math.round(value * 100) / 100.0
    }

    /**
     * Форматирует число для отображения (UI, чеки, сообщения).
     * - Округляет до 2 знаков.
     * - Если число целое (150.0), возвращает "150".
     * - Если дробное (150.28999), возвращает "150.29".
     */
    fun format(value: Double): String {
        val rounded = round(value)
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    fun formatCurrency(amount: Number?, currencyCode: String?): String {
        if (amount == null) return ""
        val valueStr = format(amount.toDouble())
        val code = currencyCode?.uppercase() ?: ""
        return if (code.isNotEmpty()) "$valueStr ${getCurrencySymbol(code)}" else valueStr
    }

    fun getCurrencySymbol(currencyCode: String?): String {
        val code = currencyCode?.uppercase() ?: ""
        return when (code) {
            "KGS" -> "c"
            "RUB" -> "₽"
            "USD" -> "$"
            "EUR" -> "€"
            "KZT" -> "₸"
            "UZS" -> "sum"
            "BYN" -> "Br"
            else -> code
        }
    }
}
