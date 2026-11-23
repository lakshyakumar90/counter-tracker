package com.pace.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pace.app.BuildConfig
import com.pace.app.PaceApplication
import com.pace.app.auth.PaceAccountState
import com.pace.app.data.local.entity.SyncOperationEntity
import com.pace.app.domain.sync.ActivityTrackArchiveCodec
import com.pace.app.data.local.entity.TrackPointEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val SYNC_WORK_NAME = "pace-cloud-backup-sync"

object PaceSyncScheduler {
    fun enqueue(context: Context, allowMetered: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<PaceSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueRetry(context: Context, delayMillis: Long, allowMetered: Boolean) {
        val request = OneTimeWorkRequestBuilder<PaceSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
            .setInitialDelay(delayMillis.coerceAtLeast(1), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun enqueueNow(context: Context, allowMetered: Boolean) {
        val request = OneTimeWorkRequestBuilder<PaceSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }
}

class PaceSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as PaceApplication
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) return@withContext Result.success()
        val account = app.container.authRepository.accountState.first { it !is PaceAccountState.Loading }
        val signedIn = account as? PaceAccountState.SignedIn ?: return@withContext Result.success()
        val preferences = app.container.settingsRepository.preferences.first()
        if (!preferences.backupEnabled || preferences.backupOwnerUserId != signedIn.userId) return@withContext Result.success()
        val token = app.container.authRepository.accessTokenOrNull() ?: return@withContext Result.retry()
        val cloud = SupabaseBackupClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        val repository = app.container.syncOperationRepository
        val batch = repository.nextBatch(System.currentTimeMillis(), accountUserId = signedIn.userId)
        var batchHadFailure = false
        for (operation in batch) {
            try {
                if (operationIsStale(app, signedIn.userId, operation)) {
                    repository.updateStatus(operation.id, "DONE", operation.retryCount, System.currentTimeMillis(), null)
                    continue
                }
                syncOperation(app, cloud, signedIn.userId, token, operation)
                repository.updateStatus(operation.id, "DONE", operation.retryCount, System.currentTimeMillis(), null)
            } catch (failure: Exception) {
                batchHadFailure = true
                val cloudFailure = failure as? PaceCloudHttpException
                val retryable = when (failure) {
                    is PaceBackupQuotaExceededException -> false
                    else -> cloudFailure?.retryable ?: (failure !is IllegalArgumentException)
                }
                if (retryable) {
                    val count = operation.retryCount + 1
                    val delaySeconds = (30L shl count.coerceAtMost(10)).coerceAtMost(21_600L)
                    repository.updateStatus(operation.id, "RETRY", count, System.currentTimeMillis() + delaySeconds * 1_000, safeErrorCode(cloudFailure))
                } else {
                    val errorCode = when (failure) {
                        is PaceBackupQuotaExceededException -> "BACKUP_QUOTA_EXCEEDED"
                        else -> safeErrorCode(cloudFailure) ?: "INVALID_BACKUP_ROW"
                    }
                    repository.updateStatus(operation.id, "FAILED", operation.retryCount, System.currentTimeMillis(), errorCode)
                }
            }
        }
        val now = System.currentTimeMillis()
        val nextAttempt = app.container.database.syncOperationDao().earliestPendingAttempt(signedIn.userId)
        if (nextAttempt != null) {
            PaceSyncScheduler.enqueueRetry(
                applicationContext,
                (nextAttempt - now).coerceAtLeast(1_000),
                preferences.allowMeteredBackup,
            )
        }
        if (!batchHadFailure && batch.isNotEmpty()) {
            app.container.settingsRepository.save(preferences.copy(lastSuccessfulSyncEpochMillis = now))
        }
        Result.success()
    }

    private suspend fun syncOperation(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, operation: SyncOperationEntity) {
        if (operation.accountUserId != userId) throw IllegalArgumentException("Backup operation owner does not match the signed-in account.")
        if (operation.operationType == "DELETE") {
            syncEntity(app, cloud, userId, token, operation)
            if (operation.entityType == "activity") cloud.deleteTrack(token, userId, operation.entityId)
            if (operation.entityType == "saved_route") cloud.deleteRoute(token, userId, operation.entityId)
            return
        }
        syncEntity(app, cloud, userId, token, operation)
    }

    private suspend fun syncEntity(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, operation: SyncOperationEntity) {
        when (operation.entityType) {
            "activity" -> syncActivity(app, cloud, userId, token, operation.entityId)
            "workout_template" -> syncTemplate(app, cloud, userId, token, operation.entityId)
            "planned_session" -> syncPlannedSession(app, cloud, userId, token, operation.entityId)
            "goal" -> syncGoal(app, cloud, userId, token, operation.entityId)
            "personal_record" -> syncPersonalRecord(app, cloud, userId, token, operation.entityId, operation.sequence)
            "saved_route" -> syncSavedRoute(app, cloud, userId, token, operation.entityId)
            else -> throw IllegalArgumentException("Unsupported backup operation type.")
        }
    }

