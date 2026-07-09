package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.loyaltyloop.server.models.TradingPointSearchCriteria
import io.loyaltyloop.server.repository.MapRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.utils.getTimezone
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.getWorkspaceIdOrThrow
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MapsRoutes")

// TODO checked
fun Route.mapsRoutes(
    applicationConfig: ApplicationConfig,
    userRepository: UserRepository,
    tradingPointRepository: TradingPointRepository,
    mapRepository: MapRepository,
    accessControlService: AccessControlService,
) {
    val mapMinRadius = applicationConfig.int("app.maps.minRadiusMeters", 50)
    val mapDefaultRadius = applicationConfig.int("app.maps.defaultRadiusMeters", 2000)
    val mapMaxRadius = applicationConfig.int("app.maps.maxRadiusMeters", 15000)

    authenticate("auth-jwt") {
        route("/map") {

            get("/partners/points") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                try {
                    val points = tradingPointRepository.getPointsByPartnerId(workspaceId)
                    call.respond(points)
                } catch (e: Exception) {
                    logger.warn("Failed to load partner points for workspace", e)
                    call.respond(emptyList<TradingPointDto>())
                }
            }

            get("/points/search") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                val user = userRepository.getUserById(userId)
                val timezone = call.getTimezone()

                if (user == null){
                    call.respond(HttpStatusCode.Unauthorized, "User not found")
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

                val response = mapRepository.searchPublicPoints(criteria, offset = 0, timezone)
                call.respond(response)
            }
        }
    }

}
