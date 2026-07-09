package io.loyaltyloop.server.routes

import io.ktor.server.application.call
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.loyaltyloop.server.utils.bool
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.server.utils.lang
import io.loyaltyloop.server.utils.string
import io.loyaltyloop.shared.models.AppVersionResponse

fun Route.appRoutes(config: ApplicationConfig) {

    fun getWhatsNew(platform: String, lang: String): List<String> {
        val key = "app.version.$platform.whatsNew.$lang"
        val fallbackKey = "app.version.$platform.whatsNew.default"

        val raw = config.propertyOrNull(key)?.getString()
            ?: config.propertyOrNull(fallbackKey)?.getString()

        if (!raw.isNullOrBlank()) {
            return raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }

        // Дефолты, если в конфиге нет значений
        return listOf(
            "• Added Russia support (+7, ₽)",
            "• Stability improvements"
        )
    }

    route("/app") {
        get("/version") {
            val platform = call.request.queryParameters["platform"] ?: "android"
            val lang = call.lang()
            val latest = when (platform.lowercase()) {
                "android" -> AppVersionResponse(
                    platform = "android",
                    latestVersionCode = config.int("app.version.android.code", 1),
                    storeUrl = config.string("app.version.android.url", "https://play.google.com/store/apps/details?id=io.loyaltyloop.app"),
                    force = config.bool("app.version.android.force", false),
                    whatsNew = getWhatsNew("android", lang)
                )
                "ios" -> AppVersionResponse(
                    platform = "ios",
                    latestVersionCode = config.int("app.version.ios.code", 1),
                    storeUrl = config.string("app.version.ios.url", "https://apps.apple.com/"),
                    force = config.bool("app.version.ios.force", false),
                    whatsNew = getWhatsNew("ios", lang)
                )
                else -> AppVersionResponse(
                    platform = platform,
                    latestVersionCode = 1,
                    storeUrl = "",
                    force = false,
                    whatsNew = emptyList()
                )
            }
            call.respond(latest)
        }
    }
}


