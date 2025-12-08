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
import io.loyaltyloop.server.repository.PlatformRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePlatformRequest
import io.loyaltyloop.shared.models.JoinPlatformAdminRequest
import io.loyaltyloop.shared.models.PlatformRequestStatus
import io.loyaltyloop.shared.models.RejectRequestDto
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.server.service.email.EmailService // Added

fun Route.adminPlatformRoutes(
    platformRepository: PlatformRepository,
    userRepository: UserRepository,
    emailService: EmailService // Injected
) {
    authenticate("auth-jwt") {
        route("/platform") {
            
            // --- JOIN ---
            post("/join") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<JoinPlatformAdminRequest>()
                
                val role = platformRepository.validateInvite(request.inviteCode) 
                    ?: throw LoyaltyException(AppErrorCode.INVALID_INVITE_CODE)
                
                // Check if already joined?
                val existingRole = platformRepository.getSystemRole(userId)
                if (existingRole != null) {
                    throw LoyaltyException(AppErrorCode.ALREADY_JOINED)
                }

                platformRepository.addSystemStaff(userId, role)
                call.respond(HttpStatusCode.OK)
            }

            get("/alerts") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val role = platformRepository.getSystemRole(userId)
                
                if (role !in listOf(UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER, UserRole.PLATFORM_MANAGER)) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN)
                }
                
                val alerts = platformRepository.getExpiringSubscriptions()
                call.respond(alerts)
            }

            post("/requests") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val role = platformRepository.getSystemRole(userId)
                
                if (role !in listOf(UserRole.PLATFORM_MANAGER, UserRole.PLATFORM_SUPER_MANAGER, UserRole.PLATFORM_SUPER_ADMIN)) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied")
                }

                val request = call.receive<CreatePlatformRequest>()
                val id = platformRepository.createRequest(userId, request)
                call.respond(mapOf("id" to id))
            }

            get("/requests") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val role = platformRepository.getSystemRole(userId)
                
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
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val role = platformRepository.getSystemRole(userId)
                
                if (role !in listOf(UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN)
                }

                val requestId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST)
                platformRepository.approveRequest(requestId, userId, emailService)
                call.respond(HttpStatusCode.OK)
            }

            post("/requests/{id}/reject") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val role = platformRepository.getSystemRole(userId)
                
                if (role !in listOf(UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN)
                }

                val requestId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST)
                val body = call.receive<RejectRequestDto>()
                platformRepository.rejectRequest(requestId, userId, body.reason)
                call.respond(HttpStatusCode.OK)
            }

            // --- STAFF & INVITES ---

            post("/invite") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val role = platformRepository.getSystemRole(userId)
                val targetRoleStr = call.request.queryParameters["role"] ?: throw LoyaltyException(AppErrorCode.FORBIDDEN, "You cannot invite this role target role not found")
                val targetRole = UserRole.valueOf(targetRoleStr)

                // Permission check
                val canInvite = when (role) {
                    UserRole.PLATFORM_SUPER_ADMIN -> targetRole == UserRole.PLATFORM_SUPER_MANAGER || targetRole == UserRole.PLATFORM_MANAGER
                    UserRole.PLATFORM_SUPER_MANAGER -> targetRole == UserRole.PLATFORM_MANAGER
                    else -> false
                }

                if (!canInvite) throw LoyaltyException(AppErrorCode.FORBIDDEN, "You cannot invite this role")

                val code = platformRepository.generateInvite(targetRole, userId)
                call.respond(mapOf("code" to code))
            }

            get("/staff") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val role = platformRepository.getSystemRole(userId)

                if (role !in listOf(UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN)
                }

                val roleFilter = call.request.queryParameters["role"]?.let { UserRole.valueOf(it) }
                val staff = platformRepository.getSystemStaff(roleFilter)
                call.respond(staff)
            }

            delete("/staff/{id}") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@delete
                val role = platformRepository.getSystemRole(userId)
                val staffId = call.parameters["id"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST)

                if (role !in listOf(UserRole.PLATFORM_SUPER_ADMIN, UserRole.PLATFORM_SUPER_MANAGER)) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN)
                }
                
                // Prevent deleting higher or equal roles
                val targetStaffRole = platformRepository.getSystemRoleByStaffId(staffId)
                if (targetStaffRole != null) {
                     if (targetStaffRole == UserRole.PLATFORM_SUPER_ADMIN) {
                         throw LoyaltyException(AppErrorCode.FORBIDDEN, "Cannot delete Super Admin")
                     }
                     if (role == UserRole.PLATFORM_SUPER_MANAGER && targetStaffRole == UserRole.PLATFORM_SUPER_MANAGER) {
                         throw LoyaltyException(AppErrorCode.FORBIDDEN, "Cannot delete another Super Manager")
                     }
                }
                
                platformRepository.removeSystemStaff(staffId)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
