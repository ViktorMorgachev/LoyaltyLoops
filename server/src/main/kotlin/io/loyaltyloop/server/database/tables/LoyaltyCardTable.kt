package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object LoyaltyCardTable : Table("loyalty_cards") {
    // Уникальный ID самой карты
    val id = varchar("id", 50)

    // Чья это карта? (Ссылка на глобального юзера)
    val userId = varchar("user_id", 50).index()

    // Какого заведения эта карта? (Ссылка на Партнера)
    val partnerId = varchar("partner_id", 50).index()

    // ФИНАНСЫ (накопления в этом конкретном заведении)
    val balance = double("balance").default(0.0)       // Доступные баллы

    // Приостановка карты (архивная)
    val isPaused = bool("is_closed").default(false)
    val pauseReason = varchar("pause_reason", 255).nullable()
    val totalSpent = double("total_spent").default(0.0) // Сколько денег потратил всего (LTV)

    // УРОВЕНЬ (Кешируем текущий уровень, чтобы не пересчитывать каждый раз)
    // Например: 0 - Start, 1 - Middle, 2 - Top
    val tierLevel = integer("tier_level").default(0)

    val visitsCount = integer("visits_count").default(0)

    // Блокировка конкретно в этом заведении
    val blockedUntil = long("blocked_until").nullable()
    val blockedReason = varchar("blocked_reason", 255).nullable()

    // Рейтинг доверия (Social Score)
    val trustScore = double("trust_score").default(4.0)
    val fraudFlag = bool("fraud_flag").default(false)

    // Дата последней активности (для сгорания бонусов)
    val lastActivityAt = long("last_activity_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uk_user_partner", userId, partnerId)
    }
}
