package io.loyaltyloop.server.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.WaitlistRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import kotlinx.serialization.Serializable

// TODO Checked
@Serializable
data class WaitlistRequest(val email: String)

fun Route.publicRoutes(waitlistRepository: WaitlistRepository) {
    route("/public") {
        post("/waitlist") {
            val request = call.receive<WaitlistRequest>()
            if (request.email.isBlank() || !request.email.contains("@")) {
                call.respond(HttpStatusCode.BadRequest, ApiMessage(AppErrorCode.INVALID_REQUEST, "Invalid email"))
                return@post
            }

            if (waitlistRepository.hasMail(request.email)) {
                 call.respond(HttpStatusCode.Conflict, ApiMessage(AppErrorCode.ALREADY_JOINED, "You are already in the waitlist"))
                 return@post
            }

            waitlistRepository.add(request.email)
            
            call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Added to waitlist"))
        }

        get("/open-app") {
            val lang = call.request.queryParameters["lang"] ?: "en"
            val uuid = call.request.queryParameters["uuid"]
            
            val titleMap = mapOf(
                "en" to "Opening App...",
                "ru" to "Открываем приложение...",
                "kk" to "Қосымша ашылуда...",
                "ky" to "Тиркеме ачылууда...",
                "uz" to "Ilova ochilmoqda...",
                "be" to "Адкрываем дадатак..."
            )
            val btnMap = mapOf(
                "en" to "Open App",
                "ru" to "Открыть",
                "kk" to "Ашу",
                "ky" to "Ачуу",
                "uz" to "Ochish",
                "be" to "Адкрыць"
            )

            val title = titleMap[lang] ?: titleMap["en"]!!
            val btn = btnMap[lang] ?: btnMap["en"]!!
            
            val appLink = if (uuid != null) "loyaltyloop://auth?uuid=$uuid" else "loyaltyloop://auth"

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>$title</title>
                    <meta http-equiv="refresh" content="0;url=$appLink">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0f2f5; }
                        .card { background: white; padding: 2rem; border-radius: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); text-align: center; }
                        .btn { display: inline-block; padding: 12px 24px; background-color: #0088cc; color: white; text-decoration: none; border-radius: 12px; font-weight: 600; margin-top: 16px; transition: background 0.2s; }
                        .btn:hover { background-color: #0077b5; }
                        p { margin: 0 0 16px; font-size: 18px; color: #333; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <p>$title</p>
                        <a href="$appLink" class="btn">$btn</a>
                        <script>
                            setTimeout(function() { window.location.href = '$appLink'; }, 100);
                        </script>
                    </div>
                </body>
                </html>
            """.trimIndent()
            call.respondText(html, ContentType.Text.Html)
        }
    }
}

