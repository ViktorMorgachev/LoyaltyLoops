package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.TradingPointSearchCriteria
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType

fun Route.mapsRoutes(
    applicationConfig: ApplicationConfig,
    partnerRepository: PartnerRepository,
    userRepository: UserRepository
) {
    val mapMinRadius = applicationConfig.int("app.maps.minRadiusMeters", 50)
    val mapDefaultRadius = applicationConfig.int("app.maps.defaultRadiusMeters", 2000)
    val mapMaxRadius = applicationConfig.int("app.maps.maxRadiusMeters", 15000)


    authenticate("auth-jwt") {
        route("/map") {
            // Новый эндпоинт для получения точек текущего партнера
            get("/partners/points") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get

                try {
                    // Пытаемся найти партнера по userId
                    val partner = partnerRepository.getPartnerByUserId(userId)
                    // Если нашли - возвращаем его точки
                    val points = partnerRepository.getPointsByPartnerId(partner.id)
                    call.respond(points)
                } catch (e: Exception) {
                    // Если пользователь не партнер (или ошибка) - возвращаем пустой список
                    // Чтобы на фронте кнопка "Мои филиалы" просто не работала, но не крашила интерфейс
                    call.respond(emptyList<TradingPointDto>())
                }
            }

            get("/points/search") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val user = userRepository.getUserById(userId)

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

                val response = partnerRepository.searchPublicPoints(criteria)
                call.respond(response)
            }
        }
    }

}
