package com.pace.app.data.local.repository

import androidx.room.withTransaction
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.dao.ActivityWithTrack
import com.pace.app.data.local.dao.WorkoutTemplateWithIntervals
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.GoalEntity
import com.pace.app.data.local.entity.MapPackMetadataEntity
import com.pace.app.data.local.entity.PersonalRecordEntity
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.data.local.entity.SensorSampleEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.SyncOperationEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import com.pace.app.domain.repository.ActivityRepository
import com.pace.app.domain.repository.FinalizedActivity
import com.pace.app.domain.repository.GoalRepository
import com.pace.app.domain.repository.MapPackRepository
import com.pace.app.domain.repository.SyncOperationRepository
import com.pace.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow

class RoomActivityRepository(private val database: PaceDatabase) : ActivityRepository {
    private val activities = database.activityDao()
    private val points = database.trackPointDao()
    private val splits = database.splitDao()
    private val sensors = database.sensorSampleDao()
    private val sync = database.syncOperationDao()

    override fun observeHistory(ownerId: String): Flow<List<ActivityEntity>> = activities.observeHistory(ownerId)

    override suspend fun getActivity(id: String): ActivityWithTrack? = activities.getWithTrack(id)

    override suspend fun page(
        ownerId: String,
        sport: String?,
        query: String?,
        beforeStartedAt: Long?,
        beforeId: String,
        limit: Int,
    ): List<ActivityEntity> {
        require(limit in 1..200) { "Page size must be between 1 and 200." }
        return activities.page(ownerId, sport, query?.trim()?.takeIf(String::isNotEmpty), beforeStartedAt, beforeId, limit)
    }

    override suspend fun finalizeActivity(activity: FinalizedActivity) {
        val summary = activity.summary
        require(summary.status == "SAVED") { "Only finalized activity summaries can be saved." }
        require(summary.deletedAtEpochMillis == null) { "A deleted activity cannot be finalized." }
        require(summary.elapsedSeconds >= 0 && summary.movingSeconds in 0..summary.elapsedSeconds) {
            "Activity durations must be non-negative and moving time cannot exceed elapsed time."
        }
        require(summary.distanceMeters >= 0) { "Activity distance cannot be negative." }
        require(activity.trackPoints.all { it.activityId == summary.id })
        require(activity.splits.all { it.activityId == summary.id })
        require(activity.sensorSamples.all { it.activityId == summary.id })
        require(activity.trackPoints.map { it.sequence }.distinct().size == activity.trackPoints.size)
        require(activity.trackPoints.all { it.sequence >= 0 && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 })
        require(activity.syncOperation == null || activity.syncOperation.entityId == summary.id)

        database.withTransaction {
            val existing = activities.getIncludingDeleted(summary.id)
            require(existing?.deletedAtEpochMillis == null) {
                "A deleted activity ID cannot be reused; create a new activity ID."
            }
            activities.upsert(summary)
            points.deleteForActivity(summary.id)
            splits.deleteForActivity(summary.id)
            sensors.deleteForActivity(summary.id)
            if (activity.trackPoints.isNotEmpty()) points.insertAll(activity.trackPoints)
            if (activity.splits.isNotEmpty()) splits.insertAll(activity.splits)
            if (activity.sensorSamples.isNotEmpty()) sensors.insertAll(activity.sensorSamples)
            val operation = activity.syncOperation ?: SyncOperationEntity(
                id = "activity-upsert:${summary.id}:${summary.updatedAtEpochMillis}",
                entityType = "activity",
                entityId = summary.id,
                operationType = "UPSERT",
                payload = "",
                sequence = summary.updatedAtEpochMillis,
                nextAttemptAtEpochMillis = summary.updatedAtEpochMillis,
                createdAtEpochMillis = summary.updatedAtEpochMillis,
            )
            sync.enqueue(operation)
        }
    }

    override suspend fun deleteActivity(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val changed = activities.markDeleted(id, deletedAtEpochMillis) > 0
        if (changed) {
            sync.enqueue(
                SyncOperationEntity(
                    id = "activity-delete:$id:$deletedAtEpochMillis",
                    entityType = "activity",
                    entityId = id,
                    operationType = "DELETE",
                    payload = "{\"deleted\":true}",
                    sequence = deletedAtEpochMillis,
                    nextAttemptAtEpochMillis = deletedAtEpochMillis,
                    createdAtEpochMillis = deletedAtEpochMillis,
                ),
            )
        }
        changed
    }

    override suspend fun purgeDeletedBefore(epochMillis: Long): Int = activities.purgeDeletedBefore(epochMillis)
}

class RoomWorkoutRepository(private val database: PaceDatabase) : WorkoutRepository {
    private val workouts = database.workoutDao()
    private val sync = database.syncOperationDao()

    override fun observeTemplates(): Flow<List<WorkoutTemplateWithIntervals>> = workouts.observeTemplates()

    override suspend fun getTemplate(id: String): WorkoutTemplateWithIntervals? = workouts.getTemplate(id)

    override suspend fun saveTemplate(template: WorkoutTemplateEntity, intervals: List<WorkoutIntervalEntity>) {
        require(intervals.all { it.templateId == template.id })
        require(intervals.map { it.position }.distinct().size == intervals.size)
        require(intervals.all { it.position >= 0 && it.repeatCount > 0 })
        database.withTransaction {
            require(workouts.getTemplateIncludingDeleted(template.id)?.deletedAtEpochMillis == null) {
                "A deleted workout template ID cannot be reused."
            }
            workouts.upsertTemplate(template)
            // Delete stale positions before inserting the new complete definition.
            workouts.deleteIntervals(template.id)
            if (intervals.isNotEmpty()) workouts.upsertIntervals(intervals.sortedBy { it.position })
        }
    }

