package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.DeviceTokenRepository
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.resolveLanguage
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CountryCode
import io.loyaltyloop.shared.models.DeviceTokenContext
import io.loyaltyloop.shared.models.RegisterDeviceTokenRequest
import io.loyaltyloop.shared.models.UpdateLanguageRequest
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserProfileResponse
import io.loyaltyloop.shared.models.Country
import io.loyaltyloop.shared.models.CreateServiceReviewDto
import io.loyaltyloop.server.service.RatingService
import io.loyaltyloop.server.utils.CardUtils
import io.loyaltyloop.server.utils.getCurrencyForTimezone

@Suppress("ThrowsCount")
fun Route.clientRoutes(
    userRepository: UserRepository,
    deviceTokenRepository: DeviceTokenRepository,
    ratingService: RatingService,
    cardUtils: CardUtils
) {
    route("/client") {
        authenticate("auth-jwt") {

            
            post("/rate-service") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<CreateServiceReviewDto>()
                
                ratingService.rateService(userId, request)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }
            
           post("/profile") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post

                // 1. Получаем текущего юзера

                val user = userRepository.getUserById(userId)

                if (user == null){
                    call.respond(HttpStatusCode.Unauthorized, "User not found")
                    return@post
                }

                val request = call.receive<UpdateProfileRequest>()
                val lang = call.resolveLanguage(default = user.language)


                if (request.firstName.isBlank()) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Name cannot be empty")
                }

                request.email?.let { email->
                    if (!io.loyaltyloop.server.utils.isValidEmail(email)) {
                        throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Invalid email format")
                    }
                }

                userRepository.updateUserProfile(userDto = user, lang = lang, request)

                call.respond(HttpStatusCode.OK, ApiMessage(code = AppErrorCode.SUCCESS, message = AppErrorCode.SUCCESS.name))
            }

            post("/language") {
                val userId = call.getUserIdOrRespond(userRepository, allowFrozenActions = true) ?: return@post
                val request = call.receive<UpdateLanguageRequest>()
                val normalized = request.language.lowercase()
                val allowed = setOf("ru", "en", "ky", "kk", "uz", "be")
                if (normalized !in allowed) {
                    throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Unsupported language code: ${request.language}")
                }

                userRepository.updateUserLanguage(userId, normalized)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            // GET /client/cards - Список карт для кошелька
            get("/cards") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get

                val timezoneCurrency = call.getCurrencyForTimezone()

                call.respond(cardUtils.getUserCards(userId = userId, estimatedCurrency = timezoneCurrency))
            }

            post("/device-token") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@post
                val request = call.receive<RegisterDeviceTokenRequest>()

                deviceTokenRepository.upsertToken(
                    userId = userId,
                    platform = request.platform,
                    role = request.role,
                    workspaceId = request.workspaceId,
                    token = request.token
                )

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            delete("/device-token") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@delete
                val request = call.receive<DeviceTokenContext>()
                deviceTokenRepository.deleteToken(
                    userId = userId,
                    platform = request.platform,
                    role = request.role,
                    workspaceId = request.workspaceId
                )
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }
        }
        authenticate("auth-jwt") {
            get("/me") {
                val userId = call.getUserIdOrRespond(userRepository) ?: return@get

                // 1. Проверяем, существует ли юзер реально
                val user = userRepository.getUserById(userId)

                if (user == null){
                    call.respond(HttpStatusCode.Unauthorized, "User not found")
                    return@get
                }


                // 2. Если юзер есть, собираем его актуальные роли
                val workspaces = userRepository.getUserWorkspaces(userId)

                val countryCodeByPhone = Country.entries.find { user.phoneNumber.startsWith(it.phonePrefix) }?.code ?: CountryCode.KG

                // 3. Отдаем профиль
                call.respond(
                    UserProfileResponse(
                        userId = user.id,
                        phone = user.phoneNumber,
                        countryCode = countryCodeByPhone,
                        firstName = user.firstName,
                        lastName = user.lastName,
                        email = user.email,
                        language = user.language,
                        workspaces = workspaces,
                        isFrozenUntil = user.isFrozenUntil
                    )
                )
            }
        }
    }
}