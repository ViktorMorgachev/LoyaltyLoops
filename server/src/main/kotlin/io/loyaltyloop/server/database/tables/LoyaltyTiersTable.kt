package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.math.BigDecimal

/**
 * Уровни лояльности (Tiers).
 * Привязаны строго к ПАРТНЕРУ (Бизнесу), а не к точке.
 *
 * **Важные моменты:**
 * 1. **Единая система:** Клиент копит прогресс (`TotalSpent`) во всей сети.
 *    Поэтому уровни не могут отличаться в разных филиалах.
 *
 * 2. **Base Currency:**
 *    Поле `threshold` (порог входа) хранится в Базовой Валюте Партнера (USD).
 *    При расчете уровня в другой стране (KGS), сумма трат клиента конвертируется
 *    обратно в USD для сравнения с этим порогом.
 *
 * 3. **Decimal:**
 *    Используется `Decimal(12, 2)` для денег и `Decimal(5, 2)` для процентов,
 *    чтобы избежать ошибок округления `Double`.
 */
// TODO checked
object LoyaltyTiersTable : UUIDTable("loyalty_tiers") {
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val levelIndex = integer("level_index")
    val name = varchar("name", 50)
    val threshold = decimal("threshold", 12, 2).default(BigDecimal.ZERO)
    val cashbackPercent = decimal("cashback_percent", 5, 2).default(BigDecimal.ZERO)
    init {
        uniqueIndex(partner, levelIndex)
    }
}
