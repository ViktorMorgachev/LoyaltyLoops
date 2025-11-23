package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.getUserIdOrRespond
import io.loyaltyloop.server.utils.resolveLanguage
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CountryCode
import io.loyaltyloop.shared.models.TestApiErrors
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserProfileResponse

fun Route.clientRoutes(repository: UserRepository) {
    route("/client") {
        authenticate("auth-jwt") {

            
           post("/profile") {
                val userId = call.getUserIdOrRespond(repository) ?: return@post

                // 1. Получаем текущего юзера

                val user = repository.getUserById(userId)

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

                repository.updateUserProfile(userDto = user, lang = lang, request)

                call.respond(HttpStatusCode.OK, ApiMessage(code = AppErrorCode.SUCCESS, message = AppErrorCode.SUCCESS.name))
            }

            // GET /client/cards - Список карт для кошелька
            get("/cards") {
                val userId = call.getUserIdOrRespond(repository) ?: return@get

                val cards = repository.getUserCards(userId)
                call.respond(cards)
            }
        }
        authenticate("auth-jwt") {
            get("/me") {
                val userId = call.getUserIdOrRespond(repository) ?: return@get

                // 1. Проверяем, существует ли юзер реально
                val user = repository.getUserById(userId)

                if (user == null){
                    call.respond(HttpStatusCode.Unauthorized, "User not found")
                    return@get
                }


                // 2. Если юзер есть, собираем его актуальные роли
                val workspaces = repository.getUserWorkspaces(userId)

                // 3. Отдаем профиль
                call.respond(
                    UserProfileResponse(
                        userId = user.id,
                        phone = user.phoneNumber,
                        countryCode = CountryCode.entries.first { user.countryCode == it.name },
                        firstName = user.firstName,
                        lastName = user.lastName,
                        email = user.email,
                        language = user.language,
                        workspaces = workspaces
                    )
                )
            }
        }
    }
}