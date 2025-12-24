package io.loyaltyloop.server.utils

import io.loyaltyloop.server.database.tables.ClientRatingsTable
import io.loyaltyloop.server.database.tables.LoyaltyCardsTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.SupportThreadsTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.database.tables.blockStatus
import io.loyaltyloop.server.database.tables.pauseStatus
import io.loyaltyloop.server.repository.RatingRepository.ClientRatingEntity
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.PartnerEntity
import io.loyaltyloop.shared.models.RiskLevel
import io.loyaltyloop.shared.models.SupportThreadDto
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TransactionHistoryDto
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.WeeklyScheduleDto
import io.loyaltyloop.shared.utils.LoyaltyFormatter
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow

val json = Json { ignoreUnknownKeys = true }

// TODO checked
fun ResultRow.toUserDto(): UserDto =
    UserDto(
        id = this[UsersTable.id].value.toString(),
        createdAt = this[UsersTable.createdAt].toUtcMillis(),
        phoneNumber = this[UsersTable.phoneNumber],
        countryCode = this[UsersTable.countryCode],
        firstName = this[UsersTable.firstName],
        lastName = this[UsersTable.lastName],
        email = this[UsersTable.email],
        qrSecret = this[UsersTable.qrSecret],
        isFrozenUntil = this[UsersTable.frozenUntil]?.toUtcMillis(),
        language = this[UsersTable.language],
        isDeleted = this[UsersTable.isDeleted]
    )

// TODO checked
fun ResultRow.toClientRatingEntity(): ClientRatingEntity {
    return ClientRatingEntity(
        rating = this[ClientRatingsTable.rating],
        tags = this[ClientRatingsTable.tags].toClientRatingTags(),
        date = this[ClientRatingsTable.createdAt].toUtcMillis()
    )
}

// TODO checked
fun ResultRow.toThreadDto(): SupportThreadDto {
    return SupportThreadDto(
        id = this[SupportThreadsTable.id].value.toString(),
        partnerId = this[PartnersTable.id].value.toString(),
        partnerName = this[PartnersTable.businessName],
        lastMessageSnippet = this[SupportThreadsTable.lastMessageSnippet],
        lastMessageAt = this[SupportThreadsTable.lastMessageAt]?.toUtcMillis(),
        unreadForPartner = this[SupportThreadsTable.unreadForPartner],
        unreadForAdmin = this[SupportThreadsTable.unreadForAdmin],
        createdAt = this[SupportThreadsTable.createdAt].toUtcMillis(),
        isClosed = this[SupportThreadsTable.isClosed]
    )
}

// TODO checked
fun ResultRow.toTradingPointDto(
    schedule: WeeklyScheduleDto? = this.parseSchedule(),
    distanceMeters: Double? = null,
    isOpenNow: Boolean? = null
) = TradingPointDto(
    id = this[TradingPointsTable.id].toString(),
    name = this[TradingPointsTable.name],
    address = this[TradingPointsTable.address],
    type = this[TradingPointsTable.type],
    latitude = this[TradingPointsTable.latitude] ?: 0.0,
    longitude = this[TradingPointsTable.longitude] ?: 0.0,
    active = this[TradingPointsTable.isActive],
    inviteCode = this[TradingPointsTable.inviteCode],
    currency = this[TradingPointsTable.currency],
    schedule = schedule,
    rating = this[TradingPointsTable.rating],
    reviewCount = this[TradingPointsTable.ratingCount],
    distanceMeters = distanceMeters,
    isOpenNow = isOpenNow,
    temporarilyPaused = this[TradingPointsTable.isTemporarilyPaused],
    contactPhone = this[TradingPointsTable.contactPhone],
    contactLink = this[TradingPointsTable.contactLink],
    additionalInfo = this[TradingPointsTable.additionalInfo],
    timezone = this[TradingPointsTable.timezone]
)

