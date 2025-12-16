package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.ApplicationConfig
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
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.shared.models.SendSupportMessageRequest

import io.loyaltyloop.server.models.SystemEventFilter
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.SystemEventRepository

fun Route.adminRoutes(
    applicationConfig: ApplicationConfig,
    userRepo: UserRepository,
    partnerRepo: PartnerRepository,
    supportChatService: SupportChatService,
    systemEventRepository: SystemEventRepository
) {
    authenticate("auth-jwt") {
        route("/admin") {

            get("/events") {
                val userId = call.getUserIdOrRespond(userRepo) ?: return@get
                
                // RESTRICT ACCESS: Only Super Admin and Super Manager can view logs
                val workspaces = userRepo.getUserWorkspaces(userId)
                val canViewLogs = workspaces.any { 
                    it.role == UserRole.PLATFORM_SUPER_ADMIN || it.role == UserRole.PLATFORM_SUPER_MANAGER 
                }
                
                if (!canViewLogs) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied: Logs are restricted")
                }

                val filter = SystemEventFilter(
                    type = call.request.queryParameters["type"]?.let { SystemEventType.valueOf(it) },
                    userId = call.request.queryParameters["userId"],
                    userPhone = call.request.queryParameters["userPhone"],
                    partnerId = call.request.queryParameters["partnerId"],
                    from = call.request.queryParameters["from"]?.toLongOrNull(),
                    to = call.request.queryParameters["to"]?.toLongOrNull(),
                    limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100,
                    offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
                )

                val events = systemEventRepository.getEvents(filter)
                call.respond(events)
            }

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

            // ДЕТАЛИ ПАРТНЕРА
            get("/partners/{id}") {
                val userId = call.getUserIdOrRespond(userRepo) ?: return@get
                requireAdminRole(userRepo, userId, requireWrite = false)

                val partnerId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Partner ID required")
                val partner = partnerRepo.getPartnerByIdQ(partnerId)
                call.respond(partner)
            }

            delete("/users/{id}") {
                val requesterId = call.getUserIdOrRespond(userRepo) ?: return@delete
                requireAdminRole(userRepo, requesterId, requireWrite = true)

                val targetId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "User ID is required")

                if (targetId == requesterId) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "You cannot delete yourself")
                }

                val deleted = userRepo.deleteUser(targetId)
                if (!deleted) {
                    throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "User not found")
                }

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "User deleted"))
            }

            route("/support") {
                get("/threads") {
                    val userId = call.getUserIdOrRespond(userRepo) ?: return@get
                    requireAdminRole(userRepo, userId, requireWrite = false)

                    val threads = supportChatService.listThreads()
                    call.respond(threads)
                }

                get("/threads/{threadId}") {
                    val userId = call.getUserIdOrRespond(userRepo) ?: return@get
                    requireAdminRole(userRepo, userId, requireWrite = false)

                    val threadId = call.parameters["threadId"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Thread ID required")
                    val response = supportChatService.getAdminThread(threadId)
                        ?: throw LoyaltyException(AppErrorCode.NOT_FOUND, "Thread not found")
                    call.respond(response)
                }

                post("/threads/{threadId}/messages") {
                    val userId = call.getUserIdOrRespond(userRepo) ?: return@post
                    requireAdminRole(userRepo, userId, requireWrite = false)

                    val threadId = call.parameters["threadId"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Thread ID required")
                    val payload = call.receive<SendSupportMessageRequest>()
                    val text = payload.content.trim()
                    if (text.isEmpty()) {
                        throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Message cannot be empty")
                    }

                    val role = resolveAdminSenderRole(userRepo, userId)
                    supportChatService.sendAdminMessage(threadId, userId, role, text)
                    call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Message sent"))
                }
            }
        }
    }
}

// Helper for RBAC checks
private suspend fun requireAdminRole(userRepo: UserRepository, userId: String, requireWrite: Boolean) {
    val workspaces = userRepo.getUserWorkspaces(userId)

    val isSuperAdmin = workspaces.any { it.role == UserRole.PLATFORM_SUPER_ADMIN }
    val isSuperManager = workspaces.any { it.role == UserRole.PLATFORM_SUPER_MANAGER }
    val isManager = workspaces.any { it.role == UserRole.PLATFORM_MANAGER }

    if (!isSuperAdmin && !isManager && !isSuperManager) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied: Admins only")
    }

    // Write access (direct manipulation) is restricted for Managers
    // They must use Requests flow.
    if (requireWrite && !isSuperAdmin && !isSuperManager) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied: Read-only for Managers")
    }
}

private suspend fun resolveAdminSenderRole(userRepo: UserRepository, userId: String): UserRole {
    val workspaces = userRepo.getUserWorkspaces(userId)
    return when {
        workspaces.any { it.role == UserRole.PLATFORM_SUPER_ADMIN } -> UserRole.PLATFORM_SUPER_ADMIN
        workspaces.any { it.role == UserRole.PLATFORM_SUPER_MANAGER } -> UserRole.PLATFORM_SUPER_MANAGER
        workspaces.any { it.role == UserRole.PLATFORM_MANAGER } -> UserRole.PLATFORM_MANAGER
        else -> UserRole.CLIENT
    }
}
