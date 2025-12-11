package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.ClientRatingsTable
import io.loyaltyloop.server.database.tables.ServiceReviewsTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.shared.models.*
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.time.ZoneId
import java.time.ZoneOffset

class RatingRepository {

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
        }
    }

    suspend fun getLastRatingsForUser(
        userId: String, 
        partnerId: String, 
        limit: Int = 20
    ): List<ClientRatingEntity> = dbQuery {
        ClientRatingsTable
            .select { (ClientRatingsTable.userId eq userId) and (ClientRatingsTable.partnerId eq partnerId) and (ClientRatingsTable.isIgnored eq false) }
            .orderBy(ClientRatingsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { rowToClientRating(it) }
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

    // --- Analytics ---

    suspend fun getServiceReviews(
        partnerId: String,
        limit: Int,
        offset: Long
    ): List<ReviewDto> = dbQuery {
        ServiceReviewsTable
            .join(UsersTable, JoinType.LEFT, ServiceReviewsTable.userId, UsersTable.id)
            .join(TradingPointsTable, JoinType.LEFT, ServiceReviewsTable.tradingPointId, TradingPointsTable.id)
            .select { ServiceReviewsTable.partnerId eq partnerId }
            .orderBy(ServiceReviewsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row ->
                ReviewDto(
                    id = row[ServiceReviewsTable.id],
                    rating = row[ServiceReviewsTable.rating],
                    tags = row[ServiceReviewsTable.tags]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    comment = row[ServiceReviewsTable.comment],
                    createdAt = row[ServiceReviewsTable.createdAt],
                    authorName = row[UsersTable.firstName] ?: "Client",
                    authorPhone = row[UsersTable.phoneNumber],
                    pointName = row[TradingPointsTable.name],
                    type = ReviewTypes.CLIENT_TO_SERVICE
                )
            }
    }

    suspend fun getClientRatings(
        partnerId: String,
        limit: Int,
        offset: Long
    ): List<ReviewDto> = dbQuery {
        ClientRatingsTable
            .join(UsersTable, JoinType.LEFT, ClientRatingsTable.userId, UsersTable.id) // Rated User
            .join(TradingPointsTable, JoinType.LEFT, ClientRatingsTable.tradingPointId, TradingPointsTable.id)
            .select { ClientRatingsTable.partnerId eq partnerId }
            .orderBy(ClientRatingsTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row ->
                ReviewDto(
                    id = row[ClientRatingsTable.id],
                    rating = row[ClientRatingsTable.rating],
                    tags = row[ClientRatingsTable.tags]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    comment = null,
                    createdAt = row[ClientRatingsTable.createdAt],
                    authorName = "Cashier", // Ideally join Cashier -> User to get name, but for now simple
                    targetName = row[UsersTable.firstName] ?: "Client",
                    targetPhone = row[UsersTable.phoneNumber],
                    pointName = row[TradingPointsTable.name],
                    type = ReviewTypes.CASHIER_TO_CLIENT
                )
            }
    }

    suspend fun getAnalyticsData(partnerId: String, from: Long? = null, to: Long? = null, pointId: String? = null): AnalyticsDataDto = dbQuery {
        // 1. NPS / Average Rating
        val baseQuery = ServiceReviewsTable
            .slice(ServiceReviewsTable.rating, ServiceReviewsTable.createdAt, ServiceReviewsTable.tradingPointId)
            .select { ServiceReviewsTable.partnerId eq partnerId }
            .let { query ->
                var q = query
                from?.let { q = q.andWhere { ServiceReviewsTable.createdAt greaterEq it } }
                to?.let { q = q.andWhere { ServiceReviewsTable.createdAt lessEq it } }
                pointId?.let { q = q.andWhere { ServiceReviewsTable.tradingPointId eq it } }
                q
            }
            .toList()

        val ratings = baseQuery.map { it[ServiceReviewsTable.rating] }

        val total = ratings.size
        val avgRating = if (total > 0) ratings.average() else 0.0
        
        // NPS-like: Promoters (5), Passives (4), Detractors (1-3)
        val promoters = ratings.count { it == 5 }
        val detractors = ratings.count { it <= 3 }
        val nps = if (total > 0) ((promoters - detractors).toDouble() / total * 100).toInt() else 0

        // Series by day
        val series = baseQuery
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

        // 2. Heatmap (Tags count per Point)
        val rows = ServiceReviewsTable
            .slice(ServiceReviewsTable.tradingPointId, ServiceReviewsTable.tags)
            .select { (ServiceReviewsTable.partnerId eq partnerId) and (ServiceReviewsTable.tags.isNotNull()) }
            .let { query ->
                var q = query
                from?.let { q = q.andWhere { ServiceReviewsTable.createdAt greaterEq it } }
                to?.let { q = q.andWhere { ServiceReviewsTable.createdAt lessEq it } }
                pointId?.let { q = q.andWhere { ServiceReviewsTable.tradingPointId eq it } }
                q
            }
            .map { it[ServiceReviewsTable.tradingPointId] to (it[ServiceReviewsTable.tags] ?: "") }

        val pointNames = TradingPointsTable
            .slice(TradingPointsTable.id, TradingPointsTable.name)
            .select { TradingPointsTable.partnerId eq partnerId }
            .associate { it[TradingPointsTable.id] to it[TradingPointsTable.name] }

        val heatmap = rows.flatMap { (pointId, tagsStr) ->
            tagsStr.split(",").filter { it.isNotBlank() }.map { tag -> pointId to tag }
        }.groupBy { it.first } // Group by PointID
         .map { (pointId, list) ->
             val pointName = pointNames[pointId] ?: "Unknown"
             val tagCounts = list.groupingBy { it.second }.eachCount()
             
             HeatmapPointDto(
                 pointId = pointId,
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

    private fun rowToClientRating(row: ResultRow): ClientRatingEntity {
        return ClientRatingEntity(
            rating = row[ClientRatingsTable.rating],
            tags = row[ClientRatingsTable.tags]?.split(",")?.filter { it.isNotBlank() }?.map { ClientRatingTag.valueOf(it) } ?: emptyList()
        )
    }

    data class ClientRatingEntity(
        val rating: Int,
        val tags: List<ClientRatingTag>
    )

    suspend fun getPartnerIdByPointId(pointId: String): String? = dbQuery {
        TradingPointsTable
            .select { TradingPointsTable.id eq pointId }
            .map { it[TradingPointsTable.partnerId] }
            .singleOrNull()
    }
}
