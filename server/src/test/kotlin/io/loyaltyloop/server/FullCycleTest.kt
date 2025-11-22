package io.loyaltyloop.server

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FullCycleTest {

    @Test
    fun testCompleteLoyaltyLoop() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val partnerRepo = PartnerRepository()
        val userRepo = UserRepository()

        // --- АКТ 1: ВЛАДЕЛЕЦ ---
        // 1. Регистрируется Владелец
        val ownerAuth = client.registerAndLogin(phone = "+996555111111")
        
        // 2. Создает Бизнес
        client.post("/partners/create") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreatePartnerRequest("Best Coffee", "KG"))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        // 3. Создает Точку
        client.post("/partners/points") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreateTradingPointRequest("Center Point", TradingPointType.COFFEE_SHOP))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        // Получаем данные точки (нам нужен ID и InviteCode) через БД для теста
        val partnerId = partnerRepo.getPartnersByOwner(ownerAuth.userId).first().id
        val point = partnerRepo.getPointsByPartnerId(partnerId).first()
        val inviteCode = point.inviteCode!!
        val pointId = point.id

        // --- АКТ 2: КАССИР ---
        // 4. Регистрируется Кассир
        val cashierAuth = client.registerAndLogin(phone = "+996555222222")

        // 5. Вводит Инвайт
        client.post("/partners/join") {
            header("Authorization", "Bearer ${cashierAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(JoinTradingPointRequest(inviteCode))
        }.apply { assertEquals(HttpStatusCode.OK, status) }

        // --- АКТ 3: КЛИЕНТ ---
        // 6. Регистрируется Клиент
        val clientAuth = client.registerAndLogin(phone = "+996555333333")
        
        // Получаем его секрет (он пришел при логине)
        val qrSecret = clientAuth.qrSecret
        // Генерируем QR (пока простая строка, но с секретом в будущем)
        // Формат: loyalty_v1:USER_ID:TIMESTAMP
        val qrContent = "loyalty_v1:${clientAuth.userId}:${System.currentTimeMillis()}"

        // --- АКТ 4: СКАНИРОВАНИЕ ---
        // 7. Кассир сканирует QR клиента
        val scanRes = client.post("/terminal/scan") {
            header("Authorization", "Bearer ${cashierAuth.accessToken}") // <-- Токен Кассира!
            contentType(ContentType.Application.Json)
            setBody(ScanQrRequest(qrContent, tradingPointId = pointId)) // <-- ID Точки
        }
        
        assertEquals(HttpStatusCode.OK, scanRes.status)
        val scanResponse = scanRes.body<ScanQrResponse>()

        // --- ПРОВЕРКИ ---
        assertEquals(clientAuth.userId, scanResponse.userId)
        assertTrue(scanResponse.isNewCard, "Карта должна создаться впервые")
        assertEquals(1, scanResponse.tierLevel)
        
        // Проверяем, что в базе карта создалась
        val cards = userRepo.getUserCards(clientAuth.userId)
        assertEquals(1, cards.size)
        assertEquals("Best Coffee", cards[0].partnerName)
    }
}