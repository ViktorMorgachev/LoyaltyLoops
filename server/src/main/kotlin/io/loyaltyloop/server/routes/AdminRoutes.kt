package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.ChangePartnerStatusRequest
import io.loyaltyloop.shared.models.UserRole

fun Route.adminRoutes(
    userRepo: UserRepository,
    partnerRepo: PartnerRepository
) {
    route("/admin") {
        authenticate("auth-jwt") {
            
            // INTERCEPTOR: Проверка прав СУПЕР-АДМИНА
            intercept(ApplicationCallPipeline.Call) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@intercept finish()
                
                // Проверяем через таблицу SystemStaff
                val workspaces = userRepo.getUserWorkspaces(userId)
                val isSuperAdmin = workspaces.any { it.role == UserRole.PLATFORM_SUPER_ADMIN }
                
                if (!isSuperAdmin) {
                    call.respond(HttpStatusCode.Forbidden, ApiMessage("Access denied: Super Admin only"))
                    return@intercept finish()
                }
            }

            // СМЕНА СТАТУСА
            // POST /admin/partners/{id}/status
            post("/partners/{id}/status") {
                val partnerId = call.parameters["id"] ?: return@post
                val request = call.receive<ChangePartnerStatusRequest>()
                
                partnerRepo.updateStatus(partnerId, request.status)
                
                call.respond(HttpStatusCode.OK, ApiMessage("Status changed to ${request.status}"))
            }

            get("/partners") {
                val partners = partnerRepo.getAllPartners()
                call.respond(partners)
            }
        }
    }
}