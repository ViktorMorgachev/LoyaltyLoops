package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.Country

// TODO checked
private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()


fun isValidEmail(email: String): Boolean {
    return email.isNotBlank() && email.matches(EMAIL_REGEX)
}
/**
 * Проверяет номер телефона.
 * @return null, если номер валиден.
 * @return Строку с ошибкой, если не валиден.
 */

fun validatePhoneNumber(phone: String) {
    val country = Country.entries
        .sortedByDescending { it.phonePrefix.length }
        .find { phone.startsWith(it.phonePrefix) }

    if (country == null) {
        throw LoyaltyException(AppErrorCode.INVALID_PHONE, "Неизвестный код страны (поддерживаются: KG, RU, KZ, UZ, BY)")
    }
    val phoneBody = phone.removePrefix(country.phonePrefix)

    if (!country.isValidNumber(phoneBody)) {
        throw LoyaltyException(AppErrorCode.INVALID_PHONE, "Некорректная длина номера для ${country.nameRu}")
    }
}