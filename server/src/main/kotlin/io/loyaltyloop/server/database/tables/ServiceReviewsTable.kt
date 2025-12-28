package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица приватных отзывов (Книга жалоб и предложений).
 *
 * **Логика связей:**
 * 1. **Partner (CASCADE):** Отзыв принадлежит бизнесу. Нет бизнеса — нет отзывов.
 * 2. **TradingPoint (SET_NULL):** Если филиал закрылся, отзыв остается в архиве Партнера
 *    (как "Общий отзыв"), но ссылка на точку стирается.
 * 3. **User (SET_NULL):** Если клиент удалил аккаунт, отзыв становится "Анонимным",
 *    но рейтинг заведения не должен меняться (иначе конкуренты могут накрутить, а потом удалиться).
 */
// TODO checked
object ServiceReviewsTable : UUIDTable("service_reviews") {
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val tradingPoint = reference("trading_point_id", TradingPointsTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val rating = integer("rating")
    val tags = text("tags").nullable()
    val comment = text("comment").nullable()
    val isReadByOwner = bool("is_read_by_owner").default(false)
    val ownerReply = text("owner_reply").nullable()
    val repliedAt = datetime("replied_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    init {
        index(isUnique = false, partner, createdAt)
        index(isUnique = false, tradingPoint, createdAt)
    }
}
