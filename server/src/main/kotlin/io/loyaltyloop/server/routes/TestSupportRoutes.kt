package io.loyaltyloop.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.loyaltyloop.server.repository.TransactionRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.CardRealtimeService
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.*
import io.loyaltyloop.server.service.LoyaltyEngineService
import org.slf4j.LoggerFactory
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.PlatformRepository
import io.loyaltyloop.server.repository.RatingRepository
import kotlinx.serialization.Serializable




@Serializable
data class PromoteSuperAdminRequest(
    val userId: String,
    val role: UserRole,
    val pin: String? = null
)

@Serializable
data class CardMutationRequest(
    val cardId: String,
    val balance: Double? = null,
    val visits: Int? = null,
    val tierLevel: Int? = null,
    val totalSpent: Double? = null,
    val block: CardBlockStatus? = null,
    val blockUpdate: Boolean = false,
    val pause: CardPauseStatus? = null,
    val pauseUpdate: Boolean = false
)

@Serializable
data class UpdateCardSocialRequest(
    val cardId: String,
    val trustScore: Double,
    val fraudFlag: Boolean
)

@Serializable
data class CardAnimationRequest(
    val cardId: String,
    val successType: TransactionSuccessType,
    val args: List<String>? = null,
    val newBalance: Double? = null,
    val newVisits: Int? = null
)

private suspend fun ensureSuperAdmin(call: io.ktor.server.application.ApplicationCall, platformRepository: PlatformRepository, userRepository: UserRepository): String {
    val userId = ensurePlatformUser(call, platformRepository, userRepository)
    if (!platformRepository.hasPlatformRole(userId, UserRole.PLATFORM_SUPER_ADMIN)) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Test support endpoints require super admin role")
    }
    return userId
}

@Suppress("ThrowsCount")
private suspend fun ensurePlatformUser(call: io.ktor.server.application.ApplicationCall, platformRepository: PlatformRepository, userRepository: UserRepository): String {
    val principal = call.principal<JWTPrincipal>() ?: throw LoyaltyException(AppErrorCode.UNAUTHORIZED, "Missing auth")
    val userId = principal.payload.getClaim("id").asString()
    val userExists = userRepository.getUserById(userId) != null
    if (!userExists) {
        throw LoyaltyException(AppErrorCode.UNAUTHORIZED, "User not found")
    }
    if (!platformRepository.hasAnyPlatformRole(userId)) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Platform staff role required")
    }
    return userId
}

