package com.pace.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
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

    @Query("SELECT * FROM activities WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun getById(id: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): ActivityEntity?

    @Transaction
    @Query("SELECT * FROM activities WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun getWithTrack(id: String): ActivityWithTrack?

    @Query("""
        SELECT * FROM activities
        WHERE owner_id = :ownerId
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
        WHERE owner_id = :ownerId AND deleted_at_epoch_millis IS NULL
        ORDER BY started_at_epoch_millis DESC, id DESC
    """)
    fun observeHistory(ownerId: String = "local"): Flow<List<ActivityEntity>>

    @Query("UPDATE activities SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun markDeleted(id: String, deletedAt: Long): Int

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
}

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY sequence ASC")
    suspend fun forActivity(activityId: String): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE activity_id = :activityId")
    suspend fun deleteForActivity(activityId: String)

    @Query("SELECT COUNT(*) FROM track_points WHERE activity_id = :activityId")
    suspend fun countForActivity(activityId: String): Int
}

@Dao
interface SplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(splits: List<SplitEntity>)

    @Query("SELECT * FROM splits WHERE activity_id = :activityId ORDER BY split_number ASC")
    suspend fun forActivity(activityId: String): List<SplitEntity>

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
    @Query("SELECT * FROM workout_templates WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun getTemplate(id: String): WorkoutTemplateWithIntervals?

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getTemplateIncludingDeleted(id: String): WorkoutTemplateEntity?

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE deleted_at_epoch_millis IS NULL ORDER BY updated_at_epoch_millis DESC, id ASC")
    fun observeTemplates(): Flow<List<WorkoutTemplateWithIntervals>>

    @Query("""
        SELECT * FROM planned_sessions
        WHERE scheduled_date >= :fromDate AND scheduled_date < :toDateExclusive
          AND deleted_at_epoch_millis IS NULL
        ORDER BY scheduled_date ASC, id ASC
    """)
    fun observeCalendar(fromDate: String, toDateExclusive: String): Flow<List<PlannedSessionEntity>>

    @Upsert suspend fun upsertPlannedSession(session: PlannedSessionEntity)

    @Query("SELECT * FROM planned_sessions WHERE id = :id")
    suspend fun getPlannedSessionIncludingDeleted(id: String): PlannedSessionEntity?

    @Query("UPDATE workout_templates SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun markTemplateDeleted(id: String, deletedAt: Long): Int

    @Query("UPDATE planned_sessions SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun markPlannedSessionDeleted(id: String, deletedAt: Long): Int
}

@Dao
interface GoalDao {
    @Upsert suspend fun upsert(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun getById(id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE deleted_at_epoch_millis IS NULL ORDER BY start_date DESC, id ASC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Upsert suspend fun upsertPersonalRecord(record: PersonalRecordEntity)

    @Query("SELECT * FROM personal_records WHERE id = :id")
    suspend fun getPersonalRecordIncludingDeleted(id: String): PersonalRecordEntity?

    @Query("""
        SELECT * FROM personal_records
        WHERE deleted_at_epoch_millis IS NULL
          AND (:sport IS NULL OR sport = :sport)
          AND (:metric IS NULL OR metric = :metric)
        ORDER BY achieved_at_epoch_millis DESC, id ASC
    """)
    fun observePersonalRecords(sport: String? = null, metric: String? = null): Flow<List<PersonalRecordEntity>>

    @Query("UPDATE goals SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun markGoalDeleted(id: String, deletedAt: Long): Int

    @Query("UPDATE personal_records SET deleted_at_epoch_millis = :deletedAt WHERE id = :id AND deleted_at_epoch_millis IS NULL")
    suspend fun markRecordDeleted(id: String, deletedAt: Long): Int

    @Query("SELECT MAX(record_value) FROM personal_records WHERE sport = :sport AND metric = :metric AND deleted_at_epoch_millis IS NULL")
    suspend fun maximumRecord(sport: String, metric: String): Double?

    @Query("SELECT MIN(record_value) FROM personal_records WHERE sport = :sport AND metric = :metric AND deleted_at_epoch_millis IS NULL")
    suspend fun minimumRecord(sport: String, metric: String): Double?
}

@Dao
interface MapPackDao {
    @Upsert suspend fun upsert(metadata: MapPackMetadataEntity)

    @Query("SELECT * FROM map_pack_metadata ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MapPackMetadataEntity>>

    @Query("SELECT * FROM map_pack_metadata WHERE region_id = :regionId LIMIT 1")
    suspend fun getByRegion(regionId: String): MapPackMetadataEntity?

    @Query("DELETE FROM map_pack_metadata WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface SyncOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(operation: SyncOperationEntity): Long

    @Query("""
        SELECT * FROM sync_operations
        WHERE status IN ('PENDING', 'RETRY') AND next_attempt_at_epoch_millis <= :now
        ORDER BY sequence ASC, created_at_epoch_millis ASC, id ASC
        LIMIT :limit
    """)
    suspend fun nextBatch(now: Long, limit: Int): List<SyncOperationEntity>

    @Query("UPDATE sync_operations SET status = :status, retry_count = :retryCount, next_attempt_at_epoch_millis = :nextAttemptAt, last_error_code = :errorCode WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, retryCount: Int, nextAttemptAt: Long, errorCode: String?): Int

    @Query("DELETE FROM sync_operations WHERE status = 'DONE' AND created_at_epoch_millis < :olderThan")
    suspend fun purgeCompletedBefore(olderThan: Long): Int
}
