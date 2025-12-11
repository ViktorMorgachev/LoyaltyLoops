package io.loyaltyloop.shared.utils

fun formatCurrency(amount: Number?, currencyCode: String?): String {
    if (amount == null) return ""

    val doubleValue = amount.toDouble()
    val valueString = if (doubleValue % 1.0 == 0.0) {
        doubleValue.toLong().toString()
    } else {
        doubleValue.toString()
    }

    val code = currencyCode?.uppercase() ?: ""

    return if (code.isNotEmpty()) "$valueString ${getCurrencySymbol(code)}" else valueString
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
