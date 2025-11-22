package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.server.utils.resolveLanguage
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.RefreshTokenRequest
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.VerifyCodeRequest
import java.util.UUID
import io.loyaltyloop.shared.models.SendCodeRequest
import io.loyaltyloop.shared.models.UserProfileResponse

fun Route.authRoutes(
    repository: UserRepository,
    tokenService: TokenService,
    otpService: OtpService
) {

    get("/health") {
        // Возвращаем JSON, чтобы тест мог его проверить
        call.respond(
            mapOf(
                "status" to "OK",
                "db" to "connected",
                "version" to "1.0.0"
            )
        )
    }

    route("/auth") {

        post("/send-code") {
            val request = call.receive<SendCodeRequest>()

            val error = io.loyaltyloop.server.utils.validatePhoneNumber(request.phone)
            if (error != null) {
                call.respond(HttpStatusCode.BadRequest, error)
                return@post
            }

            // Генерируем реальный случайный код
            val code = otpService.generateCode(request.phone)

            println("SMS для ${request.phone}: $code") // Лог для нас

            // ВАЖНО: Для MVP возвращаем код в ответе, чтобы клиент мог его подставить
            // В Проде мы уберем поле code из ответа и будем слать СМС
            call.respond(
                mapOf(
                    "status" to "Code sent",
                    "debugCode" to code // <-- ОТДАЕМ КОД КЛИЕНТУ
                )
            )
        }

        post("/login") {
            val request = call.receive<VerifyCodeRequest>()
            val lang = call.resolveLanguage()

            // Валидация номера
            val error = io.loyaltyloop.server.utils.validatePhoneNumber(request.phone)
            if (error != null) {
                call.respond(HttpStatusCode.BadRequest, error)
                return@post
            }

            // --- ИСПОЛЬЗУЕМ OTP SERVICE ---
            val isValid = otpService.validateCode(request.phone, request.code)

            if (isValid) {
                var user = repository.getUserByPhone(request.phone)
                var isNew = false
                if (user == null) {
                    val secret = tokenService.generateQrSecret()
                    val userId = UUID.randomUUID().toString()
                    val newUser = UserDto(
                        id = userId,
                        phoneNumber = request.phone,
                        countryCode = "KG", //TODO Получать код по номеру
                        language = lang,
                        qrSecret = secret,
                        firstName = null
                    )
                    repository.createUser(newUser)
                    val savedUser = repository.getUserById(userId)
                    if (savedUser == null) {
                        call.respond(HttpStatusCode.InternalServerError, "DB Error")
                        return@post
                    }
                    user = savedUser
                    isNew = true
                } else {
                    // Юзер уже есть. Проверяем, сменил ли он язык?
                    if (user.language != lang) {
                        // Да, в базе "ru", а пришел "en". Обновляем базу!
                        repository.updateUserLanguage(user.id, lang)

                        // Обновляем объект user в памяти, чтобы в токен/ответ попали актуальные данные (если нужно)
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

            // 1. Валидация подписи (Stateless)
            val userIdFromToken = tokenService.validateRefreshToken(oldRefreshToken)

            if (userIdFromToken != null) {
                // 2. Валидация базы данных (Stateful)
                // Проверяем, существует ли этот токен в белом списке
                val dbUserId = repository.findRefreshToken(oldRefreshToken)

                if (dbUserId != null && dbUserId == userIdFromToken) {
                    // Токен валиден и найден в базе!

                    // 3. Удаляем СТАРЫЙ токен (чтобы его нельзя было использовать дважды)
                    repository.deleteRefreshToken(oldRefreshToken)

                    // 4. Генерируем НОВЫЙ
                    val user = repository.getUserById(userIdFromToken)!! // Юзер точно есть, если есть токен
                    val (newAccess, newRefresh) = tokenService.generateTokens(user)

                    // 5. Сохраняем НОВЫЙ в базу
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
            // Клиент может прислать refresh token, чтобы мы его удалили
            val request = call.receiveNullable<RefreshTokenRequest>()
            if (request != null) {
                repository.deleteRefreshToken(request.refreshToken)
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}
