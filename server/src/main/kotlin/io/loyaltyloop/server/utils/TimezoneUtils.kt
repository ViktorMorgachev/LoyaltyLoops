package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CountryCode

object TimezoneUtils {

    // Порядок важен: специфичные зоны (Europe/Minsk) должны матчиться раньше общих фоллбэков (Europe/)
    private val countryByZonePrefix: List<Pair<String, CountryCode>> = buildList {
        add("Asia/Bishkek" to CountryCode.KG)

        listOf(
            "Asia/Almaty", "Asia/Qyzylorda", "Asia/Aqtobe",
            "Asia/Aqtau", "Asia/Atyrau", "Asia/Oral"
        ).forEach { add(it to CountryCode.KZ) }

        listOf("Asia/Tashkent", "Asia/Samarkand").forEach { add(it to CountryCode.UZ) }

        add("Europe/Minsk" to CountryCode.BY)

        listOf(
            // Европейская часть
            "Europe/Moscow", "Europe/Kaliningrad", "Europe/Samara", "Europe/Volgograd",
            "Europe/Kirov", "Europe/Astrakhan", "Europe/Saratov", "Europe/Ulyanovsk",
            // Азиатская часть
            "Asia/Yekaterinburg", "Asia/Omsk", "Asia/Novosibirsk", "Asia/Barnaul",
            "Asia/Tomsk", "Asia/Novokuznetsk", "Asia/Krasnoyarsk", "Asia/Irkutsk",
            "Asia/Chita", "Asia/Yakutsk", "Asia/Vladivostok", "Asia/Magadan",
            "Asia/Sakhalin", "Asia/Kamchatka", "Asia/Anadyr"
        ).forEach { add(it to CountryCode.RU) }
    }

    private val currencyByCountry = mapOf(
        CountryCode.KG to "KGS",
        CountryCode.KZ to "KZT",
        CountryCode.UZ to "UZS",
        CountryCode.BY to "BYN",
        CountryCode.RU to "RUB"
    )

    /**
     * Determines the CountryCode based on the provided timezone ID.
     */
    fun getCountryForTimezone(timezoneId: String): CountryCode {
        if (timezoneId.isBlank()) throw LoyaltyException(
            AppErrorCode.INVALID_REQUEST,
            "Cannot determine country for empty timezone"
        )

        return findCountry(timezoneId) ?: throw LoyaltyException(
            AppErrorCode.INVALID_REQUEST,
            "Unsupported country for timezone: $timezoneId"
        )
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

        findCountry(timezoneId)?.let { country ->
            currencyByCountry[country]?.let { return it }
        }

        return when {
            timezoneId.startsWith("America/") -> "USD"
            timezoneId.startsWith("Europe/") -> "EUR"
            else -> throw LoyaltyException(
                AppErrorCode.INVALID_REQUEST,
                "Cannot determine currency for timezone: $timezoneId"
            )
        }
    }

    private fun findCountry(timezoneId: String): CountryCode? =
        countryByZonePrefix.firstOrNull { (prefix, _) -> timezoneId.startsWith(prefix) }?.second
}
