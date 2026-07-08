package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardsTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.service.ExchangeRateService
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.initializeLoyaltySettings
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toLoyaltyCardDto
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcLocalDateTime
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.LoyaltyCardDto
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime

// TODO in checked
class LoyaltyCardRepository(
    private val exchangeRateService: ExchangeRateService,
) {

    suspend fun countByPartnerId(partnerId: String): Int = dbQuery {
        LoyaltyCardsTable
            .select { LoyaltyCardsTable.partner eq partnerId.toUUID() }
            .count()
            .toInt()
    }

    suspend fun findOrCreateCard(
        userId: String,
        partnerId: String,
        estimatedCurrency: String,
    ): Pair<LoyaltyCardDto, Boolean> {
        val existingCard = getCardByUserAndPartner(userId, partnerId, estimatedCurrency)

        if (existingCard != null) {
            return existingCard to false
        }
        try {
            insertNewCard(userId, partnerId)
        } catch (_: Exception) {
        }
        val newCard = getCardByUserAndPartner(userId, partnerId, estimatedCurrency)
            ?: throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Failed to create card")

        return newCard to true
    }

    suspend fun getUserCards(userId: String, estimatedCurrency: String): List<LoyaltyCardDto> {
        val userUuid = userId.toUUID()

        val rawRows = dbQuery {
            LoyaltyCardsTable
                .innerJoin(PartnersTable)
                .selectAll()
                .where { LoyaltyCardsTable.user eq userUuid }
                .toList()
        }

        return rawRows.map { row ->
            val partnerBaseCurrency = row[PartnersTable.baseCurrency]
            val rate = exchangeRateService.getRate(partnerBaseCurrency, estimatedCurrency)
            row.toLoyaltyCardDto(estimatedCurrency, rate)
        }
    }

    suspend fun getCardByID(cardId: String, estimatedCurrency: String): LoyaltyCardDto? {
        val cardUuid = cardId.toUUID()

        val row = dbQuery {
            LoyaltyCardsTable
                .innerJoin(PartnersTable)
                .selectAll()
                .where { LoyaltyCardsTable.id eq cardUuid }
                .singleOrNull()
        } ?: return null

        val rate = exchangeRateService.getRate(row[PartnersTable.baseCurrency], estimatedCurrency)
        return row.toLoyaltyCardDto(estimatedCurrency, rate)
    }

    suspend fun getCardByUserAndPartner(
        userId: String,
        partnerId: String,
        estimatedCurrency: String
    ): LoyaltyCardDto? {
        val userUuid = userId.toUUID()
        val partnerUuid = partnerId.toUUID()

        val row = dbQuery {
            LoyaltyCardsTable
                .innerJoin(PartnersTable)
                .selectAll()
                .where {
                    (LoyaltyCardsTable.user eq userUuid) and
                            (LoyaltyCardsTable.partner eq partnerUuid)
                }
                .singleOrNull()
        } ?: return null

        val rate = exchangeRateService.getRate(row[PartnersTable.baseCurrency], estimatedCurrency)
        return row.toLoyaltyCardDto(estimatedCurrency, rate)
    }

    suspend fun insertNewCard(userId: String, partnerId: String) = dbQuery{
        val userUuid = userId.toUUID()
        val partnerUuid = partnerId.toUUID()
        val now = nowUtc()

        LoyaltyCardsTable.insert {
            it[user] = userUuid
            it[partner] = partnerUuid
            it[balance] = BigDecimal.ZERO
            it[totalSpent] = BigDecimal.ZERO
            it[tierLevel] = 1
            it[visitsCount] = 0
            it[trustScore] = 4.0
            it[fraudFlag] = false
            it[lastActivityAt] = now
            it[createdAt] = now
        }

    }


    suspend fun lockCardRow(cardId: String) = dbQuery {
        val found = LoyaltyCardsTable
            .select { LoyaltyCardsTable.id eq cardId.toUUID() }
            .forUpdate()
            .any()
        if (!found) {
            throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")
        }
    }

    suspend fun incrementVisits(cardId: String, updatedAt: LocalDateTime) = dbQuery {
        val cardUuid = cardId.toUUID()
        LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardUuid }) {
            it[visitsCount] = visitsCount + 1
            it[lastActivityAt] = updatedAt
        }
    }

    suspend fun dropVisits(cardId: String, updatedAt: LocalDateTime) = dbQuery {
        val cardUuid = cardId.toUUID()
        LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardUuid }) {
            it[visitsCount] = 1
            it[lastActivityAt] = updatedAt
        }
    }

    suspend fun addCashback(cardId: String, cashback: Double, spentAmount: Double, updatedAt: LocalDateTime) = dbQuery {
        val cardUuid = cardId.toUUID()

        // Конвертируем Double -> BigDecimal
        val cashbackDec = BigDecimal.valueOf(cashback)
        val spentDec = BigDecimal.valueOf(spentAmount)

        val updated = LoyaltyCardsTable.update({
            if (cashbackDec.signum() < 0) {
                // Списание не может увести баланс в минус даже при гонке
                (LoyaltyCardsTable.id eq cardUuid) and (LoyaltyCardsTable.balance greaterEq cashbackDec.negate())
            } else {
                LoyaltyCardsTable.id eq cardUuid
            }
        }) {
            it[totalSpent] = totalSpent + spentDec
            it[balance] = balance + cashbackDec
            it[lastActivityAt] = updatedAt
        }

        if (updated == 0) {
            throw LoyaltyException(AppErrorCode.INVALID_AMOUNT, "Insufficient balance")
        }
    }

    suspend fun updateTier(cardId: String, level: Int, updatedAt: LocalDateTime) = dbQuery {
        val cardUuid = cardId.toUUID()

        LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardUuid }) {
            it[tierLevel] = level
            it[lastActivityAt] = updatedAt
        }
    }

    suspend fun blockCard(cardId: String, untilTimestamp: Long, reason: String) = dbQuery {
        val cardUuid = cardId.toUUID()
        val untilDate = untilTimestamp.toUtcLocalDateTime()

        LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardUuid }) {
            it[blockedUntil] = untilDate
            it[blockedReason] = reason
        }
    }

    suspend fun unblockCard(cardId: String) = dbQuery {
        val cardUuid = cardId.toUUID()

        LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardUuid }) {
            it[blockedUntil] = null
            it[blockedReason] = null
        }
    }
}