package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class Country(
    val code: CountryCode,
    val nameRu: String,
    val phonePrefix: String,
    val mask: String,
    val flagEmoji: String
) {
    KYRGYZSTAN(CountryCode.KG, "Кыргызстан", "+996", "### ### ###", "🇰🇬"),
    KAZAKHSTAN(CountryCode.KZ, "Казахстан", "+7", "### ### ## ##", "🇰🇿"),
    UZBEKISTAN(CountryCode.UZ, "Узбекистан", "+998", "## ### ## ##", "🇺🇿"),
    BELARUS(CountryCode.BY, "Беларусь", "+375", "## ### ## ##", "🇧🇾");

    // Функция для получения чистого номера (без пробелов и скобок)
    fun getFullNumber(rawInput: String): String {
        return phonePrefix + rawInput.filter { it.isDigit() }
    }

    // --- ВОТ ЭТА ФУНКЦИЯ, КОТОРОЙ НЕ ХВАТАЛО ---
    fun isValidNumber(phoneInput: String): Boolean {
        // Оставляем только цифры
        val cleanPhone = phoneInput.filter { it.isDigit() }

        // Проверяем длину "тела" номера (без кода страны)
        val expectedLength = when(this) {
            KYRGYZSTAN -> 9   // 996 (555 123 456)
            UZBEKISTAN -> 9   // 998 (90 123 45 67)
            BELARUS -> 9      // 375 (29 123 45 67)
            KAZAKHSTAN -> 10  // 7 (777 123 45 67)
        }

        return cleanPhone.length == expectedLength
    }

    companion object {
        fun default() = KYRGYZSTAN
    }
}

enum class CountryCode{
    KG, KZ, UZ, BY
}

