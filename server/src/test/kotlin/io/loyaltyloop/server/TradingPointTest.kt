package io.loyaltyloop.server

import io.loyaltyloop.server.database.DatabaseFactory
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.shared.models.LoyaltyProgramType
import kotlinx.coroutines.runBlocking
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TradingPointTest {

    @Before
    fun setup() {
        // Инициализируем H2 базу вручную
        DatabaseFactory.connect(
            driver = "org.h2.Driver",
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            user = "root",
            pass = ""
        )
    }

    @Test
    fun testCreatePointGeneratesDefaultSettings() {
        val partnerRepo = PartnerRepository()
        val userRepo = io.loyaltyloop.server.repository.UserRepository()

        val ownerId = "owner_1"
        val partnerId = "partner_1"
        val pointId = "point_center"

        runBlocking {
            // 1. Создаем Партнера
            userRepo.createUser(
                io.loyaltyloop.shared.models.UserDto(
                    id = ownerId,
                    phoneNumber = "+996555000000",
                    countryCode = "KG",
                    qrSecret = "test_secret",
                    firstName = null
                )
            )

            /// 2. Теперь создаем Партнера (ссылаясь на ownerId)
            partnerRepo.createPartner(partnerId, ownerId, "Test Business")

            // 3. Создаем Точку


            // 3. Проверяем
            val settings = partnerRepo.getSettingsByPointId(pointId)

            // 4. Утверждения
            assertNotNull(settings, "Настройки должны создаться")
            assertEquals(pointId, settings.tradingPointId)
            assertEquals(LoyaltyProgramType.TIERED_LTV, settings.programType)

            assertEquals(3, settings.tiers.size, "Должно быть 3 уровня по умолчанию")

            val startLevel = settings.tiers.find { it.levelIndex == 1 }
            assertEquals("Start", startLevel?.name)
            assertEquals(0.03, startLevel?.cashbackPercent) // Проверяем 3%
        }
    }
}