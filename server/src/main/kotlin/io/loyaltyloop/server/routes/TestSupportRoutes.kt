package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.UserRole
import kotlinx.serialization.Serializable

fun Route.testSupportRoutes(
    userRepository: UserRepository
) {
    route("/test-support") {

        /**
         * Продвигает выбранного пользователя до роли PLATFORM_SUPER_ADMIN.
         * Маршрут предназначен для интеграционных тестов и окружений QA.
         */
        post("/promote-super-admin") {
            val request = call.receive<PromoteSuperAdminRequest>()

            val user = userRepository.getUserById(request.userId)
                ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "User not found")

            userRepository.setSuperAdmin(user.id, true)
            userRepository.createSystemStaff(
                userId = user.id,
                role = UserRole.PLATFORM_SUPER_ADMIN,
                defaultPinHash = request.pin ?: "0000"
            )

            call.respond(
                HttpStatusCode.OK,
                ApiMessage(AppErrorCode.SUCCESS, "User ${user.phoneNumber} promoted to super admin")
            )
        }
    }
}

@Serializable
data class PromoteSuperAdminRequest(
    val userId: String,
    val pin: String? = null
)

