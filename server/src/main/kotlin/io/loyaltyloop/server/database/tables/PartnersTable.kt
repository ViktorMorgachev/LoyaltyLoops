package io.loyaltyloop.server.database.tables

import io.loyaltyloop.server.database.generateCode
import io.loyaltyloop.shared.models.PartnerStatus
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица Партнеров (Бизнес-аккаунтов).
 * Является корневой сущностью для `TradingPoints`, `LoyaltyCards`, `PartnerStaff`.
 *
 * **Важные архитектурные моменты:**
 * 1. **Owner vs Partner:** `owner` — это ссылка на `UsersTable` (Личность).
 *    `Partner` — это Компания. Один человек теоретически может владеть несколькими Компаниями.
 *    Все настройки лояльности привязываются к Компании (`Partner`), а не к человеку.
 *
 * 2. **Base Currency (Мультивалютность):**
 *    Поле `baseCurrency` определяет валюту, в которой физически хранятся баллы клиентов в БД.
 *    Даже если торговые точки работают в разных странах (KGS, RUB), баланс всегда приводится
 *    к этой валюте для корректного расчета уровней (Tier Thresholds).
 *
 * 3. **Timezone:**
 *    Важно для Cron-задач (ежедневные отчеты, поздравления).
 *    Отчеты генерируются в 21:00 по времени, указанному в этом поле, а не по времени сервера (UTC).
 *
 * 4. **Invite Code:**
 *    `managerInviteCode` генерируется автоматически (`clientDefault`) при создании партнера.
 *    Используется для быстрого найма менеджеров без создания лишних заявок.
 */
// TODO checked
object PartnersTable : UUIDTable("partners") {
    val owner = reference("owner_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val businessName = varchar("business_name", 50)
    val countryCode = varchar("country_code", 4).default("KG")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val adminPinHash = varchar("admin_pin_hash", 128).nullable()
    val logoUrl = varchar("logo_url", 255).nullable()
    val color = varchar("color", 9).default("#4F46E5")
    val defaultVisitsTarget = integer("default_visits_target").default(10)
    val status = enumerationByName("status", 20, PartnerStatus::class).default(PartnerStatus.PENDING)
    val burnBonusesDays = integer("burn_bonuses_days").nullable()
    val downgradeTierDays = integer("downgrade_tier_days").nullable()
    val timezone = varchar("timezone", 50).default("UTC")
    val managerInviteCode = varchar("manager_invite_code", 20)
        .uniqueIndex()
        .clientDefault {
            generateCode(suffix = "M-")
        }
    val baseCurrency = varchar("base_currency", 3).default("USD")
}
