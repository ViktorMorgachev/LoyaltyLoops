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
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CardBlockStatus
import io.loyaltyloop.shared.models.CardPauseStatus
import io.loyaltyloop.shared.models.CardRealtimeEventType
import io.loyaltyloop.shared.models.CardRealtimePayload
import io.loyaltyloop.shared.models.TransactionSuccessType
import io.loyaltyloop.shared.models.UserRole
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Suppress("CyclomaticComplexMethod", "ThrowsCount")
fun Route.testSupportRoutes(
    userRepository: UserRepository,
    transactionRepository: TransactionRepository,
    cardRealtimeService: CardRealtimeService
) {
    val logger = LoggerFactory.getLogger("TestSupportRoutes")

    route("/test-support") {
        authenticate("auth-jwt") {
            /**
             * Продвигает выбранного пользователя до роли PLATFORM_SUPER_ADMIN.
             * Маршрут предназначен для интеграционных тестов и окружений QA.
             */
            post("/promote-super-admin") {
                val currentUser = ensurePlatformUser(call, userRepository)
                val request = call.receive<PromoteSuperAdminRequest>()

                val target = userRepository.getUserById(request.userId)
                    ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "User not found")

                if (request.role == UserRole.PLATFORM_SUPER_ADMIN && !userRepository.isSuperAdmin(currentUser)) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN, "Only platform super admin can grant this role")
                }

                if (!userRepository.hasPlatformRole(target.id, request.role)) {
                    userRepository.createSystemStaff(
                        userId = target.id,
                        role = request.role,
                        defaultPinHash = request.pin ?: "0000"
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiMessage(AppErrorCode.SUCCESS, "Role ${request.role} granted to ${target.phoneNumber}")
                )
            }

            delete("/users/{userId}") {
                ensureSuperAdmin(call, userRepository)
                val userId = call.parameters["userId"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "userId missing")
                val deleted = userRepository.deleteUser(userId)
                if (!deleted) {
                    throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "User not found")
                }
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "User $userId deleted"))
            }

            post("/cards/mutate") {
                ensureSuperAdmin(call, userRepository)
                val request = call.receive<CardMutationRequest>()
                val sanitizedBlock = request.block?.let { block ->
                    block.copy(reason = block.reason?.takeIf { it.isNotBlank() })
                }
                val sanitizedPause = request.pause?.let { pause ->
                    pause.copy(reason = pause.reason?.takeIf { it.isNotBlank() })
                }
                val card = transactionRepository.mutateCard(
                    cardId = request.cardId,
                    balance = request.balance,
                    visits = request.visits,
                    tierLevel = request.tierLevel,
                    totalSpent = request.totalSpent,
                    block = sanitizedBlock,
                    blockUpdate = request.blockUpdate,
                    pause = sanitizedPause,
                    pauseUpdate = request.pauseUpdate
                ) ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")

                val enriched = userRepository.getUserCards(card.userId).firstOrNull { it.id == card.id } ?: card

                cardRealtimeService.notifyUser(
                    enriched.userId,
                    CardRealtimePayload(
                        eventType = CardRealtimeEventType.CARD_UPDATED,
                        cardId = enriched.id,
                        cardSnapshot = enriched,
                        newBalance = enriched.balance,
                        newVisits = enriched.visitsCount
                    )
                )

                call.respond(HttpStatusCode.OK, enriched)
            }

            delete("/cards/{cardId}") {
                ensureSuperAdmin(call, userRepository)
                val cardId = call.parameters["cardId"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "cardId missing")
                val existing = transactionRepository.getCardById(cardId)
                    ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")

                val enriched = userRepository.getUserCards(existing.userId).firstOrNull { it.id == cardId } ?: existing
                transactionRepository.deleteCard(cardId)
                cardRealtimeService.notifyUser(
                    enriched.userId,
                    CardRealtimePayload(
                        eventType = CardRealtimeEventType.CARD_DELETED,
                        cardId = enriched.id,
                        cardSnapshot = enriched
                    )
                )
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Card $cardId deleted"))
            }

            post("/cards/animation") {
                ensureSuperAdmin(call, userRepository)
                val request = call.receive<CardAnimationRequest>()
                val card = transactionRepository.getCardById(request.cardId)
                    ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")

                val payload = CardRealtimePayload(
                    cardId = card.id,
                    successType = request.successType,
                    args = request.args ?: emptyList(),
                    newBalance = request.newBalance ?: card.balance,
                    newVisits = request.newVisits ?: card.visitsCount
                )

                logger.info("TestLab -> sending realtime payload {}", payload)
                cardRealtimeService.notifyUser(card.userId, payload)

                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Realtime event delivered"))
            }
        }
    }
}

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
data class CardAnimationRequest(
    val cardId: String,
    val successType: TransactionSuccessType,
    val args: List<String>? = null,
    val newBalance: Double? = null,
    val newVisits: Int? = null
)

private suspend fun ensureSuperAdmin(call: io.ktor.server.application.ApplicationCall, userRepository: UserRepository): String {
    val userId = ensurePlatformUser(call, userRepository)
    if (!userRepository.hasPlatformRole(userId, UserRole.PLATFORM_SUPER_ADMIN)) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Test support endpoints require super admin role")
    }
    return userId
}

@Suppress("ThrowsCount")
private suspend fun ensurePlatformUser(call: io.ktor.server.application.ApplicationCall, userRepository: UserRepository): String {
    val principal = call.principal<JWTPrincipal>() ?: throw LoyaltyException(AppErrorCode.UNAUTHORIZED, "Missing auth")
    val userId = principal.payload.getClaim("id").asString()
    val userExists = userRepository.getUserById(userId) != null
    if (!userExists) {
        throw LoyaltyException(AppErrorCode.UNAUTHORIZED, "User not found")
    }
    if (!userRepository.hasAnyPlatformRole(userId)) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Platform staff role required")
    }
    return userId
}

