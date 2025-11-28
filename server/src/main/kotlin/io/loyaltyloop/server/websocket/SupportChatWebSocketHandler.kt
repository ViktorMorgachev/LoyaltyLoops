package io.loyaltyloop.server.websocket

import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.shared.models.UserRole

class SupportChatWebSocketHandler(
    private val tokenService: TokenService,
    private val partnerRepository: PartnerRepository,
    private val userRepository: UserRepository,
    private val supportChatService: SupportChatService
) {

    suspend fun handlePartner(call: ApplicationCall, session: DefaultWebSocketServerSession) {
        val userId = resolveUserId(call, session) ?: return
        val partner = runCatching { partnerRepository.getPartnerByUserId(userId) }.getOrNull()
        if (partner == null || partner.ownerId != userId) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Access denied"))
            return
        }

        supportChatService.registerPartnerSession(partner.id, session)
        try {
            drainIncoming(session)
        } finally {
            supportChatService.unregisterPartnerSession(partner.id, session)
        }
    }

    suspend fun handleAdmin(call: ApplicationCall, session: DefaultWebSocketServerSession) {
        val userId = resolveUserId(call, session) ?: return
        val workspaces = userRepository.getUserWorkspaces(userId)
        val isStaff = workspaces.any {
            it.role == UserRole.PLATFORM_SUPER_ADMIN || it.role == UserRole.PLATFORM_MANAGER
        }

        if (!isStaff) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Admins only"))
            return
        }

        supportChatService.registerAdminSession(userId, session)
        try {
            drainIncoming(session)
        } finally {
            supportChatService.unregisterAdminSession(userId, session)
        }
    }

    private suspend fun resolveUserId(call: ApplicationCall, session: DefaultWebSocketServerSession): String? {
        val token = call.request.queryParameters["token"]
        return when {
            token.isNullOrBlank() -> {
                session.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing token"))
                null
            }
            else -> {
                val userId = tokenService.validateAccessToken(token)
                if (userId.isNullOrBlank()) {
                    session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                    null
                } else {
                    userId
                }
            }
        }
    }

    private suspend fun drainIncoming(session: DefaultWebSocketServerSession) {
        for (@Suppress("UNUSED_VARIABLE") frame in session.incoming) {
            // Currently read-only stream, messages ignored
        }
    }
}


