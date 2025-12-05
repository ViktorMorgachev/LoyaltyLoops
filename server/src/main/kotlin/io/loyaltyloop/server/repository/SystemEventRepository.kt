package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.SystemEventsTable
import io.loyaltyloop.server.models.SystemEvent
import io.loyaltyloop.server.models.SystemEventFilter
import io.loyaltyloop.server.models.SystemEventType
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class SystemEventRepository {

    suspend fun logEvent(
        type: SystemEventType,
        userId: String? = null,
        userPhone: String? = null,
        partnerId: String? = null,
        payload: String? = null
    ): SystemEvent = newSuspendedTransaction(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        SystemEventsTable.insert {
            it[id] = newId
            it[this.type] = type.name
            it[this.userId] = userId
            it[this.userPhone] = userPhone
            it[this.partnerId] = partnerId
            it[this.payload] = payload
            it[this.timestamp] = now
        }

        SystemEvent(newId, type, userId, userPhone, partnerId, payload, now)
    }

    suspend fun countEvents(
        type: SystemEventType,
        userPhone: String,
        since: Long
    ): Long = newSuspendedTransaction(Dispatchers.IO) {
        SystemEventsTable.select {
            (SystemEventsTable.type eq type.name) and
            (SystemEventsTable.userPhone eq userPhone) and
            (SystemEventsTable.timestamp greaterEq since)
        }.count()
    }

    suspend fun getEvents(filter: SystemEventFilter): List<SystemEvent> = newSuspendedTransaction(Dispatchers.IO) {
        val query = SystemEventsTable.selectAll()

        filter.type?.let { query.andWhere { SystemEventsTable.type eq it.name } }
        filter.userId?.let { query.andWhere { SystemEventsTable.userId like "%$it%" } }
        filter.userPhone?.let { query.andWhere { SystemEventsTable.userPhone like "%$it%" } }
        filter.partnerId?.let { query.andWhere { SystemEventsTable.partnerId eq it } }
        filter.from?.let { query.andWhere { SystemEventsTable.timestamp greaterEq it } }
        filter.to?.let { query.andWhere { SystemEventsTable.timestamp lessEq it } }

        query.orderBy(SystemEventsTable.timestamp to SortOrder.DESC)
            .limit(filter.limit, offset = filter.offset)
            .map {
                SystemEvent(
                    id = it[SystemEventsTable.id],
                    type = SystemEventType.valueOf(it[SystemEventsTable.type]),
                    userId = it[SystemEventsTable.userId],
                    userPhone = it[SystemEventsTable.userPhone],
                    partnerId = it[SystemEventsTable.partnerId],
                    payload = it[SystemEventsTable.payload],
                    timestamp = it[SystemEventsTable.timestamp]
                )
            }
    }
}

