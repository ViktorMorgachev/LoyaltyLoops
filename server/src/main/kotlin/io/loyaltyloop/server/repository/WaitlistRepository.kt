package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.WaitlistTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

// TODO checked
class WaitlistRepository {
    suspend fun add(emailInput: String) = dbQuery {
        val exists = WaitlistTable.select { WaitlistTable.email eq emailInput }.count() > 0

        if (!exists) {
            WaitlistTable.insert {
                it[email] = emailInput
                it[isInvited] = false
            }
        }
    }

    suspend fun hasMail(emailInput: String): Boolean = dbQuery {
        WaitlistTable
            .select { WaitlistTable.email eq emailInput }
            .count() > 0
    }

    suspend fun getNextBatchToInvite(limit: Int): List<String> = dbQuery {
        WaitlistTable
            .slice(WaitlistTable.email)
            .select { WaitlistTable.isInvited eq false }
            .orderBy(WaitlistTable.createdAt, SortOrder.ASC)
            .limit(limit)
            .map { it[WaitlistTable.email] }
    }

    suspend fun markAsInvited(emails: List<String>) = dbQuery {
        WaitlistTable.update({ WaitlistTable.email inList emails }) {
            it[isInvited] = true
        }
    }
}

