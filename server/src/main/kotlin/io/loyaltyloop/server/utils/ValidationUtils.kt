package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.Country

private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()

fun isValidEmail(email: String): Boolean {
    return email.isNotBlank() && email.matches(EMAIL_REGEX)
}
/**
 * Проверяет номер телефона.
 * @return null, если номер валиден.
 * @return Строку с ошибкой, если не валиден.
 */
fun validatePhoneNumber(phone: String): String? {
    // 1. Ищем страну по префиксу
    val country = Country.entries.find { phone.startsWith(it.phonePrefix) }

    if (country == null) {
        return "Неизвестный код страны (поддерживаются: KG, KZ, UZ, BY)"
    }

    // 2. Отрезаем префикс, чтобы проверить длину
    val phoneBody = phone.removePrefix(country.phonePrefix)

    // 3. Используем логику из Shared модуля
    if (!country.isValidNumber(phoneBody)) {
        return "Некорректная длина номера для ${country.nameRu}"
    }

    return null // Всё отлично
}