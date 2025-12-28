package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.AppErrorCode

object TimezoneUtils {

    /**
     * Determines the local currency based on the provided timezone ID.
     * This is a heuristic mapping for common regions in the CIS/Central Asia.
     */
    // TODO checked
    fun getCurrencyForTimezone(timezoneId: String): String {
        if (timezoneId.isBlank()) throw LoyaltyException(
            AppErrorCode.INVALID_REQUEST,
            "Cannot determine currency for empty timezone: $timezoneId"
        )

        val timezoneCurrency = when {
            // Kyrgyzstan
            timezoneId == "Asia/Bishkek" -> "KGS"

            // Kazakhstan
            timezoneId.startsWith("Asia/Almaty") -> "KZT"
            timezoneId.startsWith("Asia/Qyzylorda") -> "KZT"
            timezoneId.startsWith("Asia/Aqtobe") -> "KZT"
            timezoneId.startsWith("Asia/Aqtau") -> "KZT"
            timezoneId.startsWith("Asia/Atyrau") -> "KZT"
            timezoneId.startsWith("Asia/Oral") -> "KZT"

            // Uzbekistan
            timezoneId == "Asia/Tashkent" -> "UZS"
            timezoneId == "Asia/Samarkand" -> "UZS"

            // Belarus
            timezoneId == "Europe/Minsk" -> "BYN"

            // Russia (Partial list, major zones)
            timezoneId.startsWith("Europe/Moscow") -> "RUB"
            timezoneId.startsWith("Asia/Yekaterinburg") -> "RUB"
            timezoneId.startsWith("Asia/Novosibirsk") -> "RUB"
            timezoneId.startsWith("Asia/Krasnoyarsk") -> "RUB"
            timezoneId.startsWith("Asia/Irkutsk") -> "RUB"
            timezoneId.startsWith("Asia/Vladivostok") -> "RUB"

            // USA (Example)
            timezoneId.startsWith("America/") -> "USD"

            else -> throw LoyaltyException(
                AppErrorCode.INVALID_REQUEST,
                "Cannot determine currency for timezone: $timezoneId")
        }
        return timezoneCurrency
    }


}

