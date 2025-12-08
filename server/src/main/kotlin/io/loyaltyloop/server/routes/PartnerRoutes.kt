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
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.repository.PinResetTokenRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.requirePartnerWriteAccess
import io.loyaltyloop.server.utils.requirePointReadAccess
import io.loyaltyloop.server.utils.requirePointWriteAccess
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.JoinTradingPointRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.UpdatePartnerRequest
import io.loyaltyloop.shared.models.TradingPointDetailsDto
import io.loyaltyloop.shared.models.UpdateTradingPointRequest
import io.loyaltyloop.shared.models.UpdatePinRequest
import io.loyaltyloop.shared.models.ResetPinRequest
import io.loyaltyloop.shared.models.PinResetConfirmRequest
import io.loyaltyloop.shared.models.UpdateLoyaltySettingsRequest
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.shared.models.PartnerEntity
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.server.service.email.EmailService
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.shared.models.SendSupportMessageRequest
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.models.SystemEventType

private const val PIN_FREEZE_MS = 24 * 60 * 60 * 1000L
private const val PIN_RESET_TOKEN_TTL = 15 * 60 * 1000L

fun Route.partnerRoutes(
    userRepository: UserRepository,
    partnerRepository: PartnerRepository,
    transactionService: TransactionService,
    pinResetTokenRepository: PinResetTokenRepository,
    emailService: EmailService,
    webBaseUrl: String,
    supportChatService: SupportChatService,
    eventLogger: EventLogger
) {
    route("/partners") {
        authenticate("auth-jwt") {

            get("/analytics") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get

                val periodStr = call.request.queryParameters["period"] ?: "WEEK"
                
                val period = try {
                    io.loyaltyloop.shared.models.AnalyticsPeriod.valueOf(periodStr.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Invalid period. Use WEEK, MONTH, SIX_MONTHS, YEAR")
                }

                val analytics = transactionService.getAnalytics(userId, period)
                call.respond(analytics)
            }

            get("/history") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                // Service exceptions will be handled by ErrorHandler
                val history = transactionService.getPartnerHistory(userId)
                call.respond(history)
            }
            
            post("/create") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<CreatePartnerRequest>()
                
                if (request.businessName.isBlank()) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Business name cannot be empty")
                }

                val partnerId = partnerRepository.createPartner(userId, request)
                
                call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Business created: $partnerId"))
            }

            put("/pin") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@put
                val request = call.receive<UpdatePinRequest>()
                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)

                if (!SecurityUtils.isStrongPin(request.newPin)) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "PIN must contain 4-12 digits")
                }

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

                partnerRepository.updatePartnerPin(partner.id, request.newPin)
                userRepository.setFrozenUntil(userId, null)
                
                eventLogger.log(
                    type = SystemEventType.PIN_CHANGE_SUCCESS,
                    userId = userId,
                    partnerId = partner.id,
                    payload = "Owner PIN updated"
                )
                
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "PIN updated"))
            }

            post("/pin/reset") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<ResetPinRequest>()
                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)

                if (!request.confirm) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Reset confirmation required")
                }

                partnerRepository.clearPartnerPin(partner.id)
                val freezeUntil = System.currentTimeMillis() + PIN_FREEZE_MS
                userRepository.setFrozenUntil(userId, freezeUntil)
                
                eventLogger.log(
                    type = SystemEventType.PIN_RESET_SUCCESS,
                    userId = userId,
                    partnerId = partner.id,
                    payload = "Owner PIN reset (cleared)"
                )

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "PIN reset. Account frozen for 24h"))
            }

            post("/pin/reset/request") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val user = userRepository.getUserById(userId) ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND)
                val email = user.email ?: throw LoyaltyException(AppErrorCode.EMAIL_NOT_SET, "Email is required for PIN reset")
                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)

                pinResetTokenRepository.revokeAll(userId)
                val rawToken = SecurityUtils.generateToken()
                val expiresAt = System.currentTimeMillis() + PIN_RESET_TOKEN_TTL
                pinResetTokenRepository.createToken(userId, rawToken, expiresAt)

                val resetLink = "$webBaseUrl/reset-pin?token=$rawToken"
                emailService.sendPinResetEmail(email, resetLink)

                eventLogger.log(
                    type = SystemEventType.PIN_RESET_REQUEST,
                    userId = userId,
                    partnerId = partner.id,
                    payload = "PIN reset link sent to email"
                )

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Reset link sent"))
            }

            route("/support") {
                get("/thread") {
                    val userId = call.getUserIdOrRespond(userRepository, allowFrozenActions = true) ?: return@get
                    val partner = partnerRepository.getPartnerByUserId(userId)
                    ensureOwner(partner, userId)

                    val response = supportChatService.getPartnerThread(partner.id)
                    call.respond(response)
                }

                post("/messages") {
                    val userId = call.getUserIdOrRespond(userRepository, allowFrozenActions = true) ?: return@post
                    val payload = call.receive<SendSupportMessageRequest>()
                    val text = payload.content.trim()
                    if (text.isEmpty()) {
                        throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Message cannot be empty")
                    }

                    val partner = partnerRepository.getPartnerByUserId(userId)
                    ensureOwner(partner, userId)

                    supportChatService.sendPartnerMessage(partner.id, userId, UserRole.PARTNER_ADMIN, text)
                    call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Message sent"))
                }
            }

            post("/join") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post

                val request = call.receive<JoinTradingPointRequest>()

                // 1. Ищем точку
                val point = partnerRepository.findTradingPointByInvite(request.inviteCode)

                // 2. Проверяем дубли
                if (partnerRepository.isUserCashierAtPoint(userId, point.id)) {
                    throw LoyaltyException(AppErrorCode.ALREADY_JOINED)
                }

                // 3. Получаем PartnerID
                val partnerId = partnerRepository.getPartnerIdByPoint(point.id)

                // 4. Добавляем
                partnerRepository.addCashier(userId, point.id, partnerId)

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Success! Joined ${point.name}"))
            }

            get("/points") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get

                val partners = partnerRepository.getPartnersByOwner(userId)
                val myPartner = partners.firstOrNull()

                if (myPartner == null) {
                    call.respond(listOf<TradingPointDto>())
                    return@get
                }

                val points = partnerRepository.getPointsByPartnerId(myPartner.id)
                call.respond(points)
            }

            // --- NEW: Point Management ---

            get("/points/{pointId}") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val pointId = call.parameters["pointId"]!!
                
                requirePointReadAccess(partnerRepository, userId, pointId)

                val point = partnerRepository.getPointById(pointId)
                val settings = partnerRepository.getSettingsByPointId(pointId)
                
                call.respond(TradingPointDetailsDto(point, settings))
            }

            put("/points/{pointId}") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@put
                val pointId = call.parameters["pointId"]!!
                
                requirePointWriteAccess(partnerRepository, userId, pointId)
                
                val req = call.receive<UpdateTradingPointRequest>()
                
                req.contactPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                    val error = io.loyaltyloop.server.utils.validatePhoneNumber(phone)
                    if (error != null) {
                        throw LoyaltyException(AppErrorCode.INVALID_PHONE_NUMBER, "$error (received: '$phone')")
                    }
                }

                validateTierSettings(req.settings)
                partnerRepository.updateTradingPoint(pointId, req)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            delete("/points/{pointId}") {
                 val userId = call.getUserIdOrRespond(userRepository) ?: return@delete
                 val pointId = call.parameters["pointId"]!!
                 
                 requirePointWriteAccess(partnerRepository, userId, pointId)
                 
                 partnerRepository.deleteTradingPoint(pointId)
                 call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }
            
            get("/points/{pointId}/cashiers") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val pointId = call.parameters["pointId"]!!
                
                requirePointReadAccess(partnerRepository, userId, pointId)
                
                val list = partnerRepository.getCashiersByPoint(pointId)
                call.respond(list)
            }

            delete("/cashiers/{cashierId}") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@delete
                val cashierId = call.parameters["cashierId"]!!
                
                // Найти точку или партнера, к которому относится кассир
                val partnerId = partnerRepository.getPartnerIdByCashierId(cashierId) 
                    ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "Cashier link not found")
                
                requirePartnerWriteAccess(partnerRepository, userId, partnerId)
                
                partnerRepository.deleteCashier(cashierId)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            get("/me") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get

                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)
                call.respond(partner)
            }

            // PUT /partners/me - Обновить настройки
            put("/me") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@put

                val request = call.receive<UpdatePartnerRequest>()
                
                if (request.businessName.isBlank()) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Business name cannot be empty")
                }
                if (request.defaultVisitsTarget < 1) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Default visits target must be at least 1")
                }
                
                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)

                partnerRepository.updatePartner(userId, request)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Settings saved"))
            }

            post("/points") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post

                val request = call.receive<CreateTradingPointRequest>()
                validateBaseCashback(request.baseCashback)

                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)

                partnerRepository.createTradingPoint(
                    partnerId = partner.id,
                    request = request,
                )

                call.respond(HttpStatusCode.Created, ApiMessage(AppErrorCode.SUCCESS, "Point created"))
            }

            // --- MANAGERS & CASHIERS ---

            post("/managers/invite") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                
                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)
                
                val code = partnerRepository.generateManagerInvite(partner.id)
                call.respond(mapOf("inviteCode" to code))
            }
            
            post("/managers/join") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<JoinTradingPointRequest>() 
                
                val partnerId = partnerRepository.findPartnerByManagerInvite(request.inviteCode) 
                    ?: throw LoyaltyException(AppErrorCode.INVALID_INVITE_CODE)
                
                if (partnerRepository.isUserManager(userId, partnerId)) {
                     throw LoyaltyException(AppErrorCode.ALREADY_JOINED, "Already a manager")
                }
                
                partnerRepository.addManager(userId, partnerId)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Joined as Manager!"))
            }
            
            get("/managers") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)
                
                val list = partnerRepository.getManagers(partner.id)
                call.respond(list)
            }
            
            delete("/managers/{id}") {
                 val userId = call.getUserIdOrRespond(userRepository) ?: return@delete
                 val managerId = call.parameters["id"]!!
                 
                 val partner = partnerRepository.getPartnerByUserId(userId)
                 ensureOwner(partner, userId)
                 
                 // Проверить, принадлежит ли менеджер этому партнеру
                 if (!partnerRepository.isUserManager(managerId, partner.id)) {
                     throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "Manager not found in your business")
                 }
                 
                 partnerRepository.deleteManager(managerId)
                 call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            get("/cashiers") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get
                val partner = partnerRepository.getPartnerByUserId(userId)
                ensureOwner(partner, userId)
                
                val list = partnerRepository.getAllCashiers(partner.id)
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

            val user = userRepository.getUserById(record.userId)
                ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND)

            val partner = partnerRepository.getPartnerByUserId(record.userId)

            partnerRepository.updatePartnerPin(partner.id, request.newPin)
            pinResetTokenRepository.markUsed(record.id)
            val freezeUntil = System.currentTimeMillis() + PIN_FREEZE_MS
            userRepository.setFrozenUntil(record.userId, freezeUntil)

            eventLogger.log(
                type = SystemEventType.PIN_RESET_SUCCESS,
                userId = record.userId,
                partnerId = partner.id,
                payload = "PIN reset confirmed via email token"
            )

            call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "PIN updated"))
        }
    }
}

