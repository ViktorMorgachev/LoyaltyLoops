package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CountryCode

// TODO checked
object TimezoneUtils {

    /**
     * Determines the CountryCode based on the provided timezone ID.
     */
    fun getCountryForTimezone(timezoneId: String): CountryCode {
        if (timezoneId.isBlank()) throw LoyaltyException(
            AppErrorCode.INVALID_REQUEST,
            "Cannot determine country for empty timezone"
        )

        return when {
            // Kyrgyzstan
            timezoneId == "Asia/Bishkek" -> CountryCode.KG

            // Kazakhstan
            timezoneId.startsWith("Asia/Almaty") ||
                    timezoneId.startsWith("Asia/Qyzylorda") ||
                    timezoneId.startsWith("Asia/Aqtobe") ||
                    timezoneId.startsWith("Asia/Aqtau") ||
                    timezoneId.startsWith("Asia/Atyrau") ||
                    timezoneId.startsWith("Asia/Oral") -> CountryCode.KZ

            // Uzbekistan
            timezoneId == "Asia/Tashkent" ||
                    timezoneId == "Asia/Samarkand" -> CountryCode.UZ

            // Belarus
            timezoneId == "Europe/Minsk" -> CountryCode.BY

            // Russia (Expanded list to cover major timezones)
            // Europe part
            timezoneId.startsWith("Europe/Moscow") ||
                    timezoneId.startsWith("Europe/Kaliningrad") ||
                    timezoneId.startsWith("Europe/Samara") ||
                    timezoneId.startsWith("Europe/Volgograd") ||
                    timezoneId.startsWith("Europe/Kirov") ||
                    timezoneId.startsWith("Europe/Astrakhan") ||
                    timezoneId.startsWith("Europe/Saratov") ||
                    timezoneId.startsWith("Europe/Ulyanovsk") ||
                    // Asia part
                    timezoneId.startsWith("Asia/Yekaterinburg") ||
                    timezoneId.startsWith("Asia/Omsk") ||
                    timezoneId.startsWith("Asia/Novosibirsk") ||
                    timezoneId.startsWith("Asia/Barnaul") ||
                    timezoneId.startsWith("Asia/Tomsk") ||
                    timezoneId.startsWith("Asia/Novokuznetsk") ||
                    timezoneId.startsWith("Asia/Krasnoyarsk") ||
                    timezoneId.startsWith("Asia/Irkutsk") ||
                    timezoneId.startsWith("Asia/Chita") ||
                    timezoneId.startsWith("Asia/Yakutsk") ||
                    timezoneId.startsWith("Asia/Vladivostok") ||
                    timezoneId.startsWith("Asia/Magadan") ||
                    timezoneId.startsWith("Asia/Sakhalin") ||
                    timezoneId.startsWith("Asia/Kamchatka") ||
                    timezoneId.startsWith("Asia/Anadyr") -> CountryCode.RU

            else -> throw LoyaltyException(
                AppErrorCode.INVALID_REQUEST,
                "Unsupported country for timezone: $timezoneId"
            )
        }
    }

    /**
     * Determines the local currency based on the provided timezone ID.
     * This is a heuristic mapping for common regions in the CIS/Central Asia.
     */
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

            timezoneId.startsWith("Europe/Kaliningrad") -> "RUB"
            timezoneId.startsWith("Europe/Moscow") -> "RUB"
            timezoneId.startsWith("Europe/Samara") -> "RUB"
            timezoneId.startsWith("Asia/Yekaterinburg") -> "RUB"
            timezoneId.startsWith("Asia/Omsk") -> "RUB"
            timezoneId.startsWith("Asia/Novosibirsk") -> "RUB"
            timezoneId.startsWith("Asia/Krasnoyarsk") -> "RUB"
            timezoneId.startsWith("Asia/Irkutsk") -> "RUB"
            timezoneId.startsWith("Asia/Yakutsk") -> "RUB"
            timezoneId.startsWith("Asia/Vladivostok") -> "RUB"
            timezoneId.startsWith("Asia/Magadan") -> "RUB"
            timezoneId.startsWith("Asia/Kamchatka") -> "RUB"
            timezoneId.startsWith("Asia/Anadyr") -> "RUB"

            // USA (Example)
            timezoneId.startsWith("America/") -> "USD"
            timezoneId.startsWith("Europe/") -> "EUR"

            else -> throw LoyaltyException(
                AppErrorCode.INVALID_REQUEST,
                "Cannot determine currency for timezone: $timezoneId")
        }
        return timezoneCurrency
    }


}

