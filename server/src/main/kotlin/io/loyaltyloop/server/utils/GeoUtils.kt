package io.loyaltyloop.server.utils

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// TODO checked
const val EARTH_RADIUS_METERS = 6371000.0

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    // Оптимизация: вычисляем синусы один раз
    val sinDeltaPhi = sin(deltaPhi / 2)
    val sinDeltaLambda = sin(deltaLambda / 2)

    // Оптимизация: используем умножение вместо pow(..., 2.0)
    val a = (sinDeltaPhi * sinDeltaPhi) +
            (cos(phi1) * cos(phi2) * sinDeltaLambda * sinDeltaLambda)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return EARTH_RADIUS_METERS * c
}

