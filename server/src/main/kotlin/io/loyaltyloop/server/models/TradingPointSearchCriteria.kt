package io.loyaltyloop.server.models

import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType

data class TradingPointSearchCriteria(
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
    if (!includeInactive && !point.active) return false
    if (types.isNotEmpty() && point.type !in types) return false
    if (openNowOnly && point.isOpenNow == false) return false
    if (minRating != null) {
        val rating = point.rating ?: 0.0
        if (rating < minRating) return false
    }

    val normalizedQuery = query?.takeIf { it.isNotBlank() }?.normalizeQuery()
    if (!normalizedQuery.isNullOrEmpty()) {
        val haystack = listOfNotNull(point.name, point.address)
            .joinToString(separator = " ")
            .lowercase()
        if (!haystack.contains(normalizedQuery)) {
            return false
        }
    }

    return true
}


