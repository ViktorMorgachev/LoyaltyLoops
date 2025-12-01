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
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointSearchResponse
import io.loyaltyloop.shared.models.TradingPointType

class PartnerRepository(private val client: HttpClient) {


    // Присоединиться к компании по инвайт-коду
    suspend fun joinCompany(inviteCode: String): NetworkResult<ApiMessage> {
        return safeNetworkCall {
            client.post("/partners/join") {
                contentType(ContentType.Application.Json)
                setBody(JoinTradingPointRequest(inviteCode))
            }
        }
    }

    suspend fun scanQr(request: ScanQrRequest): NetworkResult<ScanQrResponse> {
        return safeNetworkCall {
            client.post("/terminal/scan") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    suspend fun calculateTransaction(
        tradingPointId: String,
        cardId: String,
        amount: Double,
        strategy: TransactionStrategy
    ): NetworkResult<TransactionCalculationDto> {
        return safeNetworkCall {
            client.post("/terminal/calculate") {
                contentType(ContentType.Application.Json)
                setBody(CalculateTransactionRequest(
                    tradingPointId = tradingPointId,
                    cardId = cardId,
                    purchaseAmount = amount,
                    strategy = strategy
                ))
            }
        }
    }

    suspend fun processTransaction(
        tradingPointId: String, 
        cardId: String, 
        amount: Double?, 
        strategy: TransactionStrategy
    ): NetworkResult<TransactionResult> {
        return safeNetworkCall {
            client.post("/terminal/process") {
                contentType(ContentType.Application.Json)
                setBody(ProcessTransactionRequest(
                    tradingPointId = tradingPointId, 
                    cardId = cardId, 
                    purchaseAmount = amount ?: 0.0, 
                    strategy = strategy
                ))
            }
        }
    }

    // 1. Поиск публичных точек (для карты)
    suspend fun searchPublicPoints(
        lat: Double,
        lon: Double,
        radius: Int,
        query: String = "",
        type: TradingPointType? = null,
        openNow: Boolean = false
    ): NetworkResult<TradingPointSearchResponse> {
        return safeNetworkCall {
            client.get("/public/points/search") {
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

    // 2. Получение "Моих" точек (для фильтра "Мои филиалы")
    suspend fun getPartnerPoints(): NetworkResult<List<TradingPointDto>> {
        return safeNetworkCall {
            client.get("/partners/points")
        }
    }
}
