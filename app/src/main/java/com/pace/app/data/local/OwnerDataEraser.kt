package com.pace.app.data.local

import androidx.room.withTransaction

/** Erases one authenticated owner's local data while preserving local-only and other account rows. */
class OwnerDataEraser(private val database: PaceDatabase) {
    suspend fun erase(ownerId: String) {
        require(runCatching { java.util.UUID.fromString(ownerId) }.isSuccess) { "Account ID is invalid." }
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            listOf(
                "home_workout_drafts",
                "saved_routes",
                "planned_sessions",
                "activities",
                "workout_templates",
                "goals",
                "personal_records",
                "activity_aggregate_cache",
            ).forEach { table -> db.execSQL("DELETE FROM $table WHERE owner_id = ?", arrayOf(ownerId)) }
            db.execSQL("DELETE FROM sync_operations WHERE account_user_id = ?", arrayOf(ownerId))
        }
    }
}
