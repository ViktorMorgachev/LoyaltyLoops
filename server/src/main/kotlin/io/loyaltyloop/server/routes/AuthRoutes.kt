package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.resolveLanguage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.RefreshTokenRequest
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.VerifyCodeRequest
import io.loyaltyloop.shared.models.SendCodeRequest

import io.loyaltyloop.shared.models.ConfirmAccountDeletionRequest
import io.loyaltyloop.shared.models.RequestAccountDeletionResponse
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.service.sms.SmsService

import io.loyaltyloop.shared.models.TelegramAuthStartResponse
import io.loyaltyloop.shared.models.AuthSessionStatusResponse
import io.loyaltyloop.server.service.TelegramAuthService
import io.loyaltyloop.server.repository.AuthSessionRepository
import io.loyaltyloop.server.repository.RefreshTokenRepository
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.utils.extractSignals
import io.loyaltyloop.server.utils.long
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.server.utils.validatePhoneNumber

// TODO checked
fun Route.authRoutes(
    repository: UserRepository,
    tokenService: TokenService,
    smsService: SmsService,
    eventLogger: EventLogger,
    applicationConfig: ApplicationConfig,
    telegramAuthService: TelegramAuthService,
    authSessionRepository: AuthSessionRepository,
    refreshTokenRepository: RefreshTokenRepository,
    accessControlService: AccessControlService,
) {

    route("/auth") {

        route("/telegram") {
            post("/start") {
                val ttl = applicationConfig.long("telegram.ttl", default = 120_000L)
                val uuid = authSessionRepository.createSession(ttl)
                call.respond(TelegramAuthStartResponse(uuid, telegramAuthService.botUsername, (ttl / 1000).toInt()))
            }

            get("/status/{uuid}") {
                val uuid = call.parameters["uuid"]!!
                val session = authSessionRepository.getSession(uuid)
                if (session == null) {
                    throw LoyaltyException(AppErrorCode.NOT_FOUND, "Session not found")
                }

                if (session.status == "CONFIRMED") {
                    val userId = session.userId ?: return@get call.respond(HttpStatusCode.InternalServerError, "Session confirmed but userID is missing")

                    if (session.phone == null) {
                        call.respond(HttpStatusCode.InternalServerError, "Session confirmed but phone number is missing")
                        return@get
                    }
                    val user = repository.getUserById(userId)
                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                        return@get
                    }
                    if (user.isDeleted) {
                        throw LoyaltyException(AppErrorCode.ACCOUNT_DELETED, "Account deleted")
                    }

                    val (access, refresh) = tokenService.generateTokens(user)
                    val expiresAt = System.currentTimeMillis() + tokenService.refreshLifetime
                    refreshTokenRepository.saveRefreshToken(refresh, user.id, expiresAt)
                    val workspaces = repository.getUserWorkspaces(user.id)

                    call.respond(AuthSessionStatusResponse(
                        status = "CONFIRMED",
                        auth = AuthResponse(access, refresh, user.id, false, workspaces, qrSecret = user.qrSecret)
                    ))
                } else {
                    call.respond(AuthSessionStatusResponse(status = session.status))
                }
            }
        }

        post("/send-code") {
            val request = call.receive<SendCodeRequest>()
            validatePhoneNumber(request.phone)
            val existingUser = repository.getUserByPhone(request.phone)
            if (existingUser != null && existingUser.isDeleted) {
                throw LoyaltyException(AppErrorCode.ACCOUNT_DELETED, "Account deleted")
            }

            val signals = call.extractSignals()
            val verificationId = smsService.startVerification(phone = request.phone, userId = existingUser?.id, signals = signals)

            call.respond(
                mapOf(
                    "status" to "Code sent",
                    "verificationId" to verificationId,
                    "debugCode" to verificationId
                )
            )
        }

        post("/login") {
            val request = call.receive<VerifyCodeRequest>()
            val lang = call.resolveLanguage()
            validatePhoneNumber(request.phone)

            val verificationId = request.verificationId ?: request.phone

            if (smsService.checkCode(verificationId, request.phone, request.code)) {
                var user = repository.getUserByPhone(request.phone)

                val isNew = user == null
                if (isNew) {
                    user = UserDto(
                        id = "will_ignore",
                        phoneNumber = request.phone,
                        countryCode = request.countryCode.name,
                        language = lang,
                        qrSecret = tokenService.generateQrSecret(),
                        firstName = null,
                        createdAt = nowUtc().toUtcMillis()
                    )
                    val userId  = repository.createUser(user)

                    if (repository.getUserById(userId) == null){
                        throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "User is not created in database")
                    }

                    user = user.copy(id = userId)
                    eventLogger.log(
                        type = SystemEventType.REGISTER,
                        userId = userId,
                        userPhone = request.phone,
                        payload = "User registered via phone"
                    )
                } else {
                    if (user.isDeleted) {
                        throw LoyaltyException(AppErrorCode.ACCOUNT_DELETED, "Account deleted")
                    }
                    if (user.language != lang) {
                        repository.updateUserLanguage(user.id, lang)
                        user = user.copy(language = lang)
                    }
                }

                val workspaces = repository.getUserWorkspaces(user.id)
                val (access, refresh) = tokenService.generateTokens(user)
                val expiresAt = System.currentTimeMillis() + tokenService.refreshLifetime
                refreshTokenRepository.saveRefreshToken(refresh, user.id, expiresAt)
                
                eventLogger.log(
                    type = SystemEventType.LOGIN,
                    userId = user.id,
                    userPhone = request.phone,
                    payload = "User logged in"
                )
                call.respond(AuthResponse(access, refresh, user.id, isNew, workspaces, qrSecret = user.qrSecret))

            } else {
                throw LoyaltyException(AppErrorCode.INVALID_CODE)
            }
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            val oldRefreshToken = request.refreshToken

            val userIdFromToken = tokenService.validateRefreshToken(oldRefreshToken)

            if (userIdFromToken != null) {
                val dbUserId = refreshTokenRepository.findUserIdByToken(oldRefreshToken)

                if (dbUserId != null && dbUserId == userIdFromToken) {
                    refreshTokenRepository.deleteRefreshToken(oldRefreshToken)

                    val user = repository.getUserById(userIdFromToken)!!
                    val (newAccess, newRefresh) = tokenService.generateTokens(user)

                    val expiresAt = System.currentTimeMillis() + tokenService.refreshLifetime
                    refreshTokenRepository.saveRefreshToken(newRefresh, user.id, expiresAt)

                    val workspaces = repository.getUserWorkspaces(user.id)

                    call.respond(
                        AuthResponse(
                            accessToken = newAccess,
                            refreshToken = newRefresh,
                            userId = user.id,
                            isNewUser = false,
                            qrSecret = user.qrSecret,
                            workspaces = workspaces
                        )
                    )
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Token revoked or already used")
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid Refresh Token")
            }
        }

        post("/logout") {
            val request = call.receiveNullable<RefreshTokenRequest>()
            if (request != null) {
                refreshTokenRepository.deleteRefreshToken(request.refreshToken)
            }
            call.respond(HttpStatusCode.OK)
        }

        authenticate("auth-jwt") {

            post("/account/delete/request") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val user = repository.getUserById(userId)!!

                val signals = call.extractSignals()
                val verificationId = smsService.startVerification(phone = user.phoneNumber, userId = userId, signals = signals)

                call.respond(RequestAccountDeletionResponse("Confirmation code sent", verificationId))
            }

            post("/account/delete/confirm") {
                val userId = call.getUserIdOrRespond(accessControlService) ?: return@post
                val user = repository.getUserById(userId)!!
                val request = call.receive<ConfirmAccountDeletionRequest>()

                val verificationId = request.verificationId ?: user.phoneNumber

                if (smsService.checkCode(verificationId, user.phoneNumber, request.code)) {
                    repository.deleteUser(userId, request.reason)

                    eventLogger.log(
                        type = SystemEventType.DELETING,
                        userId = userId,
                        userPhone = user.phoneNumber,
                        payload = "Account deleted. Reason: ${request.reason}"
                    )

                    call.respond(HttpStatusCode.OK)
                } else {
                    throw LoyaltyException(AppErrorCode.INVALID_CODE)
                }
            }
        }
    }
}
