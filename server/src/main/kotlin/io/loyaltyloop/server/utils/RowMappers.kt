package io.loyaltyloop.server.utils

import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.shared.models.CardBlockStatus
import io.loyaltyloop.shared.models.CardPauseStatus
import io.loyaltyloop.shared.models.ClientRatingTag
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.RiskLevel
import io.loyaltyloop.shared.models.UserDto
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toUserDto(): UserDto =
    UserDto(
        id = this[UsersTable.id],
        phoneNumber = this[UsersTable.phoneNumber],
        countryCode = this[UsersTable.countryCode],
        firstName = this[UsersTable.firstName],
        lastName = this[UsersTable.lastName],
        email = this[UsersTable.email],
        qrSecret = this[UsersTable.qrSecret],
        language = this[UsersTable.language],
        isFrozenUntil = this[UsersTable.frozenUntil],
        isDeleted = this[UsersTable.isDeleted]
    )

fun ResultRow.toBaseCardDto(): LoyaltyCardDto {
    val block = this[LoyaltyCardTable.blockedUntil]?.let {
        CardBlockStatus(until = it, reason = this[LoyaltyCardTable.blockedReason])
    }

    // Оптимизация: создание объекта только если карта на паузе
    val pause = if (this[LoyaltyCardTable.isPaused]) {
        CardPauseStatus(reason = this[LoyaltyCardTable.pauseReason])
    } else {
        null
    }

    val score = this[LoyaltyCardTable.trustScore]
    val fraud = this[LoyaltyCardTable.fraudFlag]

    // Логика расчета риска (выглядит верно)
    val risk = when {
        fraud -> RiskLevel.BLACK
        score >= 4.5 -> RiskLevel.GREEN
        score >= 3.5 -> RiskLevel.YELLOW
        score >= 2.0 -> RiskLevel.ORANGE
        else -> RiskLevel.RED
    }

    return LoyaltyCardDto(
        id = this[LoyaltyCardTable.id],
        userId = this[LoyaltyCardTable.userId],
        partnerId = this[LoyaltyCardTable.partnerId],
        balance = this[LoyaltyCardTable.balance],
        totalSpent = this[LoyaltyCardTable.totalSpent],
        tierLevel = this[LoyaltyCardTable.tierLevel],
        block = block,
        pause = pause,
        // Заглушки, которые будут перезаписаны в Repository через .copy()
        partnerName = "",
        cardColor = "",
        logoUrl = null,
        visitsTarget = 0, // <-- Добавлено дефолтное значение, чтобы DTO собрался
        visitsCount = this[LoyaltyCardTable.visitsCount],
        trustScore = score,
        fraudFlag = fraud,
        riskLevel = risk
    )
}

fun String?.toClientRatingTags(): List<ClientRatingTag> =
    this.splitCsv().map { ClientRatingTag.valueOf(it) }

fun ResultRow.clientRatingTags(): List<ClientRatingTag> =
    this[io.loyaltyloop.server.database.tables.ClientRatingsTable.tags].toClientRatingTags()

fun String?.splitCsv(): List<String> =
    this
        ?.split(",")
        ?.mapNotNull { it.trim().takeIf { tag -> tag.isNotEmpty() } }
        ?: emptyList()

