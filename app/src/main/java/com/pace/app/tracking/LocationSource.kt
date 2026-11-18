package com.pace.app.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

interface LocationSource {
    fun start(intervalMillis: Long, onSample: (TrackSample) -> Unit, onUnavailable: () -> Unit)
    fun stop()
}

private fun Location.toTrackSample() = TrackSample(
    time, latitude, longitude, if (hasAltitude()) altitude else null,
    if (hasAccuracy()) accuracy else null,
    if (hasSpeed()) speed else null,
    if (hasBearing()) bearing else null,
)

class FusedLocationSource(context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    override fun start(intervalMillis: Long, onSample: (TrackSample) -> Unit, onUnavailable: () -> Unit) {
        val updates = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { onSample(it.toTrackSample()) }
            }
        }
        callback = updates
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, updates, Looper.getMainLooper())
                .addOnFailureListener { onUnavailable() }
        } catch (_: SecurityException) {
            onUnavailable()
        }
    }

    override fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }
}

class PlatformLocationSource(private val context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    override fun start(intervalMillis: Long, onSample: (TrackSample) -> Unit, onUnavailable: () -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) { onUnavailable(); return }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) { onUnavailable(); return }
        val callback = LocationListener { onSample(it.toTrackSample()) }
        listener = callback
        try {
            providers.forEach { manager.requestLocationUpdates(it, intervalMillis, 0f, callback, Looper.getMainLooper()) }
        } catch (_: SecurityException) {
            onUnavailable()
        }
    }

    override fun stop() {
        listener?.let(manager::removeUpdates)
        listener = null
    }
}

class FallbackLocationSource(context: Context) : LocationSource {
    private val primary = FusedLocationSource(context)
    private val fallback = PlatformLocationSource(context)
    private var usingFallback = false

    override fun start(intervalMillis: Long, onSample: (TrackSample) -> Unit, onUnavailable: () -> Unit) {
        usingFallback = false
        primary.start(intervalMillis, onSample) {
            if (!usingFallback) {
                usingFallback = true
                primary.stop()
                fallback.start(intervalMillis, onSample, onUnavailable)
            }
        }
    }

    override fun stop() {
        primary.stop()
        fallback.stop()
    }
}
