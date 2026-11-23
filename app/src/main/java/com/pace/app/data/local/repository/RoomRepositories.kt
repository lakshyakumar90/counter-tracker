package com.pace.app.data.local.repository

import androidx.room.withTransaction
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.dao.ActivityWithTrack
import com.pace.app.data.local.dao.SavedRouteWithPoints
import com.pace.app.data.local.dao.WorkoutTemplateWithIntervals
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.ActivityAggregateCacheEntity
import com.pace.app.data.local.entity.SavedRouteEntity
import com.pace.app.data.local.entity.SavedRoutePointEntity
import com.pace.app.data.local.entity.GoalEntity
import com.pace.app.data.local.entity.MapPackMetadataEntity
import com.pace.app.data.local.entity.HomeWorkoutDraftEntity
import com.pace.app.data.local.entity.PersonalRecordEntity
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.data.local.entity.SensorSampleEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.SyncOperationEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import com.pace.app.domain.repository.ActivityRepository
import com.pace.app.domain.metrics.ActivityMetrics
import com.pace.app.domain.repository.FinalizedActivity
import com.pace.app.domain.repository.GoalRepository
import com.pace.app.domain.repository.SavedRouteRepository
import com.pace.app.domain.repository.MapPackRepository
import com.pace.app.domain.repository.HomeWorkoutDraftRepository
import com.pace.app.domain.repository.SyncOperationRepository
import com.pace.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import org.json.JSONObject
import java.util.Locale