// TODO checked
fun ResultRow.parseSchedule(): WeeklyScheduleDto? {
    val raw = this[TradingPointsTable.workingHoursJson] ?: return null
    return runCatching { json.decodeFromString<WeeklyScheduleDto>(raw) }.getOrNull()
}

// TODO checked
fun ResultRow.toPartnerEntity(): PartnerEntity {
    return PartnerEntity(
        id = this[PartnersTable.id].value.toString(),
        ownerId = this[PartnersTable.owner].value.toString(),
        businessName = this[PartnersTable.businessName],
        countryCode = this[PartnersTable.countryCode],
        baseCurrency = this[PartnersTable.baseCurrency], // Не забываем!
        managerInviteCode = this[PartnersTable.managerInviteCode],
        hasPin = !this[PartnersTable.adminPinHash].isNullOrBlank(),
        status = this[PartnersTable.status],
        logoUrl = this[PartnersTable.logoUrl],
        color = this[PartnersTable.color],
        burnBonusesDays = this[PartnersTable.burnBonusesDays],
        downgradeTierDays = this[PartnersTable.downgradeTierDays],
        defaultVisitsTarget = this[PartnersTable.defaultVisitsTarget],
        ownerPhone = null,
        subscriptionWarnings = null
    )
}

// TODO checked
fun ResultRow.toTransactionHistoryDto(): TransactionHistoryDto{
    return TransactionHistoryDto(
        id = this[TransactionsHistoryTable.id].value.toString(),
        timestamp = this[TransactionsHistoryTable.createdAt].toUtcMillis(),
        pointName = this[TradingPointsTable.name],
        type = this[TransactionsHistoryTable.type].name,
        amount = this[TransactionsHistoryTable.amount].toDouble(),
        pointsDelta = this[TransactionsHistoryTable.pointsDelta].toDouble(),
        visitsDelta = this[TransactionsHistoryTable.visitsDelta],
        currency = this[TransactionsHistoryTable.currency],
        exchangeRateSnapshot = this[TransactionsHistoryTable.exchangeRateSnapshot].toDouble()
    )
}

// TODO checked
fun ResultRow.toLoyaltyCardDto(estimatedCurrency: String, rate: Double): LoyaltyCardDto {

    val partnerBaseCurrency = this[PartnersTable.baseCurrency]
    val balance = this[LoyaltyCardsTable.balance].toDouble()
    val totalSpent = this[LoyaltyCardsTable.totalSpent].toDouble()

    val estimatedVal = if (estimatedCurrency != partnerBaseCurrency) {
        LoyaltyFormatter.round(balance * rate)
    } else {
        balance
    }

    // 4. Риски
    val score = this[LoyaltyCardsTable.trustScore]
    val fraud = this[LoyaltyCardsTable.fraudFlag]

    val risk = when {
        fraud -> RiskLevel.BLACK
        score >= 4.5 -> RiskLevel.GREEN
        score >= 3.5 -> RiskLevel.YELLOW
        score >= 2.0 -> RiskLevel.ORANGE
        else -> RiskLevel.RED
    }

    return LoyaltyCardDto(
        id = this[LoyaltyCardsTable.id].value.toString(),
        userId = this[LoyaltyCardsTable.user].value.toString(),
        partnerId = this[LoyaltyCardsTable.partner].value.toString(),
        balance = balance,
        totalSpent = totalSpent,
        tierLevel = this[LoyaltyCardsTable.tierLevel],
        block = this.blockStatus(),
        pause = this.pauseStatus(),
        partnerName = this[PartnersTable.businessName],
        cardColor = this[PartnersTable.color],
        logoUrl = this[PartnersTable.logoUrl],
        visitsTarget = this[PartnersTable.defaultVisitsTarget],
        visitsCount = this[LoyaltyCardsTable.visitsCount],
        trustScore = score,
        fraudFlag = fraud,
        riskLevel = risk,
        totalScore = this[LoyaltyCardsTable.totalScore],
        partnerBaseCurrency = partnerBaseCurrency,
        estimatedValue = estimatedVal,
        estimatedCurrency = estimatedCurrency
    )
}
