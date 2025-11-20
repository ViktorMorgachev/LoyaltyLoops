package io.loyaltyloop.server.utils

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Извлекает язык из заголовка Accept-Language.
 * Если заголовка нет, возвращает "ru".
 */
fun ApplicationCall.resolveLanguage(): String {
    // Заголовок может быть сложным, например "en-US,en;q=0.9"
    // Для MVP берем просто первые 2 буквы
    val header = request.header(HttpHeaders.AcceptLanguage)
    return header?.take(2) ?: "ru"
}