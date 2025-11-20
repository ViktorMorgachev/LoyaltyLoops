package io.loyaltyloop.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.loyaltyloop.server.database.tables.UsersTable
import io.ktor.server.config.*
import io.loyaltyloop.server.database.tables.CashiersTable
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet


object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val driverClassName = config.property("storage.driverClassName").getString()
        val jdbcUrl = config.property("storage.jdbcUrl").getString()
        val user = config.property("storage.user").getString()
        val password = config.property("storage.password").getString()

        val hikariConfig = HikariConfig().apply {
            this.driverClassName = driverClassName
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password

            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create( UsersTable,
                PartnersTable,
                TradingPointsTable,
                CashiersTable,
                LoyaltyCardTable,
                RefreshTokensTable
            )
        }
    }

    suspend fun ping(): Boolean {
        return try {
            // Пытаемся выполнить легкий запрос
            dbQuery {
                // exec выполняет "сырой" SQL
                TransactionManager.current().exec("SELECT 1") { rs: ResultSet ->
                    rs.next() // Просто проверяем, что что-то вернулось
                }
            }
            true // Если не вылетело исключение - база жива
        } catch (e: Exception) {
            println("Database Ping Failed: ${e.message}")
            false
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            // --- ВКЛЮЧАЕМ ЛОГИ SQL ---
            addLogger(StdOutSqlLogger)
            // ------------------------
            block()
        }
}