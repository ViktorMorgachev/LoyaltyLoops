package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.SystemEventsTable
import io.loyaltyloop.server.models.SystemEvent
import io.loyaltyloop.server.models.SystemEventFilter
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcLocalDateTime
import io.loyaltyloop.server.utils.toUtcMillis
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

// TODO checked
class SystemEventRepository {

    suspend fun logEvent(
        type: SystemEventType,
        userId: String? = null,
        userPhone: String? = null,
        partnerId: String? = null,
        payload: String? = null
    ): SystemEvent = dbQuery {
        val userUuid = try { userId?.let { UUID.fromString(it) } } catch (_: Exception) { null }
        val partnerUuid = try { partnerId?.let { UUID.fromString(it) } } catch (_: Exception) { null }
        val now = nowUtc()

        val newId = SystemEventsTable.insertAndGetId {
            it[this.type] = type
            it[this.user] = userUuid
            it[this.partner] = partnerUuid
            it[this.userPhoneSnapshot] = userPhone
            it[this.payload] = payload
            it[this.createdAt] = now
        }

        SystemEvent(
            id = newId.value.toString(),
            type = type,
            userId = userId,
            userPhone = userPhone,
            partnerId = partnerId,
            payload = payload,
            timestamp = now.toUtcMillis()
        )
    }

    suspend fun countEvents(
        type: SystemEventType,
        userPhone: String,
        since: Long
    ): Long = dbQuery {
        val sinceDate = since.toUtcLocalDateTime()

        SystemEventsTable
            .select {
                (SystemEventsTable.type eq type) and
                        (SystemEventsTable.userPhoneSnapshot eq userPhone) and
                        (SystemEventsTable.createdAt greaterEq sinceDate)
            }
            .count()
    }


    suspend fun getEvents(filter: SystemEventFilter): List<SystemEvent> = dbQuery {
        val query = SystemEventsTable.selectAll()

        if (filter.type != null) {
            query.andWhere { SystemEventsTable.type eq filter.type }
        }
        if (filter.userId != null) {
            try {
                query.andWhere { SystemEventsTable.user eq filter.userId.toUUID() }
            } catch (_: Exception) {
                query.andWhere { Op.FALSE }
            }
        }
        if (filter.partnerId != null) {
            try {
                query.andWhere { SystemEventsTable.partner eq filter.partnerId.toUUID() }
            } catch (_: Exception) {
                query.andWhere { Op.FALSE }
            }
        }
        if (filter.userPhone != null) {
            query.andWhere { SystemEventsTable.userPhoneSnapshot like "%${filter.userPhone}%" }
        }

        if (filter.from != null) {
            query.andWhere { SystemEventsTable.createdAt greaterEq filter.from.toUtcLocalDateTime() }
        }
        if (filter.to != null) {
            query.andWhere { SystemEventsTable.createdAt lessEq filter.to.toUtcLocalDateTime() }
        }

        query.orderBy(SystemEventsTable.createdAt to SortOrder.DESC)
            .limit(filter.limit, offset = filter.offset)
            .map { row ->
                SystemEvent(
                    id = row[SystemEventsTable.id].value.toString(),
                    type = row[SystemEventsTable.type],
                    userId = row[SystemEventsTable.user]?.value?.toString(),
                    userPhone = row[SystemEventsTable.userPhoneSnapshot],
                    partnerId = row[SystemEventsTable.partner]?.value?.toString(),
                    payload = row[SystemEventsTable.payload],
                    timestamp = row[SystemEventsTable.createdAt].toUtcMillis()
                )
            }
    }
}

