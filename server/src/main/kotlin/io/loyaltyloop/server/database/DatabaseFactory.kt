package io.loyaltyloop.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.loyaltyloop.server.database.tables.UsersTable
import io.ktor.server.config.*
import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.DeviceTokensTable
import io.loyaltyloop.server.database.tables.PinResetTokensTable
import io.loyaltyloop.server.database.tables.SupportMessagesTable
import io.loyaltyloop.server.database.tables.SupportThreadsTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.database.tables.SystemEventsTable
import io.loyaltyloop.server.database.tables.PlatformInvitesTable
import io.loyaltyloop.server.database.tables.PlatformRequestsTable
import io.loyaltyloop.server.database.tables.PlatformSubscriptionsTable
import io.loyaltyloop.server.database.tables.ClientRatingsTable
import io.loyaltyloop.server.database.tables.ServiceReviewsTable
import io.loyaltyloop.server.database.tables.WaitlistTable
import io.loyaltyloop.server.database.tables.AuthSessionsTable
import io.loyaltyloop.server.database.tables.ExchangeRatesTable
import io.loyaltyloop.server.database.tables.LoyaltyCardsTable
import io.loyaltyloop.server.database.tables.PartnerStaffTable

object DatabaseFactory {


    private fun config(driver: String, url: String, user: String, pass: String): HikariConfig{
        return HikariConfig().apply {
            driverClassName = driver
            jdbcUrl = url
            username = user
            password = pass
            idleTimeout = 120_000 // 2 минуты простоя
            keepaliveTime = 30_000 // Пинг каждые 30 сек
            minimumIdle = 2
            maximumPoolSize = 10
            maxLifetime = 300_000
            isAutoCommit = false
            connectionInitSql = "SET TIME ZONE 'UTC'"
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
            println("🔥🔥🔥 DEBUG: Connecting to DB URL: $url, driver: ${driver}, user: ${user} pass: ${pass}") // <-- ДОБАВЬ ЭТО
        }
    }

    fun init(config: ApplicationConfig) {
        val driverClassName = config.property("storage.driverClassName").getString()
        val jdbcUrl = config.property("storage.jdbcUrl").getString()
        val user = config.property("storage.user").getString()
        val password = config.property("storage.password").getString()

        val hikariConfig = config(driver = driverClassName, url = jdbcUrl, user = user, pass = password)

        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                PartnersTable,
                TradingPointsTable,
                RefreshTokensTable,
                LoyaltySettingsTable,
                LoyaltyTiersTable,
                PartnerStaffTable,
                SystemStaffTable,
                TransactionsHistoryTable,
                PinResetTokensTable,
                SupportThreadsTable,
                SupportMessagesTable,
                DeviceTokensTable,
                SystemEventsTable,
                PlatformSubscriptionsTable,
                PlatformRequestsTable,
                PlatformInvitesTable,
                ClientRatingsTable,
                ServiceReviewsTable,
                WaitlistTable,
                AuthSessionsTable,
                ExchangeRatesTable,
                LoyaltyCardsTable
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

    // 2. Точка входа для Тестов
    fun connect(driver: String, url: String, user: String, pass: String) {
        val hikariConfig = config(driver = driver, url = url, user = user, pass = pass)
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                PartnersTable,
                TradingPointsTable,
                RefreshTokensTable,
                LoyaltySettingsTable,
                LoyaltyTiersTable,
                PartnerStaffTable,
                SystemStaffTable,
                TransactionsHistoryTable,
                PinResetTokensTable,
                SupportThreadsTable,
                SupportMessagesTable,
                DeviceTokensTable,
                SystemEventsTable,
                PlatformSubscriptionsTable,
                PlatformRequestsTable,
                PlatformInvitesTable,
                ClientRatingsTable,
                ServiceReviewsTable,
                WaitlistTable,
                AuthSessionsTable,
                ExchangeRatesTable,
                LoyaltyCardsTable
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            // --- ВКЛЮЧАЕМ ЛОГИ SQL ---
          //  addLogger(StdOutSqlLogger)
            // ------------------------
            block()
        }
}
