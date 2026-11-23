package com.pace.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.ActivityAggregateCacheEntity
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

data class ActivityWithTrack(
    @androidx.room.Embedded val activity: ActivityEntity,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val trackPoints: List<TrackPointEntity>,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val splits: List<SplitEntity>,
    @Relation(parentColumn = "id", entityColumn = "activity_id")
    val sensorSamples: List<SensorSampleEntity>,
)

data class WorkoutTemplateWithIntervals(
    @androidx.room.Embedded val template: WorkoutTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "template_id")
    val intervals: List<WorkoutIntervalEntity>,
)

@Dao
interface ActivityDao {
    @Upsert suspend fun upsert(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun getById(id: String, ownerId: String = "local"): ActivityEntity?

    @Query("SELECT * FROM activities WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    fun observeById(id: String, ownerId: String = "local"): Flow<ActivityEntity?>

    @Query("SELECT * FROM activities WHERE id = :id AND owner_id = :ownerId")
    suspend fun getIncludingDeleted(id: String, ownerId: String = "local"): ActivityEntity?

    @Query("SELECT * FROM activities WHERE owner_id = :ownerId AND status IN ('RECORDING', 'PAUSED', 'RECOVERED') AND deleted_at_epoch_millis IS NULL ORDER BY started_at_epoch_millis DESC LIMIT 1")
    suspend fun getActiveRecording(ownerId: String = "local"): ActivityEntity?

    @Query("SELECT COUNT(*) FROM activities WHERE status IN ('RECORDING', 'PAUSED', 'RECOVERED', 'FINISHING') AND deleted_at_epoch_millis IS NULL")
    suspend fun countActiveRecordingsAcrossOwners(): Int

    @Query("SELECT * FROM activities WHERE owner_id = :ownerId AND status IN ('RECORDING', 'PAUSED', 'RECOVERED', 'FINISHING') AND deleted_at_epoch_millis IS NULL ORDER BY started_at_epoch_millis DESC LIMIT 1")
    fun observeActiveRecording(ownerId: String = "local"): Flow<ActivityEntity?>

    @Query("DELETE FROM activities WHERE id = :id AND owner_id = :ownerId AND status != 'SAVED'")
    suspend fun discardInProgress(id: String, ownerId: String = "local"): Int

    @Transaction
    @Query("SELECT * FROM activities WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun getWithTrack(id: String, ownerId: String = "local"): ActivityWithTrack?

    @Query("""
        SELECT * FROM activities
        WHERE owner_id = :ownerId
          AND status = 'SAVED'
          AND deleted_at_epoch_millis IS NULL
          AND (:sport IS NULL OR sport = :sport)
          AND (:query IS NULL OR title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
          AND (:beforeStartedAt IS NULL OR started_at_epoch_millis < :beforeStartedAt
               OR (started_at_epoch_millis = :beforeStartedAt AND id < :beforeId))
        ORDER BY started_at_epoch_millis DESC, id DESC
        LIMIT :limit
    """)
    suspend fun page(
        ownerId: String = "local",
        sport: String? = null,
        query: String? = null,
        beforeStartedAt: Long? = null,
        beforeId: String = "",
        limit: Int,
    ): List<ActivityEntity>

    @Query("""
        SELECT * FROM activities
        WHERE owner_id = :ownerId AND status = 'SAVED' AND deleted_at_epoch_millis IS NULL
          AND (:sport IS NULL OR sport = :sport)
          AND (:query IS NULL OR title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
          AND (:fromStartedAt IS NULL OR started_at_epoch_millis >= :fromStartedAt)
          AND (:toStartedAt IS NULL OR started_at_epoch_millis < :toStartedAt)
        ORDER BY CASE WHEN :sortBy = 'OLDEST' THEN started_at_epoch_millis END ASC,
                 CASE WHEN :sortBy = 'DISTANCE' THEN distance_meters END DESC,
                 CASE WHEN :sortBy = 'DURATION' THEN elapsed_seconds END DESC,
                 CASE WHEN :sortBy = 'NEWEST' THEN started_at_epoch_millis END DESC,
                 started_at_epoch_millis DESC, id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun sortedPage(ownerId: String, sport: String?, query: String?, fromStartedAt: Long?, toStartedAt: Long?, sortBy: String, offset: Int, limit: Int): List<ActivityEntity>

    @Query("""
        SELECT * FROM activities
        WHERE owner_id = :ownerId AND status = 'SAVED' AND deleted_at_epoch_millis IS NULL
        ORDER BY started_at_epoch_millis DESC, id DESC
    """)
    fun observeHistory(ownerId: String = "local"): Flow<List<ActivityEntity>>

    @Query("UPDATE activities SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun markDeleted(id: String, deletedAt: Long, ownerId: String = "local"): Int

    @Query("UPDATE activities SET title = :title, notes = :notes, updated_at_epoch_millis = :updatedAt WHERE id = :id AND owner_id = :ownerId AND status = 'SAVED' AND deleted_at_epoch_millis IS NULL")
    suspend fun updateMetadata(id: String, title: String?, notes: String?, updatedAt: Long, ownerId: String = "local"): Int

    @Query("""
        DELETE FROM activities
        WHERE deleted_at_epoch_millis IS NOT NULL
          AND deleted_at_epoch_millis < :olderThan
          AND NOT EXISTS (
              SELECT 1 FROM sync_operations
              WHERE entity_type = 'activity' AND entity_id = activities.id AND status != 'DONE'
          )
    """)
    suspend fun purgeDeletedBefore(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM activities WHERE deleted_at_epoch_millis IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT * FROM activities WHERE owner_id = :ownerId AND sport = :sport AND status = 'SAVED' AND deleted_at_epoch_millis IS NULL AND started_at_epoch_millis >= :fromMillis AND started_at_epoch_millis < :toMillis")
    suspend fun summariesForAggregate(ownerId: String, sport: String, fromMillis: Long, toMillis: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE owner_id = :ownerId AND status = 'SAVED' AND deleted_at_epoch_millis IS NULL")
    suspend fun allSavedSummaries(ownerId: String): List<ActivityEntity>
}

@Dao
interface ActivityAggregateDao {
    @Upsert suspend fun upsert(row: ActivityAggregateCacheEntity)
    @Query("DELETE FROM activity_aggregate_cache WHERE owner_id = :ownerId AND period_key = :periodKey AND sport = :sport") suspend fun delete(ownerId: String, periodKey: String, sport: String)
    @Query("DELETE FROM activity_aggregate_cache WHERE owner_id = :ownerId") suspend fun clear(ownerId: String)
    @Query("SELECT * FROM activity_aggregate_cache WHERE owner_id = :ownerId ORDER BY period_key DESC, sport ASC") fun observe(ownerId: String = "local"): Flow<List<ActivityAggregateCacheEntity>>
}

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY sequence ASC")
    suspend fun forActivity(activityId: String): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId AND activity_id IN (SELECT id FROM activities WHERE owner_id = :ownerId) AND (sequence % 10 = 0 OR sequence = (SELECT MAX(sequence) FROM track_points WHERE activity_id = :activityId)) ORDER BY sequence ASC LIMIT 41")
    suspend fun thumbnailForActivity(activityId: String, ownerId: String = "local"): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY sequence ASC")
    fun observeForActivity(activityId: String): Flow<List<TrackPointEntity>>

    @Query("DELETE FROM track_points WHERE activity_id = :activityId")
    suspend fun deleteForActivity(activityId: String)

    @Query("SELECT COUNT(*) FROM track_points WHERE activity_id = :activityId")
    suspend fun countForActivity(activityId: String): Int

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY sequence DESC LIMIT 1")
    suspend fun lastForActivity(activityId: String): TrackPointEntity?
}

@Dao
interface SplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(splits: List<SplitEntity>)

    @Query("SELECT * FROM splits WHERE activity_id = :activityId ORDER BY split_number ASC")
    suspend fun forActivity(activityId: String): List<SplitEntity>

    @Query("SELECT * FROM splits WHERE activity_id = :activityId ORDER BY split_number ASC")
    fun observeForActivity(activityId: String): Flow<List<SplitEntity>>

    @Query("DELETE FROM splits WHERE activity_id = :activityId")
    suspend fun deleteForActivity(activityId: String)
}

@Dao
interface SensorSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<SensorSampleEntity>)

    @Query("SELECT * FROM sensor_samples WHERE activity_id = :activityId AND sample_type = :sampleType ORDER BY timestamp_epoch_millis ASC")
    suspend fun forActivity(activityId: String, sampleType: String): List<SensorSampleEntity>

    @Query("DELETE FROM sensor_samples WHERE activity_id = :activityId")
    suspend fun deleteForActivity(activityId: String)
}

@Dao
interface WorkoutDao {
    @Upsert suspend fun upsertTemplate(template: WorkoutTemplateEntity)

    @Upsert suspend fun upsertIntervals(intervals: List<WorkoutIntervalEntity>)

    @Query("DELETE FROM workout_intervals WHERE template_id = :templateId")
    suspend fun deleteIntervals(templateId: String)

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun getTemplate(id: String, ownerId: String = "local"): WorkoutTemplateWithIntervals?

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :id AND owner_id = :ownerId")
    suspend fun getTemplateWithIntervalsIncludingDeleted(id: String, ownerId: String = "local"): WorkoutTemplateWithIntervals?

    @Query("SELECT * FROM workout_templates WHERE id = :id AND owner_id = :ownerId")
    suspend fun getTemplateIncludingDeleted(id: String, ownerId: String = "local"): WorkoutTemplateEntity?

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE owner_id = :ownerId AND deleted_at_epoch_millis IS NULL ORDER BY updated_at_epoch_millis DESC, id ASC")
    fun observeTemplates(ownerId: String = "local"): Flow<List<WorkoutTemplateWithIntervals>>

    @Query("""
        SELECT * FROM planned_sessions
        WHERE owner_id = :ownerId AND scheduled_date >= :fromDate AND scheduled_date < :toDateExclusive
          AND deleted_at_epoch_millis IS NULL
        ORDER BY scheduled_date ASC, id ASC
    """)
    fun observeCalendar(fromDate: String, toDateExclusive: String, ownerId: String = "local"): Flow<List<PlannedSessionEntity>>

    @Upsert suspend fun upsertPlannedSession(session: PlannedSessionEntity)

    @Query("UPDATE planned_sessions SET scheduled_date = :newDate, updated_at_epoch_millis = :updatedAt WHERE id = :sessionId AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun reschedulePlannedSession(sessionId: String, newDate: String, updatedAt: Long, ownerId: String = "local"): Int

    @Query("UPDATE planned_sessions SET status = 'COMPLETED', completed_activity_id = :activityId, reminder_enabled = 0, updated_at_epoch_millis = :updatedAt WHERE id = :sessionId AND owner_id = :ownerId AND status = 'PLANNED' AND completed_activity_id IS NULL AND deleted_at_epoch_millis IS NULL")
    suspend fun linkCompletedActivity(sessionId: String, activityId: String, updatedAt: Long, ownerId: String = "local"): Int

    @Query("SELECT * FROM planned_sessions WHERE id = :id AND owner_id = :ownerId")
    suspend fun getPlannedSessionIncludingDeleted(id: String, ownerId: String = "local"): PlannedSessionEntity?

    @Query("SELECT * FROM planned_sessions WHERE owner_id = :ownerId AND reminder_enabled = 1 AND status = 'PLANNED' AND deleted_at_epoch_millis IS NULL")
    suspend fun remindersToSchedule(ownerId: String = "local"): List<PlannedSessionEntity>

    @Query("UPDATE workout_templates SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun markTemplateDeleted(id: String, deletedAt: Long, ownerId: String = "local"): Int

    @Query("UPDATE planned_sessions SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun markPlannedSessionDeleted(id: String, deletedAt: Long, ownerId: String = "local"): Int
}

@Dao
interface GoalDao {
    @Upsert suspend fun upsert(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun getById(id: String, ownerId: String = "local"): GoalEntity?

    @Query("SELECT * FROM goals WHERE id = :id AND owner_id = :ownerId")
    suspend fun getIncludingDeleted(id: String, ownerId: String = "local"): GoalEntity?

    @Query("SELECT * FROM goals WHERE owner_id = :ownerId AND deleted_at_epoch_millis IS NULL ORDER BY start_date DESC, id ASC")
    fun observeActive(ownerId: String = "local"): Flow<List<GoalEntity>>

    @Upsert suspend fun upsertPersonalRecord(record: PersonalRecordEntity)

    @Query("SELECT * FROM personal_records WHERE id = :id AND owner_id = :ownerId")
    suspend fun getPersonalRecordIncludingDeleted(id: String, ownerId: String = "local"): PersonalRecordEntity?

    @Query("""
        SELECT * FROM personal_records
        WHERE owner_id = :ownerId AND deleted_at_epoch_millis IS NULL
          AND (:sport IS NULL OR sport = :sport)
          AND (:metric IS NULL OR metric = :metric)
        ORDER BY achieved_at_epoch_millis DESC, id ASC
    """)
    fun observePersonalRecords(sport: String? = null, metric: String? = null, ownerId: String = "local"): Flow<List<PersonalRecordEntity>>

    @Query("UPDATE goals SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun markGoalDeleted(id: String, deletedAt: Long, ownerId: String = "local"): Int

    @Query("UPDATE personal_records SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun markRecordDeleted(id: String, deletedAt: Long, ownerId: String = "local"): Int

    @Query("SELECT MAX(record_value) FROM personal_records WHERE owner_id = :ownerId AND sport = :sport AND metric = :metric AND deleted_at_epoch_millis IS NULL")
    suspend fun maximumRecord(sport: String, metric: String, ownerId: String = "local"): Double?

    @Query("SELECT MIN(record_value) FROM personal_records WHERE owner_id = :ownerId AND sport = :sport AND metric = :metric AND deleted_at_epoch_millis IS NULL")
    suspend fun minimumRecord(sport: String, metric: String, ownerId: String = "local"): Double?
}

@Dao
interface MapPackDao {
    @Upsert suspend fun upsert(metadata: MapPackMetadataEntity)

    @Query("SELECT * FROM map_pack_metadata ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MapPackMetadataEntity>>

    @Query("SELECT * FROM map_pack_metadata ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<MapPackMetadataEntity>

    @Query("SELECT * FROM map_pack_metadata WHERE region_id = :regionId LIMIT 1")
    suspend fun getByRegion(regionId: String): MapPackMetadataEntity?

    @Query("DELETE FROM map_pack_metadata WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface HomeWorkoutDraftDao {
    @Query("SELECT * FROM home_workout_drafts WHERE owner_id = :ownerId LIMIT 1")
    suspend fun get(ownerId: String): HomeWorkoutDraftEntity?

    @Upsert
    suspend fun upsert(draft: HomeWorkoutDraftEntity)

    @Query("DELETE FROM home_workout_drafts WHERE owner_id = :ownerId")
    suspend fun delete(ownerId: String): Int
}

@Dao
interface SyncOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(operation: SyncOperationEntity): Long

    @Query("""
        SELECT * FROM sync_operations
        WHERE account_user_id = :accountUserId AND status IN ('PENDING', 'RETRY') AND next_attempt_at_epoch_millis <= :now
        ORDER BY sequence ASC, created_at_epoch_millis ASC, id ASC
        LIMIT :limit
    """)
    suspend fun nextBatch(now: Long, limit: Int, accountUserId: String = "local"): List<SyncOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE account_user_id = :accountUserId AND status IN ('PENDING', 'RETRY')")
    fun observeQueuedCount(accountUserId: String): Flow<Int>

    @Query("SELECT MIN(next_attempt_at_epoch_millis) FROM sync_operations WHERE account_user_id = :accountUserId AND status IN ('PENDING', 'RETRY')")
    suspend fun earliestPendingAttempt(accountUserId: String): Long?

    @Query("SELECT last_error_code FROM sync_operations WHERE account_user_id = :accountUserId AND status IN ('FAILED', 'RETRY') AND last_error_code IS NOT NULL ORDER BY sequence DESC LIMIT 1")
    fun observeLastErrorCode(accountUserId: String): Flow<String?>

    @Query("UPDATE sync_operations SET account_user_id = :accountUserId WHERE account_user_id = 'local' AND status IN ('PENDING', 'RETRY')")
    suspend fun bindLocalPendingOperations(accountUserId: String): Int

    @Query("UPDATE activities SET owner_id = :ownerId WHERE owner_id = 'local'")
    suspend fun linkLocalActivities(ownerId: String): Int

    @Query("UPDATE workout_templates SET owner_id = :ownerId WHERE owner_id = 'local'")
    suspend fun linkLocalTemplates(ownerId: String): Int

    @Query("UPDATE planned_sessions SET owner_id = :ownerId WHERE owner_id = 'local'")
    suspend fun linkLocalPlannedSessions(ownerId: String): Int

    @Query("UPDATE goals SET owner_id = :ownerId WHERE owner_id = 'local'")
    suspend fun linkLocalGoals(ownerId: String): Int

    @Query("UPDATE personal_records SET owner_id = :ownerId WHERE owner_id = 'local'")
    suspend fun linkLocalPersonalRecords(ownerId: String): Int

    @Query("UPDATE saved_routes SET owner_id = :ownerId WHERE owner_id = 'local'")
    suspend fun linkLocalSavedRoutes(ownerId: String): Int

    @Query("INSERT OR IGNORE INTO sync_operations (id, entity_type, entity_id, account_user_id, operation_type, payload, sequence, retry_count, next_attempt_at_epoch_millis, created_at_epoch_millis, status, last_error_code) SELECT 'saved_route-upsert:' || id || ':' || updated_at_epoch_millis, 'saved_route', id, :ownerId, CASE WHEN deleted_at_epoch_millis IS NULL THEN 'UPSERT' ELSE 'DELETE' END, '', updated_at_epoch_millis, 0, updated_at_epoch_millis, updated_at_epoch_millis, 'PENDING', NULL FROM saved_routes WHERE owner_id = :ownerId")
    suspend fun enqueueSavedRouteBackfill(ownerId: String)

    @Query("DELETE FROM activity_aggregate_cache WHERE owner_id = 'local'")
    suspend fun clearLocalAggregates(): Int

    @Query("INSERT OR IGNORE INTO sync_operations (id, entity_type, entity_id, account_user_id, operation_type, payload, sequence, retry_count, next_attempt_at_epoch_millis, created_at_epoch_millis, status, last_error_code) SELECT 'activity-upsert:' || id || ':' || updated_at_epoch_millis, 'activity', id, :ownerId, CASE WHEN deleted_at_epoch_millis IS NULL THEN 'UPSERT' ELSE 'DELETE' END, '', updated_at_epoch_millis, 0, updated_at_epoch_millis, updated_at_epoch_millis, 'PENDING', NULL FROM activities WHERE owner_id = :ownerId")
    suspend fun enqueueActivityBackfill(ownerId: String)

    @Query("INSERT OR IGNORE INTO sync_operations (id, entity_type, entity_id, account_user_id, operation_type, payload, sequence, retry_count, next_attempt_at_epoch_millis, created_at_epoch_millis, status, last_error_code) SELECT 'workout_template-upsert:' || id || ':' || updated_at_epoch_millis, 'workout_template', id, :ownerId, CASE WHEN deleted_at_epoch_millis IS NULL THEN 'UPSERT' ELSE 'DELETE' END, '', updated_at_epoch_millis, 0, updated_at_epoch_millis, updated_at_epoch_millis, 'PENDING', NULL FROM workout_templates WHERE owner_id = :ownerId")
    suspend fun enqueueTemplateBackfill(ownerId: String)

    @Query("INSERT OR IGNORE INTO sync_operations (id, entity_type, entity_id, account_user_id, operation_type, payload, sequence, retry_count, next_attempt_at_epoch_millis, created_at_epoch_millis, status, last_error_code) SELECT 'planned_session-upsert:' || id || ':' || updated_at_epoch_millis, 'planned_session', id, :ownerId, CASE WHEN deleted_at_epoch_millis IS NULL THEN 'UPSERT' ELSE 'DELETE' END, '', updated_at_epoch_millis, 0, updated_at_epoch_millis, updated_at_epoch_millis, 'PENDING', NULL FROM planned_sessions WHERE owner_id = :ownerId")
    suspend fun enqueuePlannedSessionBackfill(ownerId: String)

    @Query("INSERT OR IGNORE INTO sync_operations (id, entity_type, entity_id, account_user_id, operation_type, payload, sequence, retry_count, next_attempt_at_epoch_millis, created_at_epoch_millis, status, last_error_code) SELECT 'goal-upsert:' || id || ':' || updated_at_epoch_millis, 'goal', id, :ownerId, CASE WHEN deleted_at_epoch_millis IS NULL THEN 'UPSERT' ELSE 'DELETE' END, '', updated_at_epoch_millis, 0, updated_at_epoch_millis, updated_at_epoch_millis, 'PENDING', NULL FROM goals WHERE owner_id = :ownerId")
    suspend fun enqueueGoalBackfill(ownerId: String)

    @Query("INSERT OR IGNORE INTO sync_operations (id, entity_type, entity_id, account_user_id, operation_type, payload, sequence, retry_count, next_attempt_at_epoch_millis, created_at_epoch_millis, status, last_error_code) SELECT 'personal_record-upsert:' || id || ':' || updated_at_epoch_millis, 'personal_record', id, :ownerId, CASE WHEN deleted_at_epoch_millis IS NULL THEN 'UPSERT' ELSE 'DELETE' END, '', updated_at_epoch_millis, 0, updated_at_epoch_millis, updated_at_epoch_millis, 'PENDING', NULL FROM personal_records WHERE owner_id = :ownerId")
    suspend fun enqueuePersonalRecordBackfill(ownerId: String)

    @Query("UPDATE sync_operations SET status = :status, retry_count = :retryCount, next_attempt_at_epoch_millis = :nextAttemptAt, last_error_code = :errorCode WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, retryCount: Int, nextAttemptAt: Long, errorCode: String?): Int

    @Query("UPDATE sync_operations SET status = 'RETRY', next_attempt_at_epoch_millis = :now, last_error_code = NULL WHERE account_user_id = :accountUserId AND status IN ('FAILED', 'RETRY')")
    suspend fun retryOutstandingNow(accountUserId: String, now: Long): Int

    @Query("DELETE FROM sync_operations WHERE status = 'DONE' AND created_at_epoch_millis < :olderThan")
    suspend fun purgeCompletedBefore(olderThan: Long): Int
}
