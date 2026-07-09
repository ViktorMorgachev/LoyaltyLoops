package io.loyaltyloop.server

import io.ktor.server.testing.testApplication
import kotlin.test.Test

class TerminalFlowTest {

    @Test
    fun testRealCashierScan() = testApplication {
        configureTestEnv()

    }
}
