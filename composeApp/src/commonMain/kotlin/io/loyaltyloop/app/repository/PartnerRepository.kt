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
import io.loyaltyloop.shared.models.CashierDailyStatsDto
import io.loyaltyloop.shared.models.NetworkResult

import io.loyaltyloop.shared.models.CreateClientRatingDto
import io.loyaltyloop.shared.models.CreateServiceReviewDto
import io.loyaltyloop.shared.models.TrustScoreDto

class PartnerRepository(private val client: HttpClient) {

    suspend fun rateClient(dto: CreateClientRatingDto): NetworkResult<TrustScoreDto> {
        return safeNetworkCall {
             client.post("/terminal/rate-client") {
                 contentType(ContentType.Application.Json)
                 setBody(dto)
             }
        }
    }

    suspend fun rateService(dto: CreateServiceReviewDto): NetworkResult<ApiMessage> {
        return safeNetworkCall {
            client.post("/client/rate-service") {
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
        }
    }

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
        cardId: String,
        amount: Double,
        strategy: TransactionStrategy
    ): NetworkResult<TransactionCalculationDto> {
        return safeNetworkCall {
            client.post("/terminal/calculate") {
                contentType(ContentType.Application.Json)
                setBody(CalculateTransactionRequest(
                    cardId = cardId,
                    purchaseAmount = amount,
                    strategy = strategy
                ))
            }
        }
    }

    suspend fun processTransaction(
        cardId: String, 
        amount: Double?, 
        strategy: TransactionStrategy
    ): NetworkResult<TransactionResult> {
        return safeNetworkCall {
            client.post("/terminal/process") {
                contentType(ContentType.Application.Json)
                setBody(ProcessTransactionRequest(
                    cardId = cardId, 
                    purchaseAmount = amount ?: 0.0, 
                    strategy = strategy
                ))
            }
        }
    }

    suspend fun getCashierStats(): NetworkResult<CashierDailyStatsDto> {
        return safeNetworkCall {
            client.get("/terminal/stats")
        }
    }
}
