package com.pace.app.data.sync

import androidx.room.withTransaction
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.repository.RoomActivityRepository
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.GoalEntity
import com.pace.app.data.local.entity.PersonalRecordEntity
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.data.local.entity.SavedRouteEntity
import com.pace.app.data.local.entity.SavedRoutePointEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import com.pace.app.domain.sync.ActivityTrackArchiveCodec
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PaceRestoreProgress(val table: String, val rowsRead: Int, val rowsApplied: Int)
data class PaceRestoreReport(val rowsRead: Int, val rowsApplied: Int, val rowsKeptLocal: Int, val tracksRestored: Int)

/**
 * Restores an authenticated backup into Room without enqueueing upload mutations.
 * Callers must first enforce per-account local database isolation and explicit
 * restore consent; those gates intentionally stay outside this coordinator.
 */
class PaceRestoreCoordinator(
    private val database: PaceDatabase,
    private val cloud: SupabaseBackupClient,
) {
    suspend fun restore(
        userId: String,
        accessToken: String,
        onProgress: suspend (PaceRestoreProgress) -> Unit = {},
    ): PaceRestoreReport {
        require(userId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Backup account ID is invalid." }
        var read = 0
        var applied = 0
        var keptLocal = 0
        var tracks = 0

        suspend fun rows(table: String, apply: suspend (JSONObject) -> Boolean) {
            var offset = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = cloud.fetchPage(table, accessToken, offset)
                for (index in 0 until page.length()) {
                    currentCoroutineContext().ensureActive()
                    val row = page.getJSONObject(index)
                    require(row.optString("user_id") == userId) { "Cloud row owner did not match the signed-in account." }
                    read++
                    val changed = apply(row)
                    if (changed) applied++ else keptLocal++
                    onProgress(PaceRestoreProgress(table, read, applied))
                }
                if (page.length() < 500) break
                offset += page.length()
            }
        }

        // Parent templates and activity IDs are restored before dependent rows.
        rows("pace_workout_templates") { row ->
            val id = row.getString("template_id")
            val updatedAt = row.getLong("updated_at_epoch_millis")
            val dao = database.workoutDao()
            val local = dao.getTemplateIncludingDeleted(id, userId)
            if (!BackupRestorePolicy.shouldApply(local?.updatedAtEpochMillis, updatedAt)) return@rows false
            val template = WorkoutTemplateEntity(
                id = id,
                ownerId = userId,
                name = row.getString("name"),
                sport = row.getString("sport"),
                description = row.nullableString("description"),
                targetDurationSeconds = row.nullableLong("target_duration_seconds"),
                targetDistanceMeters = row.nullableDouble("target_distance_meters"),
                createdAtEpochMillis = row.getLong("created_at_epoch_millis"),
                updatedAtEpochMillis = updatedAt,
                deletedAtEpochMillis = row.nullableLong("deleted_at_epoch_millis"),
            )
            val intervalArray = row.optJSONArray("intervals") ?: JSONArray()
            val intervals = buildList {
                for (index in 0 until intervalArray.length()) {
                    val interval = intervalArray.getJSONObject(index)
                    add(WorkoutIntervalEntity(
                        templateId = id,
                        position = interval.getInt("position"),
                        intervalType = interval.getString("interval_type"),
                        repeatCount = interval.optInt("repeat_count", 1),
                        durationSeconds = interval.nullableLong("duration_seconds"),
                        targetMetric = interval.nullableString("target_metric"),
                        targetMin = interval.nullableDouble("target_min"),
                        targetMax = interval.nullableDouble("target_max"),
                    ))
                }
            }
            database.withTransaction {
                dao.upsertTemplate(template)
                dao.deleteIntervals(id)
                if (intervals.isNotEmpty()) dao.upsertIntervals(intervals)
            }
            true
        }

        rows("pace_activities") { row ->
            val id = row.getString("activity_id")
            val updatedAt = row.getLong("updated_at_epoch_millis")
            val dao = database.activityDao()
            val local = dao.getIncludingDeleted(id, userId)
            val deletedAt = row.nullableLong("deleted_at_epoch_millis")
            val objectPath = row.nullableString("track_object_path")
            val localHasTrack = local != null && database.trackPointDao().countForActivity(id) > 0
            val remoteHasTrack = deletedAt == null && objectPath != null
            if (!BackupRestorePolicy.shouldApplyActivity(local?.updatedAtEpochMillis, updatedAt, remoteHasTrack, localHasTrack)) return@rows false
            val points = if (deletedAt == null && objectPath != null) {
                val expectedHash = row.getString("track_sha256")
                val formatVersion = row.getInt("track_format_version")
                require(formatVersion == ActivityTrackArchiveCodec.FORMAT_VERSION) { "Track archive version is unsupported." }
                val bytes = cloud.downloadTrack(accessToken, userId, objectPath)
                require(ActivityTrackArchiveCodec.sha256(bytes).equals(expectedHash, ignoreCase = true)) { "Track archive checksum did not match." }
                ActivityTrackArchiveCodec.decode(bytes, id)
            } else null
            val entity = ActivityEntity(
                id = id,
                ownerId = userId,
                sport = row.getString("sport"),
                status = "SAVED",
                startedAtEpochMillis = row.getLong("started_at_epoch_millis"),
                endedAtEpochMillis = row.nullableLong("ended_at_epoch_millis"),
                elapsedSeconds = row.getLong("elapsed_seconds"),
                movingSeconds = row.getLong("moving_seconds"),
                distanceMeters = row.getLong("distance_meters"),
                elevationGainMeters = row.nullableDouble("elevation_gain_meters"),
                title = row.nullableString("title"),
                notes = row.nullableString("notes"),
                dataQualityFlags = row.optString("data_quality_flags", ""),
                createdAtEpochMillis = row.getLong("created_at_epoch_millis"),
                updatedAtEpochMillis = updatedAt,
                deletedAtEpochMillis = deletedAt,
            )
            database.withTransaction {
                dao.upsert(entity)
                database.trackPointDao().deleteForActivity(id)
                database.splitDao().deleteForActivity(id)
                database.sensorSampleDao().deleteForActivity(id)
                if (points != null) {
                    if (points.trackPoints.isNotEmpty()) database.trackPointDao().insertAll(points.trackPoints)
                    if (points.splits.isNotEmpty()) database.splitDao().insertAll(points.splits)
                    if (points.sensorSamples.isNotEmpty()) database.sensorSampleDao().insertAll(points.sensorSamples)
                }
            }
            if (points != null) tracks++
            true
        }

        rows("pace_goals") { row ->
            val id = row.getString("goal_id")
            val updatedAt = row.getLong("updated_at_epoch_millis")
            val dao = database.goalDao()
            val local = dao.getIncludingDeleted(id, userId)
            if (!BackupRestorePolicy.shouldApply(local?.updatedAtEpochMillis, updatedAt)) return@rows false
            dao.upsert(GoalEntity(
                id = id,
                ownerId = userId,
                goalType = row.getString("goal_type"),
                sport = row.nullableString("sport"),
                targetValue = row.getDouble("target_value"),
                unit = row.getString("unit"),
                startDate = row.getString("start_date"),
                endDate = row.getString("end_date"),
                createdAtEpochMillis = row.getLong("created_at_epoch_millis"),
                updatedAtEpochMillis = updatedAt,
                deletedAtEpochMillis = row.nullableLong("deleted_at_epoch_millis"),
            ))
            true
        }

        rows("pace_personal_records") { row ->
            val id = row.getString("record_id")
            val updatedAt = row.getLong("updated_at_epoch_millis")
            val dao = database.goalDao()
            val local = dao.getPersonalRecordIncludingDeleted(id, userId)
            if (!BackupRestorePolicy.shouldApply(local?.updatedAtEpochMillis, updatedAt)) return@rows false
            dao.upsertPersonalRecord(PersonalRecordEntity(
                id = id,
                ownerId = userId,
                sport = row.getString("sport"),
                metric = row.getString("metric"),
                distanceMeters = row.nullableDouble("distance_meters"),
                recordValue = row.getDouble("record_value"),
                unit = row.getString("unit"),
                achievedAtEpochMillis = row.getLong("achieved_at_epoch_millis"),
                activityId = row.nullableString("activity_id"),
                provenance = row.optString("provenance", "RESTORED"),
                deletedAtEpochMillis = row.nullableLong("deleted_at_epoch_millis"),
                updatedAtEpochMillis = updatedAt,
            ))
            true
        }

        rows("pace_planned_sessions") { row ->
            val id = row.getString("session_id")
            val updatedAt = row.getLong("updated_at_epoch_millis")
            val dao = database.workoutDao()
            val local = dao.getPlannedSessionIncludingDeleted(id, userId)
            if (!BackupRestorePolicy.shouldApply(local?.updatedAtEpochMillis, updatedAt)) return@rows false
            dao.upsertPlannedSession(PlannedSessionEntity(
                id = id,
                ownerId = userId,
                templateId = row.nullableString("template_id"),
                scheduledDate = row.getString("scheduled_date"),
                timeZoneId = row.getString("time_zone_id"),
                reminderEnabled = row.optBoolean("reminder_enabled", false),
                status = row.getString("status"),
                completedActivityId = row.nullableString("completed_activity_id"),
                notes = row.nullableString("notes"),
                updatedAtEpochMillis = updatedAt,
                deletedAtEpochMillis = row.nullableLong("deleted_at_epoch_millis"),
            ))
            true
        }

        rows("pace_saved_routes") { row ->
            val id = row.getString("route_id")
            val updatedAt = row.getLong("updated_at_epoch_millis")
            val dao = database.savedRouteDao()
            val local = dao.getIncludingDeleted(id, userId)
            if (!BackupRestorePolicy.shouldApply(local?.updatedAtEpochMillis, updatedAt)) return@rows false
            val deletedAt = row.nullableLong("deleted_at_epoch_millis")
            val objectPath = row.nullableString("route_object_path")
            val points = if (deletedAt == null && objectPath != null) {
                val expectedHash = row.getString("route_sha256")
                val formatVersion = row.getInt("route_format_version")
                require(formatVersion == ActivityTrackArchiveCodec.FORMAT_VERSION) { "Route archive version is unsupported." }
                val bytes = cloud.downloadTrack(accessToken, userId, objectPath)
                require(ActivityTrackArchiveCodec.sha256(bytes).equals(expectedHash, ignoreCase = true)) { "Route archive checksum did not match." }
                ActivityTrackArchiveCodec.decode(bytes, id).trackPoints.map { point ->
                    SavedRoutePointEntity(id, point.sequence, point.latitude, point.longitude, point.altitudeMeters, point.segmentIndex)
                }
            } else emptyList()
            val route = SavedRouteEntity(
                id = id,
                title = row.getString("title"),
                notes = row.nullableString("notes"),
                sport = row.getString("sport"),
                distanceMeters = row.getDouble("distance_meters"),
                sourceActivityId = row.nullableString("source_activity_id"),
                createdAtEpochMillis = row.getLong("created_at_epoch_millis"),
                updatedAtEpochMillis = updatedAt,
                deletedAtEpochMillis = deletedAt,
                ownerId = userId,
            )
            database.withTransaction {
                dao.upsert(route)
                dao.clearPoints(id)
                if (points.isNotEmpty()) dao.insertPoints(points)
            }
            true
        }

        RoomActivityRepository(database).recomputeAggregateCache(ownerId = userId)
        return PaceRestoreReport(read, applied, keptLocal, tracks)
    }
}

object BackupRestorePolicy {
    /** Immutable track files accompany the winning summary; metadata uses last-write-wins. */
    fun shouldApply(localUpdatedAtEpochMillis: Long?, remoteUpdatedAtEpochMillis: Long): Boolean =
        localUpdatedAtEpochMillis == null || remoteUpdatedAtEpochMillis > localUpdatedAtEpochMillis

    fun shouldApplyActivity(
        localUpdatedAtEpochMillis: Long?,
        remoteUpdatedAtEpochMillis: Long,
        remoteHasTrack: Boolean,
        localHasTrack: Boolean,
    ): Boolean = shouldApply(localUpdatedAtEpochMillis, remoteUpdatedAtEpochMillis) ||
        (localUpdatedAtEpochMillis == remoteUpdatedAtEpochMillis && remoteHasTrack && !localHasTrack)
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)
