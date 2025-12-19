package io.loyaltyloop.app.services

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.firebase.installations.FirebaseInstallationsException
import com.google.firebase.messaging.FirebaseMessaging
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.shared.models.DevicePlatform
import io.loyaltyloop.shared.models.DeviceTokenContext
import io.loyaltyloop.shared.models.RegisterDeviceTokenRequest
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.jvm.Volatile

class AndroidPushService(
    private val appContext: Context,
    private val httpClient: HttpClient,
    private val sessionManager: SessionManager,
    private val tokenStorage: TokenStorage
) : PushService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var workspaceJob: Job? = null
    private var cachedToken: String? = null
    private var lastContext: DeviceTokenContext? = null

    @Volatile
    private var isRegistered = false

    override suspend fun register() {
        scope.launch {
            if (isRegistered) {
                ensureSyncForCurrentWorkspace()
                return@launch
            }
            try {
                NotificationChannels.ensureCreated(appContext)
                FirebaseMessaging.getInstance().isAutoInitEnabled = true
                val token = fetchFcmToken() ?: return@launch
                cachedToken = token
                syncTokenWithServer(
                    context = buildContext(token, sessionManager.currentWorkspace.value),
                    force = true
                )
                startWorkspaceListener()
                isRegistered = true
                Logger.d { "FCM registration complete" }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.e(error) { "Failed to register FCM" }
            }
        }

    }

    override suspend fun unregister()  {
        scope.launch {
            if (!isRegistered) return@launch
            try {
                workspaceJob?.cancel()
                workspaceJob = null
                removeTokenFromServer(lastContext)
                cachedToken = null
                FirebaseMessaging.getInstance().deleteToken().await()
                isRegistered = false
                Logger.d { "FCM unregistered" }
            } catch (t: CancellationException) {
            } catch (error: Exception) {
                Logger.e(error) { "Failed to unregister FCM" }
            }
        }

    }

    private fun buildContext(token: String, workspace: UserWorkspace?): DeviceTokenContext {
        val role = workspace?.role ?: UserRole.CLIENT
        val workspaceId = when (role) {
            UserRole.CLIENT -> null
            else -> workspace?.id
        }
        return DeviceTokenContext(
            token = token,
            platform = DevicePlatform.ANDROID,
            role = role,
            workspaceId = workspaceId
        )
    }

    private suspend fun syncTokenWithServer(
        context: DeviceTokenContext,
        force: Boolean
    ) {
        val token = cachedToken ?: return
        if (!force && context == lastContext) return
        if (tokenStorage.getAccessToken().isNullOrBlank()) {
            Logger.w { "Skip FCM sync: no auth token yet" }
            return
        }

        if (lastContext != null && lastContext != context) {
            removeTokenFromServer(lastContext)
        }

        runCatching {
            httpClient.post("/client/device-token") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterDeviceTokenRequest(
                        token = token,
                        platform = context.platform,
                        role = context.role,
                        workspaceId = context.workspaceId
                    )
                )
            }
            lastContext = context
            Logger.d { "Synced FCM token for ${context.role} (${context.workspaceId ?: "client"})" }
        }.onFailure {
            Logger.e(it) { "Failed to sync FCM token" }
        }
    }

    private suspend fun removeTokenFromServer(context: DeviceTokenContext?) {
        val target = context ?: return
        runCatching {
            httpClient.delete("/client/device-token") {
                contentType(ContentType.Application.Json)
                setBody(target)
            }
            Logger.d { "Removed FCM token for ${target.role} (${target.workspaceId ?: "client"})" }
        }.onFailure {
            Logger.e(it) { "Failed to remove FCM token" }
        }
        if (target == lastContext) {
            lastContext = null
        }
    }

    private suspend fun fetchFcmToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val reason = when (error) {
                is FirebaseInstallationsException -> "Firebase Installations unavailable"
                else -> error.cause?.message ?: error.message ?: "unknown"
            }
            Logger.w(error) { "FCM token unavailable: $reason" }
            null
        }
    }

    private suspend fun ensureSyncForCurrentWorkspace() {
        var token = cachedToken

        var forceSync = false

        if (token == null) {
            token = fetchFcmToken()
            if (token == null) return
            cachedToken = token
            forceSync = true
        }
        val context = buildContext(token, sessionManager.currentWorkspace.value)
        syncTokenWithServer(context, force = forceSync)
    }

    private fun startWorkspaceListener() {
        if (workspaceJob?.isActive == true) return
        workspaceJob = scope.launch {
            sessionManager.currentWorkspace.collectLatest { workspace ->
                cachedToken?.let {
                    syncTokenWithServer(buildContext(it, workspace), force = false)
                }
            }
        }
    }
}

