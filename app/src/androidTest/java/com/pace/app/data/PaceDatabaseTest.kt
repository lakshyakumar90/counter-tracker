package com.pace.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pace.app.data.local.PaceDatabase
import com.pace.app.data.local.OwnerDataEraser
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.SavedRouteEntity
import com.pace.app.data.local.entity.SavedRoutePointEntity
import com.pace.app.data.local.entity.GoalEntity
import com.pace.app.data.local.entity.MapPackMetadataEntity
import com.pace.app.data.local.entity.HomeWorkoutDraftEntity
import com.pace.app.data.local.repository.RoomHomeWorkoutDraftRepository
import com.pace.app.data.local.entity.PersonalRecordEntity
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.data.local.entity.SensorSampleEntity
import com.pace.app.data.local.entity.SyncOperationEntity
import com.pace.app.data.local.entity.SplitEntity
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.data.local.entity.WorkoutIntervalEntity
import com.pace.app.data.local.entity.WorkoutTemplateEntity
import com.pace.app.data.local.repository.RoomActivityRepository
import com.pace.app.data.local.repository.RoomGoalRepository
import com.pace.app.data.local.repository.RoomMapPackRepository
import com.pace.app.data.local.repository.RoomSyncOperationRepository
import com.pace.app.data.local.repository.RoomWorkoutRepository
import com.pace.app.data.local.repository.RoomSavedRouteRepository
import com.pace.app.data.sync.PaceAccountDataLinker
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
    fun aggregateCacheUpdatesIncrementallyAndCanBeRebuiltAfterDeletion() = runBlocking {
        val first = activity("cache-1", 1_700_000_000_000)
        activities.finalizeActivity(FinalizedActivity(first))
        var rows = database.activityAggregateDao().observe().first()
        assertEquals(1, rows.size)
        assertEquals(300L, rows.single().distanceMeters)
        assertEquals(1, rows.single().activityCount)

        activities.finalizeActivity(FinalizedActivity(activity("cache-2", first.startedAtEpochMillis + 1_000, sport = "RUNNING")))
        rows = database.activityAggregateDao().observe().first()
        assertEquals(2, rows.single().activityCount)
        assertEquals(600L, rows.single().distanceMeters)

        activities.deleteActivity("cache-1", first.startedAtEpochMillis + 10_000)
        rows = database.activityAggregateDao().observe().first()
        assertEquals(1, rows.single().activityCount)
        activities.recomputeAggregateCache()
        val rebuilt = database.activityAggregateDao().observe().first()
        assertEquals(rows.single().activityCount, rebuilt.single().activityCount)
        assertEquals(rows.single().distanceMeters, rebuilt.single().distanceMeters)
    }

    @Test
    fun savedRoutePersistsPointsAndDeletionDoesNotDeleteSourceActivity() = runBlocking {
        activities.finalizeActivity(FinalizedActivity(activity("route-source", 1_700_000_000_000)))
        val repository = RoomSavedRouteRepository(database)
        val routeId = "saved-route-1"
        val route = SavedRouteEntity(routeId, "Local loop", sport = "RUNNING", distanceMeters = 240.0, sourceActivityId = "route-source", createdAtEpochMillis = 1, updatedAtEpochMillis = 1)
        val points = listOf(
            SavedRoutePointEntity(routeId, 0, 12.0, 77.0),
            SavedRoutePointEntity(routeId, 1, 12.001, 77.001),
        )
        repository.saveRoute(route, points)
        assertEquals(listOf(0, 1), repository.getRoute(routeId)?.points?.map { it.sequence })
        assertTrue(repository.deleteRoute(routeId, 10))
        assertNull(repository.getRoute(routeId))
        assertNotNull(activities.getActivity("route-source"))
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

        assertTrue(activities.updateMetadata("history-a", "Updated title", "Private notes", 350))
        assertEquals("Updated title", activities.getActivity("history-a")?.activity?.title)
        val metadataPayload = database.openHelper.readableDatabase.query("SELECT payload FROM sync_operations WHERE id = 'activity-upsert:history-a:350'")
        assertTrue(metadataPayload.moveToFirst())
        assertTrue(metadataPayload.getString(0).contains("Private notes"))
        metadataPayload.close()

        assertTrue(activities.deleteActivity("history-b", deletedAtEpochMillis = 400))
        assertFalse(activities.deleteActivity("history-b", deletedAtEpochMillis = 401))
        val resurrection = runCatching {
            activities.finalizeActivity(FinalizedActivity(activity("history-b", startedAt = 500)))
        }
        assertTrue(resurrection.isFailure)
        assertEquals(listOf("history-a", "history-c"), activities.page(limit = 10).map { it.id })
    }

    @Test
    fun sortedHistoryPagesApplyDateAndSportFiltersDeterministically() = runBlocking {
        val base = 1_700_000_000_000L
        listOf(
            activity("sort-a", base, sport = "RUNNING").copy(distanceMeters = 3_000),
            activity("sort-b", base + 86_400_000, sport = "RUNNING").copy(distanceMeters = 5_000),
            activity("sort-c", base + 2 * 86_400_000, sport = "CYCLING").copy(distanceMeters = 9_000),
        ).forEach { activities.finalizeActivity(FinalizedActivity(it)) }
        val first = activities.page(sport = "RUNNING", sortBy = "DISTANCE", fromStartedAt = base, toStartedAt = base + 3 * 86_400_000, limit = 1)
        val second = activities.page(sport = "RUNNING", sortBy = "DISTANCE", fromStartedAt = base, toStartedAt = base + 3 * 86_400_000, offset = 1, limit = 1)
        assertEquals(listOf("sort-b"), first.map { it.id })
        assertEquals(listOf("sort-a"), second.map { it.id })
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
        assertTrue(
            runCatching {
                repository.saveTemplate(
                    template,
                    listOf(interval("tempo", 0).copy(targetMetric = "SPEED_METERS_PER_SECOND", targetMin = 5.0)),
                )
            }.isFailure,
        )

        repository.savePlannedSession(planned("later", "2026-11-22").copy(status = "SKIPPED", notes = "keep note"))
        repository.savePlannedSession(planned("earlier", "2026-11-20"))
        repository.saveTemplate(template.copy(name = "Updated tempo", updatedAtEpochMillis = 30), listOf(interval("tempo", 2)))
        val later = repository.observeCalendar("2026-11-22", "2026-11-23").first().single()
        assertTrue(repository.reschedulePlannedSession(later.id, "2026-11-23", 40))
        val calendar = repository.observeCalendar("2026-11-20", "2026-11-23").first()
        assertEquals(listOf("earlier"), calendar.map { it.id })
        assertEquals("2026-11-20", calendar.first().scheduledDate)
        val moved = repository.observeCalendar("2026-11-23", "2026-11-24").first().single()
        assertEquals("later", moved.id)
        assertEquals("SKIPPED", moved.status)
        assertEquals("keep note", moved.notes)
        assertEquals("Asia/Kolkata", moved.timeZoneId)
        assertTrue(repository.reschedulePlannedSession(moved.id, "2026-11-22", 45))
        assertEquals("2026-11-22", repository.observeCalendar("2026-11-22", "2026-11-23").first().single().scheduledDate)
        assertTrue(repository.reschedulePlannedSession(moved.id, "2026-11-23", 46))
        repository.savePlannedSession(moved.copy(status = "SKIPPED", updatedAtEpochMillis = 50))
        assertEquals("SKIPPED", repository.observeCalendar("2026-11-23", "2026-11-24").first().single().status)
        repository.savePlannedSession(moved.copy(status = "PLANNED", updatedAtEpochMillis = 60))
        assertEquals("PLANNED", repository.observeCalendar("2026-11-23", "2026-11-24").first().single().status)
        repository.savePlannedSession(moved.copy(status = "MISSED", updatedAtEpochMillis = 70))
        assertEquals("MISSED", repository.observeCalendar("2026-11-23", "2026-11-24").first().single().status)
        database.activityDao().upsert(
            ActivityEntity(
                id = "linked-activity",
                sport = "RUNNING",
                startedAtEpochMillis = 100,
                createdAtEpochMillis = 100,
                updatedAtEpochMillis = 100,
            ),
        )
        assertTrue(repository.linkCompletedActivity("earlier", "linked-activity", 200))
        val linked = repository.observeCalendar("2026-11-20", "2026-11-21").first().single()
        assertEquals("COMPLETED", linked.status)
        assertEquals("linked-activity", linked.completedActivityId)
        assertEquals("Updated tempo", repository.getTemplate("tempo")?.template?.name)
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
    fun accountScopedRepositoriesHideRowsOwnedByAnotherAccount() = runBlocking {
        val ownerA = "11111111-1111-4111-8111-111111111111"
        val ownerB = "22222222-2222-4222-8222-222222222222"
        var activeOwner = ownerA
        val scopedActivities = RoomActivityRepository(database) { activeOwner }
        val scopedWorkouts = RoomWorkoutRepository(database) { activeOwner }
        val scopedGoals = RoomGoalRepository(database) { activeOwner }
        val scopedRoutes = RoomSavedRouteRepository(database) { activeOwner }

        scopedActivities.finalizeActivity(FinalizedActivity(summary = activity("account-a-activity", 10)))
        scopedWorkouts.saveTemplate(
            WorkoutTemplateEntity(id = "account-a-template", name = "A", sport = "RUNNING", createdAtEpochMillis = 1, updatedAtEpochMillis = 2),
            emptyList(),
        )
        scopedGoals.saveGoal(GoalEntity(id = "account-a-goal", goalType = "DISTANCE", targetValue = 10.0, unit = "km", startDate = "2026-01-01", endDate = "2026-12-31", createdAtEpochMillis = 1, updatedAtEpochMillis = 2))
        scopedRoutes.saveRoute(
            SavedRouteEntity(id = "account-a-route", title = "A route", sport = "RUNNING", distanceMeters = 100.0, createdAtEpochMillis = 1, updatedAtEpochMillis = 2),
            listOf(
                SavedRoutePointEntity("account-a-route", 0, 12.0, 77.0),
                SavedRoutePointEntity("account-a-route", 1, 12.1, 77.1),
            ),
        )

        activeOwner = ownerB
        assertTrue(scopedActivities.page(limit = 10).isEmpty())
        assertTrue(scopedWorkouts.observeTemplates().first().isEmpty())
        assertTrue(scopedGoals.observeGoals().first().isEmpty())
        assertTrue(scopedRoutes.observeRoutes().first().isEmpty())
        assertNull(scopedGoals.getGoal("account-a-goal"))
    }

    @Test
    fun linkingLocalDataMovesRowsAndQueuesIdempotentBackfillForAccount() = runBlocking {
        val owner = "33333333-3333-4333-8333-333333333333"
        val activityId = "44444444-4444-4444-8444-444444444444"
        val templateId = "55555555-5555-4555-8555-555555555555"
        val planId = "66666666-6666-4666-8666-666666666666"
        val goalId = "77777777-7777-4777-8777-777777777777"
        val recordId = "88888888-8888-4888-8888-888888888888"
        val routeId = "99999999-9999-4999-8999-999999999999"
        val workouts = RoomWorkoutRepository(database)
        val goals = RoomGoalRepository(database)
        val routes = RoomSavedRouteRepository(database)
        activities.finalizeActivity(FinalizedActivity(summary = activity(activityId, 10)))
        workouts.saveTemplate(WorkoutTemplateEntity(id = templateId, name = "Tempo", sport = "RUNNING", createdAtEpochMillis = 1, updatedAtEpochMillis = 2), emptyList())
        workouts.savePlannedSession(PlannedSessionEntity(id = planId, templateId = templateId, scheduledDate = "2026-11-19", timeZoneId = "Asia/Kolkata", updatedAtEpochMillis = 3))
        goals.saveGoal(GoalEntity(id = goalId, goalType = "DISTANCE", targetValue = 10.0, unit = "km", startDate = "2026-11-01", endDate = "2026-11-30", createdAtEpochMillis = 1, updatedAtEpochMillis = 4))
        goals.savePersonalRecord(personalRecord(recordId, 1_500.0))
        routes.saveRoute(SavedRouteEntity(id = routeId, title = "Home", sport = "RUNNING", distanceMeters = 100.0, createdAtEpochMillis = 1, updatedAtEpochMillis = 5), listOf(
            SavedRoutePointEntity(routeId, 0, 12.0, 77.0), SavedRoutePointEntity(routeId, 1, 12.1, 77.1),
        ))

        val report = PaceAccountDataLinker(database).linkLocalData(owner)

        assertEquals(1, report.activities)
        assertEquals(1, report.templates)
        assertEquals(1, report.plannedSessions)
        assertEquals(1, report.goals)
        assertEquals(1, report.personalRecords)
        assertEquals(1, report.savedRoutes)
        assertEquals(owner, database.activityDao().getById(activityId, owner)?.ownerId)
        assertEquals(owner, database.workoutDao().getTemplateIncludingDeleted(templateId, owner)?.ownerId)
        assertEquals(owner, database.workoutDao().getPlannedSessionIncludingDeleted(planId, owner)?.ownerId)
        assertEquals(owner, database.goalDao().getIncludingDeleted(goalId, owner)?.ownerId)
        assertEquals(owner, database.goalDao().getPersonalRecordIncludingDeleted(recordId, owner)?.ownerId)
        assertEquals(owner, database.savedRouteDao().getIncludingDeleted(routeId, owner)?.ownerId)
        assertEquals(6, database.syncOperationDao().observeQueuedCount(owner).first())
        assertEquals(0, database.syncOperationDao().observeQueuedCount("local").first())
    }

    @Test
    fun manualSyncRetryRequeuesOnlyOutstandingOperationsForThatAccount() = runBlocking {
        val repository = RoomSyncOperationRepository(database)
        val ownerA = "00000000-0000-0000-0000-000000000001"
        val ownerB = "00000000-0000-0000-0000-000000000002"
        val first = SyncOperationEntity(
            id = "failed-a", entityType = "activity", entityId = "activity-a", accountUserId = ownerA,
            operationType = "UPSERT", payload = "{}", sequence = 1, nextAttemptAtEpochMillis = 1,
            createdAtEpochMillis = 1,
        )
        val delayed = first.copy(id = "retry-a", entityId = "activity-a2", sequence = 2)
        val other = first.copy(id = "failed-b", entityId = "activity-b", accountUserId = ownerB)
        repository.enqueue(first)
        repository.enqueue(delayed)
        repository.enqueue(other)
        repository.updateStatus(first.id, "FAILED", 1, 1, "HTTP_400")
        repository.updateStatus(delayed.id, "RETRY", 2, 5_000, "HTTP_503")
        repository.updateStatus(other.id, "FAILED", 1, 1, "HTTP_400")

        assertEquals(2, repository.retryOutstandingNow(ownerA, 100))
        assertEquals(setOf(first.id, delayed.id), repository.nextBatch(100, accountUserId = ownerA).map { it.id }.toSet())
        assertTrue(repository.nextBatch(100, accountUserId = ownerB).isEmpty())
        assertEquals("HTTP_400", database.syncOperationDao().observeLastErrorCode(ownerB).first())
    }

    @Test
    fun erasingOneAccountPreservesLocalAndOtherAccountData() = runBlocking {
        val ownerA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val ownerB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        val idA = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        val idB = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
        val localId = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
        database.activityDao().upsert(activity(idA, 1).copy(ownerId = ownerA))
        database.activityDao().upsert(activity(idB, 2).copy(ownerId = ownerB))
        database.activityDao().upsert(activity(localId, 3))
        val drafts = RoomHomeWorkoutDraftRepository(database)
        drafts.save(HomeWorkoutDraftEntity(ownerA, "[]", 1, 0, 10, 0, 0, 0, true, false, 10))
        drafts.save(HomeWorkoutDraftEntity(ownerB, "[]", 1, 0, 20, 0, 0, 0, true, false, 20))

        OwnerDataEraser(database).erase(ownerA)

        assertNull(database.activityDao().getIncludingDeleted(idA, ownerA))
        assertEquals(ownerB, database.activityDao().getById(idB, ownerB)?.ownerId)
        assertEquals("local", database.activityDao().getById(localId, "local")?.ownerId)
        assertNull(drafts.get(ownerA))
        assertEquals(ownerB, drafts.get(ownerB)?.ownerId)
    }

    @Test
    fun homeWorkoutDraftsPersistByOwnerAndCanBeRemovedAfterCompletion() = runBlocking {
        val drafts = RoomHomeWorkoutDraftRepository(database)
        val draft = HomeWorkoutDraftEntity(
            ownerId = "owner-a", blocksJson = "[]", circuitRepeats = 2, restSeconds = 30,
            sessionStartedAtEpochMillis = 1_000, stepIndex = 1, remainingStepMillis = 12_000,
            elapsedActiveMillis = 45_000, paused = true, finished = false, updatedAtEpochMillis = 2_000,
        )
        drafts.save(draft)

        assertEquals(draft, drafts.get("owner-a"))
        assertNull(drafts.get("owner-b"))
        drafts.delete("owner-a")
        assertNull(drafts.get("owner-a"))
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
            .addMigrations(PaceDatabase.MIGRATION_3_4)
            .addMigrations(PaceDatabase.MIGRATION_4_5)
            .addMigrations(PaceDatabase.MIGRATION_5_6)
            .addMigrations(PaceDatabase.MIGRATION_6_7)
            .addMigrations(PaceDatabase.MIGRATION_7_8)
            .addMigrations(PaceDatabase.MIGRATION_8_9)
            .addMigrations(PaceDatabase.MIGRATION_9_10)
            .addMigrations(PaceDatabase.MIGRATION_10_11)
            .addMigrations(PaceDatabase.MIGRATION_11_12)
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
            val templateColumns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(workout_templates)")
            val templateColumnNames = buildList {
                while (templateColumns.moveToNext()) add(templateColumns.getString(templateColumns.getColumnIndexOrThrow("name")))
                templateColumns.close()
            }
            assertTrue(templateColumnNames.contains("target_duration_seconds"))
            assertTrue(templateColumnNames.contains("target_distance_meters"))
            val plannedColumns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(planned_sessions)")
            val plannedColumnNames = buildList {
                while (plannedColumns.moveToNext()) add(plannedColumns.getString(plannedColumns.getColumnIndexOrThrow("name")))
                plannedColumns.close()
            }
            assertTrue(plannedColumnNames.contains("reminder_enabled"))
            val routeTable = upgraded.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='saved_routes'")
            assertTrue(routeTable.moveToFirst())
            routeTable.close()
            val workoutDraftTable = upgraded.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='home_workout_drafts'")
            assertTrue(workoutDraftTable.moveToFirst())
            workoutDraftTable.close()
            val routePointColumns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(saved_route_points)")
            val pointColumnNames = buildList {
                while (routePointColumns.moveToNext()) add(routePointColumns.getString(routePointColumns.getColumnIndexOrThrow("name")))
                routePointColumns.close()
            }
            assertTrue(pointColumnNames.contains("segment_index"))
            val ownerTables = listOf("workout_templates", "planned_sessions", "goals", "personal_records", "saved_routes")
            ownerTables.forEach { table ->
                val columns = upgraded.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)")
                val hasOwner = buildList {
                    while (columns.moveToNext()) add(columns.getString(columns.getColumnIndexOrThrow("name")))
                    columns.close()
                }.contains("owner_id")
                assertTrue("$table should be partitioned by owner", hasOwner)
            }
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
