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

    }

    @Test
    fun `negative cashback percent is rejected`() = testApplication {
        configureTestEnv()

    }
}

