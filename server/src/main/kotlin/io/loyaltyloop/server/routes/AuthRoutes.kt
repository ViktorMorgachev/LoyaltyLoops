package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.server.service.sms.verification.VerificationSignals
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.resolveLanguage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.RefreshTokenRequest
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.VerifyCodeRequest
import java.util.UUID
import io.loyaltyloop.shared.models.SendCodeRequest

import io.loyaltyloop.shared.models.ConfirmAccountDeletionRequest
import io.loyaltyloop.shared.models.RequestAccountDeletionResponse
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.service.sms.PreludeSmsService
import io.loyaltyloop.server.service.sms.SmsService

fun Route.authRoutes(
    repository: UserRepository,
    partnerRepository: io.loyaltyloop.server.repository.PartnerRepository,
    tokenService: TokenService,
    smsService: SmsService,
    eventLogger: EventLogger
) {

    route("/auth") {

        post("/send-code") {
            val request = call.receive<SendCodeRequest>()

            val error = io.loyaltyloop.server.utils.validatePhoneNumber(request.phone)
            if (error != null) {
                throw LoyaltyException(AppErrorCode.INVALID_PHONE, error)
            }

            val deletedUser = repository.getDeletedUserByPhone(request.phone)
            if (deletedUser != null) {
                throw LoyaltyException(AppErrorCode.ACCOUNT_DELETED, "Account deleted")
            }

            val existingUser = repository.getUserByPhone(request.phone)
            val signals = call.extractSignals()
            val verificationId = smsService.startVerification(phone = request.phone, userId = existingUser?.id, signals = signals)

            call.respond(
                mapOf(
                    "status" to "Code sent",
                    "verificationId" to verificationId,
                    "debugCode" to verificationId // For backward compatibility if client used this
                )
            )
        }

        post("/login") {
            val request = call.receive<VerifyCodeRequest>()
            val lang = call.resolveLanguage()

            val error = io.loyaltyloop.server.utils.validatePhoneNumber(request.phone)
            if (error != null) {
                throw LoyaltyException(AppErrorCode.INVALID_PHONE, error)
            }

            val verificationId = request.verificationId ?: request.phone

            if (smsService.checkCode(verificationId, request.phone, request.code)) {
                var user = repository.getUserByPhone(request.phone)

                val isNew = user == null
                if (user == null) {
                    val deletedUser = repository.getDeletedUserByPhone(request.phone)
                    if (deletedUser != null) {
                        throw LoyaltyException(AppErrorCode.ACCOUNT_DELETED, "Account deleted")
                    } else {
                        user = UserDto(
                            id = UUID.randomUUID().toString(),
                            phoneNumber = request.phone,
                            countryCode = request.countryCode.name,
                            language = lang,
                            qrSecret = tokenService.generateQrSecret(),
                            firstName = null
                        )
                        repository.createUser(user)
                        val savedUser = repository.getUserById(user.id)
                        if (savedUser == null) {
                            throw LoyaltyException(AppErrorCode.USER_CREATION_FAILED, "DB Error")
                        }
                        eventLogger.log(
                            type = SystemEventType.REGISTER,
                            userId = user.id,
                            userPhone = request.phone,
                            payload = "User registered via phone"
                        )
                    }
                } else {
                    if (user.language != lang) {
                        repository.updateUserLanguage(user.id, lang)
                        user = user.copy(language = lang)
                    }

                }


                val workspaces = repository.getUserWorkspaces(user.id)
                val (access, refresh) = tokenService.generateTokens(user)
                val expiresAt = System.currentTimeMillis() + tokenService.refreshLifetime
                repository.saveRefreshToken(refresh, user.id, expiresAt)
                
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

        get("/users") {
            val users = repository.getAllUsers()
            call.respond(users)
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            val oldRefreshToken = request.refreshToken

            val userIdFromToken = tokenService.validateRefreshToken(oldRefreshToken)

            if (userIdFromToken != null) {
                val dbUserId = repository.findRefreshToken(oldRefreshToken)

                if (dbUserId != null && dbUserId == userIdFromToken) {
                    repository.deleteRefreshToken(oldRefreshToken)

                    val user = repository.getUserById(userIdFromToken)!!
                    val (newAccess, newRefresh) = tokenService.generateTokens(user)

                    val expiresAt = System.currentTimeMillis() + tokenService.refreshLifetime
                    repository.saveRefreshToken(newRefresh, user.id, expiresAt)

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
                    // Токен валиден по подписи, но его нет в базе (значит, уже использован или отозван)
                    // Это признак кражи токена! В идеале тут можно сбросить все сессии юзера.
                    call.respond(HttpStatusCode.Unauthorized, "Token revoked or already used")
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid Refresh Token")
            }
        }

        post("/logout") {
            val request = call.receiveNullable<RefreshTokenRequest>()
            if (request != null) {
                repository.deleteRefreshToken(request.refreshToken)
            }
            call.respond(HttpStatusCode.OK)
        }

        authenticate("auth-jwt") {
            post("/verify-pin") {
                val userId = call.getUserIdOrRespond(repository, allowFrozenActions = true) ?: return@post
                val request = call.receive<io.loyaltyloop.shared.models.VerifyPinRequest>()

                // TODO проверить что пользователь не кассир и не обычный пользователь и имеет доступ к workspaceId

                val partner = partnerRepository.getPartnerById(request.workspaceId)

                val isValid = partnerRepository.verifyPartnerPin(partner.id, request.pin)
                if (isValid) call.respond(HttpStatusCode.OK)
                else {
                    eventLogger.log(
                        type = SystemEventType.PIN_VERIFICATION_FAILED,
                        userId = userId,
                        partnerId = partner.id,
                        payload = "Invalid PIN verification attempt"
                    )
                    throw LoyaltyException(AppErrorCode.INVALID_PIN, "Invalid PIN")
                }
            }

            post("/account/delete/request") {
                val userId = call.getUserIdOrRespond(repository) ?: return@post
                val user = repository.getUserById(userId)!!

                val signals = call.extractSignals()
                val verificationId = smsService.startVerification(phone = user.phoneNumber, userId = userId, signals = signals)

                call.respond(RequestAccountDeletionResponse("Confirmation code sent", verificationId))
            }

            post("/account/delete/confirm") {
                val userId = call.getUserIdOrRespond(repository) ?: return@post
                val user = repository.getUserById(userId)!!
                val request = call.receive<ConfirmAccountDeletionRequest>()

                val verificationId = request.verificationId ?: user.phoneNumber

                if (smsService.checkCode(verificationId, user.phoneNumber, request.code)) {
                    repository.markUserDeleted(userId, request.reason)
                    repository.deleteAllTokensForUser(userId)

                    eventLogger.log(
                        type = SystemEventType.INFO,
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

private fun ApplicationCall.extractSignals(): VerificationSignals {
    return VerificationSignals(
        ip = request.header("X-Forwarded-For")?.split(",")?.firstOrNull() ?: request.local.remoteHost,
        deviceId = request.header("X-Device-Id"),
        platform = request.header("X-Device-Platform"),
        deviceModel = request.header("X-Device-Model"),
        osVersion = request.header("X-Os-Version"),
        appVersion = request.header("X-App-Version")
    )
}
