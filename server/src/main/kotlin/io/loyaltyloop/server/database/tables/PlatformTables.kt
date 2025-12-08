package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object PlatformSubscriptionsTable : Table("platform_subscriptions") {
    val id = varchar("id", 50)
    val pointId = varchar("point_id", 50).references(TradingPointsTable.id) // Strict NOT NULL
    val requesterId = varchar("requester_id", 50).nullable() // Manager who activated it
    val type = varchar("type", 20) // FIXED_TERM, REV_SHARE
    val startDate = long("start_date")
    val endDate = long("end_date").nullable()
    val amount = double("amount")
    val isTrial = bool("is_trial").default(false)
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PlatformRequestsTable : Table("platform_requests") {
    val id = varchar("id", 50)
    val type = varchar("type", 30) // ACTIVATE_POINT, BLOCK_PARTNER...
    val status = varchar("status", 20) // PENDING, APPROVED, REJECTED

    val requesterId = varchar("requester_id", 50).references(UsersTable.id)
    val approverId = varchar("approver_id", 50).references(UsersTable.id).nullable()

    val targetPointId = varchar("target_point_id", 50).references(TradingPointsTable.id)
        .nullable() // Nullable for Partner-level actions

    // Payload fields stored directly for simplicity (or could use JSON text column)
    val amount = double("amount").nullable()
    val duration = varchar("duration", 20).nullable() // SubscriptionDuration enum name
    val isTrial = bool("is_trial").default(false)
    val blockReason = varchar("block_reason", 255).nullable()

    val rejectReason = varchar("reject_reason", 255).nullable()

    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PlatformInvitesTable : Table("platform_invites") {
    val code = varchar("code", 20)
    val role = varchar("role", 50) // SUPER_MANAGER or MANAGER
    val createdBy = varchar("created_by", 50).references(UsersTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(code)
}