    private suspend fun operationIsStale(app: PaceApplication, userId: String, operation: SyncOperationEntity): Boolean {
        val current = when (operation.entityType) {
            "activity" -> app.container.database.activityDao().getIncludingDeleted(operation.entityId, userId)?.updatedAtEpochMillis
            "workout_template" -> app.container.database.workoutDao().getTemplateIncludingDeleted(operation.entityId, userId)?.updatedAtEpochMillis
            "planned_session" -> app.container.database.workoutDao().getPlannedSessionIncludingDeleted(operation.entityId, userId)?.updatedAtEpochMillis
            "goal" -> app.container.database.goalDao().getIncludingDeleted(operation.entityId, userId)?.updatedAtEpochMillis
            "personal_record" -> app.container.database.goalDao().getPersonalRecordIncludingDeleted(operation.entityId, userId)?.updatedAtEpochMillis
            "saved_route" -> app.container.database.savedRouteDao().getIncludingDeleted(operation.entityId, userId)?.updatedAtEpochMillis
            else -> null
        } ?: return true
        return operation.sequence < current
    }

    private suspend fun syncActivity(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, id: String) {
        val activity = app.container.database.activityDao().getIncludingDeleted(id, userId) ?: return
        val withTrack = if (activity.deletedAtEpochMillis == null) app.container.activityRepository.getActivity(id) else null
        var path: String? = null
        var checksum: String? = null
        var format: Int? = null
        var archiveBytes: Int? = null
        if (withTrack != null && (withTrack.trackPoints.isNotEmpty() || withTrack.splits.isNotEmpty() || withTrack.sensorSamples.isNotEmpty())) {
            val archive = ActivityTrackArchiveCodec.encode(id, withTrack.trackPoints, withTrack.splits, withTrack.sensorSamples)
            path = "$userId/$id.pace.gz"
            cloud.checkArchiveQuota(token, userId, path, archive.size)
            uploadArchive(app, cloud, token, userId, path, archive)
            checksum = ActivityTrackArchiveCodec.sha256(archive)
            format = ActivityTrackArchiveCodec.FORMAT_VERSION
            archiveBytes = archive.size
        }
        val row = JSONObject()
            .put("user_id", userId).put("activity_id", id).put("sport", activity.sport)
            .put("started_at_epoch_millis", activity.startedAtEpochMillis)
            .putNullable("ended_at_epoch_millis", activity.endedAtEpochMillis)
            .put("elapsed_seconds", activity.elapsedSeconds).put("moving_seconds", activity.movingSeconds)
            .put("distance_meters", activity.distanceMeters).putNullable("elevation_gain_meters", activity.elevationGainMeters)
            .putNullable("title", activity.title).putNullable("notes", activity.notes)
            .put("data_quality_flags", activity.dataQualityFlags)
            .putNullable("track_object_path", path).putNullable("track_sha256", checksum).putNullable("track_format_version", format)
            .putNullable("track_archive_bytes", archiveBytes)
            .put("created_at_epoch_millis", activity.createdAtEpochMillis).put("updated_at_epoch_millis", activity.updatedAtEpochMillis)
            .putNullable("deleted_at_epoch_millis", activity.deletedAtEpochMillis)
        cloud.upsert("pace_activities", token, row)
    }

    private suspend fun syncTemplate(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, id: String) {
        val joined = app.container.database.workoutDao().getTemplateWithIntervalsIncludingDeleted(id, userId) ?: return
        val template = joined.template
        val intervals = JSONArray()
        joined.intervals.sortedBy { it.position }.forEach { interval ->
            intervals.put(JSONObject().put("position", interval.position).put("interval_type", interval.intervalType)
                .put("repeat_count", interval.repeatCount).putNullable("duration_seconds", interval.durationSeconds)
                .putNullable("target_metric", interval.targetMetric).putNullable("target_min", interval.targetMin).putNullable("target_max", interval.targetMax))
        }
        cloud.upsert("pace_workout_templates", token, JSONObject()
            .put("user_id", userId).put("template_id", id).put("name", template.name).put("sport", template.sport)
            .putNullable("description", template.description).putNullable("target_duration_seconds", template.targetDurationSeconds)
            .putNullable("target_distance_meters", template.targetDistanceMeters).put("intervals", intervals)
            .put("created_at_epoch_millis", template.createdAtEpochMillis)
            .put("updated_at_epoch_millis", template.updatedAtEpochMillis).putNullable("deleted_at_epoch_millis", template.deletedAtEpochMillis))
    }

