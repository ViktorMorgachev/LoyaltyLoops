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
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.shared.models.SendSupportMessageRequest
import io.loyaltyloop.server.models.SystemEventFilter
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.service.AnalyticsService
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.shared.models.ChangePointStatusRequest
import io.loyaltyloop.server.repository.PlatformRepository

// TODO checked
fun Route.adminRoutes(
    userRepository: UserRepository,
    partnerRepository: PartnerRepository,
    supportChatService: SupportChatService,
    systemEventRepository: SystemEventRepository,
    accessControlService: AccessControlService,
    tradingPointRepository: TradingPointRepository,
    analyticsService: AnalyticsService,
    platformRepository: PlatformRepository
) {
    authenticate("auth-jwt") {
        route("/admin") {

            get("/events") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

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

            post("/partners/{id}/status") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

                val partnerId = call.parameters["id"] ?: return@post
                val request = call.receive<ChangePartnerStatusRequest>()
                
                platformRepository.updatePartnerStatus(partnerId, request.status)
                
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Status changed to ${request.status}"))
            }


            get("/partners/{id}/stats") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId)

                val partnerId = call.parameters["id"] ?: return@get
                val stats = analyticsService.getPartnerStats(partnerId)
                call.respond(stats)
            }


            get("/partners/{id}/points") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId)

                val partnerId = call.parameters["id"] ?: return@get
                val points = tradingPointRepository.getPointsByPartnerId(partnerId)
                call.respond(points)
            }


            put("/points/{id}/status") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@put
                accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN)

                val pointId = call.parameters["id"] ?: return@put
                val request = call.receive<ChangePointStatusRequest>()
                tradingPointRepository.updatePointStatus(pointId, request.isActive)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Point status updated"))
            }


            get("/partners") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId)
                val partners = partnerRepository.getAllPartners()
                call.respond(partners)
            }


            get("/partners/{id}") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId)

                val partnerId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Partner ID required")
                val partner = partnerRepository.getPartnerByIdOrThrow(partnerId)
                call.respond(partner)
            }


            route("/support") {
                get("/threads") {
                    val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                    accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

                    val threads = supportChatService.listThreads()
                    call.respond(threads)
                }

                get("/threads/{threadId}") {
                    val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                    accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

                    val threadId = call.parameters["threadId"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Thread ID required")
                    val response = supportChatService.getAdminThread(threadId)
                    call.respond(response)
                }

                post("/threads/{threadId}/messages") {
                    val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                    accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

                    val threadId = call.parameters["threadId"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Thread ID required")
                    val payload = call.receive<SendSupportMessageRequest>()
                    val text = payload.content.trim()
                    if (text.isEmpty()) {
                        throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Message cannot be empty")
                    }
                    supportChatService.sendAdminMessage(threadId, userId, text)
                    call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Message sent"))
                }
            }
        }
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
