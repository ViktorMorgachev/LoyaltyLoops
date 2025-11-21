package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.ServerResources
import io.loyaltyloop.server.utils.resolveLanguage
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserProfileResponse

fun Route.clientRoutes(repository: UserRepository) {
    route("/client") {
        authenticate("auth-jwt") {

            post("/profile") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@post

                // 1. Получаем текущего юзера, чтобы знать его язык (до обновления)
                // или используем язык из запроса, если он хочет его сменить
                val currentUser = repository.getUserById(userId)
                if (currentUser == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val request = call.receive<UpdateProfileRequest>()
                val lang = call.resolveLanguage(default = currentUser.language)


                if (request.firstName.isBlank()) {
                    val msg = ServerResources.get("name_empty", lang)
                    call.respond(HttpStatusCode.BadRequest, ApiMessage(msg))
                    return@post
                }

                request.email?.let { email->
                    if (!io.loyaltyloop.server.utils.isValidEmail(email)) {
                        val msg = ServerResources.get("invalid_email", lang)
                        call.respond(HttpStatusCode.BadRequest, ApiMessage(msg))
                        return@post
                    }
                }

                repository.updateUserProfile(userDto = currentUser, lang = lang, request)

                val msg = ServerResources.get("profile_updated", lang)
                call.respond(HttpStatusCode.OK, ApiMessage(msg)) // <-- JSON
            }

            // GET /client/cards - Список карт для кошелька
            get("/cards") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString() ?: return@get

                val cards = repository.getUserCards(userId)
                call.respond(cards)
            }
        }
        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                // Безопасное извлечение ID (на случай сбоя парсинга)
                val userId = principal?.payload?.getClaim("id")?.asString()

                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Token claim 'id' is missing")
                    return@get
                }

                // 1. Проверяем, существует ли юзер реально
                val user = repository.getUserById(userId)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "User no longer exists")
                    return@get
                }

                // 2. Если юзер есть, собираем его актуальные роли
                val workspaces = repository.getUserWorkspaces(userId)

                // 3. Отдаем профиль
                call.respond(
                    UserProfileResponse(
                        userId = user.id,
                        phone = user.phoneNumber,
                        countryCode = user.countryCode,
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