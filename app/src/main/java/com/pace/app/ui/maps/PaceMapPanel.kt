package com.pace.app.ui.maps

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pace.app.R
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

private const val OPENFREEMAP_LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val OPENFREEMAP_DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val MAP_LOAD_TIMEOUT_MILLIS = 12_000L

/**
 * Online MapLibre view with an honest local fallback. This view never requests location;
 * recording and GPS collection remain owned by the recording flow/service.
 */
@Composable
fun PaceMapPanel(darkTheme: Boolean, isOnline: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var styleLoaded by remember { mutableStateOf(false) }
    var loadTimedOut by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var requestGeneration by remember { mutableIntStateOf(0) }
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> if (!resumed) {
                    mapView.onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> if (resumed) {
                    mapView.onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> if (started) {
                    mapView.onStop()
                    started = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, darkTheme, isOnline, loadAttempt) {
        val activeAttempt = loadAttempt
        val activeGeneration = requestGeneration + 1
        requestGeneration = activeGeneration
        styleLoaded = false
        loadTimedOut = !isOnline
        if (!isOnline) return@LaunchedEffect
        mapView.getMapAsync { map ->
            val styleUrl = if (darkTheme) OPENFREEMAP_DARK_STYLE else OPENFREEMAP_LIBERTY_STYLE
            map.setStyle(styleUrl) {
                if (requestGeneration == activeGeneration && loadAttempt == activeAttempt) styleLoaded = true
            }
        }
        delay(MAP_LOAD_TIMEOUT_MILLIS)
        if (!styleLoaded && requestGeneration == activeGeneration && loadAttempt == activeAttempt) loadTimedOut = true
    }

    Surface(
        modifier = modifier.height(420.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!styleLoaded) OfflineMapPlaceholder(darkTheme, Modifier.fillMaxSize())
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { view: View -> view.visibility = if (styleLoaded && isOnline) View.VISIBLE else View.INVISIBLE },
            )
            if (!styleLoaded || !isOnline) {
                MapUnavailableFallback(
                    modifier = Modifier.align(Alignment.Center),
                    loading = !loadTimedOut && isOnline,
                    retryEnabled = isOnline,
                    onRetry = { loadAttempt += 1 },
                )
            }
        }
    }
}

/** A packaged, non-geographic illustration so the map surface is never an empty tile rectangle. */
@Composable
private fun OfflineMapPlaceholder(darkTheme: Boolean, modifier: Modifier = Modifier) {
    val background = if (darkTheme) Color(0xFF172329) else Color(0xFFEAF0ED)
    val road = if (darkTheme) Color(0xFF34434A) else Color.White
    val secondaryRoad = if (darkTheme) Color(0xFF26363D) else Color(0xFFD4DEDA)
    val park = if (darkTheme) Color(0xFF243B35) else Color(0xFFD5E6D7)
    val water = if (darkTheme) Color(0xFF203A47) else Color(0xFFC9E2E8)
    Canvas(modifier) {
        drawRect(background)
        drawRoundRect(park, Offset(size.width * .04f, size.height * .08f), Size(size.width * .27f, size.height * .22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f))
        drawRoundRect(park, Offset(size.width * .70f, size.height * .60f), Size(size.width * .25f, size.height * .25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f))
        val river = Path().apply {
            moveTo(size.width * .94f, 0f)
            cubicTo(size.width * .78f, size.height * .24f, size.width * .96f, size.height * .45f, size.width * .72f, size.height * .64f)
            cubicTo(size.width * .57f, size.height * .77f, size.width * .67f, size.height * .88f, size.width * .49f, size.height)
        }
        drawPath(river, water, style = Stroke(width = size.width * .11f, cap = StrokeCap.Round))
        val neighborhoodRoads = listOf(
            Offset(size.width * .00f, size.height * .39f) to Offset(size.width, size.height * .39f),
            Offset(size.width * .00f, size.height * .78f) to Offset(size.width * .68f, size.height * .78f),
            Offset(size.width * .38f, 0f) to Offset(size.width * .38f, size.height),
            Offset(size.width * .65f, 0f) to Offset(size.width * .65f, size.height * .51f),
        )
        neighborhoodRoads.forEach { (start, end) ->
            drawLine(road, start, end, strokeWidth = size.width * .035f, cap = StrokeCap.Round)
        }
        val localStreets = listOf(
            Offset(size.width * .06f, size.height * .57f) to Offset(size.width * .91f, size.height * .57f),
            Offset(size.width * .17f, 0f) to Offset(size.width * .17f, size.height),
            Offset(size.width * .84f, 0f) to Offset(size.width * .84f, size.height * .49f),
        )
        localStreets.forEach { (start, end) ->
            drawLine(secondaryRoad, start, end, strokeWidth = size.width * .012f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun MapUnavailableFallback(
    modifier: Modifier = Modifier,
    loading: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(if (loading) R.string.pace_map_loading else R.string.pace_map_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!loading) {
                Text(
                    text = stringResource(R.string.pace_map_unavailable_body),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry, enabled = retryEnabled, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.pace_map_retry))
                }
            }
        }
    }
}
