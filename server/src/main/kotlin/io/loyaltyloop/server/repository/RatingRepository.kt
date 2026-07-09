package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.*
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.splitCsv
import io.loyaltyloop.server.utils.toClientRatingEntity
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID


// TODO checked
class RatingRepository {

    private val CashierUserTable = UsersTable.alias("cashier_users")
    suspend fun createClientRating(
        partnerId: String,
        cashierId: String,
        tradingPointId: String,
        dto: CreateClientRatingDto,
        isIgnored: Boolean = false
    ) = dbQuery {
        ClientRatingsTable.insert {
            it[partner] = partnerId.toUUID()
            it[tradingPoint] = tradingPointId.toUUID()
            it[cashier] = cashierId.toUUID() // Ссылка на UsersTable
            it[user] = dto.userId.toUUID()   // Ссылка на UsersTable
            it[rating] = dto.rating
            it[tags] = dto.tags.joinToString(",") { tag -> tag.name }
            it[this.isIgnored] = isIgnored
        }
    }

    suspend fun createServiceReview(
        partnerId: String,
        userId: String,
        dto: CreateServiceReviewDto
    ) = dbQuery {
        val pointUuid = dto.tradingPointId.toUUID()

        ServiceReviewsTable.insert {
            it[partner] = partnerId.toUUID()
            it[tradingPoint] = pointUuid
            it[user] = userId.toUUID()

            it[rating] = dto.rating
            it[tags] = dto.tags.joinToString(",") { tag -> tag.name }
            it[comment] = dto.comment
            it[isReadByOwner] = false
        }

        updateTradingPointRating(pointUuid, dto.rating)
    }

    private fun updateTradingPointRating(pointUuid: UUID, newVote: Int) {
        TradingPointsTable.update({ TradingPointsTable.id eq pointUuid }) {
            val totalScore = (rating.castTo<Double>(DoubleColumnType()) * ratingCount.castTo<Double>(DoubleColumnType())) + newVote.toDouble()
            val newCount = ratingCount + 1
            
            it[rating] = totalScore / newCount.castTo<Double>(DoubleColumnType())
            it[ratingCount] = newCount
        }
    }

    suspend fun getLastRatingsForUser(
        userId: String,
        partnerId: String,
        limit: Int = 20
    ): List<ClientRatingEntity> = dbQuery {
        val userUuid = userId.toUUID()
        val partnerUuid = partnerId.toUUID()

        ClientRatingsTable
            .selectAll()
            .where {
                (ClientRatingsTable.user eq userUuid) and
                        (ClientRatingsTable.partner eq partnerUuid) and
                        (ClientRatingsTable.isIgnored eq false)
            }
            .orderBy(ClientRatingsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row -> row.toClientRatingEntity() }
    }


    suspend fun hasCashierRatedUserRecently(
        cashierId: String,
        userId: String,
        hours: Long = 24
    ): Boolean = dbQuery {
        val cashierUuid = cashierId.toUUID()
        val userUuid = userId.toUUID()

        val cutoff = nowUtc().minusHours(hours)

        // 2. Делаем запрос
        ClientRatingsTable
            .select {
                (ClientRatingsTable.cashier eq cashierUuid) and
                        (ClientRatingsTable.user eq userUuid) and
                        (ClientRatingsTable.createdAt greater cutoff)
            }
            .count() > 0
    }

