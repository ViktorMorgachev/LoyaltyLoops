package io.loyaltyloop.app.utils

fun Double.toCurrencyString(symbol: String): String {
    val formatted = if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        this.toString()
    }
    return "$formatted $symbol"
}

