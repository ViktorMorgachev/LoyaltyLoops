package io.loyaltyloop.server.models

import io.loyaltyloop.server.utils.haversineMeters
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType

data class TradingPointSearchCriteria(
    val offset: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val limit: Int = 50,
    val query: String? = null,
    val types: Set<TradingPointType> = emptySet(),
    val openNowOnly: Boolean = false,
    val minRating: Double? = null,
    val includeInactive: Boolean = false
)

private fun String.normalizeQuery(): String = trim().lowercase()

fun TradingPointSearchCriteria.matches(point: TradingPointDto): Boolean {
    // 1. Базовые проверки
    if (!includeInactive && !point.active) return false
    if (types.isNotEmpty() && point.type !in types) return false
    if (openNowOnly && point.isOpenNow == false) return false

    // 2. Рейтинг
    if (minRating != null) {
        val rating = point.rating ?: 0.0
        if (rating < minRating) return false
    }

    // 3. Текстовый поиск
    val normalizedQuery = query?.takeIf { it.isNotBlank() }?.normalizeQuery()
    if (!normalizedQuery.isNullOrEmpty()) {
        val haystack = listOfNotNull(point.name, point.address)
            .joinToString(separator = " ")
            .lowercase()
        if (!haystack.contains(normalizedQuery)) {
            return false
        }
    }

    // Если у точки есть координаты, проверяем попадание в радиус
    if (point.latitude != null && point.longitude != null) {
        val dist = haversineMeters(
            latitude, longitude,
            point.latitude!!, point.longitude!!
        )
        if (dist > radiusMeters) return false
    }

    return true
}



