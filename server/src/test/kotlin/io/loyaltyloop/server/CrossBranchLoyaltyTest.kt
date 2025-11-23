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
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.TransactionRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.JoinTradingPointRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.ProcessTransactionRequest
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TradingPointType
import io.loyaltyloop.shared.models.TransactionResult
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.shared.models.UpdatePartnerRequest
import io.loyaltyloop.shared.utils.CryptoUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossBranchLoyaltyTest {

    @Test
    fun testCrossBranchLoyalty() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val partnerRepo = PartnerRepository()
        val transactionRepository = TransactionRepository()

        // 1. Владелец создает Бизнес "My Brand"
        val ownerAuth = client.registerAndLogin(phone = "+996555000001")
        val partnerId = client.createPartner(ownerAuth.accessToken, ownerAuth.userId, partnerRepo, "My Brand")

        // 2. Создаем Точку А: "Магазин" (TIERED)
        // (Первая точка создается автоматически при создании партнера, найдем её и обновим тип, если надо, 
        // но проще создать новые для чистоты)
        
        // Создаем Точку А явно с TIERED
        client.post("/partners/points") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreateTradingPointRequest(
                name = "Shop A",
                type = TradingPointType.RETAIL,
                address = "Street 1",
                programType = LoyaltyProgramType.TIERED_LTV,
                visitsTarget = 10,
            ))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        // Создаем Точку Б явно с VISITS
        client.post("/partners/points") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CreateTradingPointRequest(
                name = "Coffee B",
                type = TradingPointType.COFFEE_SHOP,
                address = "Street 2",
                programType = LoyaltyProgramType.VISIT_COUNTER,
                visitsTarget = 5
            ))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        // Получаем ID точек из базы
        val points = partnerRepo.getPointsByPartnerId(partnerId)
        val pointA = points.find { it.name == "Shop A" }!!
        val pointB = points.find { it.name == "Coffee B" }!!

        // 3. Нанимаем Кассира А (для Shop A)
        val cashierAAuth = client.registerAndLogin(phone = "+996555000002")

        client.post("/partners/join") {
            header("Authorization", "Bearer ${cashierAAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(JoinTradingPointRequest(pointA.inviteCode!!))
        }.apply { assertEquals(HttpStatusCode.OK, status) }

        // 4. Нанимаем Кассира Б (для Coffee B)
        val cashierBAuth = client.registerAndLogin(phone = "+996555000003")
        client.post("/partners/join") {
            header("Authorization", "Bearer ${cashierBAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(JoinTradingPointRequest(pointB.inviteCode!!))
        }.apply { assertEquals(HttpStatusCode.OK, status) }

        // 5. Клиент
        val customerAuth = client.registerAndLogin(phone = "+996555000004")
        val secret = customerAuth.qrSecret ?: throw IllegalStateException("No QR Secret")

        // Хелпер для генерации подписанного QR
        fun generateQr(): String {
            val timestamp = System.currentTimeMillis() / 1000
            val data = "${customerAuth.userId}:$timestamp"
            val signature = CryptoUtils.hmacSha256(secret, data)
            return "loyalty_v1:${customerAuth.userId}:$timestamp:$signature"
        }

        // --- СЦЕНАРИЙ 1: Покупка в Магазине А (TIERED) ---
        
        // Кассир А сканирует
        val scanResA = client.post("/terminal/scan") {
            header("Authorization", "Bearer ${cashierAAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ScanQrRequest(generateQr(), tradingPointId = pointA.id))
        }
        assertEquals(HttpStatusCode.OK, scanResA.status)
        val scanDataA = scanResA.body<ScanQrResponse>()
        assertTrue(scanDataA.isNewCard)
        assertEquals(LoyaltyProgramType.TIERED_LTV, scanDataA.programType)

        // Кассир А проводит транзакцию (Покупка на 1000 сом)
        val processResA = client.post("/terminal/process") {
            header("Authorization", "Bearer ${cashierAAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ProcessTransactionRequest(
                tradingPointId = pointA.id,
                cardId = scanDataA.cardId,
                purchaseAmount = 1000.0,
                strategy = TransactionStrategy.CHARGE
            ))
        }
        assertEquals(HttpStatusCode.OK, processResA.status)
        val resultA = processResA.body<TransactionResult>()
        
        // Проверяем: 1000 * 0.03 (база) = 30 сом кешбэка
        assertEquals(30.0, resultA.newBalance)
        
        
        // --- СЦЕНАРИЙ 2: Визит в Кофейню Б (VISITS) ---
        
        // Кассир Б сканирует (ТОТ ЖЕ клиент)
        val scanResB = client.post("/terminal/scan") {
            header("Authorization", "Bearer ${cashierBAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ScanQrRequest(generateQr(), tradingPointId = pointB.id))
        }
        assertEquals(HttpStatusCode.OK, scanResB.status)
        val scanDataB = scanResB.body<ScanQrResponse>()
        
        // ВАЖНО: Карта должна быть ТА ЖЕ САМАЯ
        assertEquals(scanDataA.cardId, scanDataB.cardId, "Card ID should be same across branches")
        assertEquals(30.0, scanDataB.currentBalance, "Balance from Shop A should be visible")
        assertEquals(LoyaltyProgramType.VISIT_COUNTER, scanDataB.programType)

        // Кассир Б ставит штампик
        val processResB = client.post("/terminal/process") {
            header("Authorization", "Bearer ${cashierBAuth.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ProcessTransactionRequest(
                tradingPointId = pointB.id,
                cardId = scanDataB.cardId,
                purchaseAmount = 0.0, // Для визитов сумма не важна,
                strategy = TransactionStrategy.VISIT
            ))
        }
        assertEquals(HttpStatusCode.OK, processResB.status)
        val resultB = processResB.body<TransactionResult>()
        
        // Проверяем: Баланс не изменился, Визиты +1
        assertEquals(30.0, resultB.newBalance)
        assertEquals(1, resultB.newVisits)
        
        // Проверяем итоговое состояние в БД
        val finalCard = transactionRepository.getCardById(scanDataA.cardId)!!
        assertEquals(30.0, finalCard.balance)
        assertEquals(1, finalCard.visitsCount)
        assertEquals(1000.0, finalCard.totalSpent)

        // --- ПРОВЕРКА ИСТОРИИ ---
        // Владелец запрашивает историю
        val historyRes = client.get("/partners/history") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, historyRes.status)
        val history = historyRes.body<List<io.loyaltyloop.shared.models.TransactionHistoryDto>>()

        // Должно быть 2 транзакции (1 в Shop A, 1 в Coffee B)
        assertEquals(2, history.size)

        // Проверим сортировку (последняя сверху)
        // Сначала Coffee B (VISIT), потом Shop A (EARN)
        val lastTransaction = history[0]
        assertEquals("Coffee B", lastTransaction.pointName)
        assertEquals("VISIT", lastTransaction.type)
        assertEquals(1, lastTransaction.visitsDelta)

        val prevTransaction = history[1]
        assertEquals("Shop A", prevTransaction.pointName)
        assertEquals("EARN", prevTransaction.type)
        assertEquals(1000.0, prevTransaction.amount)
        assertEquals(30.0, prevTransaction.pointsDelta)
    }
}

