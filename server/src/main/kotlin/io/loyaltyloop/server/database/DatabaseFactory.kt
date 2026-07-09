package io.loyaltyloop.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import io.loyaltyloop.server.database.tables.AuthSessionsTable
import io.loyaltyloop.server.database.tables.ClientRatingsTable
import io.loyaltyloop.server.database.tables.DeviceTokensTable
import io.loyaltyloop.server.database.tables.ExchangeRatesTable
import io.loyaltyloop.server.database.tables.LoyaltyCardsTable
import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnerStaffTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.PinResetTokensTable
import io.loyaltyloop.server.database.tables.PlatformInvitesTable
import io.loyaltyloop.server.database.tables.PlatformRequestsTable
import io.loyaltyloop.server.database.tables.PlatformSubscriptionsTable
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.database.tables.ServiceReviewsTable
import io.loyaltyloop.server.database.tables.SupportMessagesTable
import io.loyaltyloop.server.database.tables.SupportThreadsTable
import io.loyaltyloop.server.database.tables.SystemEventsTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.database.tables.WaitlistTable
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

object DatabaseFactory {

    private val ALL_TABLES = arrayOf(
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
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
    }

    @Suppress("SpreadOperator") // один вызов на старте, копия массива несущественна
    fun init(config: ApplicationConfig) {
        val driverClassName = config.property("storage.driverClassName").getString()
        val jdbcUrl = config.property("storage.jdbcUrl").getString()
        val user = config.property("storage.user").getString()
        val password = config.property("storage.password").getString()

        val hikariConfig = config(driver = driverClassName, url = jdbcUrl, user = user, pass = password)

        val dataSource = HikariDataSource(hikariConfig)

        if (driverClassName.contains("postgresql")) {
            // baselineOnMigrate: существующая БД без flyway_schema_history помечается
            // версией 1 (V1__baseline.sql на ней не выполняется), дальше применяются V2+
            Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .load()
                .migrate()
            Database.connect(dataSource)
        } else {
            // Не-Postgres (H2 в тестах): Flyway-миграции написаны под Postgres,
            // схема создаётся из table objects
            Database.connect(dataSource)
            transaction {
                SchemaUtils.createMissingTablesAndColumns(*ALL_TABLES)
            }
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
    @Suppress("SpreadOperator")
    fun connect(driver: String, url: String, user: String, pass: String) {
        val hikariConfig = config(driver = driver, url = url, user = user, pass = pass)
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(*ALL_TABLES)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T {
        val transaction = TransactionManager.currentOrNull()
        return if (transaction != null && !transaction.connection.isClosed) {
            block()
        } else {
            newSuspendedTransaction(Dispatchers.IO) {
                // --- ВКЛЮЧАЕМ ЛОГИ SQL ---
                //  addLogger(StdOutSqlLogger)
                // ------------------------
                block()
            }
        }
    }
}
