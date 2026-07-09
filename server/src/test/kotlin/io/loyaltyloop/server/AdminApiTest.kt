package io.loyaltyloop.server

import io.ktor.server.testing.testApplication
import kotlin.test.Test

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
