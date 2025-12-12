package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class LoyaltyRepository {

    // Получить полные настройки лояльности (включая уровни) по ID точки
    suspend fun getSettingsByPointId(pointId: String): LoyaltySettingsDto = dbQuery {
        // 1. Получаем строку настроек
        val settingsRow = LoyaltySettingsTable
            .selectAll()
            .where { LoyaltySettingsTable.tradingPointId eq pointId }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.LOYALTY_SETTING_NOT_FOUND, "Settings not found for point $pointId")

        val settingsId = settingsRow[LoyaltySettingsTable.id]
        val programType = LoyaltyProgramType.valueOf(settingsRow[LoyaltySettingsTable.programType])

        // 2. Получаем уровни (если программа уровневая, или просто чтобы были)
        val tiers = LoyaltyTiersTable
            .selectAll()
            .where { LoyaltyTiersTable.settingsId eq settingsId }
            .orderBy(LoyaltyTiersTable.levelIndex to SortOrder.ASC)
            .map { row ->
                val levelIndex = row[LoyaltyTiersTable.levelIndex]

                // Маппим индекс (1,2,3) в Enum (Base, Silver, Gold)
                val tierEnum = when (levelIndex) {
                    1 -> LoyaltyTierDto.LoyaltyLevel.Base
                    2 -> LoyaltyTierDto.LoyaltyLevel.Silver
                    else -> LoyaltyTierDto.LoyaltyLevel.Gold
                }

                LoyaltyTierDto(
                    levelIndex = levelIndex,
                    loyaltyTier = LoyaltyTierDto.LoyaltyTier(
                        level = tierEnum,
                        descr = row[LoyaltyTiersTable.name]
                    ),
                    threshold = row[LoyaltyTiersTable.threshold],
                    cashbackPercent = row[LoyaltyTiersTable.cashbackPercent]
                )
            }

        // 3. Собираем DTO
        LoyaltySettingsDto(
            settingsId = settingsId,
            tradingPointId = pointId,
            programType = programType,
            tiers = tiers,
            visitsTarget = settingsRow[LoyaltySettingsTable.visitsTarget],
            visitsReward = settingsRow[LoyaltySettingsTable.visitsReward], // Может быть null
            burnBonusesAfterDays = settingsRow[LoyaltySettingsTable.burnBonusesDays],
            downgradeTierAfterDays = settingsRow[LoyaltySettingsTable.downgradeTierDays],
            maxBurnPercentage = settingsRow[LoyaltySettingsTable.maxBurnPercentage],
            awardOnMixedPayment = settingsRow[LoyaltySettingsTable.awardOnMixedPayment]
        )
    }

    // Обновить настройки лояльности
    suspend fun updateSettings(pointId: String, request: LoyaltySettingsDto) = dbQuery {
        // 1. Ищем существующую запись
        val settingsRow = LoyaltySettingsTable
            .slice(LoyaltySettingsTable.id)
            .select { LoyaltySettingsTable.tradingPointId eq pointId }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.LOYALTY_SETTING_NOT_FOUND, "Settings not found")

        val settingsId = settingsRow[LoyaltySettingsTable.id]

        // 2. Обновляем основные поля
        LoyaltySettingsTable.update({ LoyaltySettingsTable.id eq settingsId }) {
            it[programType] = request.programType.name
            it[visitsTarget] = request.visitsTarget
            it[visitsReward] = request.visitsReward
            it[burnBonusesDays] = request.burnBonusesAfterDays
            it[downgradeTierDays] = request.downgradeTierAfterDays
            it[maxBurnPercentage] = request.maxBurnPercentage
            it[awardOnMixedPayment] = request.awardOnMixedPayment
        }

        // 3. Обновляем уровни (Tiers)
        // Стратегия: Удаляем старые -> Создаем новые.
        // Это безопаснее и проще, чем пытаться обновлять каждый уровень по отдельности.

        LoyaltyTiersTable.deleteWhere { LoyaltyTiersTable.settingsId eq settingsId }

        if (request.programType == LoyaltyProgramType.TIERED_LTV || request.programType == LoyaltyProgramType.HYBRID) {
            request.tiers.forEach { tier ->
                LoyaltyTiersTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[this.settingsId] = settingsId
                    it[levelIndex] = tier.levelIndex
                    // Если имя не задано, берем дефолтное имя уровня
                    it[name] = tier.loyaltyTier.descr ?: tier.loyaltyTier.level.name
                    it[threshold] = tier.threshold
                    it[cashbackPercent] = tier.cashbackPercent
                }
            }
        }
    }

    // Создание дефолтных настроек (Обычно вызывается при создании точки, но полезно иметь отдельно)
    suspend fun createDefaultSettings(pointId: String, partnerId: String) = dbQuery {
        val settingsId = UUID.randomUUID().toString()

        LoyaltySettingsTable.insert {
            it[id] = settingsId
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = pointId
            it[programType] = LoyaltyProgramType.VISIT_COUNTER.name // По умолчанию, например
            it[visitsTarget] = 10
            it[maxBurnPercentage] = 100
            it[awardOnMixedPayment] = false
        }

        // Уровни по умолчанию не нужны для VISIT_COUNTER, но если тип другой - надо создать.
    }
}