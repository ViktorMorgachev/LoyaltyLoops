package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenContext(
    val platform: DevicePlatform,
    val role: UserRole,
    val workspaceId: String? = null,
    val token: String,
)

@Serializable
data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: DevicePlatform,
    val role: UserRole,
    val workspaceId: String? = null
) {
    fun context(): DeviceTokenContext = DeviceTokenContext(platform, role, workspaceId, token)
}

@Serializable
data class DeleteDeviceTokenRequest(
    val token: String
)

@Serializable
enum class CardRealtimeEventType {
    TRANSACTION,
    CARD_CREATED,
    CARD_UPDATED,
    CARD_DELETED
}

@Serializable
data class CardRealtimePayload(
    val eventType: CardRealtimeEventType = CardRealtimeEventType.TRANSACTION,
    val cardId: String,
    val cardSnapshot: LoyaltyCardDto? = null,
    val successType: TransactionSuccessType? = null,
    val args: List<String> = emptyList(),
    val newBalance: Double? = null,
    val newVisits: Int? = null,
    val tradingPointId: String? = null
)

@Serializable
enum class DevicePlatform {
    ANDROID,
    IOS,
    WEB
}

