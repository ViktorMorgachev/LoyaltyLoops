package io.loyaltyloop.server.utils

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.blockStatus
import io.loyaltyloop.server.database.tables.pauseStatus
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.service.ExchangeRateService
import io.loyaltyloop.server.service.LoyaltyCalculator.round
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.RiskLevel
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class CardUtils(val exchangeRateService: ExchangeRateService, val partnerRepository: PartnerRepository) {

    suspend fun getUserCards(userId: String, estimatedCurrency: String): List<LoyaltyCardDto> = dbQuery {
        // Здесь нужен явный JOIN, так как в LoyaltyCardTable нет references
        LoyaltyCardTable
            .join(PartnersTable, JoinType.INNER, LoyaltyCardTable.partnerId, PartnersTable.id)
            .selectAll().where { LoyaltyCardTable.userId eq userId }
            .map { row ->
                val block = row.blockStatus()
                val pause = row.pauseStatus()
                val partnerID = row[LoyaltyCardTable.partnerId]
                val partner = partnerRepository.getPartnerByIdQ(partnerID)
                val rate = exchangeRateService.getRate(partner.baseCurrency, estimatedCurrency)
                val score = row[LoyaltyCardTable.trustScore]
                val fraud = row[LoyaltyCardTable.fraudFlag]
                val visitsTarget = partner.defaultVisitsTarget
                val risk = when {
                    fraud -> RiskLevel.BLACK
                    score >= 4.5 -> RiskLevel.GREEN
                    score >= 3.5 -> RiskLevel.YELLOW
                    score >= 2.0 -> RiskLevel.ORANGE
                    else -> RiskLevel.RED
                }
                LoyaltyCardDto(
                    id = row[LoyaltyCardTable.id],
                    userId = row[LoyaltyCardTable.userId],
                    partnerId = row[LoyaltyCardTable.partnerId],
                    balance = row[LoyaltyCardTable.balance],
                    totalSpent = row[LoyaltyCardTable.totalSpent],
                    tierLevel = row[LoyaltyCardTable.tierLevel],
                    block = block,
                    pause = pause,
                    partnerName = partner.businessName,
                    cardColor = partner.color,
                    logoUrl = partner.logoUrl,
                    visitsTarget = visitsTarget,
                    visitsCount = row[LoyaltyCardTable.visitsCount],
                    trustScore = score,
                    fraudFlag = fraud,
                    riskLevel = risk,
                    partnerBaseCurrency = partner.baseCurrency,
                    estimatedValue = if(estimatedCurrency != partner.baseCurrency) (row[LoyaltyCardTable.balance] * rate).round(2) else 0.0,
                    estimatedCurrency = estimatedCurrency
                )
            }
    }

    suspend fun insertNewCard(userId: String, partnerID: String) = dbQuery {
        LoyaltyCardTable.insert {
            it[this.userId] = userId
            it[this.partnerId] = partnerID
            it[balance] = 0.0
            it[totalSpent] = 0.0
            it[tierLevel] = 1
            it[visitsCount] = 0
            it[trustScore] = 4.0
            it[fraudFlag] = false
        }
    }

    suspend fun getCardByID(cardId: String, estimatedCurrency: String): LoyaltyCardDto?  = dbQuery {

        LoyaltyCardTable.join(
            otherTable = PartnersTable,
            joinType = JoinType.INNER,
            onColumn = LoyaltyCardTable.partnerId,
            otherColumn = PartnersTable.id
        )
            .selectAll()
            .where { LoyaltyCardTable.id eq cardId }
            .map { row ->
                val block = row.blockStatus()
                val pause = row.pauseStatus()
                val partnerID = row[LoyaltyCardTable.partnerId]
                val partner = partnerRepository.getPartnerByIdQ(partnerID)
                val rate = exchangeRateService.getRate(partner.baseCurrency, estimatedCurrency)
                val score = row[LoyaltyCardTable.trustScore]
                val fraud = row[LoyaltyCardTable.fraudFlag]
                val visitsTarget = partner.defaultVisitsTarget
                val risk = when {
                    fraud -> RiskLevel.BLACK
                    score >= 4.5 -> RiskLevel.GREEN
                    score >= 3.5 -> RiskLevel.YELLOW
                    score >= 2.0 -> RiskLevel.ORANGE
                    else -> RiskLevel.RED
                }
                LoyaltyCardDto(
                    id = row[LoyaltyCardTable.id],
                    userId = row[LoyaltyCardTable.userId],
                    partnerId = row[LoyaltyCardTable.partnerId],
                    balance = row[LoyaltyCardTable.balance],
                    totalSpent = row[LoyaltyCardTable.totalSpent],
                    tierLevel = row[LoyaltyCardTable.tierLevel],
                    block = block,
                    pause = pause,
                    partnerName = partner.businessName,
                    cardColor = partner.color,
                    logoUrl = partner.logoUrl,
                    visitsTarget = visitsTarget,
                    visitsCount = row[LoyaltyCardTable.visitsCount],
                    trustScore = score,
                    fraudFlag = fraud,
                    riskLevel = risk,
                    partnerBaseCurrency = partner.baseCurrency,
                    estimatedValue = if(estimatedCurrency != partner.baseCurrency) (row[LoyaltyCardTable.balance] * rate).round(2) else 0.0,
                    estimatedCurrency = estimatedCurrency
                )
            }
            .singleOrNull()
    }

   suspend fun getCardByUserAndPartner(userId: String, partnerId: String, estimatedCurrency: String): LoyaltyCardDto?  = dbQuery{
       LoyaltyCardTable.join(
           otherTable = PartnersTable,
           joinType = JoinType.INNER,
           onColumn = LoyaltyCardTable.partnerId,
           otherColumn = PartnersTable.id
       )
           .selectAll()
           .where {
               (LoyaltyCardTable.userId eq userId) and
                       (LoyaltyCardTable.partnerId eq partnerId)
           }
           .map { row ->
               val block = row.blockStatus()
               val pause = row.pauseStatus()
               val partner = partnerRepository.getPartnerByIdQ(partnerId)
               val rate = exchangeRateService.getRate(partner.baseCurrency, estimatedCurrency)
               val score = row[LoyaltyCardTable.trustScore]
               val fraud = row[LoyaltyCardTable.fraudFlag]
               val visitsTarget = partner.defaultVisitsTarget
               val risk = when {
                   fraud -> RiskLevel.BLACK
                   score >= 4.5 -> RiskLevel.GREEN
                   score >= 3.5 -> RiskLevel.YELLOW
                   score >= 2.0 -> RiskLevel.ORANGE
                   else -> RiskLevel.RED
               }
               LoyaltyCardDto(
                   id = row[LoyaltyCardTable.id],
                   userId = row[LoyaltyCardTable.userId],
                   partnerId = row[LoyaltyCardTable.partnerId],
                   balance = row[LoyaltyCardTable.balance],
                   totalSpent = row[LoyaltyCardTable.totalSpent],
                   tierLevel = row[LoyaltyCardTable.tierLevel],
                   block = block,
                   pause = pause,
                   partnerName = partner.businessName,
                   cardColor = partner.color,
                   logoUrl = partner.logoUrl,
                   visitsTarget = visitsTarget, // <-- Добавлено дефолтное значение, чтобы DTO собрался
                   visitsCount = row[LoyaltyCardTable.visitsCount],
                   trustScore = score,
                   fraudFlag = fraud,
                   riskLevel = risk,
                   partnerBaseCurrency = partner.baseCurrency,
                   estimatedValue = if(estimatedCurrency != partner.baseCurrency) (row[LoyaltyCardTable.balance] * rate).round(2) else 0.0,
                   estimatedCurrency = estimatedCurrency
               )
           }
           .singleOrNull()
    }
}
