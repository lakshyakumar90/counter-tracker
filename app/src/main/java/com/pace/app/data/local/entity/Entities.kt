package com.pace.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A finalized outdoor activity summary. Distances and elevation use SI units. */
@Entity(
    tableName = "activities",
    indices = [
        Index(value = ["owner_id", "started_at_epoch_millis"], name = "index_activities_owner_started"),
        Index(value = ["sport", "started_at_epoch_millis"], name = "index_activities_sport_started"),
        Index(value = ["deleted_at_epoch_millis"], name = "index_activities_deleted_at"),
    ],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id", defaultValue = "'local'") val ownerId: String = "local",
    val sport: String,
    @ColumnInfo(defaultValue = "'SAVED'") val status: String = "SAVED",
    @ColumnInfo(name = "started_at_epoch_millis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "ended_at_epoch_millis") val endedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "elapsed_seconds", defaultValue = "0") val elapsedSeconds: Long = 0,
    @ColumnInfo(name = "moving_seconds", defaultValue = "0") val movingSeconds: Long = 0,
    @ColumnInfo(name = "distance_meters", defaultValue = "0") val distanceMeters: Long = 0,
    @ColumnInfo(name = "elevation_gain_meters") val elevationGainMeters: Double? = null,
    val title: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "data_quality_flags", defaultValue = "") val dataQualityFlags: String = "",
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at_epoch_millis") val deletedAtEpochMillis: Long? = null,
)

/** Rebuildable UTC-week summary cache; Room activities remain the source of truth. */
@Entity(tableName = "activity_aggregate_cache", primaryKeys = ["owner_id", "period_key", "sport"])
data class ActivityAggregateCacheEntity(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "period_key") val periodKey: String,
    val sport: String,
    @ColumnInfo(name = "activity_count") val activityCount: Int,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Long,
    @ColumnInfo(name = "moving_seconds") val movingSeconds: Long,
    @ColumnInfo(name = "elapsed_seconds") val elapsedSeconds: Long,
    @ColumnInfo(name = "refreshed_at_epoch_millis") val refreshedAtEpochMillis: Long,
)

@Entity(
    tableName = "saved_routes",
    indices = [
        Index(value = ["updated_at_epoch_millis"], name = "index_saved_routes_updated_at"),
        Index(value = ["owner_id", "updated_at_epoch_millis"], name = "index_saved_routes_owner_updated_at"),
        Index(value = ["deleted_at_epoch_millis"], name = "index_saved_routes_deleted_at"),
        Index(value = ["source_activity_id"], name = "index_saved_routes_source_activity"),
    ],
    foreignKeys = [ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["source_activity_id"], onDelete = ForeignKey.SET_NULL)],
)
data class SavedRouteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String? = null,
    val sport: String,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "source_activity_id") val sourceActivityId: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at_epoch_millis") val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "owner_id", defaultValue = "'local'") val ownerId: String = "local",
)

@Entity(
    tableName = "saved_route_points",
    primaryKeys = ["route_id", "sequence"],
    foreignKeys = [ForeignKey(entity = SavedRouteEntity::class, parentColumns = ["id"], childColumns = ["route_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["route_id", "sequence"], name = "index_saved_route_points_order")],
)
data class SavedRoutePointEntity(
    @ColumnInfo(name = "route_id") val routeId: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "altitude_meters") val altitudeMeters: Double? = null,
    @ColumnInfo(name = "segment_index", defaultValue = "0") val segmentIndex: Int = 0,
)

/** Coordinates are stored privately in the app sandbox; never log these values. */
@Entity(
    tableName = "track_points",
    primaryKeys = ["activity_id", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["activity_id", "timestamp_epoch_millis"], name = "index_track_points_activity_timestamp"),
    ],
)
data class TrackPointEntity(
    @ColumnInfo(name = "activity_id") val activityId: String,
    val sequence: Int,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "altitude_meters") val altitudeMeters: Double? = null,
    @ColumnInfo(name = "horizontal_accuracy_meters") val horizontalAccuracyMeters: Float? = null,
    @ColumnInfo(name = "speed_meters_per_second") val speedMetersPerSecond: Float? = null,
    @ColumnInfo(name = "bearing_degrees") val bearingDegrees: Float? = null,
    @ColumnInfo(name = "segment_index", defaultValue = "0") val segmentIndex: Int = 0,
)

