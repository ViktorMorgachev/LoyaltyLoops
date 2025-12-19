package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
/**
 * Таблица временных токенов для сброса ПИН-кода Бизнеса.
 * Используется, когда владелец забыл код доступа к админке/кассе.
 *
 * **Важные архитектурные моменты:**
 * 1. **Привязка к Бизнесу (Partner):**
 *    ПИН-код — это атрибут Компании (`PartnersTable`)
 *    Если у пользователя 2 бизнеса, он сбрасывает ПИН для каждого отдельно.
 *    Поэтому Foreign Key указывает на `partner_id`.
 *
 * 2. **Безопасность (Hashing):**
 *    В поле `tokenHash` хранится **не сам код** (например, "5921"), а его SHA-256 хэш.
 *    Даже если базу украдут, злоумышленники не увидят активные коды сброса.
 *
 * 3. **Жизненный цикл:**
 *    Токен одноразовый.
 *    - Если `usedAt != null` — токен погашен (защита от повторного использования).
 *    - Если `expiresAt < NOW` — токен протух.
 */
// TODO checked
object PinResetTokensTable : UUIDTable("pin_reset_tokens") {
    val partner = reference("partner", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 128)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val expiresAt = datetime("expires_at") // Когда токен протухнет
    val usedAt = datetime("used_at").nullable()
    init {
        index(isUnique = false, expiresAt)
    }
}

