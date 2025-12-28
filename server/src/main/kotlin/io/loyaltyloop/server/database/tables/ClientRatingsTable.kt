package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица внутренних рейтингов и взаимодействий (Кассир -> Клиент).
 * Служит для формирования "Trust Score" клиента и аналитики качества обслуживания.
 *
 * **Важные архитектурные моменты:**
 * 1. **Направление оценки:**
 *    В отличие от `ServiceReviewsTable` (где клиент оценивает бизнес), здесь хранится
 *    обратная связь или системная оценка транзакции.
 *
 * 2. **Сохранение Истории (SET_NULL):**
 *    Для полей `tradingPoint`, `cashier` и `user` используется стратегия `SET_NULL`.
 *    - **Зачем:** Если кассир уволится (удален из базы) или точка закроется,
 *      статистика партнера за прошлый месяц (NPS, активность) не должна измениться.
 *      Запись останется "историческим фактом", просто без ссылки на живого кассира.
 *
 * 3. **Производительность (Индексы):**
 *    Индексы составлены так, чтобы запросы вида "Покажи ленту оценок для Точки Х за сегодня"
 *    выполнялись мгновенно (Index Scan), минуя перебор всей таблицы.
 *
 * 4. **Аудит (isIgnored):**
 *    Мы никогда не удаляем оценки физически, если они кажутся подозрительными (фрод).
 *    Вместо этого выставляется флаг `isIgnored = true`. Это позволяет исключить оценку
 *    из расчета среднего рейтинга, но оставить её в логах для проверки.
 */
// TODO checked
object ClientRatingsTable : UUIDTable("client_ratings") {

    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val tradingPoint = reference("trading_point_id", TradingPointsTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val cashier = reference("cashier_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val rating = integer("rating")
    val tags = text("tags").nullable()
    val comment = text("comment").nullable()
    val isIgnored = bool("is_ignored").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    init {
        index(isUnique = false, partner, createdAt)
        index(isUnique = false, tradingPoint, createdAt)
    }
}