@Entity(
    tableName = "splits",
    primaryKeys = ["activity_id", "split_number"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["activity_id", "split_number"], name = "index_splits_activity_number")],
)
data class SplitEntity(
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "split_number") val splitNumber: Int,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "elapsed_seconds") val elapsedSeconds: Long,
    @ColumnInfo(name = "moving_seconds") val movingSeconds: Long,
    @ColumnInfo(name = "average_pace_seconds_per_km") val averagePaceSecondsPerKm: Double? = null,
    @ColumnInfo(name = "average_heart_rate_bpm") val averageHeartRateBpm: Int? = null,
)

@Entity(
    tableName = "sensor_samples",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["activity_id", "sample_type", "timestamp_epoch_millis"], name = "index_sensor_samples_activity_type_time")],
)
data class SensorSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "sample_type") val sampleType: String,
    @ColumnInfo(name = "timestamp_epoch_millis") val timestampEpochMillis: Long,
    val value: Double,
    val unit: String,
)

@Entity(
    tableName = "workout_templates",
    indices = [
        Index(value = ["sport", "updated_at_epoch_millis"], name = "index_workout_templates_sport_updated"),
        Index(value = ["owner_id", "sport", "updated_at_epoch_millis"], name = "index_workout_templates_owner_sport_updated_at"),
    ],
)
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sport: String,
    val description: String? = null,
    @ColumnInfo(name = "target_duration_seconds") val targetDurationSeconds: Long? = null,
    @ColumnInfo(name = "target_distance_meters") val targetDistanceMeters: Double? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at_epoch_millis") val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "owner_id", defaultValue = "'local'") val ownerId: String = "local",
)

@Entity(
    tableName = "workout_intervals",
    primaryKeys = ["template_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["template_id", "position"], name = "index_workout_intervals_template_position")],
)
data class WorkoutIntervalEntity(
    @ColumnInfo(name = "template_id") val templateId: String,
    val position: Int,
    @ColumnInfo(name = "interval_type") val intervalType: String,
    @ColumnInfo(name = "repeat_count", defaultValue = "1") val repeatCount: Int = 1,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long? = null,
    @ColumnInfo(name = "target_metric") val targetMetric: String? = null,
    @ColumnInfo(name = "target_min") val targetMin: Double? = null,
    @ColumnInfo(name = "target_max") val targetMax: Double? = null,
)

@Entity(
    tableName = "planned_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["completed_activity_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["scheduled_date"], name = "index_planned_sessions_scheduled_date"),
        Index(value = ["owner_id", "scheduled_date"], name = "index_planned_sessions_owner_scheduled_date"),
        Index(value = ["template_id"], name = "index_planned_sessions_template_id"),
        Index(value = ["completed_activity_id"], name = "index_planned_sessions_completed_activity_id"),
    ],
)
data class PlannedSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "template_id") val templateId: String? = null,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String,
    @ColumnInfo(name = "reminder_enabled", defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "'PLANNED'") val status: String = "PLANNED",
    @ColumnInfo(name = "completed_activity_id") val completedActivityId: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at_epoch_millis") val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "owner_id", defaultValue = "'local'") val ownerId: String = "local",
)

