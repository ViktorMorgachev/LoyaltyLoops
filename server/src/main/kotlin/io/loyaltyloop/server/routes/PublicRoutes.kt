package io.loyaltyloop.server.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.loyaltyloop.server.repository.WaitlistRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import kotlinx.serialization.Serializable

@Serializable
data class WaitlistRequest(val email: String)

fun Route.publicRoutes(waitlistRepository: WaitlistRepository) {
    route("/public") {
        post("/waitlist") {
            val request = call.receive<WaitlistRequest>()
            if (request.email.isBlank() || !request.email.contains("@")) {
                call.respond(HttpStatusCode.BadRequest, ApiMessage(AppErrorCode.INVALID_REQUEST, "Invalid email"))
                return@post
            }

            if (waitlistRepository.hasMail(request.email)) {
                 call.respond(HttpStatusCode.Conflict, ApiMessage(AppErrorCode.ALREADY_JOINED, "You are already in the waitlist"))
                 return@post
            }

            waitlistRepository.add(request.email)
            
            call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Added to waitlist"))
        }
    }
}

