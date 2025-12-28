package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class SupportThreadDto(
    val id: String,
    val partnerId: String,
    val partnerName: String,
    val lastMessageSnippet: String? = null,
    val lastMessageAt: Long? = null,
    val unreadForPartner: Int = 0,
    val unreadForAdmin: Int = 0,
    val createdAt: Long,
    val isClosed: Boolean = false
)

@Serializable
data class SupportMessageDto(
    val id: String,
    val threadId: String,
    val senderId: String,
    val senderRole: UserRole,
    val content: String,
    val createdAt: Long,
    val isFromPartner: Boolean
)

@Serializable
data class SupportThreadResponse(
    val thread: SupportThreadDto,
    val messages: List<SupportMessageDto>
)

@Serializable
data class SendSupportMessageRequest(
    val content: String
)

@Serializable
data class SupportChatEventDto(
    val type: SupportChatEventType,
    val thread: SupportThreadDto? = null,
    val message: SupportMessageDto? = null
)

@Serializable
enum class SupportChatEventType {
    THREAD_UPDATED,
    MESSAGE_CREATED
}

