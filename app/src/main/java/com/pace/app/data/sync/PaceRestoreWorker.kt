package com.pace.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pace.app.BuildConfig
import com.pace.app.PaceApplication
import com.pace.app.auth.PaceAccountState
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object PaceRestoreScheduler {
    fun workName(userId: String): String {
        require(runCatching { UUID.fromString(userId) }.isSuccess) { "Account ID is invalid." }
        return "pace-cloud-restore-$userId"
    }

    fun enqueue(context: Context, userId: String) {
        val request = OneTimeWorkRequestBuilder<PaceRestoreWorker>()
            .setInputData(Data.Builder().putString(PaceRestoreWorker.INPUT_USER_ID, userId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_ALL_RESTORES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(userId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, userId: String) = WorkManager.getInstance(context).cancelUniqueWork(workName(userId))

    fun cancelAll(context: Context) = WorkManager.getInstance(context).cancelAllWorkByTag(TAG_ALL_RESTORES)

    private const val TAG_ALL_RESTORES = "pace-cloud-restore"
}

/** Resumes from the app's encrypted auth session; no access or refresh token is persisted in WorkManager. */
class PaceRestoreWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = inputData.getString(INPUT_USER_ID)
            ?: return@withContext Result.failure(errorData("INVALID_ACCOUNT"))
        if (runCatching { UUID.fromString(userId) }.isFailure) return@withContext Result.failure(errorData("INVALID_ACCOUNT"))
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            return@withContext Result.failure(errorData("CLOUD_NOT_CONFIGURED"))
        }

        try {
            val app = applicationContext as PaceApplication
            val account = app.container.authRepository.accountState.first { it !is PaceAccountState.Loading }
            val signedIn = account as? PaceAccountState.SignedIn
                ?: return@withContext Result.failure(errorData("SIGN_IN_REQUIRED"))
            if (signedIn.userId != userId) return@withContext Result.failure(errorData("ACCOUNT_CHANGED"))
            val preferences = app.container.settingsRepository.preferences.first()
            val existingOwner = preferences.backupOwnerUserId
            if (existingOwner != null && existingOwner != userId) {
                return@withContext Result.failure(errorData("ACCOUNT_CHANGED"))
            }
            val token = app.container.authRepository.accessTokenOrNull()
                ?: return@withContext Result.retry()
            val cloud = SupabaseBackupClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            var lastProgressRead = 0
            var lastProgressTable: String? = null
            val report = PaceRestoreCoordinator(app.container.database, cloud).restore(userId, token) { progress ->
                if (lastProgressTable != progress.table || progress.rowsRead - lastProgressRead >= PROGRESS_INTERVAL) {
                    setProgress(
                        Data.Builder()
                            .putString(KEY_TABLE, progress.table)
                            .putInt(KEY_ROWS_READ, progress.rowsRead)
                            .putInt(KEY_ROWS_APPLIED, progress.rowsApplied)
                            .build(),
                    )
                    lastProgressRead = progress.rowsRead
                    lastProgressTable = progress.table
                }
            }
            Result.success(
                Data.Builder()
                    .putInt(KEY_ROWS_READ, report.rowsRead)
                    .putInt(KEY_ROWS_APPLIED, report.rowsApplied)
                    .putInt(KEY_ROWS_KEPT, report.rowsKeptLocal)
                    .putInt(KEY_TRACKS_RESTORED, report.tracksRestored)
                    .build(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PaceCloudHttpException) {
            if (failure.retryable) Result.retry() else Result.failure(errorData("CLOUD_HTTP_${failure.statusCode}"))
        } catch (failure: IllegalArgumentException) {
            Result.failure(errorData("INVALID_BACKUP_DATA"))
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun errorData(code: String) = Data.Builder().putString(KEY_ERROR, code).build()

    companion object {
        const val INPUT_USER_ID = "user_id"
        const val KEY_TABLE = "table"
        const val KEY_ROWS_READ = "rows_read"
        const val KEY_ROWS_APPLIED = "rows_applied"
        const val KEY_ROWS_KEPT = "rows_kept"
        const val KEY_TRACKS_RESTORED = "tracks_restored"
        const val KEY_ERROR = "error_code"
        private const val PROGRESS_INTERVAL = 25
    }
}