private fun ensureOwner(partner: PartnerEntity, userId: String) {
    if (partner.ownerId != userId) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Only owner can manage this partner")
    }
}

private fun validateTierSettings(settings: UpdateLoyaltySettingsRequest) {
    if (settings.programType == LoyaltyProgramType.TIERED_LTV || settings.programType == LoyaltyProgramType.HYBRID) {
        if (settings.tiers.isEmpty()) {
            throw LoyaltyException(AppErrorCode.INVALID_TIER_VALUE, "At least one tier is required")
        }
        settings.tiers.forEach { tier ->
            val label = tier.loyaltyTier.descr ?: tier.loyaltyTier.level.name
            if (tier.threshold.isNaN() || tier.threshold < 0) {
                throw LoyaltyException(AppErrorCode.INVALID_TIER_VALUE, "Threshold cannot be negative for $label")
            }
            if (tier.cashbackPercent.isNaN() || tier.cashbackPercent < 0) {
                throw LoyaltyException(AppErrorCode.INVALID_TIER_VALUE, "Cashback percent cannot be negative for $label")
            }
        }
    }
}

private fun validateBaseCashback(value: Double) {
    if (value.isNaN() || value < 0) {
        throw LoyaltyException(AppErrorCode.INVALID_TIER_VALUE, "Base cashback cannot be negative")
    }
}
