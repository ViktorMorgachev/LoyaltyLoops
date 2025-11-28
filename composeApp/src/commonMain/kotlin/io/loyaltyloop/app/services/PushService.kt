package io.loyaltyloop.app.services

import kotlinx.coroutines.CoroutineScope

interface PushService {
    suspend fun register()
    suspend fun unregister()
}

class NoopPushService : PushService {
    override suspend fun register() = Unit
    override suspend fun unregister() = Unit
}

