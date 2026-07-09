package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.loyaltyloop.server.models.SystemEventFilter
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.PlatformRepository
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.service.AnalyticsService
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.ChangePartnerStatusRequest
import io.loyaltyloop.shared.models.ChangePointStatusRequest
import io.loyaltyloop.shared.models.SendSupportMessageRequest
import io.loyaltyloop.shared.models.UserRole

// TODO checked
fun Route.adminRoutes(
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
                accessControlService.requireSystemRole(userId)

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

            get("/points/{pointId}") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId)

                val pointId = call.parameters["pointId"] ?: return@get
                val partnerId = tradingPointRepository.getPartnerIdByPointId(pointId)
                val details = tradingPointRepository.getPointDetails(pointId, partnerId)
                call.respond(details)
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
