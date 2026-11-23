package com.pace.app.domain.repository

import com.pace.app.data.local.dao.ActivityWithTrack
import com.pace.app.data.local.dao.WorkoutTemplateWithIntervals
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.dao.SavedRouteWithPoints
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
import kotlinx.coroutines.flow.Flow

data class FinalizedActivity(
    val summary: ActivityEntity,
    val trackPoints: List<TrackPointEntity> = emptyList(),
    val splits: List<SplitEntity> = emptyList(),
    val sensorSamples: List<SensorSampleEntity> = emptyList(),
    val syncOperation: SyncOperationEntity? = null,
)

interface ActivityRepository {
    fun observeHistory(ownerId: String = "local"): Flow<List<ActivityEntity>>
    suspend fun getActivity(id: String): ActivityWithTrack?
    suspend fun routeThumbnail(id: String): List<TrackPointEntity>
    suspend fun updateMetadata(id: String, title: String?, notes: String?, updatedAtEpochMillis: Long): Boolean
    suspend fun page(
        ownerId: String = "local",
        sport: String? = null,
        query: String? = null,
        beforeStartedAt: Long? = null,
        beforeId: String = "",
        limit: Int,
        sortBy: String = "NEWEST",
        offset: Int = 0,
        fromStartedAt: Long? = null,
        toStartedAt: Long? = null,
    ): List<ActivityEntity>
    suspend fun finalizeActivity(activity: FinalizedActivity)
    suspend fun deleteActivity(id: String, deletedAtEpochMillis: Long): Boolean
    suspend fun purgeDeletedBefore(epochMillis: Long): Int
}

interface WorkoutRepository {
    fun observeTemplates(): Flow<List<WorkoutTemplateWithIntervals>>
    suspend fun getTemplate(id: String): WorkoutTemplateWithIntervals?
    suspend fun saveTemplate(template: WorkoutTemplateEntity, intervals: List<WorkoutIntervalEntity>)
    fun observeCalendar(fromDate: String, toDateExclusive: String): Flow<List<PlannedSessionEntity>>
    suspend fun savePlannedSession(session: PlannedSessionEntity)
    suspend fun reschedulePlannedSession(sessionId: String, newDate: String, updatedAtEpochMillis: Long): Boolean
    suspend fun linkCompletedActivity(sessionId: String, activityId: String, updatedAtEpochMillis: Long): Boolean
    suspend fun deleteTemplate(id: String, deletedAtEpochMillis: Long): Boolean
    suspend fun deletePlannedSession(id: String, deletedAtEpochMillis: Long): Boolean
}

interface GoalRepository {
    fun observeGoals(): Flow<List<GoalEntity>>
    suspend fun getGoal(id: String): GoalEntity?
    suspend fun saveGoal(goal: GoalEntity)
    suspend fun deleteGoal(id: String, deletedAtEpochMillis: Long): Boolean
    fun observePersonalRecords(sport: String? = null, metric: String? = null): Flow<List<PersonalRecordEntity>>
    suspend fun savePersonalRecord(record: PersonalRecordEntity)
    suspend fun deletePersonalRecord(id: String, deletedAtEpochMillis: Long): Boolean
    suspend fun maximumRecord(sport: String, metric: String): Double?
    suspend fun minimumRecord(sport: String, metric: String): Double?
}

interface SavedRouteRepository {
    fun observeRoutes(): Flow<List<SavedRouteWithPoints>>
    suspend fun getRoute(id: String): SavedRouteWithPoints?
    suspend fun saveRoute(route: SavedRouteEntity, points: List<SavedRoutePointEntity>)
    suspend fun deleteRoute(id: String, deletedAtEpochMillis: Long): Boolean
}

interface MapPackRepository {
    fun observeMapPacks(): Flow<List<MapPackMetadataEntity>>
    suspend fun getAll(): List<MapPackMetadataEntity>
    suspend fun getByRegion(regionId: String): MapPackMetadataEntity?
    suspend fun save(metadata: MapPackMetadataEntity)
    suspend fun delete(id: String): Boolean
}

interface HomeWorkoutDraftRepository {
    suspend fun get(ownerId: String): HomeWorkoutDraftEntity?
    suspend fun save(draft: HomeWorkoutDraftEntity)
    suspend fun delete(ownerId: String)
}

interface SyncOperationRepository {
    suspend fun enqueue(operation: SyncOperationEntity): Boolean
    suspend fun nextBatch(nowEpochMillis: Long, limit: Int = 50, accountUserId: String = "local"): List<SyncOperationEntity>
    suspend fun updateStatus(
        id: String,
        status: String,
        retryCount: Int,
        nextAttemptAtEpochMillis: Long,
        errorCode: String? = null,
    ): Boolean
    suspend fun retryOutstandingNow(accountUserId: String, nowEpochMillis: Long): Int
    suspend fun purgeCompletedBefore(epochMillis: Long): Int
}
