package io.loyaltyloop.server.utils

import java.util.Locale
import java.util.ResourceBundle

object ServerResources {

    private const val BUNDLE_NAME = "i18n/messages"

    /**
     * Получает строку по ключу и языку.
     * Если перевод не найден, возвращает английский или сам ключ.
     */
    fun get(key: String, languageCode: String): String {
        val locale = try {
            Locale.forLanguageTag(languageCode)
        } catch (e: Exception) {
            Locale("ru")
        }

        return try {
            val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale)
            val value = bundle.getString(key)

            // --- FIX КОДИРОВКИ ---
            // Читаем "битые" байты как ISO-8859-1 и собираем обратно в UTF-8
            String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            // ---------------------

        } catch (e: Exception) {
            key
        }
    }
}