    suspend fun getClientRatings(
        partnerId: String,
        limit: Int,
        offset: Long
    ): List<ReviewDto> = dbQuery {
        val partnerUuid = partnerId.toUUID()

        ClientRatingsTable
            // Джойн Клиента (Кого оценивали)
            .join(
                otherTable = UsersTable,
                joinType = JoinType.LEFT,
                onColumn = ClientRatingsTable.user,
                otherColumn = UsersTable.id
            )
            // Джойн Точки
            .join(
                otherTable = TradingPointsTable,
                joinType = JoinType.LEFT,
                onColumn = ClientRatingsTable.tradingPoint,
                otherColumn = TradingPointsTable.id
            )
            // Джойн Кассира (Кто оценивал) -> Имя берем из UsersTable (через алиас)
            .join(
                otherTable = CashierUserTable,
                joinType = JoinType.LEFT,
                onColumn = ClientRatingsTable.cashier,
                otherColumn = CashierUserTable[UsersTable.id]
            )
            .selectAll()
            .where { ClientRatingsTable.partner eq partnerUuid }
            .orderBy(ClientRatingsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row ->
                // Собираем имена
                val clientFirst = row.getOrNull(UsersTable.firstName) ?: ""
                val clientLast = row.getOrNull(UsersTable.lastName) ?: ""
                val clientName = "$clientFirst $clientLast".trim().ifBlank { "Client" }
                val cashierFirst = row.getOrNull(CashierUserTable[UsersTable.firstName])
                val cashierName = if (cashierFirst != null) {
                    val last = row.getOrNull(CashierUserTable[UsersTable.lastName]) ?: ""
                    "$cashierFirst $last".trim()
                } else {
                    "Unknown Cashier"
                }

                ReviewDto(
                    id = row[ClientRatingsTable.id].value.toString(),
                    rating = row[ClientRatingsTable.rating],
                    tags = row[ClientRatingsTable.tags]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    comment = row[ClientRatingsTable.comment],
                    createdAt = row[ClientRatingsTable.createdAt].toUtcMillis(),

                    authorName = cashierName, // Имя сотрудника
                    targetName = clientName,  // Имя клиента
                    targetPhone = row.getOrNull(UsersTable.phoneNumber),

                    pointName = row.getOrNull(TradingPointsTable.name) ?: "Deleted Point",
                    type = ReviewTypes.CASHIER_TO_CLIENT
                )
            }
    }

    // --- READ OPERATIONS (SERVICE REVIEWS) ---
    suspend fun getServiceReviews(
        partnerId: String,
        limit: Int,
        offset: Long
    ): List<ReviewDto> = dbQuery {
        val partnerUuid = partnerId.toUUID()

        ServiceReviewsTable
            // 1. Джойним Автора отзыва (UsersTable)
            .join(
                otherTable = UsersTable,
                joinType = JoinType.LEFT,
                onColumn = ServiceReviewsTable.user, // Колонка-ссылка
                otherColumn = UsersTable.id
            )
            // 2. Джойним Точку (TradingPointsTable)
            .join(
                otherTable = TradingPointsTable,
                joinType = JoinType.LEFT,
                onColumn = ServiceReviewsTable.tradingPoint, // Колонка-ссылка
                otherColumn = TradingPointsTable.id
            )
            .selectAll()
            .where { ServiceReviewsTable.partner eq partnerUuid }
            .orderBy(ServiceReviewsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row ->
                // Сборка имени клиента
                val firstName = row.getOrNull(UsersTable.firstName) ?: ""
                val lastName = row.getOrNull(UsersTable.lastName) ?: ""
                val clientName = "$firstName $lastName".trim().ifBlank { "Anonymous" }

                // Парсинг тегов
                val tagsString = row[ServiceReviewsTable.tags]
                val tagsList = if (!tagsString.isNullOrBlank()) {
                    tagsString.split(",").map { it.trim() }
                } else {
                    emptyList()
                }

                ReviewDto(
                    id = row[ServiceReviewsTable.id].value.toString(),
                    rating = row[ServiceReviewsTable.rating],
                    tags = tagsList,
                    comment = row[ServiceReviewsTable.comment],

                    // [ВАЖНО] Конвертация времени: LocalDateTime -> Long
                    createdAt = row[ServiceReviewsTable.createdAt].toUtcMillis(),

                    authorName = clientName,
                    authorPhone = row.getOrNull(UsersTable.phoneNumber), // Может быть null

                    pointName = row.getOrNull(TradingPointsTable.name) ?: "Business", // Если точка удалена или отзыв на бренд

                    type = ReviewTypes.CLIENT_TO_SERVICE
                )
            }
    }

    // --- ANALYTICS ---

