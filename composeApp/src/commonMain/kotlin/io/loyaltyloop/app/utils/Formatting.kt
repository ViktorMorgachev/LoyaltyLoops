package io.loyaltyloop.app.utils

fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        amount.toString()
    }
}

fun getCurrencySymbol(currencyCode: String): String {
    return when (currencyCode.uppercase()) {
        "USD" -> "$"
        "EUR" -> "€"
        "RUB" -> "₽"
        "KGS" -> "c"
        "KZT" -> "₸"
        "UZS" -> "so'm"
        "BYN" -> "Br"
        else -> currencyCode
    }
}
