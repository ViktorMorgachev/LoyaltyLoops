package io.loyaltyloop.server.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.config.ApplicationConfig
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.server.utils.string
import io.loyaltyloop.shared.models.AppVersionResponse

fun Route.appRoutes(config: ApplicationConfig) {
    route("/app") {
        get("/version") {
            val platform = call.request.queryParameters["platform"] ?: "android"
            val latest = when (platform.lowercase()) {
                "android" -> AppVersionResponse(
                    platform = "android",
                    latestVersionCode = config.int("app.version.android.code", 1),
                    storeUrl = config.string("app.version.android.url", "https://play.google.com/store/apps/details?id=io.loyaltyloop.app"),
                    force = config.bool("app.version.android.force", false)
                )
                "ios" -> AppVersionResponse(
                    platform = "ios",
                    latestVersionCode = config.int("app.version.ios.code", 1),
                    storeUrl = config.string("app.version.ios.url", "https://apps.apple.com/"),
                    force = config.bool("app.version.ios.force", false)
                )
                else -> AppVersionResponse(
                    platform = platform,
                    latestVersionCode = 1,
                    storeUrl = "",
                    force = false
                )
            }
            call.respond(latest)
        }
    }
}


