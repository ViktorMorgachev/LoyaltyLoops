package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.TransactionRepository
import io.loyaltyloop.shared.models.*
import io.loyaltyloop.shared.utils.CryptoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridLoyaltyTest {

    @Test
    fun testHybridFlow() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val partnerRepo = PartnerRepository()
        val transactionRepo = TransactionRepository()

        // 1. Owner creates Partner
        val ownerAuth = client.registerAndLogin(phone = "+996555999888")
        val partnerId = client.createPartner(ownerAuth.accessToken, ownerAuth.userId, partnerRepo, "Hybrid Brand")

        // 2. Create Hybrid Point
        client.post("/partners/points") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreateTradingPointRequest(
                name = "Hybrid Spot",
                type = TradingPointType.COFFEE_SHOP,
                programType = LoyaltyProgramType.HYBRID,
                visitsTarget = 5,
                baseCashback = 0.10 // 10%
            ))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        val point = partnerRepo.getPointsByPartnerId(partnerId).first()
        partnerRepo.updatePointStatus(point.id, true) // Activate manually

        // 3. Hire Cashier
        val cashierAuth = client.registerAndLogin(phone = "+996555999777")
        client.post("/partners/join") {
            header("Authorization", "Bearer ${cashierAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(JoinTradingPointRequest(point.inviteCode!!))
        }.apply { assertEquals(HttpStatusCode.OK, status) }

        // 4. Client
        val clientAuth = client.registerAndLogin(phone = "+996555999666")
        val secret = clientAuth.qrSecret!!

        fun generateQr(): String {
            val timestamp = System.currentTimeMillis() / 1000
            val data = "${clientAuth.userId}:$timestamp"
            val signature = CryptoUtils.hmacSha256(secret, data)
            return "loyalty_v1:${clientAuth.userId}:$timestamp:$signature"
        }

        // --- SCENARIO: VISIT ---
        val scanRes1 = client.post("/terminal/scan") {
            header("Authorization", "Bearer ${cashierAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ScanQrRequest(generateQr(), tradingPointId = point.id))
        }
        assertEquals(HttpStatusCode.OK, scanRes1.status)
        val scanData1 = scanRes1.body<ScanQrResponse>()
        assertEquals(LoyaltyProgramType.HYBRID, scanData1.programType)

        val visitRes = client.post("/terminal/process") {
            header("Authorization", "Bearer ${cashierAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ProcessTransactionRequest(
                tradingPointId = point.id,
                cardId = scanData1.cardId,
                strategy = TransactionStrategy.VISIT,
                purchaseAmount = 0.0
            ))
        }
        assertEquals(HttpStatusCode.OK, visitRes.status)
        val res1 = visitRes.body<TransactionResult>()
        assertEquals(1, res1.newVisits)

        // --- SCENARIO: EARN (CHARGE) ---
        // Use cardId from first scan
        val earnRes = client.post("/terminal/process") {
            header("Authorization", "Bearer ${cashierAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ProcessTransactionRequest(
                tradingPointId = point.id,
                cardId = scanData1.cardId,
                purchaseAmount = 100.0,
                strategy = TransactionStrategy.CHARGE
            ))
        }
        assertEquals(HttpStatusCode.OK, earnRes.status)
        val res2 = earnRes.body<TransactionResult>()
        assertEquals(10.0, res2.newBalance) // 10% of 100 = 10.0 (Previous balance was 0)
        assertEquals(1, res2.newVisits) // Visits unchanged

        // --- SCENARIO: SPEND ---
        // Balance is 10.0
        // Purchase 50.0
        // Will spend all 10.0
        // Paid: 40.0
        // Earned: 40 * 0.10 = 4.0
        // Final Balance: 10 - 10 + 4 = 4.0
        val spendRes = client.post("/terminal/process") {
            header("Authorization", "Bearer ${cashierAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ProcessTransactionRequest(
                tradingPointId = point.id,
                cardId = scanData1.cardId,
                purchaseAmount = 50.0,
                strategy = TransactionStrategy.SPEND
            ))
        }
        assertEquals(HttpStatusCode.OK, spendRes.status)
        val res3 = spendRes.body<TransactionResult>()
        assertEquals(4.0, res3.newBalance) 
    }
}
