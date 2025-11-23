package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object TransactionsHistoryTable : Table("transactions_history") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50).index()
    val tradingPointId = varchar("trading_point_id", 50).index()
    val cashierId = varchar("cashier_id", 50)
    
    // Тип операции: "VISIT", "EARN", "SPEND"
    val type = varchar("type", 20)
    
    // Детали
    val amount = double("amount").default(0.0) // Сумма чека (если есть)
    val pointsDelta = double("points_delta").default(0.0) // Изменение баланса (+50 или -100)
    val visitsDelta = integer("visits_delta").default(0)  // Изменение визитов (+1)
    
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

