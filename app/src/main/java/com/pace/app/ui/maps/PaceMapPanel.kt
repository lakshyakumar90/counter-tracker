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
import com.pace.app.data.local.entity.TrackPointEntity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pace.app.R
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val OPENFREEMAP_LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val OPENFREEMAP_DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val MAP_LOAD_TIMEOUT_MILLIS = 12_000L
private const val ROUTE_SOURCE_ID = "pace-recorded-route-source"
private const val ROUTE_LAYER_ID = "pace-recorded-route-layer"
private const val ROUTE_START_SOURCE_ID = "pace-route-start-source"
private const val ROUTE_START_LAYER_ID = "pace-route-start-layer"
private const val POSITION_SOURCE_ID = "pace-recorded-position-source"
private const val ACCURACY_LAYER_ID = "pace-recorded-accuracy-layer"
private const val POSITION_LAYER_ID = "pace-recorded-position-layer"

/**
 * MapLibre view with an honest local fallback. This view never requests location;
 * recording and GPS collection remain owned by the recording flow/service.
 */
@Composable
fun PaceMapPanel(
    darkTheme: Boolean,
    isOnline: Boolean,
    routePoints: List<TrackPointEntity> = emptyList(),
    highlightedTrackPoint: TrackPointEntity? = null,
    followLocation: Boolean = false,
    fitRoute: Boolean = false,
    recenterKey: Int = 0,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    onMapTap: ((latitude: Double, longitude: Double) -> Unit)? = null,
    selection: LatLng? = null,
    selectionRadiusKm: Int = 10,
    initialCamera: org.maplibre.android.camera.CameraPosition? = null,
    onCameraIdle: ((org.maplibre.android.camera.CameraPosition) -> Unit)? = null,
    locationJump: LatLng? = null,
    locationJumpKey: Int = 0,
    snapshotKey: Int = 0,
    onSnapshot: ((android.graphics.Bitmap?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var styleLoaded by remember { mutableStateOf(false) }
    var loadTimedOut by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var requestGeneration by remember { mutableIntStateOf(0) }
    var lastRecenterKey by remember { mutableIntStateOf(recenterKey) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    val mapView = remember(context) {
        MapLibre.getInstance(context)
        object : MapView(context) {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                parent?.requestDisallowInterceptTouchEvent(
                    event.actionMasked != android.view.MotionEvent.ACTION_UP &&
                        event.actionMasked != android.view.MotionEvent.ACTION_CANCEL,
                )
                return super.dispatchTouchEvent(event)
            }
        }.apply {
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
        loadedStyle = null
        loadTimedOut = false
        mapView.getMapAsync { map ->
            if (mapRef == null && initialCamera != null) map.cameraPosition = initialCamera
            mapRef = map
            val styleUrl = if (isOnline && darkTheme) OPENFREEMAP_DARK_STYLE else OPENFREEMAP_LIBERTY_STYLE
            map.setStyle(styleUrl) { style ->
                if (requestGeneration == activeGeneration && loadAttempt == activeAttempt) {
                    loadedStyle = style
                    styleLoaded = true
                }
            }
        }
        delay(MAP_LOAD_TIMEOUT_MILLIS)
        if (!styleLoaded && requestGeneration == activeGeneration && loadAttempt == activeAttempt) loadTimedOut = true
    }

    LaunchedEffect(mapRef, locationJump, locationJumpKey) {
        val position = locationJump ?: return@LaunchedEffect
        mapRef?.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15.0))
    }
    val cameraIdle by androidx.compose.runtime.rememberUpdatedState(onCameraIdle)
    DisposableEffect(mapRef, onCameraIdle != null) {
        val map = mapRef
        if (map == null || onCameraIdle == null) return@DisposableEffect onDispose { }
        val listener = MapLibreMap.OnCameraIdleListener { cameraIdle?.invoke(map.cameraPosition) }
        map.addOnCameraIdleListener(listener)
        onDispose { map.removeOnCameraIdleListener(listener) }
    }

    val currentMapTap by androidx.compose.runtime.rememberUpdatedState(onMapTap)
    val snapshotResult by androidx.compose.runtime.rememberUpdatedState(onSnapshot)
    LaunchedEffect(snapshotKey) {
        if (snapshotKey != 0) {
            val map = mapRef
            if (map != null && styleLoaded) map.snapshot { image -> snapshotResult?.invoke(image) }
            else snapshotResult?.invoke(null)
        }
    }
    DisposableEffect(mapRef, onMapTap != null) {
        val map = mapRef
        if (map == null || onMapTap == null) return@DisposableEffect onDispose { }
        val listener = MapLibreMap.OnMapClickListener { tapped ->
            currentMapTap?.invoke(tapped.latitude, tapped.longitude)
            true
        }
        map.addOnMapClickListener(listener)
        onDispose { map.removeOnMapClickListener(listener) }
    }

    LaunchedEffect(loadedStyle, selection, selectionRadiusKm, locationJump) {
        val style = loadedStyle ?: return@LaunchedEffect
        val picked = selection ?: locationJump ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>("pace-area-selection")
            ?: GeoJsonSource("pace-area-selection").also {
                style.addSource(it)
                style.addLayer(FillLayer("pace-area-coverage", it.id).withProperties(
                    PropertyFactory.fillColor("#7C3AED"),
                    PropertyFactory.fillOpacity(.22f),
                ))
                style.addLayer(CircleLayer("pace-area-center", it.id).withProperties(
                    PropertyFactory.circleColor("#7C3AED"),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2f),
                ))
            }
        source.setGeoJson(FeatureCollection.fromFeatures(buildList {
            if (selection != null) add(Feature.fromGeometry(accuracyPolygon(picked.latitude, picked.longitude, selectionRadiusKm * 1_000.0)))
            add(Feature.fromGeometry(Point.fromLngLat(picked.longitude, picked.latitude)))
        }))
    }

    LaunchedEffect(loadedStyle, routePoints, followLocation, fitRoute, recenterKey, darkTheme) {
        val style = loadedStyle ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val recenterRequested = recenterKey != lastRecenterKey
        lastRecenterKey = recenterKey
        val lines = routePoints.groupBy { it.segmentIndex }.toSortedMap().values.mapNotNull { segment ->
            segment.map { Point.fromLngLat(it.longitude, it.latitude) }
                .takeIf { it.size >= 2 }
                ?.let { LineString.fromLngLats(it) }
        }
        val routeSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: GeoJsonSource(ROUTE_SOURCE_ID).also { source ->
            style.addSource(source)
            style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(if (darkTheme) "#58D8CF" else "#087F78"),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(.92f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ))
        }
        routeSource.setGeoJson(FeatureCollection.fromFeatures(lines.map(Feature::fromGeometry)))

        val startSource = style.getSourceAs<GeoJsonSource>(ROUTE_START_SOURCE_ID) ?: GeoJsonSource(ROUTE_START_SOURCE_ID).also { source ->
            style.addSource(source)
            style.addLayer(CircleLayer(ROUTE_START_LAYER_ID, ROUTE_START_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor("#F5B942"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ))
        }
        val startPoint = routePoints.firstOrNull()?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
        startSource.setGeoJson(FeatureCollection.fromFeatures(if (startPoint == null) emptyList() else listOf(startPoint)))

        val positionSource = style.getSourceAs<GeoJsonSource>(POSITION_SOURCE_ID) ?: GeoJsonSource(POSITION_SOURCE_ID).also { source ->
            style.addSource(source)
            style.addLayer(FillLayer(ACCURACY_LAYER_ID, POSITION_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(if (darkTheme) "#58D8CF" else "#087F78"),
                PropertyFactory.fillOpacity(.16f),
            ))
            style.addLayer(CircleLayer(POSITION_LAYER_ID, POSITION_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(if (darkTheme) "#58D8CF" else "#087F78"),
                PropertyFactory.circleStrokeColor(if (darkTheme) "#10211F" else "#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ))
        }
        val last = routePoints.lastOrNull()
        if (last == null) {
            positionSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return@LaunchedEffect
        }
        val features = buildList {
            add(Feature.fromGeometry(Point.fromLngLat(last.longitude, last.latitude)))
            last.horizontalAccuracyMeters?.toDouble()?.takeIf { it > 0.0 }?.let { radius ->
                add(Feature.fromGeometry(accuracyPolygon(last.latitude, last.longitude, radius)))
            }
        }
        positionSource.setGeoJson(FeatureCollection.fromFeatures(features))
        if (followLocation || recenterRequested) {
            val position = LatLng(last.latitude, last.longitude)
            val update = if (recenterRequested || routePoints.size == 1) CameraUpdateFactory.newLatLngZoom(position, 16.0)
            else CameraUpdateFactory.newLatLng(position)
            if (reducedMotion || !recenterRequested) map.moveCamera(update) else map.animateCamera(update)
        } else if (fitRoute && routePoints.size >= 2) {
            val bounds = LatLngBounds.Builder().apply {
                routePoints.forEach { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            val update = CameraUpdateFactory.newLatLngBounds(bounds, 48)
            if (reducedMotion) map.moveCamera(update) else map.animateCamera(update)
        } else if (routePoints.size == 1) {
            val update = CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), 15.0)
            if (reducedMotion) map.moveCamera(update) else map.animateCamera(update)
        }
    }

    Surface(
        modifier = modifier.height(420.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!styleLoaded) OfflineMapPlaceholder(darkTheme, routePoints, highlightedTrackPoint, Modifier.fillMaxSize())
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { view: View -> view.visibility = if (styleLoaded) View.VISIBLE else View.INVISIBLE },
            )
            if (!styleLoaded) {
                MapUnavailableFallback(
                    modifier = Modifier.align(Alignment.Center),
                    loading = !loadTimedOut && isOnline,
                    retryEnabled = true,
                    onRetry = { loadAttempt += 1 },
                )
            }
        }
    }
}

/** A packaged, non-geographic illustration so the map surface is never an empty tile rectangle. */
@Composable
private fun OfflineMapPlaceholder(
    darkTheme: Boolean,
    routePoints: List<TrackPointEntity>,
    highlightedTrackPoint: TrackPointEntity?,
    modifier: Modifier = Modifier,
) {
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
        if (routePoints.isNotEmpty()) {
            val projectionPoints = routePoints + listOfNotNull(highlightedTrackPoint)
            val minLatitude = projectionPoints.minOf { it.latitude }
            val maxLatitude = projectionPoints.maxOf { it.latitude }
            val minLongitude = projectionPoints.minOf { it.longitude }
            val maxLongitude = projectionPoints.maxOf { it.longitude }
            val latitudeSpan = (maxLatitude - minLatitude).coerceAtLeast(0.00001)
            val longitudeSpan = (maxLongitude - minLongitude).coerceAtLeast(0.00001)
            val padding = size.minDimension * .16f
            val scale = minOf(
                (size.width - 2 * padding) / longitudeSpan.toFloat(),
                (size.height - 2 * padding) / latitudeSpan.toFloat(),
            )
            val routeColor = if (darkTheme) Color(0xFF58D8CF) else Color(0xFF087F78)
            val projected = routePoints.groupBy { it.segmentIndex }.toSortedMap().values
            projected.forEach { segment ->
                if (segment.size < 2) return@forEach
                val path = Path()
                segment.forEachIndexed { index, point ->
                    val x = padding + (point.longitude - minLongitude).toFloat() * scale
                    val y = size.height - padding - (point.latitude - minLatitude).toFloat() * scale
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, routeColor, style = Stroke(width = size.minDimension * .018f, cap = StrokeCap.Round))
            }
            val last = routePoints.last()
            val first = routePoints.first()
            val startX = padding + (first.longitude - minLongitude).toFloat() * scale
            val startY = size.height - padding - (first.latitude - minLatitude).toFloat() * scale
            drawCircle(Color(0xFFF5B942), radius = size.minDimension * .022f, center = Offset(startX, startY))
            drawCircle(Color.White, radius = size.minDimension * .013f, center = Offset(startX, startY))
            val x = padding + (last.longitude - minLongitude).toFloat() * scale
            val y = size.height - padding - (last.latitude - minLatitude).toFloat() * scale
            last.horizontalAccuracyMeters?.let { accuracy ->
                val radius = (accuracy / 111_320f * scale).coerceIn(size.minDimension * .018f, size.minDimension * .3f)
                drawCircle(routeColor.copy(alpha = .14f), radius = radius, center = Offset(x, y))
            }
            drawCircle(routeColor, radius = size.minDimension * .022f, center = Offset(x, y))
            drawCircle(Color.White, radius = size.minDimension * .013f, center = Offset(x, y))
            highlightedTrackPoint?.let { point ->
                val selectedX = padding + (point.longitude - minLongitude).toFloat() * scale
                val selectedY = size.height - padding - (point.latitude - minLatitude).toFloat() * scale
                drawCircle(Color(0xFFF1C56B), radius = size.minDimension * .035f, center = Offset(selectedX, selectedY))
                drawCircle(Color(0xFF18343A), radius = size.minDimension * .015f, center = Offset(selectedX, selectedY))
            }
        }
    }
}

private fun accuracyPolygon(latitude: Double, longitude: Double, radiusMeters: Double): Polygon {
    val latitudeRadians = Math.toRadians(latitude)
    val longitudeRadians = Math.toRadians(longitude)
    val angularDistance = radiusMeters / 6_371_008.8
    val coordinates = (0..48).map { step ->
        val bearing = step * (2.0 * Math.PI / 48.0)
        val pointLatitude = kotlin.math.asin(
            kotlin.math.sin(latitudeRadians) * kotlin.math.cos(angularDistance) +
                kotlin.math.cos(latitudeRadians) * kotlin.math.sin(angularDistance) * kotlin.math.cos(bearing),
        )
        val pointLongitude = longitudeRadians + kotlin.math.atan2(
            kotlin.math.sin(bearing) * kotlin.math.sin(angularDistance) * kotlin.math.cos(latitudeRadians),
            kotlin.math.cos(angularDistance) - kotlin.math.sin(latitudeRadians) * kotlin.math.sin(pointLatitude),
        )
        Point.fromLngLat(Math.toDegrees(pointLongitude), Math.toDegrees(pointLatitude))
    }
    return Polygon.fromLngLats(listOf(coordinates))
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
