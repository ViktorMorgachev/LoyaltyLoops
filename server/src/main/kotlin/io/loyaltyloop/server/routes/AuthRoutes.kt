package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.SendCodeRequest
import io.loyaltyloop.shared.models.VerifyCodeRequest

fun Route.authRoutes(repository: UserRepository) {

    route("/auth") {

        post("/send-code") {
            val request = call.receive<SendCodeRequest>()
            // Тут мы бы подключили SMS-шлюз.
            // Пока просто логируем.
            println("SMS для ${request.phone}: 1111")
            call.respond(HttpStatusCode.OK, "Code sent")
        }

        post("/login") {
            val request = call.receive<VerifyCodeRequest>()

            // ХАРДКОД для MVP: Код всегда 1111
            if (request.code == "1111") {

                // Проверяем, есть ли юзер в базе?
                // (Для MVP пока просто создаем нового или возвращаем фейк)
                // В реальности тут будет поиск в БД

                val token = "fake_jwt_token_${System.currentTimeMillis()}"
                val userId = "user_${request.phone}"

                val response = AuthResponse(
                    token = token,
                    userId = userId,
                    isNewUser = true // Допустим, он новый
                )

                call.respond(response)
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Неверный код")
            }
        }

    }
}