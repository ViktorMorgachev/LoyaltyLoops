package io.loyaltyloop.server.routes

/** Дефолтные значения карты — fallback'и для `app.maps.*` из конфига. */
internal object MapDefaults {
    const val MIN_RADIUS_M = 50
    const val DEFAULT_RADIUS_M = 2000
    const val MAX_RADIUS_M = 15000
}
