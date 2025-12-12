package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.WaitlistTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

class WaitlistRepository {
    suspend fun add(email: String) = dbQuery {
        WaitlistTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.email] = email
        }
    }

    suspend fun hasMail(email: String): Boolean = dbQuery {
        !WaitlistTable
            .selectAll().where { WaitlistTable.email eq email }
            .empty()
    }
}

