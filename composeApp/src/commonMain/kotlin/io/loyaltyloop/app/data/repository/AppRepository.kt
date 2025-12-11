package io.loyaltyloop.app.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.loyaltyloop.app.data.network.safeNetworkCall
import io.loyaltyloop.shared.models.AppVersionResponse
import io.loyaltyloop.shared.models.NetworkResult

class AppRepository(
    private val httpClient: HttpClient
) {
    suspend fun getAppVersion(platform: String = "android"): NetworkResult<AppVersionResponse> {
        return safeNetworkCall {
            httpClient.get("/app/version") {
                parameter("platform", platform)
            }
        }
    }
}

