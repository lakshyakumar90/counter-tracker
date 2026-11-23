package com.pace.app.tracking.steps

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.pace.app.MainActivity
import com.pace.app.PaceApplication
import com.pace.app.PaceUnits
import com.pace.app.R
import com.pace.app.data.local.dao.DailySteps
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal object PaceSteps {
    private const val ENABLED = "enabled"
    private const val RESET_BASELINE = "reset_baseline"
    private fun options(context: Context) = context.getSharedPreferences("pace_step_tracking", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = options(context).getBoolean(ENABLED, false)
    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun sensor(context: Context): Sensor? {
        val manager = context.getSystemService(SensorManager::class.java) ?: return null
        return if (Build.VERSION.SDK_INT >= 21) {
            manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true) ?: manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        } else {
            manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        }
    }

    fun start(context: Context, userEnabled: Boolean): Boolean {
        if (!hasPermission(context) || sensor(context) == null) return false
        if (userEnabled) options(context).edit().putBoolean(ENABLED, true).putBoolean(RESET_BASELINE, true).apply()
        val started = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, PaceStepService::class.java))
        }.onFailure { if (userEnabled) options(context).edit().putBoolean(ENABLED, false).apply() }.isSuccess
        if (started) PaceStepWatchdog.schedule(context)
        return started
    }

    /** Restarts the foreground service if the system reclaimed it. Safe to call repeatedly. */
    fun ensureRunning(context: Context): Boolean {
        if (!enabled(context) || !hasPermission(context) || sensor(context) == null) return false
        PaceStepWatchdog.schedule(context)
        if (PaceStepService.isRunning) return true
        return runCatching {
            ContextCompat.startForegroundService(context, Intent(context, PaceStepService::class.java))
        }.isSuccess
    }

    /** True when Android's battery optimiser is still allowed to freeze the step service. */
    fun batteryOptimisationActive(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 23 &&
            context.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != true

    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun stop(context: Context) {
        options(context).edit().putBoolean(ENABLED, false).apply()
        PaceStepWatchdog.cancel(context)
        context.stopService(Intent(context, PaceStepService::class.java))
    }

    fun needsBaseline(context: Context): Boolean = options(context).getBoolean(RESET_BASELINE, false)
    fun baselineSaved(context: Context) = options(context).edit().remove(RESET_BASELINE).apply()
    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        270,
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Periodically repairs step tracking after aggressive OEM task killers or a process restart. */
internal object PaceStepWatchdog {
    private const val UNIQUE_WORK = "pace-step-watchdog"
    private const val UNIQUE_REPAIR = "pace-step-repair"

    fun schedule(context: Context) {
        if (!PaceSteps.enabled(context)) return
        val request = PeriodicWorkRequestBuilder<PaceStepWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Asks WorkManager for a near-immediate repair, e.g. after the task is swiped away. */
    fun repairSoon(context: Context) {
        if (!PaceSteps.enabled(context)) return
        val request = OneTimeWorkRequestBuilder<PaceStepWatchdogWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_REPAIR, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_REPAIR)
    }
}

class PaceStepWatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runCatching {
            PaceSteps.ensureRunning(applicationContext)
            val container = (applicationContext as PaceApplication).container
            val dao = container.database.dailyStepsDao()
            val (dailySteps, imperial) = kotlinx.coroutines.runBlocking {
                dao.forDay(LocalDate.now().toString()) to (container.settingsRepository.preferences.first().units == PaceUnits.Imperial)
            }
            PaceStepWidget.render(applicationContext, dailySteps, imperial = imperial)
        }
        return Result.success()
    }
}