    suspend fun getAnalyticsData(
        partnerId: String,
        from: Long? = null,
        to: Long? = null,
        pointId: String? = null, // Фильтр по конкретной точке (опционально)
        timezone: String
    ): AnalyticsDataDto = dbQuery {
        val partnerUuid = partnerId.toUUID()

        // 1. Настраиваем Таймзону (для графика по дням)
        val zoneId = try {
            java.time.ZoneId.of(timezone)
        } catch (_: Exception) {
            java.time.ZoneId.of("UTC")
        }

        // 2. Подготовка дат для фильтра (Timestamp -> LocalDateTime UTC)
        // Используем наши экстеншены или Instant
        val fromDate = from?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDateTime()
        }
        val toDate = to?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDateTime()
        }

        // 3. Основной запрос (Сразу джойним точки, чтобы получить их имена)
        val query = ServiceReviewsTable
            .join(
                otherTable = TradingPointsTable,
                joinType = JoinType.LEFT, // Left, так как точка могла быть удалена
                onColumn = ServiceReviewsTable.tradingPoint,
                otherColumn = TradingPointsTable.id
            )
            .slice(
                ServiceReviewsTable.createdAt,
                ServiceReviewsTable.rating,
                ServiceReviewsTable.tags,
                ServiceReviewsTable.tradingPoint, // ID точки
                TradingPointsTable.name           // Имя точки
            )
            .select { ServiceReviewsTable.partner eq partnerUuid }

        // Применяем фильтры
        if (fromDate != null) {
            query.andWhere { ServiceReviewsTable.createdAt greaterEq fromDate }
        }
        if (toDate != null) {
            query.andWhere { ServiceReviewsTable.createdAt lessEq toDate }
        }
        if (pointId != null) {
            // [FIX] Раньше тут была ошибка (partnerId вместо pointId)
            query.andWhere { ServiceReviewsTable.tradingPoint eq pointId.toUUID() }
        }

        // Выгружаем в память (для аналитики это нормально, SQL группировки сложнее мапить)
        val rows = query.toList()

        // --- А. ОБЩАЯ СТАТИСТИКА ---
        val total = rows.size
        val avgRating = if (total > 0) rows.map { it[ServiceReviewsTable.rating] }.average() else 0.0

        // NPS (Net Promoter Score)
        // 5 = Promoter, 4 = Passive, 1-3 = Detractor
        val promoters = rows.count { it[ServiceReviewsTable.rating] == 5 }
        val detractors = rows.count { it[ServiceReviewsTable.rating] <= 3 }
        val nps = if (total > 0) ((promoters - detractors).toDouble() / total * 100).toInt() else 0

        // --- Б. ГРАФИК ПО ДНЯМ (С учетом Таймзоны) ---
        val series = rows
            .groupBy { row ->
                // Конвертируем UTC из базы в Локальную дату Партнера
                row[ServiceReviewsTable.createdAt]
                    .atZone(java.time.ZoneOffset.UTC)
                    .withZoneSameInstant(zoneId)
                    .toLocalDate()
            }
            .map { (date, dailyRows) ->
                val ratings = dailyRows.map { it[ServiceReviewsTable.rating] }
                val count = ratings.size
                val promDay = ratings.count { it == 5 }
                val detDay = ratings.count { it <= 3 }
                val npsDay = if (count > 0) ((promDay - detDay).toDouble() / count * 100).toInt() else 0

                AnalyticsSeriesPointDto(
                    // Возвращаем начало дня в миллисекундах
                    date = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    nps = npsDay,
                    totalReviews = count,
                    averageRating = if (count > 0) ratings.average() else 0.0
                )
            }
            .sortedBy { it.date }

        // --- В. ТЕПЛОВАЯ КАРТА (Heatmap) ---
        // Группируем по Точке -> Считаем теги
        val heatmap = rows
            .filter { it[ServiceReviewsTable.tradingPoint] != null } // Исключаем отзывы без точки
            .groupBy { it[ServiceReviewsTable.tradingPoint]!! }      // Группировка по ID точки (EntityID)
            .map { (pId, pointRows) ->
                val pointName = pointRows.first().getOrNull(TradingPointsTable.name) ?: "Unknown"

                // Собираем все теги этой точки в одну кучу
                val allTags = pointRows.flatMap { row ->
                    row[ServiceReviewsTable.tags]
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                }
                // Считаем частоту: { "CLEAN": 5, "SLOW": 2 }
                val tagStats = allTags
                    .groupingBy { it }
                    .eachCount()
                    .map { (tag, count) -> TagStatDto(tag, count) }
                    .sortedByDescending { it.count }

                HeatmapPointDto(
                    pointId = pId.value.toString(),
                    pointName = pointName,
                    tagStats = tagStats
                )
            }

        AnalyticsDataDto(
            nps = nps,
            averageRating = avgRating,
            totalReviews = total,
            heatmap = heatmap,
            series = series
        )
    }

    data class ClientRatingEntity(
        val rating: Int,
        val tags: List<ClientRatingTag>,
        val date: Long // Timestamp
    )
}
