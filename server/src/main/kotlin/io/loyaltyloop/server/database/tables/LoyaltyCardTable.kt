package io.loyaltyloop.server.database.tables

import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.CardBlockStatus
import io.loyaltyloop.shared.models.CardPauseStatus
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Таблица Бонусных Карт (Счетов) пользователей.
 * Центральная сущность для хранения баланса и статуса клиента в конкретном бизнесе.
 *
 * **Важные архитектурные моменты:**
 * 1. **Принцип "Один Бизнес — Одна Карта":**
 *    Индекс `uniqueIndex(user, partner)` гарантирует, что у пользователя не может быть
 *    двух счетов у одного партнера.
 *
 * 2. **Base Currency Storage:**
 *    Баланс (`balance`) и LTV (`totalSpent`) всегда хранятся в **Базовой Валюте Партнера** (напр. USD).
 *    Конвертация в локальную валюту точки происходит только в момент отображения (UI)
 *    или расчета транзакции. Это защищает от курсовых колебаний.
 *
 * 3. **Жизненный цикл (CASCADE):**
 *    Если удаляется Пользователь ИЛИ Партнер — карта удаляется.
 *    Для сохранения финансовой отчетности используется `TransactionsHistoryTable`,
 *    но сама карта (как активный счет) существовать без владельца бизнеса не может.
 *
 * 4. **Fraud & Scoring:**
 *    Поля `trustScore` и `fraudFlag` позволяют блокировать начисление бонусов
 *    подозрительным пользователям, не блокируя их полностью в системе.
 */
// TODO checked
object LoyaltyCardsTable : UUIDTable("loyalty_cards") {
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val balance = decimal("balance", 10, 2).default(BigDecimal.ZERO)
    val totalSpent = decimal("total_spent", 12, 2).default(BigDecimal.ZERO)
    val tierLevel = integer("tier_level").default(1)
    val visitsCount = integer("visits_count").default(0)
    val isPaused = bool("is_paused").default(false)
    val pauseReason = varchar("pause_reason", 255).nullable()
    val blockedUntil = datetime("blocked_until").nullable()
    val blockedReason = varchar("blocked_reason", 255).nullable()
    val trustScore = double("trust_score").default(4.0)
    val fraudFlag = bool("fraud_flag").default(false)

    val totalScore = integer("total_score").default(1)
    val lastActivityAt = datetime("last_activity_at").defaultExpression(CurrentDateTime)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    init {
        uniqueIndex("uk_user_partner", user, partner)
    }
}

fun ResultRow.pauseStatus(): CardPauseStatus? {
    val isPaused = this[LoyaltyCardsTable.isPaused]
    return if (isPaused) {
        CardPauseStatus(
            reason = this[LoyaltyCardsTable.pauseReason]
        )
    } else {
        null
    }
}

fun ResultRow.blockStatus(): CardBlockStatus? {
    val until = this[LoyaltyCardsTable.blockedUntil]
    val now = nowUtc()
    return if (until != null && until.isAfter(now)) {
        val untilMillis = until.toUtcMillis()
        CardBlockStatus(
            until = untilMillis,
            reason = this[LoyaltyCardsTable.blockedReason]
        )
    } else {
        null
    }
}