class PaceStepService : Service(), SensorEventListener {
    private data class Sample(val counter: Long, val timeNanos: Long)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val samples = Channel<Sample>(Channel.CONFLATED)
    private var listening = false
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sensor = PaceSteps.sensor(this)
        if (!PaceSteps.enabled(this) || !PaceSteps.hasPermission(this) || sensor == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (listening) {
            isRunning = true
            return START_STICKY
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("pace_daily_steps", getString(R.string.pace_steps_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(this, "pace_daily_steps")
            .setSmallIcon(R.drawable.ic_pace_mark)
            .setContentTitle(getString(R.string.pace_steps_notification))
            .setContentIntent(PaceSteps.openApp(this))
            .setOngoing(true)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
            ServiceCompat.startForeground(this, 270, notification, type)
        } catch (_: SecurityException) {
            PaceSteps.stop(this)
            return START_NOT_STICKY
        }
        val sm = getSystemService(SensorManager::class.java)
        listening = if (Build.VERSION.SDK_INT >= 19) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 60_000_000)
        } else {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (!listening) {
            PaceSteps.stop(this)
            return START_NOT_STICKY
        }
        val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "pace:step_tracking")?.apply {
            setReferenceCounted(false)
            runCatching { acquire(24 * 60 * 60 * 1000L) }
        }
        isRunning = true
        scope.launch {
            val container = (application as PaceApplication).container
            val database = container.database
            val dao = database.dailyStepsDao()
            val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
            var lastWidgetUpdate = 0L
            var widgetRefresh: Job? = null
            for (sample in samples) {
                val nowMillis = System.currentTimeMillis()
                val delayMillis = ((SystemClock.elapsedRealtimeNanos() - sample.timeNanos) / 1_000_000).coerceAtLeast(0)
                val day = Instant.ofEpochMilli(nowMillis - delayMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                val reset = PaceSteps.needsBaseline(this@PaceStepService)
                val value = database.withTransaction {
                    val previous = dao.current()
                    val today = if (previous?.day == day) previous else dao.forDay(day)
                    val next = updateDailySteps(previous, day, sample.counter, boot, reset, today?.count ?: 0L)
                        .copy(updatedAt = maxOf(nowMillis, (previous?.updatedAt ?: 0L) + 1))
                    dao.save(next)
                    next
                }
                if (reset) PaceSteps.baselineSaved(this@PaceStepService)
                val timestamp = SystemClock.elapsedRealtime()
                if (lastWidgetUpdate == 0L || timestamp - lastWidgetUpdate >= 10_000) {
                    widgetRefresh?.cancel()
                    PaceStepWidget.render(this@PaceStepService, value, imperial = container.settingsRepository.preferences.first().units == PaceUnits.Imperial)
                    lastWidgetUpdate = timestamp
                } else if (widgetRefresh?.isActive != true) {
                    widgetRefresh = launch {
                        delay(10_000 - (timestamp - lastWidgetUpdate))
                        val currentDay = dao.forDay(LocalDate.now().toString())
                        PaceStepWidget.render(this@PaceStepService, currentDay, imperial = container.settingsRepository.preferences.first().units == PaceUnits.Imperial)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val counter = event.values.firstOrNull() ?: return
        if (counter.isFinite() && counter >= 0f) samples.trySend(Sample(counter.toLong(), event.timestamp))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (PaceSteps.enabled(this)) {
            PaceStepWatchdog.schedule(this)
            PaceStepWatchdog.repairSoon(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { wakeLock?.release() }
        wakeLock = null
        getSystemService(SensorManager::class.java).unregisterListener(this)
        samples.close()
        scope.cancel()
        // Scheduled work repairs the service if the system or an OEM task killer stopped it.
        if (PaceSteps.enabled(this)) PaceStepWatchdog.repairSoon(this)
        super.onDestroy()
    }

    companion object {
        /** True while the sensor listener is registered, so repair calls do not double-start it. */
        @Volatile var isRunning: Boolean = false
            private set
    }
}

internal fun updateDailySteps(prior: DailySteps?, day: String, sensor: Long, boot: Int, reset: Boolean, previousToday: Long = prior?.count?.takeIf { prior.day == day } ?: 0L): DailySteps {
    val increment = if (prior != null && !reset && prior.bootCount == boot && sensor >= prior.sensorCount) sensor - prior.sensorCount else 0L
    return DailySteps(day = day, count = previousToday + increment, sensorCount = sensor, bootCount = boot)
}

class PaceStepBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!PaceSteps.enabled(context)) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                PaceStepWatchdog.schedule(context)
                PaceSteps.start(context, userEnabled = false)
            }
        }
    }
}

class PaceStepWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as PaceApplication).container
                val dao = container.database.dailyStepsDao()
                render(context, dao.forDay(LocalDate.now().toString()), ids, container.settingsRepository.preferences.first().units == PaceUnits.Imperial)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        internal fun render(context: Context, value: DailySteps?, widgetIds: IntArray? = null, imperial: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds ?: manager.getAppWidgetIds(ComponentName(context, PaceStepWidget::class.java))
            if (ids.isEmpty()) return
            val count = value?.count?.takeIf { value.day == LocalDate.now().toString() } ?: 0L
            val views = RemoteViews(context.packageName, R.layout.pace_step_widget).apply {
                setTextViewText(R.id.pace_step_value, NumberFormat.getIntegerInstance().format(count))
                setTextViewText(R.id.pace_step_estimates, context.getString(
                    R.string.pace_steps_estimated_widget,
                    estimatedDistance(count, imperial),
                    estimatedCalories(count),
                ))
                setOnClickPendingIntent(R.id.pace_step_widget, PaceSteps.openApp(context))
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