@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["goal_type", "sport", "start_date", "end_date"], name = "index_goals_type_sport_range"),
        Index(value = ["owner_id", "goal_type", "sport", "start_date", "end_date"], name = "index_goals_owner_type_sport_range"),
        Index(value = ["deleted_at_epoch_millis"], name = "index_goals_deleted_at"),
    ],
)
data class GoalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goal_type") val goalType: String,
    val sport: String? = null,
    @ColumnInfo(name = "target_value") val targetValue: Double,
    val unit: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "deleted_at_epoch_millis") val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "owner_id", defaultValue = "'local'") val ownerId: String = "local",
)

@Entity(
    tableName = "personal_records",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sport", "metric", "distance_meters", "achieved_at_epoch_millis"], name = "index_personal_records_best_lookup"),
        Index(value = ["owner_id", "sport", "metric", "distance_meters", "achieved_at_epoch_millis"], name = "index_personal_records_owner_lookup"),
        Index(value = ["activity_id"], name = "index_personal_records_activity_id"),
    ],
)
data class PersonalRecordEntity(
    @PrimaryKey val id: String,
    val sport: String,
    val metric: String,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double? = null,
    @ColumnInfo(name = "record_value") val recordValue: Double,
    val unit: String,
    @ColumnInfo(name = "achieved_at_epoch_millis") val achievedAtEpochMillis: Long,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
    val provenance: String,
    @ColumnInfo(name = "deleted_at_epoch_millis") val deletedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "owner_id", defaultValue = "'local'") val ownerId: String = "local",
    @ColumnInfo(name = "updated_at_epoch_millis", defaultValue = "0") val updatedAtEpochMillis: Long = 0,
)

@Entity(
    tableName = "map_pack_metadata",
    indices = [
        Index(value = ["region_id"], unique = true, name = "index_map_pack_metadata_region_id"),
        Index(value = ["status", "updated_at_epoch_millis"], name = "index_map_pack_metadata_status_updated"),
    ],
)
data class MapPackMetadataEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "region_id") val regionId: String,
    val name: String,
    @ColumnInfo(name = "south_latitude") val southLatitude: Double,
    @ColumnInfo(name = "west_longitude") val westLongitude: Double,
    @ColumnInfo(name = "north_latitude") val northLatitude: Double,
    @ColumnInfo(name = "east_longitude") val eastLongitude: Double,
    @ColumnInfo(name = "resource_path") val resourcePath: String? = null,
    val version: String,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0,
    val checksum: String? = null,
    @ColumnInfo(defaultValue = "'AVAILABLE'") val status: String = "AVAILABLE",
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(tableName = "home_workout_drafts")
data class HomeWorkoutDraftEntity(
    @PrimaryKey @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "blocks_json") val blocksJson: String,
    @ColumnInfo(name = "circuit_repeats") val circuitRepeats: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    @ColumnInfo(name = "session_started_at_epoch_millis") val sessionStartedAtEpochMillis: Long,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "remaining_step_millis") val remainingStepMillis: Long,
    @ColumnInfo(name = "elapsed_active_millis") val elapsedActiveMillis: Long,
    @ColumnInfo(name = "paused") val paused: Boolean,
    @ColumnInfo(name = "finished") val finished: Boolean,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["status", "next_attempt_at_epoch_millis", "sequence"], name = "index_sync_operations_queue_order"),
        Index(value = ["entity_type", "entity_id"], name = "index_sync_operations_entity"),
        Index(value = ["account_user_id", "status", "next_attempt_at_epoch_millis", "sequence"], name = "index_sync_operations_account_queue"),
    ],
)
data class SyncOperationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "account_user_id", defaultValue = "'local'") val accountUserId: String = "local",
    @ColumnInfo(name = "operation_type") val operationType: String,
    /** Summary metadata only; exact coordinates belong in the separately protected track store. */
    val payload: String,
    val sequence: Long,
    @ColumnInfo(name = "retry_count", defaultValue = "0") val retryCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at_epoch_millis") val nextAttemptAtEpochMillis: Long,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "'PENDING'") val status: String = "PENDING",
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
)
