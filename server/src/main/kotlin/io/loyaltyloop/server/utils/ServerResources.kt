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
            Locale("ru") // Дефолт, если код кривой
        }

        return try {
            // Java сама найдет нужный файл (messages_en, messages_ru и т.д.)
            // Если не найдет messages_ky, она возьмет дефолтный (messages_ru)
            val bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale)
            bundle.getString(key)
        } catch (e: Exception) {
            // Если ключа нет даже в дефолтном файле
            key
        }
    }
}