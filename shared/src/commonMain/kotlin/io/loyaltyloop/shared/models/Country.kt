package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class Country(
    val code: String,   // ISO код (KG)
    val nameRu: String, // Имя
    val phonePrefix: String, // +996
    val mask: String,   // Визуальная маска (для UI)
    val flagEmoji: String
) {
    KYRGYZSTAN("KG", "Кыргызстан", "+996", "### ### ###", "🇰🇬"),
    KAZAKHSTAN("KZ", "Казахстан", "+7", "### ### ## ##", "🇰🇿"),
    UZBEKISTAN("UZ", "Узбекистан", "+998", "## ### ## ##", "🇺🇿"),
    BELARUS("BY", "Беларусь", "+375", "## ### ## ##", "🇧🇾");

    // Метод для очистки номера (убираем пробелы и скобки)
    fun getFullNumber(rawInput: String): String {
        return phonePrefix + rawInput.filter { it.isDigit() }
    }

    companion object {
        fun default() = KYRGYZSTAN
    }
}