class RoomActivityRepository(
    private val database: PaceDatabase,
    private val currentOwnerId: () -> String = { "local" },
) : ActivityRepository {
    private val activities = database.activityDao()
    private val points = database.trackPointDao()
    private val splits = database.splitDao()
    private val sensors = database.sensorSampleDao()
    private val sync = database.syncOperationDao()
    private val aggregates = database.activityAggregateDao()

    override fun observeHistory(ownerId: String): Flow<List<ActivityEntity>> = activities.observeHistory(ownerId.takeUnless { it == "local" } ?: currentOwnerId())

    override suspend fun getActivity(id: String): ActivityWithTrack? = activities.getWithTrack(id, currentOwnerId())
    override suspend fun routeThumbnail(id: String): List<TrackPointEntity> = points.thumbnailForActivity(id, currentOwnerId())

    override suspend fun updateMetadata(id: String, title: String?, notes: String?, updatedAtEpochMillis: Long): Boolean = database.withTransaction {
        require(title == null || title.length <= 80) { "Activity titles are limited to 80 characters." }
        require(notes == null || notes.length <= 1_000) { "Activity notes are limited to 1,000 characters." }
        val owner = currentOwnerId()
        if (activities.updateMetadata(id, title, notes, updatedAtEpochMillis, owner) == 0) return@withTransaction false
        sync.enqueue(SyncOperationEntity(
            id = "activity-upsert:$id:$updatedAtEpochMillis", entityType = "activity", entityId = id,
            accountUserId = owner,
            operationType = "UPSERT", payload = JSONObject().put("title", title ?: JSONObject.NULL).put("notes", notes ?: JSONObject.NULL).toString(), sequence = updatedAtEpochMillis,
            nextAttemptAtEpochMillis = updatedAtEpochMillis, createdAtEpochMillis = updatedAtEpochMillis,
        ))
        true
    }

    override suspend fun page(
        ownerId: String,
        sport: String?,
        query: String?,
        beforeStartedAt: Long?,
        beforeId: String,
        limit: Int,
        sortBy: String,
        offset: Int,
        fromStartedAt: Long?,
        toStartedAt: Long?,
    ): List<ActivityEntity> {
        val scopedOwnerId = ownerId.takeUnless { it == "local" } ?: currentOwnerId()
        require(limit in 1..200) { "Page size must be between 1 and 200." }
        require(offset >= 0)
        require(sortBy in setOf("NEWEST", "OLDEST", "DISTANCE", "DURATION"))
        if (beforeStartedAt != null) return activities.page(scopedOwnerId, sport, query?.trim()?.takeIf(String::isNotEmpty), beforeStartedAt, beforeId, limit)
        require(fromStartedAt == null || toStartedAt == null || fromStartedAt <= toStartedAt)
        return activities.sortedPage(scopedOwnerId, sport, query?.trim()?.takeIf(String::isNotEmpty), fromStartedAt, toStartedAt, sortBy, offset, limit)
    }

    override suspend fun finalizeActivity(activity: FinalizedActivity) {
        val summary = activity.summary.copy(ownerId = currentOwnerId())
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
            val existing = activities.getIncludingDeleted(summary.id, summary.ownerId)
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
            existing?.let { refreshAggregateWeek(it.ownerId, it.sport, it.startedAtEpochMillis) }
            refreshAggregateWeek(summary.ownerId, summary.sport, summary.startedAtEpochMillis)
            val operation = activity.syncOperation?.copy(accountUserId = summary.ownerId) ?: SyncOperationEntity(
                id = "activity-upsert:${summary.id}:${summary.updatedAtEpochMillis}",
                entityType = "activity",
                entityId = summary.id,
                accountUserId = summary.ownerId,
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
        val owner = currentOwnerId()
        val before = activities.getById(id, owner)
        val changed = activities.markDeleted(id, deletedAtEpochMillis, owner) > 0
        if (changed) {
            before?.let { refreshAggregateWeek(it.ownerId, it.sport, it.startedAtEpochMillis) }
            sync.enqueue(
                SyncOperationEntity(
                    id = "activity-delete:$id:$deletedAtEpochMillis",
                    entityType = "activity",
                    entityId = id,
                    accountUserId = owner,
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

    /** Full repair path; cached values are disposable and reconstructed from Room summaries. */
    suspend fun recomputeAggregateCache(ownerId: String = "local", nowMillis: Long = System.currentTimeMillis()) = database.withTransaction {
        val scopedOwnerId = ownerId.takeUnless { it == "local" } ?: currentOwnerId()
        aggregates.clear(scopedOwnerId)
        activities.allSavedSummaries(scopedOwnerId).asSequence()
            .filter(::aggregateEligible)
            .groupBy { Triple(it.ownerId, weekKey(it.startedAtEpochMillis), it.sport.uppercase()) }
            .forEach { (key, rows) -> aggregates.upsert(ActivityAggregateCacheEntity(key.first, key.second, key.third, rows.size, rows.sumOf { it.distanceMeters }, rows.sumOf { it.movingSeconds }, rows.sumOf { it.elapsedSeconds }, nowMillis)) }
    }

    private suspend fun refreshAggregateWeek(ownerId: String, sport: String, timestamp: Long) {
        val week = weekKey(timestamp)
        aggregates.delete(ownerId, week, sport.uppercase())
        val (from, until) = weekBounds(timestamp)
        val rows = activities.summariesForAggregate(ownerId, sport, from, until).filter(::aggregateEligible)
        if (rows.isNotEmpty()) aggregates.upsert(ActivityAggregateCacheEntity(ownerId, week, sport.uppercase(), rows.size, rows.sumOf { it.distanceMeters }, rows.sumOf { it.movingSeconds }, rows.sumOf { it.elapsedSeconds }, System.currentTimeMillis()))
    }

    private fun aggregateEligible(row: ActivityEntity) = ActivityMetrics.isEligibleSummary(row)
    private fun weekKey(timestamp: Long): String {
        val zone = java.time.ZoneId.systemDefault(); val locale = Locale.getDefault(); val firstDay = WeekFields.of(locale).firstDayOfWeek
        val start = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDay))
        return "${locale.toLanguageTag()}@${zone.id}#$start"
    }
    private fun weekBounds(timestamp: Long): Pair<Long, Long> {
        val zone = java.time.ZoneId.systemDefault(); val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val start = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDay))
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to start.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    override suspend fun purgeDeletedBefore(epochMillis: Long): Int = activities.purgeDeletedBefore(epochMillis)
}

class RoomWorkoutRepository(
    private val database: PaceDatabase,
    private val currentOwnerId: () -> String = { "local" },
) : WorkoutRepository {
    private val workouts = database.workoutDao()
    private val sync = database.syncOperationDao()

    override fun observeTemplates(): Flow<List<WorkoutTemplateWithIntervals>> = workouts.observeTemplates(currentOwnerId())

    override suspend fun getTemplate(id: String): WorkoutTemplateWithIntervals? = workouts.getTemplate(id, currentOwnerId())

    override suspend fun saveTemplate(template: WorkoutTemplateEntity, intervals: List<WorkoutIntervalEntity>) {
        require(template.targetDurationSeconds == null || template.targetDurationSeconds > 0L)
        require(template.targetDistanceMeters == null || template.targetDistanceMeters > 0.0)
        require(intervals.all { it.templateId == template.id })
        require(intervals.map { it.position }.distinct().size == intervals.size)
        require(intervals.all { it.position >= 0 && it.repeatCount > 0 })
        require(intervals.all { interval ->
            interval.targetMetric in setOf(null, "PACE_SECONDS_PER_KILOMETER", "SPEED_METERS_PER_SECOND", "DISTANCE_METERS") &&
                (interval.targetMetric == null && interval.targetMin == null && interval.targetMax == null ||
                    interval.targetMetric != null && interval.targetMin != null && interval.targetMax != null &&
                    interval.targetMin > 0.0 && interval.targetMax >= interval.targetMin)
        }) { "Interval targets must use a supported metric and a complete positive range." }
        val owner = currentOwnerId()
        val scopedTemplate = template.copy(ownerId = owner)
        database.withTransaction {
            require(workouts.getTemplateIncludingDeleted(template.id, owner)?.deletedAtEpochMillis == null) {
                "A deleted workout template ID cannot be reused."
            }
            workouts.upsertTemplate(scopedTemplate)
            // Delete stale positions before inserting the new complete definition.
            workouts.deleteIntervals(template.id)
            if (intervals.isNotEmpty()) workouts.upsertIntervals(intervals.sortedBy { it.position })
            sync.enqueue(upsertOperation("workout_template", template.id, template.updatedAtEpochMillis, owner))
        }
    }

    override fun observeCalendar(fromDate: String, toDateExclusive: String): Flow<List<PlannedSessionEntity>> {
        require(fromDate < toDateExclusive) { "Calendar end date must be after its start date." }
        return workouts.observeCalendar(fromDate, toDateExclusive, currentOwnerId())
    }

    override suspend fun savePlannedSession(session: PlannedSessionEntity) = database.withTransaction {
        val owner = currentOwnerId()
        require(workouts.getPlannedSessionIncludingDeleted(session.id, owner)?.deletedAtEpochMillis == null) {
            "A deleted planned session ID cannot be reused."
        }
        workouts.upsertPlannedSession(session.copy(ownerId = owner))
        sync.enqueue(upsertOperation("planned_session", session.id, session.updatedAtEpochMillis, owner))
        Unit
    }

    override suspend fun reschedulePlannedSession(sessionId: String, newDate: String, updatedAtEpochMillis: Long): Boolean =
        database.withTransaction {
            require(runCatching { java.time.LocalDate.parse(newDate) }.isSuccess) { "The scheduled date must be a valid ISO local date." }
            val owner = currentOwnerId()
            val changed = workouts.reschedulePlannedSession(sessionId, newDate, updatedAtEpochMillis, owner) == 1
            if (changed) sync.enqueue(upsertOperation("planned_session", sessionId, updatedAtEpochMillis, owner))
            changed
        }

    override suspend fun linkCompletedActivity(sessionId: String, activityId: String, updatedAtEpochMillis: Long): Boolean =
        database.withTransaction {
            val owner = currentOwnerId()
            require(database.activityDao().getById(activityId, owner) != null) { "The completed activity is unavailable." }
            val changed = workouts.linkCompletedActivity(sessionId, activityId, updatedAtEpochMillis, owner) == 1
            if (changed) sync.enqueue(upsertOperation("planned_session", sessionId, updatedAtEpochMillis, owner))
            changed
        }

    override suspend fun deleteTemplate(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val owner = currentOwnerId()
        val changed = workouts.markTemplateDeleted(id, deletedAtEpochMillis, owner) > 0
        if (changed) sync.enqueue(deleteOperation("workout_template", id, deletedAtEpochMillis, owner))
        changed
    }

    override suspend fun deletePlannedSession(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val owner = currentOwnerId()
        val changed = workouts.markPlannedSessionDeleted(id, deletedAtEpochMillis, owner) > 0
        if (changed) sync.enqueue(deleteOperation("planned_session", id, deletedAtEpochMillis, owner))
        changed
    }
}

class RoomGoalRepository(
    private val database: PaceDatabase,
    private val currentOwnerId: () -> String = { "local" },
) : GoalRepository {
    private val goals = database.goalDao()
    private val sync = database.syncOperationDao()

    override fun observeGoals(): Flow<List<GoalEntity>> = goals.observeActive(currentOwnerId())
    override suspend fun getGoal(id: String): GoalEntity? = goals.getById(id, currentOwnerId())
    override suspend fun saveGoal(goal: GoalEntity) = database.withTransaction {
        val owner = currentOwnerId()
        require(goals.getIncludingDeleted(goal.id, owner)?.deletedAtEpochMillis == null) {
            "A deleted goal ID cannot be reused."
        }
        goals.upsert(goal.copy(ownerId = owner))
        sync.enqueue(upsertOperation("goal", goal.id, goal.updatedAtEpochMillis, owner))
        Unit
    }
    override suspend fun deleteGoal(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val owner = currentOwnerId()
        val changed = goals.markGoalDeleted(id, deletedAtEpochMillis, owner) > 0
        if (changed) sync.enqueue(deleteOperation("goal", id, deletedAtEpochMillis, owner))
        changed
    }
    override fun observePersonalRecords(sport: String?, metric: String?): Flow<List<PersonalRecordEntity>> =
        goals.observePersonalRecords(sport, metric, currentOwnerId())
    override suspend fun savePersonalRecord(record: PersonalRecordEntity) = database.withTransaction {
        val owner = currentOwnerId()
        require(goals.getPersonalRecordIncludingDeleted(record.id, owner)?.deletedAtEpochMillis == null) {
            "A deleted personal record ID cannot be reused."
        }
        val updatedAt = System.currentTimeMillis()
        goals.upsertPersonalRecord(record.copy(ownerId = owner, updatedAtEpochMillis = updatedAt))
        sync.enqueue(upsertOperation("personal_record", record.id, updatedAt, owner))
        Unit
    }
    override suspend fun deletePersonalRecord(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val owner = currentOwnerId()
        val changed = goals.markRecordDeleted(id, deletedAtEpochMillis, owner) > 0
        if (changed) sync.enqueue(deleteOperation("personal_record", id, deletedAtEpochMillis, owner))
        changed
    }
    override suspend fun maximumRecord(sport: String, metric: String): Double? = goals.maximumRecord(sport, metric, currentOwnerId())
    override suspend fun minimumRecord(sport: String, metric: String): Double? = goals.minimumRecord(sport, metric, currentOwnerId())
}

class RoomSavedRouteRepository(
    private val database: PaceDatabase,
    private val currentOwnerId: () -> String = { "local" },
) : SavedRouteRepository {
    private val routes = database.savedRouteDao()

    override fun observeRoutes(): Flow<List<SavedRouteWithPoints>> = routes.observeRoutes(currentOwnerId())
    override suspend fun getRoute(id: String): SavedRouteWithPoints? = routes.getRoute(id, currentOwnerId())

    override suspend fun saveRoute(route: SavedRouteEntity, points: List<SavedRoutePointEntity>) = database.withTransaction {
        require(route.title.isNotBlank() && route.title.length <= 80) { "Route title must be between 1 and 80 characters." }
        require(route.sport.uppercase() in setOf("CYCLING", "RUNNING", "JOGGING", "WALKING")) { "Unsupported route sport." }
        require(route.distanceMeters.isFinite() && route.distanceMeters >= 0.0)
        require(points.size >= 2) { "A saved route needs at least two points." }
        require(points.map { it.sequence } == points.indices.toList()) { "Route point sequence must be contiguous and ordered." }
        require(points.all { it.routeId == route.id && it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0 && (it.altitudeMeters == null || it.altitudeMeters.isFinite()) })
        val owner = currentOwnerId()
        require(routes.getIncludingDeleted(route.id, owner)?.deletedAtEpochMillis == null) { "A deleted route ID cannot be reused." }
        val saved = route.copy(sport = route.sport.uppercase(), ownerId = owner)
        routes.upsert(saved)
        routes.clearPoints(route.id)
        routes.insertPoints(points)
        database.syncOperationDao().enqueue(upsertOperation("saved_route", saved.id, saved.updatedAtEpochMillis, owner))
        Unit
    }

    override suspend fun deleteRoute(id: String, deletedAtEpochMillis: Long): Boolean = database.withTransaction {
        val owner = currentOwnerId()
        val changed = routes.markDeleted(id, deletedAtEpochMillis, owner) > 0
        if (changed) {
            routes.clearPoints(id)
            database.syncOperationDao().enqueue(deleteOperation("saved_route", id, deletedAtEpochMillis, owner))
        }
        changed
    }
}

class RoomMapPackRepository(private val database: PaceDatabase) : MapPackRepository {
    private val mapPacks = database.mapPackDao()
    override fun observeMapPacks(): Flow<List<MapPackMetadataEntity>> = mapPacks.observeAll()
    override suspend fun getAll(): List<MapPackMetadataEntity> = mapPacks.getAll()
    override suspend fun getByRegion(regionId: String): MapPackMetadataEntity? = mapPacks.getByRegion(regionId)
    override suspend fun save(metadata: MapPackMetadataEntity) {
        require(metadata.southLatitude <= metadata.northLatitude)
        require(metadata.westLongitude <= metadata.eastLongitude)
        require(metadata.downloadedBytes >= 0)
        mapPacks.upsert(metadata)
    }
    override suspend fun delete(id: String): Boolean = mapPacks.delete(id) > 0
}

class RoomHomeWorkoutDraftRepository(private val database: PaceDatabase) : HomeWorkoutDraftRepository {
    private val drafts = database.homeWorkoutDraftDao()
    override suspend fun get(ownerId: String): HomeWorkoutDraftEntity? = drafts.get(ownerId)
    override suspend fun save(draft: HomeWorkoutDraftEntity) {
        require(draft.ownerId.isNotBlank())
        require(draft.blocksJson.length <= 64_000) { "Workout draft is too large." }
        require(draft.circuitRepeats in 1..20 && draft.restSeconds in 0..600)
        require(draft.stepIndex >= 0 && draft.remainingStepMillis >= 0 && draft.elapsedActiveMillis >= 0)
        drafts.upsert(draft)
    }
    override suspend fun delete(ownerId: String) { drafts.delete(ownerId) }
}

class RoomSyncOperationRepository(private val database: PaceDatabase) : SyncOperationRepository {
    private val operations = database.syncOperationDao()
    override suspend fun enqueue(operation: SyncOperationEntity): Boolean = operations.enqueue(operation) != -1L
    override suspend fun nextBatch(nowEpochMillis: Long, limit: Int, accountUserId: String): List<SyncOperationEntity> {
        require(limit in 1..200)
        require(accountUserId == "local" || runCatching { java.util.UUID.fromString(accountUserId) }.isSuccess)
        return operations.nextBatch(nowEpochMillis, limit, accountUserId)
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
    override suspend fun retryOutstandingNow(accountUserId: String, nowEpochMillis: Long): Int {
        require(accountUserId == "local" || runCatching { java.util.UUID.fromString(accountUserId) }.isSuccess)
        return operations.retryOutstandingNow(accountUserId, nowEpochMillis)
    }
    override suspend fun purgeCompletedBefore(epochMillis: Long): Int = operations.purgeCompletedBefore(epochMillis)
}

private fun deleteOperation(entityType: String, id: String, deletedAtEpochMillis: Long, accountUserId: String = "local") = SyncOperationEntity(
    id = "$entityType-delete:$id:$deletedAtEpochMillis",
    entityType = entityType,
    entityId = id,
    accountUserId = accountUserId,
    operationType = "DELETE",
    payload = "{\"deleted\":true}",
    sequence = deletedAtEpochMillis,
    nextAttemptAtEpochMillis = deletedAtEpochMillis,
    createdAtEpochMillis = deletedAtEpochMillis,
)

private fun upsertOperation(entityType: String, id: String, updatedAtEpochMillis: Long, accountUserId: String) = SyncOperationEntity(
    id = "$entityType-upsert:$id:$updatedAtEpochMillis",
    entityType = entityType,
    entityId = id,
    accountUserId = accountUserId,
    operationType = "UPSERT",
    payload = "{}",
    sequence = updatedAtEpochMillis,
    nextAttemptAtEpochMillis = updatedAtEpochMillis,
    createdAtEpochMillis = updatedAtEpochMillis,
)
