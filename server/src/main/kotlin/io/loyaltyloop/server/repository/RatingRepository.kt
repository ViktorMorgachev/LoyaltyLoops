package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.*
import io.loyaltyloop.server.utils.splitCsv
import io.loyaltyloop.shared.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RatingRepository {

    // --- WRITE OPERATIONS ---

    suspend fun createClientRating(
        partnerId: String,
        cashierId: String,
        dto: CreateClientRatingDto,
        isIgnored: Boolean = false
    ) = dbQuery {
        ClientRatingsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = dto.tradingPointId
            it[this.cashierId] = cashierId
            it[this.userId] = dto.userId
            it[rating] = dto.rating
            it[tags] = dto.tags.joinToString(",") { tag -> tag.name }
            it[this.isIgnored] = isIgnored
            it[createdAt] = System.currentTimeMillis()
        }
    }

    suspend fun createServiceReview(
        partnerId: String,
        userId: String,
        dto: CreateServiceReviewDto
    ) = dbQuery {
        ServiceReviewsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = dto.tradingPointId
            it[this.userId] = userId
            it[rating] = dto.rating
            it[tags] = dto.tags.joinToString(",") { tag -> tag.name }
            it[comment] = dto.comment
            it[createdAt] = System.currentTimeMillis()
        }
    }

    // --- READ OPERATIONS (CLIENT RATINGS) ---

    suspend fun getLastRatingsForUser(
        userId: String,
        partnerId: String,
        limit: Int = 20
    ): List<ClientRatingEntity> = dbQuery {
        ClientRatingsTable
            .selectAll()
            .where {
                (ClientRatingsTable.userId eq userId) and
                        (ClientRatingsTable.partnerId eq partnerId) and
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
        val cutoff = System.currentTimeMillis() - (hours * 3600 * 1000)

        !ClientRatingsTable
            .select {
                (ClientRatingsTable.cashierId eq cashierId) and
                        (ClientRatingsTable.userId eq userId) and
                        (ClientRatingsTable.createdAt greater cutoff)
            }
            .empty()
    }

    suspend fun getClientRatings(
        partnerId: String,
        limit: Int,
        offset: Long
    ): List<ReviewDto> = dbQuery {
        // Явные джойны, так как нет References
        ClientRatingsTable
            .join(
                otherTable = UsersTable,
                joinType = JoinType.LEFT,
                onColumn = ClientRatingsTable.userId,
                otherColumn = UsersTable.id
            )
            .join(
                otherTable = TradingPointsTable,
                joinType = JoinType.LEFT,
                onColumn = ClientRatingsTable.tradingPointId,
                otherColumn = TradingPointsTable.id
            )
            .selectAll()
            .where { ClientRatingsTable.partnerId eq partnerId }
            .orderBy(ClientRatingsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row ->
                ReviewDto(
                    id = row[ClientRatingsTable.id],
                    rating = row[ClientRatingsTable.rating],
                    tags = try { row[ClientRatingsTable.tags]?.splitCsv() ?: emptyList() } catch (e: Exception) { emptyList() },
                    comment = null,
                    createdAt = row[ClientRatingsTable.createdAt],
                    authorName = "Cashier", // Имя кассира можно достать, но это +2 джойна. Пока оставляем так для KISS.
                    targetName = row[UsersTable.firstName] ?: "Client",
                    targetPhone = row[UsersTable.phoneNumber],
                    pointName = row[TradingPointsTable.name],
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
        // Явные джойны
        ServiceReviewsTable
            .join(
                otherTable = UsersTable,
                joinType = JoinType.LEFT,
                onColumn = ServiceReviewsTable.userId,
                otherColumn = UsersTable.id
            )
            .join(
                otherTable = TradingPointsTable,
                joinType = JoinType.LEFT,
                onColumn = ServiceReviewsTable.tradingPointId,
                otherColumn = TradingPointsTable.id
            )
            .selectAll()
            .where { ServiceReviewsTable.partnerId eq partnerId }
            .orderBy(ServiceReviewsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row ->
                ReviewDto(
                    id = row[ServiceReviewsTable.id],
                    rating = row[ServiceReviewsTable.rating],
                    tags = try { row[ServiceReviewsTable.tags]?.splitCsv() ?: emptyList() } catch (e: Exception) { emptyList() },
                    comment = row[ServiceReviewsTable.comment],
                    createdAt = row[ServiceReviewsTable.createdAt],
                    authorName = row[UsersTable.firstName] ?: "Client",
                    authorPhone = row[UsersTable.phoneNumber],
                    pointName = row[TradingPointsTable.name],
                    type = ReviewTypes.CLIENT_TO_SERVICE
                )
            }
    }

    // --- ANALYTICS ---

    suspend fun getAnalyticsData(partnerId: String, from: Long? = null, to: Long? = null, pointId: String? = null): AnalyticsDataDto = dbQuery {
        // 1. Фильтрация
        val query = ServiceReviewsTable.selectAll()
            .where { ServiceReviewsTable.partnerId eq partnerId }

        from?.let { query.andWhere { ServiceReviewsTable.createdAt greaterEq it } }
        to?.let { query.andWhere { ServiceReviewsTable.createdAt lessEq it } }
        pointId?.let { query.andWhere { ServiceReviewsTable.tradingPointId eq it } }

        // Выгружаем в память (приемлемо для < 10-20k записей)
        val allRows = query.toList()

        val ratings = allRows.map { it[ServiceReviewsTable.rating] }
        val total = ratings.size
        val avgRating = if (total > 0) ratings.average() else 0.0

        // NPS Calculation (5 = Promoter, 4 = Passive, 1-3 = Detractor)
        val promoters = ratings.count { it == 5 }
        val detractors = ratings.count { it <= 3 }
        val nps = if (total > 0) ((promoters - detractors).toDouble() / total * 100).toInt() else 0

        // 2. График по дням
        val series = allRows
            .groupBy {
                Instant.ofEpochMilli(it[ServiceReviewsTable.createdAt]).atZone(ZoneOffset.UTC).toLocalDate()
            }
            .map { (date, rows) ->
                val rts = rows.map { it[ServiceReviewsTable.rating] }
                val cnt = rts.size
                val prom = rts.count { it == 5 }
                val det = rts.count { it <= 3 }
                val npsDay = if (cnt > 0) ((prom - det).toDouble() / cnt * 100).toInt() else 0

                AnalyticsSeriesPointDto(
                    date = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    nps = npsDay,
                    totalReviews = cnt,
                    averageRating = if (cnt > 0) rts.average() else 0.0
                )
            }
            .sortedBy { it.date }

        // 3. Тепловая карта тегов
        // Берем только отзывы с тегами
        val heatmapRows = allRows.filter { it[ServiceReviewsTable.tags] != null }

        // Получаем имена точек одним легким запросом (кэшируем)
        val pointNames = TradingPointsTable.slice(TradingPointsTable.id, TradingPointsTable.name)
            .select { TradingPointsTable.partnerId eq partnerId }
            .associate { it[TradingPointsTable.id] to it[TradingPointsTable.name] }

        val heatmap = heatmapRows
            .flatMap { row ->
                val pId = row[ServiceReviewsTable.tradingPointId]
                val tagsStr = row[ServiceReviewsTable.tags] ?: ""
                val tags = try { tagsStr.splitCsv() } catch (e: Exception) { emptyList() }
                tags.map { tag -> pId to tag }
            }
            .groupBy { it.first } // Группируем по ID точки
            .map { (pId, list) ->
                val pointName = pointNames[pId] ?: "Unknown"
                val tagCounts = list.groupingBy { it.second }.eachCount()

                HeatmapPointDto(
                    pointId = pId,
                    pointName = pointName,
                    tagStats = tagCounts.map { (tag, count) -> TagStatDto(tag, count) }
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

    // --- MAPPERS ---

    data class ClientRatingEntity(
        val rating: Int,
        val tags: List<ClientRatingTag>
    )

    private fun ResultRow.toClientRatingEntity(): ClientRatingEntity =
        ClientRatingEntity(
            rating = this[ClientRatingsTable.rating],
            tags = try {
                val tagStr = this[ClientRatingsTable.tags]
                if (tagStr.isNullOrBlank()) emptyList()
                else tagStr.splitCsv().map { ClientRatingTag.valueOf(it) }
            } catch (e: Exception) { emptyList() }
        )

    suspend fun getPartnerIdByPointId(pointId: String): String? = dbQuery {
        TradingPointsTable.slice(TradingPointsTable.partnerId)
            .select { TradingPointsTable.id eq pointId }
            .singleOrNull()?.get(TradingPointsTable.partnerId)
    }
}