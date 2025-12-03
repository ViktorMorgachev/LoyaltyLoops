package io.loyaltyloop.server.routes

import io.ktor.server.application.call
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.server.utils.long
import io.loyaltyloop.shared.models.CountryCode
import io.loyaltyloop.shared.models.FeatureToggleDto
import io.loyaltyloop.shared.models.GeoLocation
import io.loyaltyloop.shared.models.MapSettingsDto
import io.loyaltyloop.shared.models.PublicConfigResponse

fun Route.configRoutes(
    applicationConfig: ApplicationConfig,
) {
    val mapMinRadius = applicationConfig.int("app.maps.minRadiusMeters", 50)
    val mapDefaultRadius = applicationConfig.int("app.maps.defaultRadiusMeters", 2000)
    val mapMaxRadius = applicationConfig.int("app.maps.maxRadiusMeters", 15000)


    // Этот роут должен быть ПУБЛИЧНЫМ, так как он вызывается при старте приложения до логина
    get("/config") {
        
        val features = FeatureToggleDto(
            pushEnabled = applicationConfig.bool("features.pushEnabled", true),
            testLabEnabled = applicationConfig.bool("features.testLabEnabled", false)
        )

        val mapSettings = MapSettingsDto(
            minRadiusMeters = mapMinRadius,
            defaultRadiusMeters = mapDefaultRadius,
            maxRadiusMeters = mapMaxRadius,
            clusterRadiusMeters = applicationConfig.int("app.maps.clusterRadiusMeters", 80),
            searchDebounceMs = applicationConfig.long("app.maps.searchDebounceMs", 350),
            showRatings = applicationConfig.bool("app.maps.showRatings", true),
            showWorkingHours = applicationConfig.bool("app.maps.showWorkingHours", true),
            basePoints = mapOf(CountryCode.KG to GeoLocation(42.8746, 74.5698)),
        )

        call.respond(PublicConfigResponse(features = features, map = mapSettings))
    }
}
