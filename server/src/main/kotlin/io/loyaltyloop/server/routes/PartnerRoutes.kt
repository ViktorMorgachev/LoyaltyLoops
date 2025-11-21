package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.CreatePartnerRequest

fun Route.partnerRoutes(repository: PartnerRepository) {
    route("/partners") {
        authenticate("auth-jwt") {
            
            post("/create") {
                // 1. Безопасность: Достаем ID из токена
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@post
                
                val request = call.receive<CreatePartnerRequest>()
                
                // Валидация
                if (request.businessName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiMessage("Название не может быть пустым"))
                    return@post
                }

                // 2. Создаем
                val partnerId = repository.createPartner(userId, request)
                
                call.respond(HttpStatusCode.Created, ApiMessage("Бизнес создан: $partnerId"))
            }
        }
    }
}