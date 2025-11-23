package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.TokenService
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

fun Route.authRoutes(
    repository: UserRepository,
    partnerRepository: io.loyaltyloop.server.repository.PartnerRepository,
    tokenService: TokenService,
    otpService: OtpService
) {

    route("/auth") {

        post("/send-code") {
            val request = call.receive<SendCodeRequest>()

            val error = io.loyaltyloop.server.utils.validatePhoneNumber(request.phone)
            if (error != null) {
                throw LoyaltyException(AppErrorCode.INVALID_PHONE, error)
            }

            val code = otpService.generateCode(request.phone)
            println("SMS для ${request.phone}: $code")

            call.respond(
                mapOf(
                    "status" to "Code sent",
                    "debugCode" to code
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

            if (otpService.validateCode(request.phone, request.code)) {
                var user = repository.getUserByPhone(request.phone)
                val isNew = user == null
                if (user == null) {
                    user = UserDto(
                        id = UUID.randomUUID().toString(),
                        phoneNumber = request.phone,
                        countryCode = request.code,
                        language = lang,
                        qrSecret = tokenService.generateQrSecret(),
                        firstName = null
                    )
                    repository.createUser(user)
                    val savedUser = repository.getUserById(user.id)
                    if (savedUser == null) {
                        throw LoyaltyException(AppErrorCode.USER_CREATION_FAILED, "DB Error")
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
                call.respond(AuthResponse(access, refresh, user.id, isNew, workspaces, qrSecret = user.qrSecret))


            } else {
                call.respond(HttpStatusCode.Unauthorized, "Неверный или устаревший код")
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
                val userId = call.getUserIdOrRespond(repository) ?: return@post
                val request = call.receive<io.loyaltyloop.shared.models.VerifyPinRequest>()

                // TODO проверить что пользователь не кассир и не обычный пользователь и имеет доступ к workspaceId

                val partner = partnerRepository.getPartnerById(request.workspaceId)

                val isValid = partnerRepository.verifyPartnerPin(partner.id, request.pin)
                if (isValid) call.respond(HttpStatusCode.OK)
                else throw LoyaltyException(AppErrorCode.INVALID_PIN, "Invalid PIN")
                return@post

                // If workspace not found or not partner? Assume OK or specific error
                // For now OK implies "no pin needed" or "not found so whatever"
                // But let's keep old logic: respond OK if partner not found? That's weird.
                // Old code: if (partner != null) ... else respond(OK).
                // Assuming it means "No pin required".
            }
        }
    }
}
