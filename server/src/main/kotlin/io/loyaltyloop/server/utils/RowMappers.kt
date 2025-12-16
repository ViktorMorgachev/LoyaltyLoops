package io.loyaltyloop.server.utils

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.service.ExchangeRateService
import io.loyaltyloop.server.service.LoyaltyCalculator.round
import io.loyaltyloop.shared.models.CardBlockStatus
import io.loyaltyloop.shared.models.CardPauseStatus
import io.loyaltyloop.shared.models.ClientRatingTag
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.RiskLevel
import io.loyaltyloop.shared.models.UserDto
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

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

fun String?.toClientRatingTags(): List<ClientRatingTag> =
    this.splitCsv().map { ClientRatingTag.valueOf(it) }

fun ResultRow.clientRatingTags(): List<ClientRatingTag> =
    this[io.loyaltyloop.server.database.tables.ClientRatingsTable.tags].toClientRatingTags()

fun String?.splitCsv(): List<String> =
    this
        ?.split(",")
        ?.mapNotNull { it.trim().takeIf { tag -> tag.isNotEmpty() } }
        ?: emptyList()
