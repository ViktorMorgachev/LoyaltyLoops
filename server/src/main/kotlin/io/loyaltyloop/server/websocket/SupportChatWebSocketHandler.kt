package io.loyaltyloop.server.websocket

import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.PartnerStaffRepository
import io.loyaltyloop.server.repository.SystemStaffRepository
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.shared.models.UserRole
import kotlinx.coroutines.channels.ClosedReceiveChannelException

class SupportChatWebSocketHandler(
    private val tokenService: TokenService,
    private val partnerRepository: PartnerRepository,
    private val partnerStaffRepository: PartnerStaffRepository,
    private val systemStaffRepository: SystemStaffRepository,
    private val supportChatService: SupportChatService
) {

    /**
     * Подключение ПАРТНЕРА (Владельца или Менеджера).
     * Требует параметр ?partnerId=... в URL, чтобы знать контекст.
     */
    suspend fun handlePartner(call: ApplicationCall, session: DefaultWebSocketServerSession) {
        val userId = resolveUserId(call, session) ?: return
        val partnerIdParam = call.request.queryParameters["partnerId"]

        // 1. Определяем Target Partner ID
        val targetPartnerId = if (!partnerIdParam.isNullOrBlank()) {
            // Проверяем права на этот конкретный бизнес
            val isOwner = partnerStaffRepository.isPartnerOwner(userId, partnerIdParam)
            val isManager = partnerStaffRepository.isHasManagerOfPartner(userId, partnerIdParam)

            if (!isOwner && !isManager) {
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Access denied to partner"))
                return
            }
            partnerIdParam
        } else {
            // Fallback: Берем первый доступный бизнес
            val partners = partnerRepository.getPartnersOwnedByUser(userId)
            if (partners.isEmpty()) {
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No partners found"))
                return
            }
            partners.first().id
        }

        // 2. Регистрируем сессию на ID БИЗНЕСА (не владельца!)
        supportChatService.registerPartnerSession(targetPartnerId, session)

        try {
            drainIncoming(session)
        } catch (_: ClosedReceiveChannelException) {
            // Штатное закрытие соединения клиентом
        } finally {
            supportChatService.unregisterPartnerSession(targetPartnerId, session)
        }
    }

    /**
     * Подключение АДМИНИСТРАТОРА ПЛАТФОРМЫ.
     */
    suspend fun handleAdmin(call: ApplicationCall, session: DefaultWebSocketServerSession) {
        val userId = resolveUserId(call, session) ?: return

        // [FIX] Проверяем роль через репозиторий
        val role = systemStaffRepository.getSystemRole(userId)
        val isAdmin = role == UserRole.PLATFORM_SUPER_ADMIN ||
                role == UserRole.PLATFORM_SUPER_MANAGER ||
                role == UserRole.PLATFORM_MANAGER

        if (!isAdmin) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Admins only"))
            return
        }

        // Админы слушают общий канал
        supportChatService.registerAdminSession(userId, session)
        try {
            drainIncoming(session)
        } catch (_: ClosedReceiveChannelException) {
            // Штатное закрытие соединения клиентом
        } finally {
            supportChatService.unregisterAdminSession(userId, session)
        }
    }

    // --- HELPERS ---

    private suspend fun resolveUserId(call: ApplicationCall, session: DefaultWebSocketServerSession): String? {
        val token = call.request.queryParameters["token"]
        if (token.isNullOrBlank()) {
            session.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing token"))
            return null
        }
        val userId = tokenService.validateAccessToken(token)
        if (userId.isNullOrBlank()) {
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return null
        }
        return userId
    }

    private suspend fun drainIncoming(session: DefaultWebSocketServerSession) {
        for (frame in session.incoming) {
            // Ignore incoming messages (Read-only stream)
        }
    }
}
