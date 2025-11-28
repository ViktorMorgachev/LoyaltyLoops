package io.loyaltyloop.app.services

import io.loyaltyloop.app.features.wallet.CardAnimationMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CardRealtimeService {
    val events: Flow<CardAnimationMessage>
    suspend fun connect(
        token: String,
        cardIds: List<String>,
        onClosed: (() -> Unit)? = null
    ): Boolean

    suspend fun disconnect()
}

class NoopCardRealtimeService : CardRealtimeService {
    override val events: Flow<CardAnimationMessage> = emptyFlow()
    override suspend fun connect(
        token: String,
        cardIds: List<String>,
        onClosed: (() -> Unit)?
    ): Boolean = false

    override suspend fun disconnect() = Unit
}

