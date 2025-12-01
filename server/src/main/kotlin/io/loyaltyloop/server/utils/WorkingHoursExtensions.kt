package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.WeekDay
import io.loyaltyloop.shared.models.WeeklyScheduleDto
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private const val DEFAULT_TIMEZONE = "Asia/Bishkek"

fun WeeklyScheduleDto?.isOpen(at: Instant = Instant.now()): Boolean? {
    this ?: return null
    val zoneId = zoneIdSafe(timezone)
    val zoned = at.atZone(zoneId)
    val weekDay = zoned.dayOfWeek.toWeekDay()
    val minutes = zoned.hour * 60 + zoned.minute

    val daySchedule = days.firstOrNull { it.day == weekDay } ?: return false
    if (daySchedule.intervals.isEmpty()) return false

    return daySchedule.intervals.any { interval ->
        val start = interval.opensAt.toMinutes() ?: return@any false
        val end = interval.closesAt.toMinutes() ?: return@any false

        if (start == end) return@any true // 24/7
        if (end > start) {
            minutes in start until end
        } else {
            minutes >= start || minutes < end // overlaps midnight
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

