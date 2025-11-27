package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.Currency
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.UpdateLoyaltySettingsRequest
import io.loyaltyloop.shared.models.UpdateTradingPointRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class TradingPointValidationTest {

    @Test
    fun `negative threshold is rejected`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()

        client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(io.loyaltyloop.shared.models.CreatePartnerRequest("Validation Biz", ownerPin = "1234"))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        client.createTradingPoint(ownerToken = owner.accessToken, currency = Currency.KGS)

        val point = client.get("/partners/points") {
            header("Authorization", "Bearer ${owner.accessToken}")
        }.body<List<TradingPointDto>>().first()

        val response = client.put("/partners/points/${point.id}") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateTradingPointRequest(
                    name = point.name,
                    type = point.type,
                    address = point.address ?: "Test address",
                    latitude = point.latitude ?: 0.0,
                    longitude = point.longitude ?: 0.0,
                    currency = point.currency,
                    settings = UpdateLoyaltySettingsRequest(
                        programType = LoyaltyProgramType.TIERED_LTV,
                        tiers = listOf(
                            LoyaltyTierDto(1, LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Base, "Base"), 0.0, 5.0),
                            LoyaltyTierDto(2, LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Silver, "Silver"), -100.0, 7.0),
                            LoyaltyTierDto(3, LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Gold, "Gold"), 5000.0, 9.0),
                        ),
                        visitsTarget = 10,
                        maxBurnPercentage = 100
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ApiMessage>()
        assertEquals(AppErrorCode.INVALID_TIER_VALUE, error.code)
    }

    @Test
    fun `negative cashback percent is rejected`() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val owner = client.registerAndLogin()

        client.post("/partners/create") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(io.loyaltyloop.shared.models.CreatePartnerRequest("Validation Biz 2", ownerPin = "1234"))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        client.createTradingPoint(ownerToken = owner.accessToken, currency = Currency.KGS)

        val point = client.get("/partners/points") {
            header("Authorization", "Bearer ${owner.accessToken}")
        }.body<List<TradingPointDto>>().first()

        val response = client.put("/partners/points/${point.id}") {
            header("Authorization", "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                UpdateTradingPointRequest(
                    name = point.name,
                    type = point.type,
                    address = point.address ?: "Test address",
                    latitude = point.latitude ?: 0.0,
                    longitude = point.longitude ?: 0.0,
                    currency = point.currency,
                    settings = UpdateLoyaltySettingsRequest(
                        programType = LoyaltyProgramType.TIERED_LTV,
                        tiers = listOf(
                            LoyaltyTierDto(1, LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Base, "Base"), 0.0, 5.0),
                            LoyaltyTierDto(2, LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Silver, "Silver"), 1000.0, -1.0),
                            LoyaltyTierDto(3, LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Gold, "Gold"), 5000.0, 9.0),
                        ),
                        visitsTarget = 10,
                        maxBurnPercentage = 100
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ApiMessage>()
        assertEquals(AppErrorCode.INVALID_TIER_VALUE, error.code)
    }
}

