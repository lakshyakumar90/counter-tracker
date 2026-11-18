package com.pace.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.GoalEntity
import com.pace.app.data.local.entity.MapPackMetadataEntity
import com.pace.app.data.local.entity.PersonalRecordEntity
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.data.local.entity.SensorSampleEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import com.pace.app.data.local.repository.RoomActivityRepository
import com.pace.app.data.local.repository.RoomGoalRepository
import com.pace.app.data.local.repository.RoomMapPackRepository
import com.pace.app.data.local.repository.RoomSyncOperationRepository
import com.pace.app.data.local.repository.RoomWorkoutRepository
import com.pace.app.domain.repository.FinalizedActivity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaceDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: PaceDatabase
    private lateinit var activities: RoomActivityRepository

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, PaceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activities = RoomActivityRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun activityFinalizationPersistsOrderedPointsSplitsAndSensors() = runBlocking {
        activities.finalizeActivity(
            FinalizedActivity(
                summary = activity("route-1", startedAt = 100),
                trackPoints = listOf(point("route-1", 2), point("route-1", 0), point("route-1", 1)),
                splits = listOf(split("route-1", 2), split("route-1", 1)),
                sensorSamples = listOf(sensor("route-1", "HEART_RATE", 200), sensor("route-1", "HEART_RATE", 100)),
            ),
        )

        val loaded = activities.getActivity("route-1")
        assertNotNull(loaded)
        assertEquals(listOf(0, 1, 2), database.trackPointDao().forActivity("route-1").map { it.sequence })
        assertEquals(listOf(1, 2), database.splitDao().forActivity("route-1").map { it.splitNumber })
        assertEquals(listOf(100L, 200L), database.sensorSampleDao().forActivity("route-1", "HEART_RATE").map { it.timestampEpochMillis })
    }

    @Test
    fun activityFinalizationRollsBackSummaryAndPointsIfAChildInsertFails() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_sensor BEFORE INSERT ON sensor_samples BEGIN SELECT RAISE(ABORT, 'injected test failure'); END",
        )

        val result = runCatching {
            activities.finalizeActivity(
                FinalizedActivity(
                    summary = activity("rollback", startedAt = 100),
                    trackPoints = listOf(point("rollback", 0)),
                    sensorSamples = listOf(sensor("rollback", "HEART_RATE", 100)),
                ),
            )
        }

        assertTrue(result.isFailure)
        assertNull(activities.getActivity("rollback"))
        assertEquals(0, database.trackPointDao().countForActivity("rollback"))
        assertEquals(0, database.activityDao().countActive())
    }

    @Test
    fun historySearchPaginationAndDeletionUseStableOrdering() = runBlocking {
        listOf(
            activity("history-a", startedAt = 300, sport = "RUNNING", title = "Morning run"),
            activity("history-b", startedAt = 200, sport = "CYCLING", title = "Evening ride"),
            activity("history-c", startedAt = 100, sport = "RUNNING", title = "Park run"),
        ).forEach { activities.finalizeActivity(FinalizedActivity(it)) }

        val firstPage = activities.page(limit = 2)
        assertEquals(listOf("history-a", "history-b"), firstPage.map { it.id })
        val secondPage = activities.page(beforeStartedAt = firstPage.last().startedAtEpochMillis, beforeId = firstPage.last().id, limit = 2)
        assertEquals(listOf("history-c"), secondPage.map { it.id })
        assertEquals(listOf("history-c"), activities.page(sport = "RUNNING", query = "Park", limit = 10).map { it.id })

        assertTrue(activities.deleteActivity("history-b", deletedAtEpochMillis = 400))
        assertFalse(activities.deleteActivity("history-b", deletedAtEpochMillis = 401))
        val resurrection = runCatching {
            activities.finalizeActivity(FinalizedActivity(activity("history-b", startedAt = 500)))
        }
        assertTrue(resurrection.isFailure)
        assertEquals(listOf("history-a", "history-c"), activities.page(limit = 10).map { it.id })
    }

    @Test
    fun tombstoneIsRetainedUntilItsSyncOperationCompletes() = runBlocking {
        activities.finalizeActivity(FinalizedActivity(activity("tombstone", startedAt = 100)))
        val sync = RoomSyncOperationRepository(database)
        val created = sync.nextBatch(nowEpochMillis = 100).single()
        assertEquals("UPSERT", created.operationType)
        assertTrue(sync.updateStatus(created.id, "DONE", 0, 100))

        assertTrue(activities.deleteActivity("tombstone", deletedAtEpochMillis = 500))
        assertEquals(0, activities.purgeDeletedBefore(1_000))

        val queued = sync.nextBatch(nowEpochMillis = 500)
        assertEquals(1, queued.size)
        assertEquals("activity", queued.single().entityType)
        assertEquals("tombstone", queued.single().entityId)
        assertTrue(sync.updateStatus(queued.single().id, "DONE", 0, 500))
        assertEquals(1, activities.purgeDeletedBefore(1_000))
    }

    @Test
    fun workoutAndCalendarRepositoriesReplaceIntervalsAndSortDates() = runBlocking {
        val repository = RoomWorkoutRepository(database)
        val template = WorkoutTemplateEntity("tempo", "Tempo", "RUNNING", createdAtEpochMillis = 10, updatedAtEpochMillis = 10)
        repository.saveTemplate(template, listOf(interval("tempo", 0), interval("tempo", 1)))
        repository.saveTemplate(template.copy(updatedAtEpochMillis = 20), listOf(interval("tempo", 2)))
        assertEquals(listOf(2), repository.getTemplate("tempo")?.intervals?.map { it.position })

        repository.savePlannedSession(planned("later", "2026-11-22"))
        repository.savePlannedSession(planned("earlier", "2026-11-20"))
        val calendar = repository.observeCalendar("2026-11-20", "2026-11-23").first()
        assertEquals(listOf("earlier", "later"), calendar.map { it.id })
    }

    @Test
    fun goalsRecordsAndMapPackMetadataRemainLocalAndQueryable() = runBlocking {
        val goalRepository = RoomGoalRepository(database)
        goalRepository.saveGoal(
            GoalEntity(
                id = "weekly-distance",
                goalType = "DISTANCE",
                sport = "RUNNING",
                targetValue = 20_000.0,
                unit = "m",
                startDate = "2026-11-16",
                endDate = "2026-11-23",
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
        )
        assertEquals("weekly-distance", goalRepository.observeGoals().first().single().id)
        goalRepository.savePersonalRecord(personalRecord("record-slow", value = 360.0))
        goalRepository.savePersonalRecord(personalRecord("record-fast", value = 330.0))
        assertEquals(360.0, goalRepository.maximumRecord("RUNNING", "5K_TIME")!!, 0.0)
        assertEquals(330.0, goalRepository.minimumRecord("RUNNING", "5K_TIME")!!, 0.0)

        val mapPacks = RoomMapPackRepository(database)
        val metadata = MapPackMetadataEntity(
            id = "region-bengaluru",
            regionId = "in-ka-bengaluru-v1",
            name = "Bengaluru",
            southLatitude = 12.7,
            westLongitude = 77.4,
            northLatitude = 13.2,
            eastLongitude = 77.8,
            version = "1",
            status = "AVAILABLE",
            updatedAtEpochMillis = 1,
        )
        mapPacks.save(metadata)
        assertEquals(metadata.id, mapPacks.getByRegion(metadata.regionId)?.id)
        assertTrue(mapPacks.delete(metadata.id))
    }

    @Test
    fun deterministicLongRouteFixtureBatchesThousandsOfSamples() = runBlocking {
        val fixture = PaceFixtureGenerator.longRoute("synthetic-long", pointCount = 2_000)
        val repeated = PaceFixtureGenerator.longRoute("synthetic-long", pointCount = 2_000)
        assertEquals(fixture, repeated)

        activities.finalizeActivity(fixture)

        assertEquals(2_000, database.trackPointDao().countForActivity("synthetic-long"))
        assertEquals(0, database.trackPointDao().forActivity("synthetic-long").first().sequence)
        assertEquals(1_999, database.trackPointDao().forActivity("synthetic-long").last().sequence)
        assertTrue(database.sensorSampleDao().forActivity("synthetic-long", "POWER").isNotEmpty())
    }

    @Test
    fun versionOneActivitySurvivesSchemaMigrationToVersionTwo() {
        val fileName = "pace-migration-${System.currentTimeMillis()}.db"
        val file: File = context.getDatabasePath(fileName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL(
                "CREATE TABLE activities (id TEXT NOT NULL PRIMARY KEY, sport TEXT NOT NULL, started_at_epoch_millis INTEGER NOT NULL, elapsed_seconds INTEGER NOT NULL, distance_meters INTEGER NOT NULL, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL)",
            )
            legacy.execSQL(
                "INSERT INTO activities (id, sport, started_at_epoch_millis, elapsed_seconds, distance_meters, created_at_epoch_millis, updated_at_epoch_millis) VALUES ('legacy-1', 'RUNNING', 1000, 1800, 5000, 1100, 1200)",
            )
            legacy.version = 1
        }

        val upgraded = Room.databaseBuilder(context, PaceDatabase::class.java, fileName)
            .addMigrations(PaceDatabase.MIGRATION_1_2)
            .addMigrations(PaceDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val restored = runBlocking { upgraded.activityDao().getById("legacy-1") }
            assertNotNull(restored)
            assertEquals("RUNNING", restored?.sport)
            assertEquals(1_000L, restored?.startedAtEpochMillis)
            assertEquals(1_800L, restored?.elapsedSeconds)
            assertEquals(5_000L, restored?.distanceMeters)
            assertEquals("local", restored?.ownerId)
            assertEquals(0L, restored?.movingSeconds)
            assertEquals("", restored?.dataQualityFlags)
            val columns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(track_points)")
            val columnNames = buildList {
                while (columns.moveToNext()) add(columns.getString(columns.getColumnIndexOrThrow("name")))
                columns.close()
            }
            assertTrue(columnNames.contains("segment_index"))
        } finally {
            upgraded.close()
            context.deleteDatabase(fileName)
        }
    }

    private fun activity(
        id: String,
        startedAt: Long,
        sport: String = "RUNNING",
        title: String? = null,
    ) = ActivityEntity(
        id = id,
        sport = sport,
        startedAtEpochMillis = startedAt,
        endedAtEpochMillis = startedAt + 60_000,
        elapsedSeconds = 60,
        movingSeconds = 55,
        distanceMeters = 300,
        title = title,
        createdAtEpochMillis = startedAt,
        updatedAtEpochMillis = startedAt,
    )

    private fun point(activityId: String, sequence: Int) = TrackPointEntity(
        activityId = activityId,
        sequence = sequence,
        timestampEpochMillis = 100L + sequence,
        latitude = 12.9 + sequence / 100_000.0,
        longitude = 77.6 + sequence / 100_000.0,
        horizontalAccuracyMeters = 4f,
    )

    private fun split(activityId: String, number: Int) = SplitEntity(
        activityId = activityId,
        splitNumber = number,
        distanceMeters = 1_000.0,
        elapsedSeconds = number * 300L,
        movingSeconds = number * 280L,
    )

    private fun sensor(activityId: String, type: String, time: Long) = SensorSampleEntity(
        activityId = activityId,
        sampleType = type,
        timestampEpochMillis = time,
        value = 145.0,
        unit = "bpm",
    )

    private fun interval(templateId: String, position: Int) = WorkoutIntervalEntity(
        templateId = templateId,
        position = position,
        intervalType = "WORK",
        durationSeconds = 300,
    )

    private fun planned(id: String, date: String) = PlannedSessionEntity(
        id = id,
        scheduledDate = date,
        timeZoneId = "Asia/Kolkata",
        updatedAtEpochMillis = 1,
    )

    private fun personalRecord(id: String, value: Double) = PersonalRecordEntity(
        id = id,
        sport = "RUNNING",
        metric = "5K_TIME",
        distanceMeters = 5_000.0,
        recordValue = value,
        unit = "s",
        achievedAtEpochMillis = value.toLong(),
        provenance = "FIXTURE",
    )
}
