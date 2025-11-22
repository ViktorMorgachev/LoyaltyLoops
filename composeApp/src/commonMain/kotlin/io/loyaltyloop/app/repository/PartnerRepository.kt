package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.loyaltyloop.app.data.network.safeApiCall
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.JoinTradingPointRequest
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse

class PartnerRepository(private val client: HttpClient) {

    // Присоединиться к компании по инвайт-коду
    suspend fun joinCompany(inviteCode: String): Result<String> {
        return safeApiCall<ApiMessage> {
            client.post("/partners/join") {
                contentType(ContentType.Application.Json)
                setBody(JoinTradingPointRequest(inviteCode))
            }
        }.map { it.message }
    }

    suspend fun scanQr(request: ScanQrRequest): Result<ScanQrResponse> {
        return safeApiCall<ScanQrResponse> {
            client.post("/terminal/scan") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }
    
    // В будущем сюда добавим методы:
    // getMyTradingPoints()
    // createTradingPoint()
    // и т.д.
}