package com.pace.app.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.pace.app.MainActivity
import com.pace.app.PaceApplication
import com.pace.app.R
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.TrackPointEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { (application as PaceApplication).container.database }
    private var source: LocationSource? = null
    @Volatile private var activity: ActivityEntity? = null
    private var filter: TrackSampleFilter? = null
    private var sequence = 0
    private var distanceRemainderMeters = 0.0
    private var pausedMillis = 0L
    private var pausedAtMillis: Long? = null
    private var segmentStartedAtMillis = 0L
    private var elapsedBeforeSegmentSeconds = 0L
    private val sampleMutex = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationId = intent?.getStringExtra(EXTRA_ACTIVITY_ID)
        startForeground(NOTIFICATION_ID, notification("Pace", "Preparing local recording", paused = false))
        when (intent?.action) {
            ACTION_START -> begin(intent.getStringExtra(EXTRA_SPORT) ?: "RUNNING", notificationId ?: UUID.randomUUID().toString())
            ACTION_RESUME -> resume(notificationId)
            ACTION_PAUSE -> pause()
            ACTION_FINISH -> finish()
        }
        return START_NOT_STICKY
    }

    private fun begin(sport: String, id: String) = scope.launch {
        val now = System.currentTimeMillis()
        val entity = ActivityEntity(
            id = id, sport = sport, status = "RECORDING", startedAtEpochMillis = now,
            createdAtEpochMillis = now, updatedAtEpochMillis = now,
        )
        database.withTransaction {
            database.activityDao().upsert(entity)
        }
        activity = entity
        isRunning = true
        sequence = 0
        distanceRemainderMeters = 0.0
        pausedMillis = 0
        segmentStartedAtMillis = now
        elapsedBeforeSegmentSeconds = 0
        filter = TrackSampleFilter(sportMaximumSpeed(sport))
        startLocation(sport)
        updateNotification(entity, paused = false)
    }

    private fun resume(id: String?) = scope.launch {
        val existing = id?.let { database.activityDao().getById(it) } ?: database.activityDao().getActiveRecording()
        if (existing == null) { stopSelf(); return@launch }
        activity = existing
        sequence = database.trackPointDao().countForActivity(existing.id)
        pausedMillis = existing.dataQualityFlags.substringAfter("PAUSED_MS=", "0").substringBefore(';').toLongOrNull() ?: 0
        val now = System.currentTimeMillis()
        val storedPauseAt = existing.dataQualityFlags.substringAfter("PAUSED_AT=", "").substringBefore(';').toLongOrNull()
        if (existing.status == "PAUSED" && storedPauseAt != null) pausedMillis += (now - storedPauseAt).coerceAtLeast(0)
        elapsedBeforeSegmentSeconds = existing.elapsedSeconds
        segmentStartedAtMillis = now
        val priorInterrupted = existing.status == "RECORDING" || existing.status == "RECOVERED"
        val flags = (existing.dataQualityFlags.split(';').filter { it.isNotBlank() && it != "INTERRUPTED" && !it.startsWith("PAUSED_MS=") && !it.startsWith("PAUSED_AT=") } +
            listOfNotNull(if (priorInterrupted) "INTERRUPTED" else null, "PAUSED_MS=$pausedMillis")).distinct().joinToString(";")
        val resumed = existing.copy(status = "RECORDING", dataQualityFlags = flags, updatedAtEpochMillis = now)
        database.activityDao().upsert(resumed)
        activity = resumed
        isRunning = true
        pausedAtMillis = null
        filter = TrackSampleFilter(sportMaximumSpeed(existing.sport))
        startLocation(existing.sport)
        updateNotification(resumed, paused = false)
    }

    private fun pause() = scope.launch {
        val current = activity ?: database.activityDao().getActiveRecording() ?: return@launch
        activity = current.copy(status = "PAUSED")
        source?.stop()
        source = null
        val now = System.currentTimeMillis()
        pausedAtMillis = now
        val elapsed = elapsedBeforeSegmentSeconds + (now - segmentStartedAtMillis).coerceAtLeast(0) / 1000
        elapsedBeforeSegmentSeconds = elapsed
        val paused = current.copy(
            status = "PAUSED", elapsedSeconds = elapsed, movingSeconds = elapsed,
            dataQualityFlags = withPausedMillis(current.dataQualityFlags, pausedMillis, now), updatedAtEpochMillis = now,
        )
        database.activityDao().upsert(paused)
        activity = paused
        isRunning = false
        updateNotification(paused, paused = true)
    }

    private fun finish() = scope.launch {
        val current = activity ?: database.activityDao().getActiveRecording() ?: return@launch
        if (activity == null) {
            elapsedBeforeSegmentSeconds = current.elapsedSeconds
            segmentStartedAtMillis = System.currentTimeMillis()
        }
        activity = current.copy(status = "FINISHING")
        source?.stop()
        source = null
        val now = System.currentTimeMillis()
        val finalPaused = pausedMillis + (pausedAtMillis?.let { (now - it).coerceAtLeast(0) } ?: 0)
        val elapsedSeconds = elapsedBeforeSegmentSeconds + if (current.status == "RECORDING") (now - segmentStartedAtMillis).coerceAtLeast(0) / 1000 else 0
        val flags = withPausedMillis(current.dataQualityFlags, finalPaused, null)
        val saved = current.copy(
            status = "SAVED", endedAtEpochMillis = now, elapsedSeconds = elapsedSeconds,
            movingSeconds = elapsedSeconds,
            dataQualityFlags = flags, updatedAtEpochMillis = now,
        )
        database.withTransaction { database.activityDao().upsert(saved) }
        activity = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLocation(sport: String) {
        if (!hasLocationPermission()) {
            updateNotification(activity, paused = true, message = getString(R.string.pace_record_location_revoked))
            scope.launch {
                activity?.let {
                    val recovered = it.copy(status = "RECOVERED", dataQualityFlags = it.dataQualityFlags.appendFlag("LOCATION_PERMISSION_REVOKED"), updatedAtEpochMillis = System.currentTimeMillis())
                    database.activityDao().upsert(recovered)
                    activity = recovered
                }
            }
            return
        }
        val locationSource = FallbackLocationSource(this)
        source = locationSource
        val interval = sportIntervalMillis(sport)
        locationSource.start(interval, ::saveSample) {
            updateNotification(activity, paused = false, message = getString(R.string.pace_record_no_gps))
            if (!hasLocationPermission()) {
                source?.stop()
                source = null
                scope.launch {
                    activity?.let {
                        val recovered = it.copy(status = "RECOVERED", dataQualityFlags = it.dataQualityFlags.appendFlag("LOCATION_PERMISSION_REVOKED"), updatedAtEpochMillis = System.currentTimeMillis())
                        database.activityDao().upsert(recovered)
                        activity = recovered
                    }
                }
            }
        }
    }

    private fun saveSample(sample: TrackSample) {
        if (activity?.status != "RECORDING") return
        val activeFilter = filter ?: return
        if (!activeFilter.accept(sample)) return
        scope.launch {
            sampleMutex.withLock {
                val current = activity ?: return@withLock
                val previous = database.trackPointDao().lastForActivity(current.id)
                val addedDistance = previous?.let { haversineMeters(it.latitude, it.longitude, sample.latitude, sample.longitude) } ?: 0.0
                val exactDistance = distanceRemainderMeters + addedDistance
                val wholeMeters = exactDistance.toLong()
                distanceRemainderMeters = exactDistance - wholeMeters
                val point = TrackPointEntity(
                    activityId = current.id,
                    sequence = sequence++,
                    timestampEpochMillis = sample.timestampEpochMillis,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    altitudeMeters = sample.altitudeMeters,
                    horizontalAccuracyMeters = sample.accuracyMeters,
                    speedMetersPerSecond = sample.speedMetersPerSecond,
                    bearingDegrees = sample.bearingDegrees,
                )
                val now = System.currentTimeMillis()
                val elapsed = elapsedBeforeSegmentSeconds + (now - segmentStartedAtMillis).coerceAtLeast(0) / 1000
                val updated = current.copy(
                    elapsedSeconds = elapsed, movingSeconds = elapsed,
                    distanceMeters = current.distanceMeters + wholeMeters, updatedAtEpochMillis = now,
                )
                try {
                    database.withTransaction {
                        database.trackPointDao().insertAll(listOf(point))
                        database.activityDao().upsert(updated)
                    }
                    activity = updated
                    updateNotification(updated, paused = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    updateNotification(current, paused = false, message = getString(R.string.pace_record_storage_error))
                }
            }
        }
    }

    private fun updateNotification(item: ActivityEntity?, paused: Boolean, message: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(item?.sport?.let(::sportLabel) ?: getString(R.string.app_name), message ?: if (paused) getString(R.string.pace_record_paused) else getString(R.string.pace_record_tracking), paused))
    }

    private fun notification(title: String, body: String, paused: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.pace_record_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val activityId = activity?.id
        val controlAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val controlLabel = if (paused) getString(R.string.pace_record_resume) else getString(R.string.pace_record_pause)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pace_mark)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_RECORD, true), pendingFlags()))
            .addAction(0, controlLabel, actionPendingIntent(controlAction, activityId, 1))
            .addAction(0, getString(R.string.pace_record_finish), actionPendingIntent(ACTION_FINISH, activityId, 2))
            .build()
    }

    private fun actionPendingIntent(action: String, id: String?, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).setAction(action).putExtra(EXTRA_ACTIVITY_ID, id)
        return PendingIntent.getService(this, requestCode, intent, pendingFlags())
    }

    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun String.appendFlag(flag: String) = (split(';').filter { it.isNotBlank() } + flag).distinct().joinToString(";")
    private fun withPausedMillis(flags: String, millis: Long, pausedAt: Long?) = flags.split(';')
        .filter { it.isNotBlank() && !it.startsWith("PAUSED_MS=") && !it.startsWith("PAUSED_AT=") }
        .plus("PAUSED_MS=$millis")
        .plus(listOfNotNull(pausedAt?.let { "PAUSED_AT=$it" }))
        .distinct().joinToString(";")

    override fun onDestroy() {
        isRunning = false
        source?.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        const val ACTION_START = "com.pace.app.tracking.START"
        const val ACTION_RESUME = "com.pace.app.tracking.RESUME"
        const val ACTION_PAUSE = "com.pace.app.tracking.PAUSE"
        const val ACTION_FINISH = "com.pace.app.tracking.FINISH"
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_SPORT = "sport"
        const val EXTRA_OPEN_RECORD = "open_record"
        private const val CHANNEL_ID = "pace_recording"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context, sport: String, activityId: String = UUID.randomUUID().toString()) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START).putExtra(EXTRA_SPORT, sport).putExtra(EXTRA_ACTIVITY_ID, activityId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun resume(context: Context, activityId: String) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_RESUME).putExtra(EXTRA_ACTIVITY_ID, activityId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun control(context: Context, action: String, activityId: String? = null) {
            val intent = Intent(context, RecordingService::class.java).setAction(action).putExtra(EXTRA_ACTIVITY_ID, activityId)
            context.startService(intent)
        }

        private fun sportIntervalMillis(sport: String) = if (sport.equals("CYCLING", ignoreCase = true)) 2_000L else 3_000L
        private fun sportMaximumSpeed(sport: String) = if (sport.equals("CYCLING", ignoreCase = true)) 45.0 else 15.0
        private fun sportLabel(sport: String) = when (sport.uppercase()) {
            "WALKING" -> "Walking"
            "JOGGING" -> "Jogging"
            "CYCLING" -> "Cycling"
            else -> "Running"
        }
    }
}

private fun sportIntervalMillis(sport: String) = if (sport.equals("CYCLING", ignoreCase = true)) 2_000L else 3_000L
private fun sportMaximumSpeed(sport: String) = if (sport.equals("CYCLING", ignoreCase = true)) 45.0 else 15.0
private fun sportLabel(sport: String) = when (sport.uppercase()) {
    "WALKING" -> "Walking"
    "JOGGING" -> "Jogging"
    "CYCLING" -> "Cycling"
    else -> "Running"
}
