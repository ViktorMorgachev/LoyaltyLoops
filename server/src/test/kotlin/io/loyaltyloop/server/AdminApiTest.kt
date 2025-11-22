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
import io.loyaltyloop.shared.models.CreatePartnerRequest
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
        val client = createJsonClient()
        val partnerRepo = PartnerRepository()
        val userRepo = UserRepository()

        // 1. СЦЕНАРИЙ ПАРТНЕРА
        val partnerUser = client.registerAndLogin(phone = "+996555111111")

        // ИСПОЛЬЗУЕМ НОВЫЙ ХЕЛПЕР (В одну строку!)
        val partnerId = client.createPartner(
            token = partnerUser.accessToken,
            ownerId = partnerUser.userId,
            repo = partnerRepo,
            name = "Test Cafe"
        )

        // Проверка статуса
        val partnerBefore = partnerRepo.getPartnerById(partnerId)!!
        assertEquals(PartnerStatus.PENDING, partnerBefore.status)

        // 2. СЦЕНАРИЙ АДМИНА
        val adminUser = client.registerAsAdmin(userRepo)

        val approveRes = client.post("/admin/partners/$partnerId/status") {
            header("Authorization", "Bearer ${adminUser.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(ChangePartnerStatusRequest(PartnerStatus.ACTIVE))
        }
        assertEquals(HttpStatusCode.OK, approveRes.status)

        // 3. ФИНАЛ
        val partnerAfter = partnerRepo.getPartnerById(partnerId)!!
        assertEquals(PartnerStatus.ACTIVE, partnerAfter.status)
    }

    //Этот тест проверит, что "Магия 1-к-1" (Точка + Настройки) работает через API.

    @Test
    fun testCreatePointTriggersSettingsCreation() = testApplication {
        configureTestEnv()
        val client = createJsonClient()

        val partnerRepo = PartnerRepository()
        val userRepo = UserRepository()

        // 1. Создаем Владельца и Бизнес (через хелперы)
        val ownerAuth = client.registerAndLogin()
        val partnerId = client.createPartner(
            token = ownerAuth.accessToken,
            ownerId = ownerAuth.userId,
            repo = partnerRepo
        )

        // 2. Создаем Точку (через API)
        // Важно: Бизнес еще в статусе PENDING, но создавать точки должно быть можно!
        client.createTradingPoint(
            token = ownerAuth.accessToken,
            name = "Coffee on Main St",
            type = TradingPointType.COFFEE_SHOP
        )

        // 3. ПРОВЕРКИ В БД

        // А. Находим точку
        val points = partnerRepo.getPointsByPartnerId(partnerId)
        assertEquals(1, points.size)
        val createdPoint = points.first()

        assertEquals("Coffee on Main St", createdPoint.name)
        assertTrue(createdPoint.inviteCode?.isNotEmpty() == true, "Инвайт-код должен сгенерироваться")

        // Б. Проверяем Настройки Лояльности (самое важное!)
        val settings = partnerRepo.getSettingsByPointId(createdPoint.id)

        assertNotNull(settings, "Настройки должны создаваться автоматически вместе с точкой")
        assertEquals(createdPoint.id, settings.tradingPointId)
        assertEquals(LoyaltyProgramType.TIERED_LTV, settings.programType, "Дефолтная стратегия должна быть TIERED")

        // В. Проверяем Уровни
        assertEquals(3, settings.tiers.size, "Должно быть 3 уровня")
        assertEquals("Start", settings.tiers[0].name)
    }
}