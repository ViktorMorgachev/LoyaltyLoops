package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardTest {

    @Test
    fun testCardCreationOnScan() = testApplication {
        configureTestEnv()
        val client = createJsonClient()

        // 1. Регистрируем КЛИЕНТА
        val clientAuth = client.registerAndLogin()
        val userId = clientAuth.userId
        
        // Генерируем QR клиента (пока простая строка, как договаривались)
        // В будущем тут будет HMAC подпись
        val timestamp = System.currentTimeMillis()
        val qrCode = "loyalty_v1:$userId:$timestamp"

        // 2. Логинимся как КАССИР (пока используем обычный логин, 
        // так как мы еще не реализовали flow приглашения сотрудников, 
        // но для теста сканирования роль пока не проверяется жестко в MVP коде)
        val cashierAuth = client.registerAndLogin(phone = generateValidPhone())
        val cashierToken = cashierAuth.accessToken

        // 3. Кассир сканирует QR
        val scanRes = client.post("/terminal/scan") {
            header("Authorization", "Bearer $cashierToken")
            contentType(ContentType.Application.Json)
            setBody(ScanQrRequest(qrContent = qrCode))
        }

        assertEquals(HttpStatusCode.OK, scanRes.status)
        
        val response = scanRes.body<ScanQrResponse>()
        
        // 4. Проверки
        assertEquals(userId, response.userId)
        assertTrue(response.isNewCard, "Карта должна быть новой при первом сканировании")
        assertEquals(1, response.tierLevel, "Начальный уровень должен быть 1")
    }
}