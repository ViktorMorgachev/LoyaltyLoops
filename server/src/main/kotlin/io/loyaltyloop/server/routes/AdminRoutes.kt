package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.ChangePartnerStatusRequest
import io.loyaltyloop.shared.models.UserRole

fun Route.adminRoutes(
    userRepo: UserRepository,
    partnerRepo: PartnerRepository
) {
    route("/admin") {
        authenticate("auth-jwt") {
            
            // СМЕНА СТАТУСА ПАРТНЕРА
            // POST /admin/partners/{id}/status
            post("/partners/{id}/status") {
                val userId = call.getUserIdOrRespond(userRepo) ?: return@post

                requireAdminRole(userRepo, userId, requireWrite = true)

                val partnerId = call.parameters["id"] ?: return@post
                val request = call.receive<ChangePartnerStatusRequest>()
                
                partnerRepo.updateStatus(partnerId, request.status)
                
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Status changed to ${request.status}"))
            }

            // СТАТИСТИКА ПАРТНЕРА
            get("/partners/{id}/stats") {
                val userId = call.getUserIdOrRespond(userRepo) ?: return@get
                requireAdminRole(userRepo, userId, requireWrite = false)

                val partnerId = call.parameters["id"] ?: return@get
                val stats = partnerRepo.getPartnerStats(partnerId)
                call.respond(stats)
            }

            // ТОЧКИ ПАРТНЕРА
            get("/partners/{id}/points") {
                val userId = call.getUserIdOrRespond(userRepo) ?: return@get
                requireAdminRole(userRepo, userId, requireWrite = false)

                val partnerId = call.parameters["id"] ?: return@get
                val points = partnerRepo.getPointsByPartnerId(partnerId)
                call.respond(points)
            }

            // БЛОКИРОВКА ТОЧКИ
            put("/points/{id}/status") {
                val userId = call.getUserIdOrRespond(userRepo) ?: return@put
                requireAdminRole(userRepo, userId, requireWrite = true)

                val pointId = call.parameters["id"] ?: return@put
                val request = call.receive<io.loyaltyloop.shared.models.ChangePointStatusRequest>()
                partnerRepo.updatePointStatus(pointId, request.isActive)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Point status updated"))
            }

            // СПИСОК ВСЕХ ПАРТНЕРОВ
            get("/partners") {
                val userId = call.getUserIdOrRespond(userRepo) ?: return@get
                requireAdminRole(userRepo, userId, requireWrite = false)

                val partners = partnerRepo.getAllPartners()
                call.respond(partners)
            }
        }
    }
}

// Helper for RBAC checks
private suspend fun requireAdminRole(userRepo: UserRepository, userId: String, requireWrite: Boolean) {
    val workspaces = userRepo.getUserWorkspaces(userId)

    val isSuperAdmin = workspaces.any { it.role == UserRole.PLATFORM_SUPER_ADMIN }
    val isManager = workspaces.any { it.role == UserRole.PLATFORM_MANAGER }

    if (!isSuperAdmin && !isManager) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied: Admins only")
    }

    if (requireWrite && !isSuperAdmin) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied: Read-only for Managers")
    }
}
