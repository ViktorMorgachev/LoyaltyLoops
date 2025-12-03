package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.CalculateTransactionRequest
import io.loyaltyloop.shared.models.JoinTradingPointRequest
import io.loyaltyloop.shared.models.ProcessTransactionRequest
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionResult
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.app.data.network.safeNetworkCall
import io.loyaltyloop.shared.models.NetworkResult
import io.loyaltyloop.shared.models.PublicConfigResponse
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointSearchResponse
import io.loyaltyloop.shared.models.TradingPointType

class MapRepository(private val client: HttpClient) {


    suspend fun searchPublicPoints(
        lat: Double,
        lon: Double,
        radius: Int,
        query: String = "",
        type: TradingPointType? = null,
        openNow: Boolean = false
    ): NetworkResult<TradingPointSearchResponse> {
        return safeNetworkCall {
            client.get("/map/points/search") {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("radius", radius)
                parameter("limit", 50)
                if (query.isNotBlank()) parameter("query", query)
                if (type != null) parameter("type", type.name)
                if (openNow) parameter("openNow", true)
            }
        }
    }
}
