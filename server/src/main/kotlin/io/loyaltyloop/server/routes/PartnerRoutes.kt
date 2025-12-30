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
import io.ktor.server.routing.delete
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.PartnerStaffRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.repository.PinResetTokenRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.JoinTradingPointRequest
import io.loyaltyloop.shared.models.UpdatePartnerRequest
import io.loyaltyloop.shared.models.TradingPointDetailsDto
import io.loyaltyloop.shared.models.UpdateTradingPointRequest
import io.loyaltyloop.shared.models.UpdatePinRequest
import io.loyaltyloop.shared.models.ResetPinRequest
import io.loyaltyloop.shared.models.PinResetConfirmRequest
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.server.service.email.EmailService
import io.loyaltyloop.server.service.AnalyticsService
import io.loyaltyloop.shared.models.SendSupportMessageRequest
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.DeviceTokenRepository
import io.loyaltyloop.server.repository.RatingRepository
import io.loyaltyloop.server.repository.SubscriptionRepository
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.server.service.email.EmailTemplate
import io.loyaltyloop.server.utils.getTimezone
import io.loyaltyloop.server.utils.getWorkspaceIdOrThrow
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.server.utils.validatePhoneNumber
import io.loyaltyloop.shared.models.AnalyticsPeriod
import io.loyaltyloop.shared.models.DevicePlatform
import io.loyaltyloop.shared.models.VerifyPinRequest

private const val PIN_FREEZE_HOURS = 24L
private const val PIN_RESET_TTL_HOURS = 12L

