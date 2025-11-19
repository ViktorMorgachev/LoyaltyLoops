package io.loyaltyloop.app.utils

import co.touchlab.kermit.Logger

/**
 * Перечисление типов логов для удобства.
 * Дефолтное значение в функции будет INFO.
 */
enum class LogType {
    Debug,   // Для отладки (d)
    Info,    // Полезная информация (i)
    Warning, // Предупреждение (w)
    Error    // Ошибка (e)
}

/**
 * Магическое расширение для ЛЮБОГО класса.
 * Автоматически создает тег по имени класса.
 *
 * Использование:
 * log.write("Message")
 * log.write("Error happened", LogType.Error, exception)
 */
val Any.log: Logger
    get() {
        // Пытаемся достать имя класса. Если это анонимный объект или лямбда, берем "LoyaltyApp"
        val tag = this::class.simpleName ?: "LoyaltyApp"
        return Logger.withTag(tag)
    }

/**
 * Универсальная функция записи лога.
 *
 * @param message Сообщение для вывода
 * @param type Тип лога (по умолчанию Info)
 * @param e Исключение (Throwable), если нужно залогировать креш (по умолчанию null)
 */
fun Logger.write(
    message: String,
    type: LogType = LogType.Info,
    e: Throwable? = null
) {
    when (type) {
        LogType.Debug -> this.d(e) { message }
        LogType.Info -> this.i(e) { message }
        LogType.Warning -> this.w(e) { message }
        LogType.Error -> this.e(e) { message }
    }
}