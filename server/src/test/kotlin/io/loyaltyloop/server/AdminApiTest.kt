package io.loyaltyloop.server

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.ChangePartnerStatusRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.TradingPointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdminApiTest {

    @Test
    fun testAdminCanApprovePartner() = testApplication {
        configureTestEnv()

    }

    //Этот тест проверит, что "Магия 1-к-1" (Точка + Настройки) работает через API.

    @Test
    fun testCreatePointTriggersSettingsCreation() = testApplication {
        configureTestEnv()

    }
}