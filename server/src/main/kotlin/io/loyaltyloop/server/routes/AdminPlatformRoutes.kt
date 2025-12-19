package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.repository.PlatformRepository
import io.loyaltyloop.server.repository.SubscriptionRepository
import io.loyaltyloop.server.repository.SystemStaffRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePlatformRequest
import io.loyaltyloop.shared.models.JoinPlatformAdminRequest
import io.loyaltyloop.shared.models.PlatformRequestStatus
import io.loyaltyloop.shared.models.RejectRequestDto
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.server.service.email.EmailService // Added
import org.jetbrains.exposed.sql.select


// TODO checked
fun Route.adminPlatformRoutes(
    platformRepository: PlatformRepository,
    systemStaffRepository: SystemStaffRepository,
    accessControlService: AccessControlService,
    subscriptionRepository: SubscriptionRepository
) {
    authenticate("auth-jwt") {

        route("/platform") {

            post("/join") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<JoinPlatformAdminRequest>()
                
                val role = systemStaffRepository.validateInvite(request.inviteCode)
                systemStaffRepository.acceptInvite(code = request.inviteCode, userId, role)
                call.respond(HttpStatusCode.OK)
            }

            get("/alerts") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId)

                val alerts = subscriptionRepository.getExpiringSubscriptions()
                call.respond(alerts)
            }

            post("/requests") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                accessControlService.requireSystemRole(userId)

                val request = call.receive<CreatePlatformRequest>()
                val id = platformRepository.createRequest(userId, request)
                call.respond(mapOf("id" to id))
            }

            get("/requests") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId)
                val role = systemStaffRepository.getSystemRole(userId)
                
                val status = call.request.queryParameters["status"]?.let { PlatformRequestStatus.valueOf(it) }
                val filterRequesterId = call.request.queryParameters["requesterId"]
                val targetPartnerId = call.request.queryParameters["targetPartnerId"]

                val requests = when (role) {
                    UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER -> {
                        // Can view all or filter
                        platformRepository.getRequests(status, filterRequesterId, targetPartnerId)
                    }
                    UserRole.PLATFORM_MANAGER -> {
                        // Can view only own
                        platformRepository.getRequests(status, userId, targetPartnerId)
                    }
                    else -> throw LoyaltyException(AppErrorCode.FORBIDDEN)
                }
                call.respond(requests)
            }

            post("/requests/{id}/approve") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

                val requestId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST)
                platformRepository.approveRequest(requestId, userId)
                call.respond(HttpStatusCode.OK)
            }

            post("/requests/{id}/reject") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

                val requestId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST)
                val body = call.receive<RejectRequestDto>()
                platformRepository.rejectRequest(requestId, userId, body.reason)
                call.respond(HttpStatusCode.OK)
            }

            // --- STAFF & INVITES ---

            post("/invite") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post

                val targetRoleStr = call.request.queryParameters["role"] ?: throw LoyaltyException(AppErrorCode.FORBIDDEN, "You cannot invite this role target role not found")
                val targetRole = UserRole.valueOf(targetRoleStr)

                val code = systemStaffRepository.generateInvite(targetRole, userId)
                call.respond(mapOf("code" to code))
            }

            get("/staff") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)

                val roleFilter = call.request.queryParameters["role"]?.let { UserRole.valueOf(it) }
                val staff = systemStaffRepository.getSystemStaff(roleFilter)
                call.respond(staff)
            }


            delete("/staff/{id}") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@delete
                accessControlService.requireSystemRole(userId, UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)
                val role = systemStaffRepository.getSystemRole(userId)
                val staffId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST)

                systemStaffRepository.removeSystemStaff(staffId,role)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
