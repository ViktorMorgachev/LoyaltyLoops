package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.UserDto

fun Route.authRoutes(repository: UserRepository) {

    route("/auth") {

        // POST: http://localhost:8080/auth/register
        // Тело: JSON с данными пользователя
        post("/register") {
            try {
                val user = call.receive<UserDto>() // Ktor сам превратит JSON в объект
                repository.createUser(user)
                call.respond(HttpStatusCode.Created, "User created!")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error: ${e.localizedMessage}")
            }
        }

        // GET: http://localhost:8080/auth/users
        // Получить список всех
        get("/users") {
            val users = repository.getAllUsers()
            call.respond(users)
        }
    }
}