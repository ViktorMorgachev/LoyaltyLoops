package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.PlatformRequestStatus
import io.loyaltyloop.shared.models.PlatformRequestType
import io.loyaltyloop.shared.models.SubscriptionDuration
import io.loyaltyloop.shared.models.SubscriptionType
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal

/**
 * Таблица Подписок (Лицензий) Торговых Точек.
 * Является "рубильником", определяющим доступ к сервису.
 *
 * **Архитектура Биллинга:**
 * 1. **Гранулярность:** Подписка выдается на конкретную **Точку** (`tradingPoint`), а не на Партнера.
 *    Это позволяет Партнеру оплатить 3 точки, а 4-ю (новую) держать на паузе.
 *
 * 2. **Operational Check (Индекс):**
 *    Индекс `uniqueIndex(tradingPoint, isActive)` критически важен для API `/terminal/scan`.
 *    Перед каждой транзакцией сервер проверяет: есть ли у этой точки активная подписка?
 *    Если нет -> ошибка 403 "Service Unavailable".
 *
 * 3. **Lifecycle Automation (Cron Job):**
 *    Поля `warningSentAt` и `criticalSentAt` нужны для фонового демона (`LoyaltyEngineService`).
 *    Они гарантируют, что мы отправим SMS о продлении ровно 1 раз, а не будем спамить каждый час.
 *    Индекс на `endDate` позволяет мгновенно находить истекающие подписки.
 */
// TODO checked
object PlatformSubscriptionsTable : UUIDTable("platform_subscriptions") {
    val tradingPoint = reference("point_id", TradingPointsTable, onDelete = ReferenceOption.CASCADE)
    val requester = reference("requester_id", SystemStaffTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val type = enumerationByName("type", 20, SubscriptionType::class) // FIXED, REV_SHARE
    val isTrial = bool("is_trial").default(false)
    val amount = decimal("amount", 10, 2).default(BigDecimal.ZERO)
    val startDate = datetime("start_date").defaultExpression(CurrentDateTime)
    val endDate = datetime("end_date").index()
    val isActive = bool("is_active").default(true)
    val warningSentAt = datetime("warning_sent_at").nullable()
    val criticalSentAt = datetime("critical_sent_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    init {
        index(isUnique = false, tradingPoint, isActive)
    }
}

/**
 * Таблица Заявок (B2B Workflow).
 * Любое изменение статуса бизнеса (Активация, Блокировка, Продление) проходит через эту таблицу.
 *
 * **Зачем это нужно:**
 * 1. **Аудит и Контроль:** Менеджеры не могут сами включать точки (чтобы не воровали).
 *    Они создают заявку -> Супер-Админ её одобряет.
 *    Эта таблица хранит историю: "Кто попросил?" и "Кто разрешил?".
 *
 * 2. **Полиморфизм Цели:**
 *    Заявка может касаться конкретной Точки (`targetPoint`) — например, активация тарифа.
 *    Или всего Партнера (`targetPartner`) — например, блокировка за нарушение правил.
 *
 * 3. **История отказов:**
 *    Поле `rejectReason` помогает менеджеру понять, почему админ завернул заявку,
 *    и исправить данные перед повторной подачей.
 */
// TODO checked
object PlatformRequestsTable : UUIDTable("platform_requests") {
    val type = enumerationByName("type", 30, PlatformRequestType::class)
    val status = enumerationByName("status", 20, PlatformRequestStatus::class).index()
    val requester = reference("requester_id", SystemStaffTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val approver = reference("approver_id", SystemStaffTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val targetPoint = reference("target_point_id", TradingPointsTable, onDelete = ReferenceOption.CASCADE).nullable()
    val targetPartner = reference("target_partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE).nullable()
    val amount = decimal("amount", 10, 2).nullable()
    val duration = enumerationByName("duration", 20, SubscriptionDuration::class).nullable() // e.g., "1_MONTH", "1_YEAR"
    val isTrial = bool("is_trial").default(false)
    val blockReason = varchar("block_reason", 255).nullable()
    val rejectReason = varchar("reject_reason", 255).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime).index()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

/**
 * Таблица Приглашений для Персонала Платформы.
 * Используется для найма сотрудников самой компании LoyaltyLoops (Sales, Support).
 *
 * **Безопасность:**
 * 1. **Одноразовость:** Флаг `isUsed` предотвращает повторную регистрацию по одному коду.
 * 2. **Срок действия:** Поле `expiresAt` делает утечку старых ссылок безопасной.
 * 3. **Аудит:** Поле `createdBy` показывает, какой админ сгенерировал инвайт.
 *    Поле `usedBy` показывает, какой юзер в итоге зарегистрировался.
 */
// TODO checked
object PlatformInvitesTable : UUIDTable("platform_invites") {
    val code = varchar("code", 20).uniqueIndex()
    val role = enumerationByName("role", 50, UserRole::class)
    val createdBy = reference("created_by", SystemStaffTable, onDelete = ReferenceOption.CASCADE)
    val isUsed = bool("is_used").default(false)
    val usedAt = datetime("used_at").nullable()
    val usedBy = reference("used_by", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val expiresAt = datetime("expires_at").index()
}