package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.loyaltyloop.app.data.network.safeApiCall
import io.loyaltyloop.shared.models.LoyaltyCardDto

class WalletRepository(private val client: HttpClient) {

    // Получить список карт
    suspend fun getMyCards(): Result<List<LoyaltyCardDto>> {
        return safeApiCall<List<LoyaltyCardDto>> {
            client.get("/client/cards")
        }
    }
}