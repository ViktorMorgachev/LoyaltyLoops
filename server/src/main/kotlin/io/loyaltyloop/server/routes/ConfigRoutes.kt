package io.loyaltyloop.server.routes

import io.ktor.server.application.call
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.double
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.server.utils.long
import io.loyaltyloop.shared.models.ClientRatingTag
import io.loyaltyloop.shared.models.CountryCode
import io.loyaltyloop.shared.models.FeatureToggleDto
import io.loyaltyloop.shared.models.GeoLocation
import io.loyaltyloop.shared.models.MapSettingsDto
import io.loyaltyloop.shared.models.PublicConfigResponse
import io.loyaltyloop.shared.models.RatingTagDto
import io.loyaltyloop.shared.models.RatingTagsDto
import io.loyaltyloop.shared.models.ServiceReviewTag

//TODO  Checked
private const val MAP_CLUSTER_RADIUS_M = 80
private const val MAP_SEARCH_DEBOUNCE_MS = 350L
private const val KG_DEFAULT_LAT = 42.8746
private const val KG_DEFAULT_LNG = 74.5698

fun Route.configRoutes(
    applicationConfig: ApplicationConfig,
) {
    val mapMinRadius = applicationConfig.int("app.maps.minRadiusMeters", MapDefaults.MIN_RADIUS_M)
    val mapDefaultRadius = applicationConfig.int("app.maps.defaultRadiusMeters", MapDefaults.DEFAULT_RADIUS_M)
    val mapMaxRadius = applicationConfig.int("app.maps.maxRadiusMeters", MapDefaults.MAX_RADIUS_M)

    get("/config") {

        val features = FeatureToggleDto(
            pushEnabled = applicationConfig.bool("features.pushEnabled", true),
            enableTestSupport = applicationConfig.bool("features.enableTestSupport", false)
        )

        val mapSettings = MapSettingsDto(
            minRadiusMeters = mapMinRadius,
            defaultRadiusMeters = mapDefaultRadius,
            maxRadiusMeters = mapMaxRadius,
            clusterRadiusMeters = applicationConfig.int("app.maps.clusterRadiusMeters", MAP_CLUSTER_RADIUS_M),
            searchDebounceMs = applicationConfig.long("app.maps.searchDebounceMs", MAP_SEARCH_DEBOUNCE_MS),
            showRatings = applicationConfig.bool("app.maps.showRatings", true),
            showWorkingHours = applicationConfig.bool("app.maps.showWorkingHours", true),
            basePoints = mapOf(CountryCode.KG to GeoLocation(KG_DEFAULT_LAT, KG_DEFAULT_LNG)),
        )

        val ratingTags = RatingTagsDto(
            client = ClientRatingTag.entries.filter { it != ClientRatingTag.NONE }.map { tag ->
                RatingTagDto(
                    code = tag.name,
                    weight = applicationConfig.double("rating.tags.client.${tag.name}", tag.penalty)
                )
            },
            service = ServiceReviewTag.entries.map { tag ->
                RatingTagDto(
                    code = tag.name,
                    weight = applicationConfig.double("rating.tags.service.${tag.name}", tag.penalty)
                )
            }
        )

        call.respond(PublicConfigResponse(features = features, map = mapSettings, ratingTags = ratingTags))
    }
}
