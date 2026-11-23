package com.pace.app.data.sync

import androidx.room.withTransaction
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.repository.RoomActivityRepository

data class PaceDataLinkReport(
    val activities: Int,
    val templates: Int,
    val plannedSessions: Int,
    val goals: Int,
    val personalRecords: Int,
    val savedRoutes: Int,
)

/** Moves the local profile into one authenticated account only after explicit user consent. */
class PaceAccountDataLinker(private val database: PaceDatabase) {
    suspend fun linkLocalData(userId: String): PaceDataLinkReport {
        require(runCatching { java.util.UUID.fromString(userId) }.isSuccess) { "Account ID is invalid." }
        require(database.activityDao().countActiveRecordingsAcrossOwners() == 0) {
            "Finish or discard every active recording before linking local data."
        }
        val report = database.withTransaction {
            val sync = database.syncOperationDao()
            val activities = sync.linkLocalActivities(userId)
            val templates = sync.linkLocalTemplates(userId)
            val plans = sync.linkLocalPlannedSessions(userId)
            val goals = sync.linkLocalGoals(userId)
            val records = sync.linkLocalPersonalRecords(userId)
            val routes = sync.linkLocalSavedRoutes(userId)
            sync.clearLocalAggregates()
            sync.bindLocalPendingOperations(userId)
            sync.enqueueActivityBackfill(userId)
            sync.enqueueTemplateBackfill(userId)
            sync.enqueuePlannedSessionBackfill(userId)
            sync.enqueueGoalBackfill(userId)
            sync.enqueuePersonalRecordBackfill(userId)
            sync.enqueueSavedRouteBackfill(userId)
            PaceDataLinkReport(activities, templates, plans, goals, records, routes)
        }
        RoomActivityRepository(database).recomputeAggregateCache(userId)
        return report
    }
}
