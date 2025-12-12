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


@Suppress("CyclomaticComplexMethod", "ThrowsCount")
fun Route.testSupportRoutes(
    userRepository: UserRepository,
    platformRepository: PlatformRepository,
    transactionRepository: TransactionRepository,
    cardRealtimeService: CardRealtimeService,
    loyaltyEngineService: LoyaltyEngineService,
) {
    val logger = LoggerFactory.getLogger("TestSupportRoutes")

    route("/test-support") {
        // Unprotected seed endpoint removed due to compilation issues.
        // Data will be generated via app usage.

        authenticate("auth-jwt") {
            /**
             * Продвигает выбранного пользователя до роли PLATFORM_SUPER_ADMIN.
             * Маршрут предназначен для интеграционных тестов и окружений QA.
             */
            post("/promote-super-admin") {
                ensureSuperAdmin(call, platformRepository, userRepository)
                val request = call.receive<PromoteSuperAdminRequest>()

                val target = userRepository.getUserById(request.userId)
                    ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "User not found")


                if (!platformRepository.hasPlatformRole(target.id, request.role)) {
                    platformRepository.createSystemStaff(
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
                ensureSuperAdmin(call, platformRepository, userRepository)
                val userId = call.parameters["userId"] ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "userId missing")
                val deleted = userRepository.deleteUser(userId)
                if (!deleted) {
                    throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "User not found")
                }
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "User $userId deleted"))
            }

            post("/cards/mutate") {
                ensureSuperAdmin(call, platformRepository, userRepository)
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
                ensureSuperAdmin(call, platformRepository, userRepository)
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
                ensureSuperAdmin(call, platformRepository, userRepository)
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

            post("/cards/social") {
                ensureSuperAdmin(call, platformRepository, userRepository)
                val request = call.receive<UpdateCardSocialRequest>()
                
                transactionRepository.updateTrustScore(request.cardId, request.trustScore, request.fraudFlag)
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS))
            }

            post("/subscription-check") {
                ensureSuperAdmin(call, platformRepository, userRepository)
                loyaltyEngineService.runSubscriptionCheck()
                call.respond(HttpStatusCode.OK, ApiMessage(AppErrorCode.SUCCESS, "Subscription check triggered"))
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

