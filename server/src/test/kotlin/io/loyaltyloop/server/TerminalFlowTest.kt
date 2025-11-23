package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.utils.CryptoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalFlowTest {

    @Test
    fun testRealCashierScan() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val partnerRepo = PartnerRepository()

        // 1. Создаем бизнес
        val ownerAuth = client.registerAndLogin()
        val partnerId = client.createPartner(ownerAuth.accessToken, ownerAuth.userId, partnerRepo)

        // 2. Создаем экосистему Кассира
        // ВАЖНО: Нам нужно вернуть не только токен, но и ID ТОЧКИ, где он работает
        // (Придется чуть доработать хелпер или достать ID тут)
        val cashierToken = client.createCashierEcosystem(ownerAuth.accessToken, ownerAuth.userId, partnerRepo)

        // Достаем ID точки, которую создал хелпер (мы знаем, что она одна у этого партнера)
        val pointId = partnerRepo.getPointsByPartnerId(partnerId).first().id

        // 3. Клиент
        val customerAuth = client.registerAndLogin()
        val timestamp = System.currentTimeMillis() / 1000
        val data = "${customerAuth.userId}:$timestamp"
        val signature = CryptoUtils.hmacSha256(customerAuth.qrSecret, data)
        val qrContent = "loyalty_v1:${customerAuth.userId}:$timestamp:$signature"

        // 4. СКАНИРОВАНИЕ (С КОНТЕКСТОМ)
        val scanRes = client.post("/terminal/scan") {
            header("Authorization", "Bearer $cashierToken")
            contentType(ContentType.Application.Json)
            // Передаем ID точки
            setBody(ScanQrRequest(qrContent, tradingPointId = pointId))
        }

        assertEquals(HttpStatusCode.OK, scanRes.status)
        val response = scanRes.body<ScanQrResponse>()
        
        // --- ПРОВЕРКИ ---
        assertEquals(customerAuth.userId, response.userId)
        assertTrue(response.isNewCard, "Карта должна создаться автоматически")
        
        // Проверяем, что карта привязалась к ПРАВИЛЬНОМУ партнеру (которого создал ownerAuth)
        // Для этого можно залезть в UserRepository и проверить карту
        val userRepo = UserRepository()
        val cards = userRepo.getUserCards(customerAuth.userId)
        assertEquals(1, cards.size)
        assertEquals("Test Cafe", cards[0].partnerName)

        val scanRes2 = client.post("/terminal/scan") {
            header("Authorization", "Bearer $cashierToken")
            contentType(ContentType.Application.Json)
            // Передаем ID точки
            setBody(ScanQrRequest(qrContent, tradingPointId = pointId))
        }
        val response2 = scanRes2.body<ScanQrResponse>()
        assertTrue(!response2.isNewCard, "При повторном сканировании карта должна быть старой")
    }
}