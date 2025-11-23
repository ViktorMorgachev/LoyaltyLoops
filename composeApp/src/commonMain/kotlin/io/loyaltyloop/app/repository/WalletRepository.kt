package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.loyaltyloop.app.data.network.safeNetworkCall
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.NetworkResult

class WalletRepository(private val client: HttpClient) {

    // Получить список карт
    suspend fun getMyCards(): NetworkResult<List<LoyaltyCardDto>> {
        return safeNetworkCall {
            client.get("/client/cards")
        }
    }
}