    override fun observeCalendar(fromDate: String, toDateExclusive: String): Flow<List<PlannedSessionEntity>> {
        require(fromDate < toDateExclusive) { "Calendar end date must be after its start date." }
        return workouts.observeCalendar(fromDate, toDateExclusive)
    }

    override suspend fun savePlannedSession(session: PlannedSessionEntity) = database.withTransaction {
        require(workouts.getPlannedSessionIncludingDeleted(session.id)?.deletedAtEpochMillis == null) {
            "A deleted planned session ID cannot be reused."
        }
        workouts.upsertPlannedSession(session)
    }

    override suspend fun deleteTemplate(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val changed = workouts.markTemplateDeleted(id, deletedAtEpochMillis) > 0
        if (changed) sync.enqueue(deleteOperation("workout_template", id, deletedAtEpochMillis))
        changed
    }

    override suspend fun deletePlannedSession(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val changed = workouts.markPlannedSessionDeleted(id, deletedAtEpochMillis) > 0
        if (changed) sync.enqueue(deleteOperation("planned_session", id, deletedAtEpochMillis))
        changed
    }
}

class RoomGoalRepository(private val database: PaceDatabase) : GoalRepository {
    private val goals = database.goalDao()
    private val sync = database.syncOperationDao()

    override fun observeGoals(): Flow<List<GoalEntity>> = goals.observeActive()
    override suspend fun getGoal(id: String): GoalEntity? = goals.getById(id)
    override suspend fun saveGoal(goal: GoalEntity) = database.withTransaction {
        require(goals.getIncludingDeleted(goal.id)?.deletedAtEpochMillis == null) {
            "A deleted goal ID cannot be reused."
        }
        goals.upsert(goal)
    }
    override suspend fun deleteGoal(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val changed = goals.markGoalDeleted(id, deletedAtEpochMillis) > 0
        if (changed) sync.enqueue(deleteOperation("goal", id, deletedAtEpochMillis))
        changed
    }
    override fun observePersonalRecords(sport: String?, metric: String?): Flow<List<PersonalRecordEntity>> =
        goals.observePersonalRecords(sport, metric)
    override suspend fun savePersonalRecord(record: PersonalRecordEntity) = database.withTransaction {
        require(goals.getPersonalRecordIncludingDeleted(record.id)?.deletedAtEpochMillis == null) {
            "A deleted personal record ID cannot be reused."
        }
        goals.upsertPersonalRecord(record)
    }
    override suspend fun deletePersonalRecord(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val changed = goals.markRecordDeleted(id, deletedAtEpochMillis) > 0
        if (changed) sync.enqueue(deleteOperation("personal_record", id, deletedAtEpochMillis))
        changed
    }
    override suspend fun maximumRecord(sport: String, metric: String): Double? = goals.maximumRecord(sport, metric)
    override suspend fun minimumRecord(sport: String, metric: String): Double? = goals.minimumRecord(sport, metric)
}

class RoomMapPackRepository(private val database: PaceDatabase) : MapPackRepository {
    private val mapPacks = database.mapPackDao()
    override fun observeMapPacks(): Flow<List<MapPackMetadataEntity>> = mapPacks.observeAll()
    override suspend fun getByRegion(regionId: String): MapPackMetadataEntity? = mapPacks.getByRegion(regionId)
    override suspend fun save(metadata: MapPackMetadataEntity) {
        require(metadata.southLatitude <= metadata.northLatitude)
        require(metadata.westLongitude <= metadata.eastLongitude)
        require(metadata.downloadedBytes >= 0)
        mapPacks.upsert(metadata)
    }
    override suspend fun delete(id: String): Boolean = mapPacks.delete(id) > 0
}

class RoomSyncOperationRepository(private val database: PaceDatabase) : SyncOperationRepository {
    private val operations = database.syncOperationDao()
    override suspend fun enqueue(operation: SyncOperationEntity): Boolean = operations.enqueue(operation) != -1L
    override suspend fun nextBatch(nowEpochMillis: Long, limit: Int): List<SyncOperationEntity> {
        require(limit in 1..200)
        return operations.nextBatch(nowEpochMillis, limit)
    }
    override suspend fun updateStatus(
        id: String,
        status: String,
        retryCount: Int,
        nextAttemptAtEpochMillis: Long,
        errorCode: String?,
    ): Boolean {
        require(status in setOf("PENDING", "RETRY", "DONE", "FAILED"))
        require(retryCount >= 0)
        return operations.updateStatus(id, status, retryCount, nextAttemptAtEpochMillis, errorCode) > 0
    }
    override suspend fun purgeCompletedBefore(epochMillis: Long): Int = operations.purgeCompletedBefore(epochMillis)
}

private fun deleteOperation(entityType: String, id: String, deletedAtEpochMillis: Long) = SyncOperationEntity(
    id = "$entityType-delete:$id:$deletedAtEpochMillis",
    entityType = entityType,
    entityId = id,
    operationType = "DELETE",
    payload = "{\"deleted\":true}",
    sequence = deletedAtEpochMillis,
    nextAttemptAtEpochMillis = deletedAtEpochMillis,
    createdAtEpochMillis = deletedAtEpochMillis,
)