    private suspend fun syncPlannedSession(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, id: String) {
        val row = app.container.database.workoutDao().getPlannedSessionIncludingDeleted(id, userId) ?: return
        cloud.upsert("pace_planned_sessions", token, JSONObject()
            .put("user_id", userId).put("session_id", id).putNullable("template_id", row.templateId)
            .put("scheduled_date", row.scheduledDate).put("time_zone_id", row.timeZoneId)
            .put("reminder_enabled", row.reminderEnabled).put("status", row.status)
            .putNullable("completed_activity_id", row.completedActivityId).putNullable("notes", row.notes)
            .put("updated_at_epoch_millis", row.updatedAtEpochMillis).putNullable("deleted_at_epoch_millis", row.deletedAtEpochMillis))
    }

    private suspend fun syncGoal(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, id: String) {
        val row = app.container.database.goalDao().getIncludingDeleted(id, userId) ?: return
        cloud.upsert("pace_goals", token, JSONObject()
            .put("user_id", userId).put("goal_id", id).put("goal_type", row.goalType).putNullable("sport", row.sport)
            .put("target_value", row.targetValue).put("unit", row.unit).put("start_date", row.startDate).put("end_date", row.endDate)
            .put("created_at_epoch_millis", row.createdAtEpochMillis)
            .put("updated_at_epoch_millis", row.updatedAtEpochMillis).putNullable("deleted_at_epoch_millis", row.deletedAtEpochMillis))
    }

    private suspend fun syncPersonalRecord(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, id: String, updatedAtEpochMillis: Long) {
        val row = app.container.database.goalDao().getPersonalRecordIncludingDeleted(id, userId) ?: return
        cloud.upsert("pace_personal_records", token, JSONObject()
            .put("user_id", userId).put("record_id", id).put("sport", row.sport).put("metric", row.metric)
            .putNullable("distance_meters", row.distanceMeters).put("record_value", row.recordValue).put("unit", row.unit)
            .put("achieved_at_epoch_millis", row.achievedAtEpochMillis).putNullable("activity_id", row.activityId)
            .put("provenance", row.provenance)
            .put("updated_at_epoch_millis", row.updatedAtEpochMillis.takeIf { it > 0 } ?: updatedAtEpochMillis).putNullable("deleted_at_epoch_millis", row.deletedAtEpochMillis))
    }

    private suspend fun syncSavedRoute(app: PaceApplication, cloud: SupabaseBackupClient, userId: String, token: String, id: String) {
        val joined = app.container.database.savedRouteDao().getRouteIncludingDeleted(id, userId) ?: return
        val route = joined.route
        var path: String? = null
        var checksum: String? = null
        var format: Int? = null
        var archiveBytes: Int? = null
        if (route.deletedAtEpochMillis == null && joined.points.size >= 2) {
            val trackPoints = joined.points.map { point ->
                TrackPointEntity(
                    activityId = id,
                    sequence = point.sequence,
                    timestampEpochMillis = point.sequence.toLong(),
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitudeMeters = point.altitudeMeters,
                    segmentIndex = point.segmentIndex,
                )
            }
            val archive = ActivityTrackArchiveCodec.encode(id, trackPoints, emptyList(), emptyList())
            path = "$userId/routes/$id.pace.gz"
            cloud.checkArchiveQuota(token, userId, path, archive.size)
            uploadArchive(app, cloud, token, userId, path, archive)
            checksum = ActivityTrackArchiveCodec.sha256(archive)
            format = ActivityTrackArchiveCodec.FORMAT_VERSION
            archiveBytes = archive.size
        }
        cloud.upsert("pace_saved_routes", token, JSONObject()
            .put("user_id", userId).put("route_id", id).put("title", route.title).putNullable("notes", route.notes)
            .put("sport", route.sport).put("distance_meters", route.distanceMeters).putNullable("source_activity_id", route.sourceActivityId)
            .put("created_at_epoch_millis", route.createdAtEpochMillis).put("updated_at_epoch_millis", route.updatedAtEpochMillis)
            .putNullable("route_object_path", path).putNullable("route_sha256", checksum).putNullable("route_format_version", format)
            .putNullable("route_archive_bytes", archiveBytes)
            .putNullable("deleted_at_epoch_millis", route.deletedAtEpochMillis))
    }

    private fun safeErrorCode(exception: PaceCloudHttpException?): String? = exception?.statusCode?.let { "HTTP_$it" }

    private suspend fun uploadArchive(
        app: PaceApplication,
        cloud: SupabaseBackupClient,
        token: String,
        userId: String,
        path: String,
        archive: ByteArray,
    ) {
        try {
            app.container.authRepository.uploadPrivateArchive(userId, path, archive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val quotaRejected = generateSequence(failure as Throwable?) { it.cause }
                .take(5)
                .any { it.message.orEmpty().contains("PACE_BACKUP_QUOTA_EXCEEDED") }
            if (quotaRejected) throw PaceBackupQuotaExceededException()
            try {
                cloud.checkArchiveQuota(token, userId, path, archive.size)
            } catch (quota: PaceBackupQuotaExceededException) {
                throw quota
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Preserve the original upload failure when quota rechecking is unavailable.
            }
            throw failure
        }
    }
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
