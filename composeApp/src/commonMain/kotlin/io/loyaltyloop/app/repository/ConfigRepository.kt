package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.loyaltyloop.app.data.network.safeNetworkCall
import io.loyaltyloop.shared.models.NetworkResult
import io.loyaltyloop.shared.models.PublicConfigResponse

class ConfigRepository(private val client: HttpClient) {

    suspend fun getPublicConfig(): NetworkResult<PublicConfigResponse> {
        return safeNetworkCall {
            client.get("/config")
        }
    }
}
