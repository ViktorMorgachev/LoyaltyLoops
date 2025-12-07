package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class JoinPlatformAdminRequest(
    val inviteCode: String
)

enum class SubscriptionType {
    FIXED_TERM, // Фиксированный срок
    REV_SHARE   // % от оборота
}

enum class PlatformRequestType {
    ACTIVATE_POINT,      // Включение точки / Продление
    BLOCK_PARTNER,       // Блокировка партнера
    UNBLOCK_PARTNER      // Разблокировка
}

enum class PlatformRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class SubscriptionDuration {
    DAY_1,
    DAY_3,
    WEEK_2,
    MONTH_1,
    MONTH_3,
    MONTH_6,
    YEAR_1,
    YEAR_2,
    YEAR_3,
    YEAR_5
}

@Serializable
data class SubscriptionDto(
    val id: String,
    val partnerId: String,
    val pointId: String? = null,
    val type: SubscriptionType,
    val startDate: Long, // Timestamp
    val endDate: Long?,  // Nullable for indefinite REV_SHARE
    val amount: Double,  // Сумма оплаты
    val isTrial: Boolean,
    val isActive: Boolean
)

@Serializable
data class CreatePlatformRequest(
    val type: PlatformRequestType,
    val targetPointId: String, // Mandatory now
    
    // Payload fields (flat structure for simplicity)
    val amount: Double? = null,
    val duration: SubscriptionDuration? = null,
    val isTrial: Boolean = false,
    val blockReason: String? = null
)

@Serializable
data class PlatformRequestDto(
    val id: String,
    val type: PlatformRequestType,
    val status: PlatformRequestStatus,
    val requesterId: String, // Manager ID
    val requesterName: String?,
    val requesterPhone: String? = null, // Added
    val approverId: String?, // Super Manager / Owner ID
    val approverName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    
    val targetPartnerId: String,
    val targetPartnerName: String? = null,
    val targetPointId: String?,
    val targetPointName: String? = null,
    val currency: String? = null, // Added
    
    val amount: Double?,
    val duration: SubscriptionDuration?,
    val isTrial: Boolean,
    val blockReason: String?,
    val rejectReason: String?
)

@Serializable
data class RejectRequestDto(
    val reason: String
)