// TODO Checked
fun Route.partnerRoutes(
    userRepository: UserRepository,
    partnerRepository: PartnerRepository,
    partnerStaffRepository: PartnerStaffRepository,
    tradingPointRepository: TradingPointRepository,
    transactionService: TransactionService,
    pinResetTokenRepository: PinResetTokenRepository,
    emailService: EmailService,
    webBaseUrl: String,
    supportChatService: SupportChatService,
    eventLogger: EventLogger,
    deviceTokenRepository: DeviceTokenRepository,
    ratingRepository: RatingRepository,
    accessControlService: AccessControlService,
    analyticsService: AnalyticsService,
    subscriptionRepository: SubscriptionRepository
) {
    route("/partners") {
        authenticate("auth-jwt") {

            post("/verify-pin") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService, allowFrozenActions = true) ?: return@post
                accessControlService.requirePartnerAccess(userId = userId, partnerId = workspaceId, allowManager = true)
                val request = call.receive<VerifyPinRequest>()

                val isValid = partnerRepository.verifyPartnerPin(workspaceId, request.pin)

                if (isValid) call.respond(HttpStatusCode.OK)
                else {
                    eventLogger.log(
                        type = SystemEventType.PIN_VERIFICATION_FAILED,
                        userId = userId,
                        partnerId = workspaceId,
                        payload = "Invalid PIN verification attempt"
                    )
                    throw LoyaltyException(AppErrorCode.INVALID_PIN, "Invalid PIN")
                }
            }

            get("/reviews/summary") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                val timezone = call.getTimezone()
                accessControlService.requirePartnerAccess(userId,workspaceId, true)

                val from = call.request.queryParameters["from"]?.toLongOrNull()
                val to = call.request.queryParameters["to"]?.toLongOrNull()
                val pointId = call.request.queryParameters["pointId"]
                
                val data = ratingRepository.getAnalyticsData(workspaceId, from, to, pointId, timezone)
                call.respond(data)
            }

            get("/reviews") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                val list = ratingRepository.getServiceReviews(workspaceId, limit, offset)
                call.respond(list)
            }
            
            get("/client-ratings") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                
                val list = ratingRepository.getClientRatings(workspaceId, limit, offset)
                call.respond(list)
            }

            get("/analytics") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                val timezone = call.getTimezone()
                accessControlService.requirePartnerAccess(userId,workspaceId, true)

                val periodStr = call.request.queryParameters["period"] ?: "WEEK"

                val period = try {
                    AnalyticsPeriod.valueOf(periodStr.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Invalid period. Use WEEK, MONTH, SIX_MONTHS, YEAR")
                }

                val analytics = analyticsService.getAnalytics(workspaceId, period, timezone)
                call.respond(analytics)
            }

            get("/history") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                val history = transactionService.getPartnerHistory(workspaceId)
                call.respond(history)
            }
            
            post("/create") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<CreatePartnerRequest>()
                val timeZone = call.getTimezone()
                
                if (request.businessName.isBlank()) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Business name cannot be empty")
                }

                if (!SecurityUtils.isStrongPin(request.ownerPin)) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "PIN must contain 4-12 digits")
                }

                val partnerId = partnerRepository.createPartner(userId, timeZone, request)
                
                // Send Welcome Email
                val user = userRepository.getUserById(userId)
                if (user?.email != null) {
                    emailService.sendEmail(
                        to = user.email!!,
                        template = EmailTemplate.PartnerWelcome(
                            name = request.businessName,
                            loginUrl = "$webBaseUrl/login"
                        ),
                        lang = user.language
                    )
                }
                
                call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Business created: $partnerId"))
            }

            put("/pin") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@put
                accessControlService.requirePartnerAccess(userId,workspaceId, false)
                val request = call.receive<UpdatePinRequest>()

                if (!SecurityUtils.isStrongPin(request.newPin)) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "PIN must contain 4-12 digits")
                }
                val partner = partnerRepository.getPartnerByIdOrThrow(workspaceId)
                if (partner.hasPin) {
                    val current = request.currentPin ?: throw LoyaltyException(AppErrorCode.INVALID_PIN, "Current PIN is required")
                    val valid = partnerRepository.verifyPartnerPin(partner.id, current)
                    if (!valid) {
                        eventLogger.log(
                            type = SystemEventType.PIN_VERIFICATION_FAILED,
                            userId = userId,
                            partnerId = partner.id,
                            payload = "Failed PIN change attempt: Invalid current PIN"
                        )
                        throw LoyaltyException(AppErrorCode.INVALID_PIN, "Current PIN is incorrect")
                    }
                }

                partnerRepository.updatePartnerPin(workspaceId, request.newPin)
                userRepository.setFrozenUntil(userId, nowUtc().plusHours(PIN_FREEZE_HOURS).toUtcMillis())

                eventLogger.log(
                    type = SystemEventType.PIN_CHANGE_SUCCESS,
                    userId = userId,
                    partnerId = partner.id,
                    payload = "Owner PIN updated"
                )
                
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "PIN updated"))
            }

            post("/pin/reset") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                accessControlService.requirePartnerAccess(userId,workspaceId, false)
                val request = call.receive<ResetPinRequest>()

                if (!request.confirm) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Reset confirmation required")
                }
                partnerRepository.clearPartnerPin(workspaceId)
                userRepository.setFrozenUntil(userId, nowUtc().plusHours(PIN_FREEZE_HOURS).toUtcMillis())
                
                eventLogger.log(
                    type = SystemEventType.PIN_RESET_SUCCESS,
                    userId = userId,
                    partnerId = workspaceId,
                    payload = "Owner PIN reset (cleared)"
                )

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "PIN reset. Account frozen for 24h"))
            }

            post("/pin/reset/request") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                accessControlService.requirePartnerAccess(userId,workspaceId, false)
                val user = userRepository.getUserById(userId)!!
                val email = user.email ?: throw LoyaltyException(AppErrorCode.EMAIL_NOT_SET, "Email is required for PIN reset")

                pinResetTokenRepository.revokeAll(workspaceId)
                val rawToken = SecurityUtils.generateToken()
                pinResetTokenRepository.createToken(workspaceId, rawToken, nowUtc().plusHours(PIN_RESET_TTL_HOURS).toUtcMillis())

                val resetLink = "$webBaseUrl/reset-pin?token=$rawToken"
                emailService.sendEmail(email, EmailTemplate.PinResetRequested(resetLink), user.language)

                eventLogger.log(
                    type = SystemEventType.PIN_RESET_REQUEST,
                    userId = userId,
                    partnerId = workspaceId,
                    payload = "PIN reset link sent to email"
                )

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Reset link sent"))
            }

            route("/support") {
                get("/thread") {
                    val workspaceId = call.getWorkspaceIdOrThrow()
                    val userId = call.getUserIdOrRespond(accessControlService, allowFrozenActions = true) ?: return@get
                    accessControlService.requirePartnerAccess(userId,workspaceId, true)

                    val response = supportChatService.getPartnerThread(workspaceId)
                    call.respond(response)
                }

                post("/messages") {
                    val workspaceId = call.getWorkspaceIdOrThrow()
                    val userId = call.getUserIdOrRespond(accessControlService, allowFrozenActions = true) ?: return@post
                    accessControlService.requirePartnerAccess(userId,workspaceId, true)

                    val payload = call.receive<SendSupportMessageRequest>()
                    val text = payload.content.trim()
                    if (text.isEmpty()) {
                        throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Message cannot be empty")
                    }

                    supportChatService.sendPartnerMessage(workspaceId, userId, text)
                    call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Message sent"))
                }
            }

            post("/join") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<JoinTradingPointRequest>()
                val point = tradingPointRepository.findTradingPointByInvite(request.inviteCode)
                val partnerId = tradingPointRepository.getPartnerIdByPointId(point.id)
                partnerStaffRepository.addCashier(userId, point.id, partnerId)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Success! Joined ${point.name}"))
            }

            get("/points") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                val points = tradingPointRepository.getPointsByPartnerId(workspaceId)
                call.respond(points)
            }

            get("/points/{pointId}") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                val pointId = call.parameters["pointId"]!!

                val details = tradingPointRepository.getPointDetails(pointId, workspaceId)
                
                call.respond(details)
            }

            put("/points/{pointId}") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@put
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                val pointId = call.parameters["pointId"]!!

                partnerRepository.getPartnerByIdOrThrow(workspaceId)

                val req = call.receive<UpdateTradingPointRequest>()

                req.contactPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                    validatePhoneNumber(phone)
                }

                tradingPointRepository.updateTradingPoint(pointId, req)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            delete("/points/{pointId}") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                 val userId = call.getUserIdOrRespond(accessControlService) ?: return@delete
                accessControlService.requirePartnerAccess(userId,workspaceId, false)
                 val pointId = call.parameters["pointId"]!!

                 tradingPointRepository.deleteTradingPoint(pointId)
                 call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }
            
            get("/points/{pointId}/cashiers") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                val pointId = call.parameters["pointId"]!!

                
                val list = partnerStaffRepository.getCashiersByPoint(pointId)
                call.respond(list)
            }

            delete("/cashiers/{pointId}/{cashierId}") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@delete
                accessControlService.requirePartnerAccess(userId,workspaceId, false)
                val cashierId = call.parameters["cashierId"]!!
                val pointId = call.parameters["pointId"]!!

                // Найти точку или партнера, к которому относится кассир
                if (!partnerStaffRepository.isHasCassierOfPartner(cashierId, workspaceId)) {
                    call.respond(HttpStatusCode.NotFound, ApiMessage(AppErrorCode.USER_NOT_FOUND, "Cashier not found in your business"))
                    return@delete
                }
                
                partnerStaffRepository.removeStaffMember(requesterUserId = userId,  partnerId = workspaceId, targetUserId = cashierId, targetPointId = pointId, targetRole = UserRole.CASHIER)
                deviceTokenRepository.deleteToken(userId, DevicePlatform.ANDROID, UserRole.CASHIER, pointId)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            get("/me") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, false)
                val partner = partnerRepository.getPartnerByIdOrThrow(workspaceId)
                val warnings = subscriptionRepository.getExpiringPointsForPartner(workspaceId)
                val response = partner.copy(subscriptionWarnings = warnings)
                call.respond(response)
            }

            // PUT /partners/me - Обновить настройки
            put("/me") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val timezone = call.getTimezone()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@put
                accessControlService.requirePartnerAccess(userId,workspaceId, false)
                val request = call.receive<UpdatePartnerRequest>()
                
                if (request.businessName.isBlank()) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Business name cannot be empty")
                }
                if (request.defaultVisitsTarget < 1) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Default visits target must be at least 1")
                }
                if (request.baseCurrency.isEmpty()) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Base currency is required")
                }
                partnerRepository.updatePartner(workspaceId, timezone, request)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Settings saved"))

            }

            post("/points") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                accessControlService.requirePartnerAccess(userId,workspaceId, false)

                val request = call.receive<CreateTradingPointRequest>()
                validateBaseCashback(request.baseCashback)

                
                // Email notification about new point
                val user = userRepository.getUserById(userId)
                if (user != null && user.email != null) {
                    val partner = partnerRepository.getPartnerByIdOrThrow(workspaceId, false)
                    emailService.sendEmail(
                        to = user.email!!,
                        template = EmailTemplate.PointCreated(
                            pointName = request.name,
                            partnerName = partner.businessName
                        ),
                        lang = user.language
                    )
                }

                call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Point created"))
            }

            // --- MANAGERS & CASHIERS ---

            get("/managers/invite") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, false)

                val partner = partnerRepository.getPartnerByIdOrThrow(workspaceId, false)
                
                val code = partnerRepository.getManagerInvite(partner.ownerId)
                call.respond(mapOf("inviteCode" to code))
            }
            
            post("/managers/join") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val request = call.receive<JoinTradingPointRequest>() 
                
                val partnerId = partnerRepository.findPartnerByManagerInvite(request.inviteCode)
                    ?: throw LoyaltyException(AppErrorCode.INVALID_INVITE_CODE)

                partnerStaffRepository.addManager(userId, partnerId)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Joined as Manager!"))
            }
            
            get("/managers") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, false)

                val list = partnerStaffRepository.getManagers(workspaceId)
                call.respond(list)
            }
            
            delete("/managers/{id}") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                 val userId = call.getUserIdOrRespond(accessControlService) ?: return@delete
                 accessControlService.requirePartnerAccess(userId,workspaceId, false)
                 val managerId = call.parameters["id"]!!
                 
                 partnerStaffRepository.removeStaffMember(userId, workspaceId, managerId, UserRole.PARTNER_MANAGER)
                 call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            get("/cashiers") {
                val workspaceId = call.getWorkspaceIdOrThrow()
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@get
                accessControlService.requirePartnerAccess(userId,workspaceId, true)
                val list = partnerStaffRepository.getAllCashiers(workspaceId)
                call.respond(list)
            }
        }

        post("/pin/reset/confirm") {
            val request = call.receive<PinResetConfirmRequest>()

            if (!SecurityUtils.isStrongPin(request.newPin)) {
                throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "PIN must contain 4-12 digits")
            }

            val record = pinResetTokenRepository.findValidToken(request.token)
                ?: throw LoyaltyException(AppErrorCode.INVALID_RESET_TOKEN, "Invalid token")

            if (record.expiresAt < System.currentTimeMillis()) {
                pinResetTokenRepository.markUsed(record.id)
                throw LoyaltyException(AppErrorCode.INVALID_RESET_TOKEN, "Token expired")
            }

            val partner = partnerRepository.getPartnerByIdOrThrow(record.partnerId, false)

            partnerRepository.updatePartnerPin(partner.id, request.newPin)

            pinResetTokenRepository.markUsed(record.id)

            userRepository.setFrozenUntil(partner.ownerId, nowUtc().plusHours(PIN_FREEZE_HOURS).toUtcMillis())

            eventLogger.log(
                type = SystemEventType.PIN_RESET_SUCCESS,
                userId = partner.ownerId,
                partnerId = partner.id,
                payload = "PIN reset confirmed via email token"
            )

            call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "PIN updated"))
        }
    }
}

private fun validateBaseCashback(value: Double) {
    if (value.isNaN() || value < 0) {
        throw LoyaltyException(AppErrorCode.INVALID_TIER_VALUE, "Base cashback cannot be negative")
    }
}
