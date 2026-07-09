package io.loyaltyloop.server

import io.ktor.server.testing.testApplication
import kotlin.test.Test

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

