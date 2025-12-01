package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.TradingPointSearchCriteria
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.double
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.server.utils.long
import io.loyaltyloop.server.utils.string
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.FeatureToggleDto
import io.loyaltyloop.shared.models.MapProvider
import io.loyaltyloop.shared.models.MapSettingsDto
import io.loyaltyloop.shared.models.PublicConfigResponse
import io.loyaltyloop.shared.models.TradingPointType

fun Route.publicRoutes(
    applicationConfig: ApplicationConfig,
    partnerRepository: PartnerRepository
) {
    val mapMinRadius = applicationConfig.int("app.maps.minRadiusMeters", 50)
    val mapDefaultRadius = applicationConfig.int("app.maps.defaultRadiusMeters", 2000)
    val mapMaxRadius = applicationConfig.int("app.maps.maxRadiusMeters", 15000)

    route("/public") {
        get("/config") {
            val features = FeatureToggleDto(
                realtimeEnabled = applicationConfig.bool("features.realtimeEnabled", true),
                pushEnabled = applicationConfig.bool("features.pushEnabled", true),
                mapEnabled = applicationConfig.bool("features.mapEnabled", false),
                testLabEnabled = applicationConfig.bool("features.testLabEnabled", false)
            )

            val providerRaw = applicationConfig.string("app.maps.provider", "YANDEX")
            val provider = runCatching { MapProvider.valueOf(providerRaw.uppercase()) }
                .getOrElse { MapProvider.YANDEX }

            val mapSettings = MapSettingsDto(
                provider = provider,
                minRadiusMeters = mapMinRadius,
                defaultRadiusMeters = mapDefaultRadius,
                maxRadiusMeters = mapMaxRadius,
                clusterRadiusMeters = applicationConfig.int("app.maps.clusterRadiusMeters", 80),
                searchDebounceMs = applicationConfig.long("app.maps.searchDebounceMs", 350),
                showFilters = applicationConfig.bool("app.maps.showFilters", true),
                showRatings = applicationConfig.bool("app.maps.showRatings", true),
                showWorkingHours = applicationConfig.bool("app.maps.showWorkingHours", true),
                yandexAndroidKey = applicationConfig.string("app.maps.yandexAndroidKey", "").ifBlank { null },
                yandexIosKey = applicationConfig.string("app.maps.yandexIosKey", "").ifBlank { null },
                defaultLat = applicationConfig.double("app.maps.defaultLat", 42.8746),
                defaultLon = applicationConfig.double("app.maps.defaultLon", 74.5698),
                yandexWebKey = applicationConfig.string("app.maps.yandexWebKey", "").ifBlank { null },
            )

            call.respond(PublicConfigResponse(features = features, map = mapSettings))
        }

        get("/points/search") {
            val featureEnabled = applicationConfig.bool("features.mapEnabled", false)
            if (!featureEnabled) {
                call.respond(HttpStatusCode.NotFound, ApiMessage(AppErrorCode.NOT_FOUND, "Map features disabled"))
                return@get
            }

            val queryParameters = call.request.queryParameters
            val lat = queryParameters["lat"]?.toDoubleOrNull()
            val lon = queryParameters["lon"]?.toDoubleOrNull()

            if (lat == null || lon == null) {
                call.respond(HttpStatusCode.BadRequest, ApiMessage(AppErrorCode.INVALID_REQUEST, "lat/lon are required"))
                return@get
            }

            val requestedRadius = queryParameters["radius"]?.toIntOrNull()
            val radius = (requestedRadius ?: mapDefaultRadius).coerceIn(mapMinRadius, mapMaxRadius)

            val requestedLimit = queryParameters["limit"]?.toIntOrNull()
            val limit = (requestedLimit ?: 50).coerceIn(1, 100)

            val query = queryParameters["query"]?.trim()?.takeIf { it.isNotEmpty() }
            val openNow = queryParameters["openNow"]?.toBoolean() ?: false
            val includeInactive = queryParameters["includeInactive"]?.toBoolean() ?: false
            val minRating = queryParameters["minRating"]?.toDoubleOrNull()?.coerceIn(0.0, 5.0)

            val typeParams = queryParameters.getAll("type")
                ?.flatMap { it.split(",", ";") }
                ?.mapNotNull { raw ->
                    val normalized = raw.trim()
                    if (normalized.isEmpty()) return@mapNotNull null
                    runCatching { TradingPointType.valueOf(normalized.uppercase()) }.getOrNull()
                }
                ?.toSet()
                ?: emptySet()

            val criteria = TradingPointSearchCriteria(
                latitude = lat,
                longitude = lon,
                radiusMeters = radius,
                limit = limit,
                query = query,
                types = typeParams,
                openNowOnly = openNow,
                minRating = minRating,
                includeInactive = includeInactive
            )

            val response = partnerRepository.searchPublicPoints(criteria)
            call.respond(response)
        }
    }
}

