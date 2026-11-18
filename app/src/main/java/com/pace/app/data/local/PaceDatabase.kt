package com.pace.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pace.app.data.local.dao.ActivityDao
import com.pace.app.data.local.dao.GoalDao
import com.pace.app.data.local.dao.MapPackDao
import com.pace.app.data.local.dao.SensorSampleDao
import com.pace.app.data.local.dao.SplitDao
import com.pace.app.data.local.dao.SyncOperationDao
import com.pace.app.data.local.dao.TrackPointDao
import com.pace.app.data.local.dao.WorkoutDao
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

@Database(
    entities = [
        ActivityEntity::class,
        TrackPointEntity::class,
        SplitEntity::class,
        SensorSampleEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutIntervalEntity::class,
        PlannedSessionEntity::class,
        GoalEntity::class,
        PersonalRecordEntity::class,
        MapPackMetadataEntity::class,
        SyncOperationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class PaceDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun splitDao(): SplitDao
    abstract fun sensorSampleDao(): SensorSampleDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun goalDao(): GoalDao
    abstract fun mapPackDao(): MapPackDao
    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        const val DATABASE_NAME = "pace_local.db"

        /** The version 1 alpha schema held only activity summaries. Preserve it when expanding the model. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild the original alpha table so SQLite defaults match Room's exported schema.
                db.execSQL("ALTER TABLE activities RENAME TO activities_v1")
                db.execSQL("CREATE TABLE activities (id TEXT NOT NULL PRIMARY KEY, owner_id TEXT NOT NULL DEFAULT 'local', sport TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'SAVED', started_at_epoch_millis INTEGER NOT NULL, ended_at_epoch_millis INTEGER, elapsed_seconds INTEGER NOT NULL DEFAULT 0, moving_seconds INTEGER NOT NULL DEFAULT 0, distance_meters INTEGER NOT NULL DEFAULT 0, elevation_gain_meters REAL, title TEXT, notes TEXT, data_quality_flags TEXT NOT NULL DEFAULT '', created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL, deleted_at_epoch_millis INTEGER)")
                db.execSQL("INSERT INTO activities (id, owner_id, sport, status, started_at_epoch_millis, elapsed_seconds, moving_seconds, distance_meters, data_quality_flags, created_at_epoch_millis, updated_at_epoch_millis) SELECT id, 'local', sport, 'SAVED', started_at_epoch_millis, elapsed_seconds, 0, distance_meters, '', created_at_epoch_millis, updated_at_epoch_millis FROM activities_v1")
                db.execSQL("DROP TABLE activities_v1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_owner_started ON activities(owner_id, started_at_epoch_millis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_sport_started ON activities(sport, started_at_epoch_millis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_deleted_at ON activities(deleted_at_epoch_millis)")

                db.execSQL("CREATE TABLE IF NOT EXISTS track_points (activity_id TEXT NOT NULL, sequence INTEGER NOT NULL, timestamp_epoch_millis INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, altitude_meters REAL, horizontal_accuracy_meters REAL, speed_meters_per_second REAL, bearing_degrees REAL, PRIMARY KEY(activity_id, sequence), FOREIGN KEY(activity_id) REFERENCES activities(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_points_activity_timestamp ON track_points(activity_id, timestamp_epoch_millis)")

                db.execSQL("CREATE TABLE IF NOT EXISTS splits (activity_id TEXT NOT NULL, split_number INTEGER NOT NULL, distance_meters REAL NOT NULL, elapsed_seconds INTEGER NOT NULL, moving_seconds INTEGER NOT NULL, average_pace_seconds_per_km REAL, average_heart_rate_bpm INTEGER, PRIMARY KEY(activity_id, split_number), FOREIGN KEY(activity_id) REFERENCES activities(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_splits_activity_number ON splits(activity_id, split_number)")

                db.execSQL("CREATE TABLE IF NOT EXISTS sensor_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, activity_id TEXT NOT NULL, sample_type TEXT NOT NULL, timestamp_epoch_millis INTEGER NOT NULL, value REAL NOT NULL, unit TEXT NOT NULL, FOREIGN KEY(activity_id) REFERENCES activities(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_samples_activity_type_time ON sensor_samples(activity_id, sample_type, timestamp_epoch_millis)")

                db.execSQL("CREATE TABLE IF NOT EXISTS workout_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sport TEXT NOT NULL, description TEXT, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL, deleted_at_epoch_millis INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_templates_sport_updated ON workout_templates(sport, updated_at_epoch_millis)")
                db.execSQL("CREATE TABLE IF NOT EXISTS workout_intervals (template_id TEXT NOT NULL, position INTEGER NOT NULL, interval_type TEXT NOT NULL, repeat_count INTEGER NOT NULL DEFAULT 1, duration_seconds INTEGER, target_metric TEXT, target_min REAL, target_max REAL, PRIMARY KEY(template_id, position), FOREIGN KEY(template_id) REFERENCES workout_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_intervals_template_position ON workout_intervals(template_id, position)")

                db.execSQL("CREATE TABLE IF NOT EXISTS planned_sessions (id TEXT NOT NULL PRIMARY KEY, template_id TEXT, scheduled_date TEXT NOT NULL, time_zone_id TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PLANNED', completed_activity_id TEXT, notes TEXT, updated_at_epoch_millis INTEGER NOT NULL, deleted_at_epoch_millis INTEGER, FOREIGN KEY(template_id) REFERENCES workout_templates(id) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(completed_activity_id) REFERENCES activities(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_sessions_scheduled_date ON planned_sessions(scheduled_date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_sessions_template_id ON planned_sessions(template_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_sessions_completed_activity_id ON planned_sessions(completed_activity_id)")

                db.execSQL("CREATE TABLE IF NOT EXISTS goals (id TEXT NOT NULL PRIMARY KEY, goal_type TEXT NOT NULL, sport TEXT, target_value REAL NOT NULL, unit TEXT NOT NULL, start_date TEXT NOT NULL, end_date TEXT NOT NULL, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL, deleted_at_epoch_millis INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_type_sport_range ON goals(goal_type, sport, start_date, end_date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goals_deleted_at ON goals(deleted_at_epoch_millis)")

                db.execSQL("CREATE TABLE IF NOT EXISTS personal_records (id TEXT NOT NULL PRIMARY KEY, sport TEXT NOT NULL, metric TEXT NOT NULL, distance_meters REAL, record_value REAL NOT NULL, unit TEXT NOT NULL, achieved_at_epoch_millis INTEGER NOT NULL, activity_id TEXT, provenance TEXT NOT NULL, deleted_at_epoch_millis INTEGER, FOREIGN KEY(activity_id) REFERENCES activities(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_records_best_lookup ON personal_records(sport, metric, distance_meters, achieved_at_epoch_millis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_records_activity_id ON personal_records(activity_id)")

                db.execSQL("CREATE TABLE IF NOT EXISTS map_pack_metadata (id TEXT NOT NULL PRIMARY KEY, region_id TEXT NOT NULL, name TEXT NOT NULL, south_latitude REAL NOT NULL, west_longitude REAL NOT NULL, north_latitude REAL NOT NULL, east_longitude REAL NOT NULL, resource_path TEXT, version TEXT NOT NULL, downloaded_bytes INTEGER NOT NULL, checksum TEXT, status TEXT NOT NULL DEFAULT 'AVAILABLE', updated_at_epoch_millis INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_map_pack_metadata_region_id ON map_pack_metadata(region_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_map_pack_metadata_status_updated ON map_pack_metadata(status, updated_at_epoch_millis)")

                db.execSQL("CREATE TABLE IF NOT EXISTS sync_operations (id TEXT NOT NULL PRIMARY KEY, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, operation_type TEXT NOT NULL, payload TEXT NOT NULL, sequence INTEGER NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0, next_attempt_at_epoch_millis INTEGER NOT NULL, created_at_epoch_millis INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', last_error_code TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_queue_order ON sync_operations(status, next_attempt_at_epoch_millis, sequence)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_entity ON sync_operations(entity_type, entity_id)")
            }
        }

        /** Keep GPS points on separate route lines when location continuity was lost. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_points ADD COLUMN segment_index INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): PaceDatabase = Room.databaseBuilder(
            context.applicationContext,
            PaceDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .build()
    }
}
