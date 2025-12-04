package io.loyaltyloop.server.service.sms.verification

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class PreludeVerificationService(
    private val client: HttpClient,
    private val apiKey: String
) : VerificationService {

    private val logger = LoggerFactory.getLogger("Prelude")
    private val baseUrl = "https://api.prelude.so/v2"

    override suspend fun startVerification(phone: String): String {
        return try {
            val response: PreludeStartResponse = client.post("$baseUrl/verification") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(PreludeRequest(
                    target = PreludeTarget(type = "phone_number", value = phone)
                ))
            }.body()

            logger.info("🚀 Prelude started. ID: ${response.id}")
            response.id
        } catch (e: Exception) {
            logger.error("❌ Prelude Start Error", e)
            throw e
        }
    }

    override suspend fun checkCode(verificationId: String, code: String): Boolean {
        return try {
            val response: PreludeCheckResponse = client.post("$baseUrl/verification/$verificationId/check") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(PreludeCheckRequest(code = code))
            }.body()

            response.status == "success"
        } catch (e: Exception) {
            logger.error("❌ Prelude Check Error", e)
            false
        }
    }
}

@Serializable data class PreludeRequest(val target: PreludeTarget)
@Serializable data class PreludeTarget(val type: String, val value: String)
@Serializable data class PreludeStartResponse(val id: String, val status: String)
@Serializable data class PreludeCheckRequest(val code: String)
@Serializable data class PreludeCheckResponse(val status: String)