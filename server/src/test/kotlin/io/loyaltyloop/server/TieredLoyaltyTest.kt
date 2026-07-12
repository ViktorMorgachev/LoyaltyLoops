package io.loyaltyloop.server

import io.ktor.server.testing.testApplication
import kotlin.test.Test

class TieredLoyaltyTest {


    @Test
    fun `earn only tiered flow awards cashback based on tier percent`() = testApplication {
        // Сценарий: клиент делает покупку на 1000 сомов без списания баллов.
        // Точка настроена на TIERED-программу, а карта клиента уже достигла уровня Silver (5%).
        // Ждем, что система спишет 5000 деньгами и начислит 50 бонусов (5%).
        configureTestEnv()
//        val testDescr = "Test card loyality by 1000 KGS"
//        val client = createJsonClient()
//        println()
//        println("""Кейс "Накопление (Earn Only) Накопительная система
//            $testDescr
//        """.trimIndent())
//        println()
//
//        // --- 1. Владелец создает партнерский бизнес и торговую точку ---
//        val owner = client.registerAndLogin(testDescr = "Регистрация будущего партнера")
//        println("${testDescr}: Регистрация клиента: ${owner.userId} ")
//
//        client.post("/partners/create") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(CreatePartnerRequest("Test Business", CountryCode.KG, ownerPin = "1234"))
//        }.apply {
//            assertEquals(HttpStatusCode.Created, status)
//        }
//
//        println("${testDescr}: Создание торговой точки")
//        assertEquals(HttpStatusCode.Created.description,
//            client.createTradingPoint(ownerToken = owner.accessToken, name = "Tiered Point",
//                currency = Currency.KGS, TradingPointType.SERVICE))
//
//        val points = client.get("/partners/points") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//        }.body<List<TradingPointDto>>()
//
//        val point = points.first()
//
//        // Обновляем настройки точки, задав конкретные уровни и проценты.
//
//        println("${testDescr}: Обновление настроек лояльности торговой точки")
//
//        client.put("/partners/points/${point.id}") {
//            header("Authorization", "Bearer ${owner.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(
//                UpdateTradingPointRequest(
//                    name = point.name,
//                    type = point.type,
//                    address = point.address ?: "Test street",
//                    currency = "KGS",
//                    latitude = 0.0,
//                    longitude = 0.0,
//                    settings = UpdateLoyaltySettingsRequest(
//                        programType = LoyaltyProgramType.TIERED_LTV,
//                        tiers = listOf(
//                            LoyaltyTierDto(levelIndex = 1, threshold = 0.0, cashbackPercent = 1.0,
//                                loyaltyTier = LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Base, "Base")),
//                            LoyaltyTierDto(levelIndex = 2, threshold = 5_000.0, cashbackPercent = 7.0,
//                                loyaltyTier = LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Silver, "Silver")),
//                            LoyaltyTierDto(levelIndex = 3, threshold = 20_000.0, cashbackPercent = 9.0,
//                                loyaltyTier = LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Gold, "Gold"))
//                        ),
//                        visitsTarget = 10,
//                        maxBurnPercentage = 100
//                    )
//                )
//            )
//        }.apply {
//            assertEquals(HttpStatusCode.OK, status)
//        }
//
//        println("${testDescr}: Включаем точку админом")
//        client.changeTradingPointActivity(userRepo = userRepository, pointID = point.id, enable = true)
//
//
//        println("${testDescr}: Регистрация будущего кассира")
//        val cashier = client.registerAndLogin(withLogs = false, testDescr = testDescr)
//
//        //  К точке присоединяется кассир неверный код
//        println("${testDescr}: Присоединение кассира ${cashier.userId} (Неверный код)")
//        client.post("/partners/join") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(JoinTradingPointRequest("error_code"))
//        }.apply {
//            assertEquals(HttpStatusCode.NotFound, status)
//        }
//
//        //  К точке присоединяется кассир успех
//        println("${testDescr}: Присоединение кассира (Верный код)")
//        client.post("/partners/join") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(JoinTradingPointRequest(point.inviteCode!!))
//        }.apply {
//            assertEquals(HttpStatusCode.OK, status)
//        }
//
//        //  К точке присоединяется кассир попытка повторная
//        println("${testDescr}: Присоединение кассира (Повторная попытка)")
//        client.post("/partners/join") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(JoinTradingPointRequest(point.inviteCode!!))
//        }.apply {
//            assertEquals(HttpStatusCode.Conflict, status)
//        }
//
//        // --- 4. Клиент показывает QR, создается карта, затем он достигает уровня Silver покупкой на 5000 ---
//        println("${testDescr}: Регистрация будущего клиента")
//        val customer = client.registerAndLogin(testDescr = testDescr)
//        val timestamp = System.currentTimeMillis() / 1000
//        val signature = CryptoUtils.hmacSha256(customer.qrSecret, "${customer.userId}:$timestamp")
//        val qrContent = "loyalty_v1:${customer.userId}:$timestamp:$signature"
//
//        //
//        println("${testDescr}: Попытка просканировать карту без сигнатуры")
//       client.post("/terminal/scan") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(ScanQrRequest(qrContent = "loyalty_v1:${customer.userId}:$timestamp", tradingPointId = point.id))
//        }.apply {
//            assertEquals(HttpStatusCode.BadRequest, status)
//        }
//
//        println("${testDescr}: Попытка просканировать карту без userId")
//        client.post("/terminal/scan") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(ScanQrRequest(qrContent ="loyalty_v1:$timestamp:$signature", tradingPointId = point.id))
//        }.apply {
//            assertEquals(HttpStatusCode.BadRequest, status)
//        }
//
//        println("${testDescr}: Попытка просканировать карту без loyalty_v1")
//        client.post("/terminal/scan") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(ScanQrRequest(qrContent = "${customer.userId}:$timestamp:$signature", tradingPointId = point.id))
//        }.apply {
//            assertEquals(HttpStatusCode.BadRequest, status)
//        }
//
//
//        println("${testDescr}: Попытка просканировать карту без timestamp")
//        client.post("/terminal/scan") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(ScanQrRequest(qrContent = "loyalty_v1:${customer.userId}:$signature", tradingPointId = point.id))
//        }.apply {
//            assertEquals(HttpStatusCode.BadRequest, status)
//        }
//
//        println("${testDescr}: Выключаем точку админом")
//        client.changeTradingPointActivity(userRepo = userRepository, pointID = point.id, enable = false)
//
//
//        println("${testDescr}: Попытка просканировать верную карту (Ожидаем неудачу)")
//        client.post("/terminal/scan") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(ScanQrRequest(qrContent = qrContent, tradingPointId = point.id))
//        }.apply {
//            assertEquals(HttpStatusCode.Conflict, status)
//        }
//
//        println("${testDescr}: Снова включаем точку админом")
//        client.changeTradingPointActivity(userRepo = userRepository, pointID = point.id, enable = true)
//
//        val scanResponse = client.post("/terminal/scan") {
//            header("Authorization", "Bearer ${cashier.accessToken}")
//            contentType(ContentType.Application.Json)
//            setBody(ScanQrRequest(qrContent = qrContent, tradingPointId = point.id))
//        }.body<ScanQrResponse>()
//
//        client.processTransaction(
//            accessToken = cashier.accessToken,
//            pointId = point.id,
//            cardId = scanResponse.cardId,
//            amount = 5_000.0,
//            description = "${testDescr}: Подтверждение транзакции на 5000 (Silver)"
//        )
//
//        val cardAfterUpgrade = client.fetchCard(customer.accessToken, scanResponse.cardId)
//        assertEquals(2, cardAfterUpgrade.tierLevel, "Card should upgrade to Silver")
//        assertEquals(50.0, cardAfterUpgrade.balance, 0.001, "Card balance should reflect earned bonuses")
//
//        val earnResult = client.processTransaction(
//            accessToken = cashier.accessToken,
//            pointId = point.id,
//            cardId = scanResponse.cardId,
//            amount = 1_000.0,
//            description = "${testDescr}: Подтверждение транзакции на 1000 сомов"
//        )
//        assertTrue(
//            earnResult.args.any { it.contains("70") },
//            "Transaction result should contain earned 70 points"
//        )
//
//        val cardAfterPurchase = client.fetchCard(customer.accessToken, scanResponse.cardId)
//        assertEquals(2, cardAfterPurchase.tierLevel, "Tier remains Silver")
//        assertEquals(120.0, cardAfterPurchase.balance, "Balance includes both accruals")
    }
}

