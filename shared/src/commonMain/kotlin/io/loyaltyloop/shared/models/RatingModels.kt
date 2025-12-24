package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateClientRatingDto(
    val userId: String,
    val rating: Int, // 1-5
    val tags: List<ClientRatingTag> = emptyList(),
    val comment: String? = null
)

@Serializable
enum class ClientRatingTag(val penalty: Double) {
    // --- НЕГАТИВ (Сумма: -4.0) ---
    ABUSE(-2.0),           // Оскорбления (Тяжелое нарушение)
    AGGRESSION(-1.0),      // Хамство / Агрессия
    NO_PAYMENT(-1.0),      // Неплатежеспособность / Проблемы с оплатой

    // --- СПЕЦ. КАТЕГОРИИ (Сумма: 0.0) ---
    FRAUD(0.0),            // Фрод (обрабатывается отдельно флагом, на рейтинг не влияет математически)
    NONE(0.0),

    // --- ПОЗИТИВ (Сумма: +4.0) ---
    TIP(1.5),              // Чаевые (Повысили вес: деньги + лояльность)
    FRIENDLY(1.5),         // Дружелюбный / "Душа компании" (Повысили вес)
    POLITE(1.0);           // Вежливый (Базовая норма)
}

@Serializable
data class CreateServiceReviewDto(
    val tradingPointId: String,
    val rating: Int, // 1-5
    val tags: List<ServiceReviewTag> = emptyList(),
    val comment: String? = null
)


@Serializable
enum class ServiceReviewTag(val penalty: Double) {
    // --- НЕГАТИВ (Сумма: -5.0) ---
    RUDE_STAFF(-2.0),  // Грубый персонал (Самое страшное)
    DIRTY(-1.0),       // Грязно (Критично, подняли с -0.5)
    SLOW(-1.0),        // Медленно (Процесс обслуживания)
    WAIT_TIME(-0.5),   // Долго ждать (Очередь/Посадка - чуть мягче, чем тормозящий персонал)
    PRICEY(-0.5),      // Дорого (Субъективно)

    // --- ПОЗИТИВ (Сумма: +5.0) ---
    FRIENDLY(1.5),     // Приветливый персонал (Снизили с 2.0, чтобы баланс сошелся)
    TASTY(1.0),        // Вкусно (Подняли с 0.5 - это ведь главное!)
    ATTENTIVE(1.0),    // Внимательный персонал (Подняли с 0.5 - качественный сервис важен)
    FAST(0.5),         // Быстро
    CLEAN(0.5),        // Чисто (Это "гигиенический фактор", норма, поэтому вес небольшой)
    COMFORT(0.5);      // Уютно
}

@Serializable
data class TrustScoreDto(
    val score: Double,
    val riskLevel: RiskLevel,
    val fraudFlag: Boolean
)

@Serializable
enum class RiskLevel {
    GREEN,   // 4.5 - 5.0+ (VIP)
    YELLOW,  // 3.5 - 4.4  (Normal)
    ORANGE,  // 2.0 - 3.4  (Warning)
    RED,     // < 2.0      (Toxic)
    BLACK    // Fraud
}

// --- Reviews & Analytics DTOs ---

typealias ReviewType = String

object ReviewTypes {
    const val CLIENT_TO_SERVICE = "CLIENT_TO_SERVICE"
    const val CASHIER_TO_CLIENT = "CASHIER_TO_CLIENT"
}

@Serializable
data class ReviewDto(
    val id: String,
    val rating: Int,
    val tags: List<String>,
    val comment: String?,
    val createdAt: Long,
    val authorName: String,
    val authorPhone: String? = null,
    val targetName: String? = null,
    val targetPhone: String? = null,
    val pointName: String,
    val type: ReviewType
)

@Serializable
data class TagStatDto(
    val tag: String,
    val count: Int
)

@Serializable
data class HeatmapPointDto(
    val pointId: String,
    val pointName: String,
    val tagStats: List<TagStatDto>
)

@Serializable
data class AnalyticsDataDto(
    val nps: Int, // Net Promoter Score (-100..100)
    val averageRating: Double,
    val totalReviews: Int,
    val heatmap: List<HeatmapPointDto>,
    val series: List<AnalyticsSeriesPointDto> = emptyList()
)

@Serializable
data class AnalyticsSeriesPointDto(
    val date: Long,
    val nps: Int,
    val totalReviews: Int,
    val averageRating: Double
)
