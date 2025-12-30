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
    RUSSIA(CountryCode.RU, "Россия", "+7", "### ### ## ##", "🇷🇺"), // Добавлено
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
            KYRGYZSTAN, UZBEKISTAN, BELARUS -> 9
            KAZAKHSTAN, RUSSIA -> 10 // У РФ 10 цифр (9xx xxx xx xx)
        }

        return cleanPhone.length == expectedLength
    }

    companion object {
        fun default() = KYRGYZSTAN
    }
}

enum class CountryCode{
    KG, KZ, UZ, BY, RU
}

fun CountryCode.toCurrency(): Currency{
    return when(this){
        CountryCode.KG -> Currency.KGS
        CountryCode.KZ -> Currency.KZT
        CountryCode.UZ -> Currency.UZS
        CountryCode.BY -> Currency.BYN
        CountryCode.RU -> Currency.RUB
    }
}

enum class Currency{
    KGS, KZT, UZS, BYN, RUB
}

