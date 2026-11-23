package com.pace.app.ui.planning

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pace.app.PaceApplication
import com.pace.app.R
import com.pace.app.auth.PaceAccountState
import com.pace.app.data.local.entity.PlannedSessionEntity
import com.pace.app.domain.planning.planReminderTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "planned_workouts"
private const val WORK_ID_KEY = "planned_session_id"
private const val WORK_OWNER_KEY = "planned_session_owner_id"
private const val PLAN_REMINDER_TAG = "pace-plan-reminder"

object PlanReminderScheduler {
    fun schedule(context: Context, session: PlannedSessionEntity) {
        val startAt = planReminderTime(session.scheduledDate, ZoneId.systemDefault())
        val delay = Duration.between(ZonedDateTime.now(), startAt).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<PlanReminderWorker>()
            .setInputData(workDataOf(WORK_ID_KEY to session.id, WORK_OWNER_KEY to session.ownerId))
            .addTag(PLAN_REMINDER_TAG)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(session.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, sessionId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(sessionId))
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(PLAN_REMINDER_TAG)
    }

    private fun workName(sessionId: String) = "pace-plan-reminder-$sessionId"
}

class PlanReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(WORK_ID_KEY) ?: return Result.failure()
        val ownerId = inputData.getString(WORK_OWNER_KEY) ?: "local"
        val application = applicationContext as PaceApplication
        if (!application.container.settingsRepository.preferences.first().notificationsEnabled) return Result.success()
        val database = application.container.database
        val session = database.workoutDao().getPlannedSessionIncludingDeleted(sessionId, ownerId) ?: return Result.success()
        if (!session.reminderEnabled || session.status != "PLANNED" || session.deletedAtEpochMillis != null) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val title = session.templateId?.let { database.workoutDao().getTemplate(it, ownerId)?.template?.name }
            ?: applicationContext.getString(R.string.pace_plan_session_missing)
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pace_mark)
            .setContentTitle(applicationContext.getString(R.string.pace_plan_reminder_title))
            .setContentText(applicationContext.getString(R.string.pace_plan_reminder_body, title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(sessionId.hashCode(), notification)
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.pace_plan_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}

class PlanReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as PaceApplication
                if (!app.container.settingsRepository.preferences.first().notificationsEnabled) {
                    PlanReminderScheduler.cancelAll(context)
                    return@launch
                }
                val account = app.container.authRepository.accountState.first { it !is PaceAccountState.Loading }
                val ownerId = (account as? PaceAccountState.SignedIn)?.userId ?: "local"
                app.container.database.workoutDao().remindersToSchedule(ownerId).forEach { PlanReminderScheduler.schedule(context, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
