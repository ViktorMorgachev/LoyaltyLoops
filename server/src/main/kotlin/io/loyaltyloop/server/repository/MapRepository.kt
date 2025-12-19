package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.models.TradingPointSearchCriteria
import io.loyaltyloop.server.models.matches
import io.loyaltyloop.server.utils.EARTH_RADIUS_METERS
import io.loyaltyloop.server.utils.haversineMeters
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toTradingPointDto
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.TradingPointSearchResponse
import io.loyaltyloop.shared.models.WeekDay
import io.loyaltyloop.shared.models.WeeklyScheduleDto
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.cos

// TODO checked
private const val DEFAULT_TIMEZONE = "Asia/Bishkek"
class MapRepository {

    suspend fun searchPublicPoints(
        criteria: TradingPointSearchCriteria,
        offset: Long = 0,
        timezone: String,
    ): TradingPointSearchResponse = dbQuery {
        val latDelta = Math.toDegrees((criteria.radiusMeters / EARTH_RADIUS_METERS) * 1.1)
        val minLat = criteria.latitude - latDelta
        val maxLat = criteria.latitude + latDelta

        val lonDelta = Math.toDegrees((criteria.radiusMeters / EARTH_RADIUS_METERS) * 1.1 / cos(Math.toRadians(criteria.latitude)))
        val minLon = criteria.longitude - lonDelta
        val maxLon = criteria.longitude + lonDelta

        val query = TradingPointsTable.innerJoin(PartnersTable)
            .selectAll()
            .where {
                (TradingPointsTable.isActive eq true) and
                        (PartnersTable.status eq PartnerStatus.ACTIVE) and
                        (TradingPointsTable.latitude.between(minLat, maxLat)) and
                        (TradingPointsTable.longitude.between(minLon, maxLon))
            }

        val filteredPoints = query.mapNotNull { row ->
            val pLat = row[TradingPointsTable.latitude] ?: return@mapNotNull null
            val pLon = row[TradingPointsTable.longitude] ?: return@mapNotNull null

            val distance = haversineMeters(criteria.latitude, criteria.longitude, pLat, pLon)

            if (distance > criteria.radiusMeters) return@mapNotNull null

            val isPaused = row[TradingPointsTable.isTemporarilyPaused]
            val dbTimezone = row[TradingPointsTable.timezone]

            val rawJson = row[TradingPointsTable.workingHoursJson]
            var scheduleDto = try {
                if (!rawJson.isNullOrBlank()) {
                    Json.decodeFromString<WeeklyScheduleDto>(rawJson)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            scheduleDto = scheduleDto?.copy(timezone = dbTimezone)

            val isOpenNow = if (isPaused) false else scheduleDto.isOpen(timezone,nowUtc()) ?: false

            val dto = row.toTradingPointDto().copy(
                distanceMeters = distance,
                isOpenNow = isOpenNow
            )

            if (!criteria.matches(dto)) return@mapNotNull null

            dto
        }
            .sortedBy { it.distanceMeters }

        val totalCount = filteredPoints.size
        val fromIndex = offset.toInt().coerceAtMost(totalCount)
        val toIndex = (fromIndex + criteria.limit).coerceAtMost(totalCount)

        val page = filteredPoints.subList(fromIndex, toIndex)
        val hasMore = toIndex < totalCount

        TradingPointSearchResponse(
            points = page,
            total = totalCount,
            radiusMeters = criteria.radiusMeters,
            limit = criteria.limit,
            hasMore = hasMore
        )
    }



 private fun WeeklyScheduleDto?.isOpen(timezone: String, at: LocalDateTime): Boolean? {
        this ?: return null
        val zoneId = zoneIdSafe(timezone)
        val zoned = at.atZone(zoneId)
        val weekDay = zoned.dayOfWeek.toWeekDay()
        val minutes = zoned.hour * 60 + zoned.minute

        // 1. Находим расписание для ТЕКУЩЕГО дня недели в часовом поясе точки
        val daySchedule = days.firstOrNull { it.day == weekDay } ?: return false

        // Если на сегодня нет интервалов, значит закрыто (предполагаем, что ночные смены разбиты на два дня)
        if (daySchedule.intervals.isEmpty()) return false

        return daySchedule.intervals.any { interval ->
            val start = interval.opensAt.toMinutes() ?: return@any false
            val end = interval.closesAt.toMinutes() ?: return@any false

            if (start == end) return@any true // Круглосуточно (24/7), если так договорились обозначать

            if (end > start) {
                // Обычный интервал внутри дня (например 09:00 - 18:00)
                minutes in start until end
            } else {
                // Переход через полночь (например 22:00 - 02:00).
                // ВАЖНО: Если данные разбиты корректно (Пн 22-24, Вт 00-02), то этот блок else вообще не должен срабатывать,
                // так как 23:59 > 22:00 и 02:00 > 00:00.
                // Но если вдруг прилетел "сырой" интервал, обрабатываем его как "от start до конца дня ИЛИ от начала дня до end"
                minutes >= start || minutes < end
            }
        }
    }

    private fun zoneIdSafe(raw: String?): ZoneId =
        runCatching { ZoneId.of(raw?.takeIf { it.isNotBlank() } ?: DEFAULT_TIMEZONE) }
            .getOrElse { ZoneId.of(DEFAULT_TIMEZONE) }

    private fun String.toMinutes(): Int? =
        runCatching { LocalTime.parse(this).run { hour * 60 + minute } }.getOrNull()

    private fun DayOfWeek.toWeekDay(): WeekDay =
        when (this) {
            DayOfWeek.MONDAY -> WeekDay.MONDAY
            DayOfWeek.TUESDAY -> WeekDay.TUESDAY
            DayOfWeek.WEDNESDAY -> WeekDay.WEDNESDAY
            DayOfWeek.THURSDAY -> WeekDay.THURSDAY
            DayOfWeek.FRIDAY -> WeekDay.FRIDAY
            DayOfWeek.SATURDAY -> WeekDay.SATURDAY
            DayOfWeek.SUNDAY -> WeekDay.SUNDAY
        }
}
