package com.pace.app.ui.record

import android.content.ClipData
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.pace.app.PaceAppIcons
import com.pace.app.R
import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.ui.components.PacePrimaryButton
import com.pace.app.ui.components.PaceSecondaryButton
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PaceActivityShare(
    points: List<TrackPointEntity>,
    distance: String,
    duration: String,
    isOnline: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val directory = remember(context) { File(context.cacheDir, "activity-exports").apply { mkdirs() } }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraOpen by rememberSaveable { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var mapMode by rememberSaveable { mutableStateOf(true) }
    var snapshotKey by remember { mutableStateOf(0) }
    var mapExportBusy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraOpen = granted
        failed = !granted
    }
    val preview by androidx.compose.runtime.key(photo, points, distance, duration) {
        produceState<Bitmap?>(null) {
            value = withContext(Dispatchers.IO) {
                val image = photo?.let { File(directory, it) }?.let(::decodeSharePhoto)
                renderShareCard(points, distance, duration, image)
            }
        }
    }
    val label = stringResource(R.string.pace_share_activity)
    fun share(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(context.contentResolver, label, uri)
        context.startActivity(Intent.createChooser(send, label))
    }
    fun sendImage(makeCard: () -> Bitmap) {
        scope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val card = makeCard()
                    File.createTempFile("pace-share-", ".jpg", directory).also {
                        it.outputStream().use { stream -> check(card.compress(Bitmap.CompressFormat.JPEG, 91, stream)) }
                    }
                }
                share(file, "image/jpeg")
            }.onFailure { failed = true }
            mapExportBusy = false
        }
    }
    fun finishMapExport(map: Bitmap?) {
        if (!mapExportBusy || snapshotKey == 0) return
        snapshotKey = 0
        sendImage { renderShareCard(points, distance, duration, map, basemap = map != null) }
    }
    androidx.compose.runtime.LaunchedEffect(snapshotKey) {
        if (snapshotKey != 0) {
            kotlinx.coroutines.delay(4_000)
            finishMapExport(null)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(PaceAppIcons.Back, stringResource(R.string.pace_action_back)) }
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
        if (points.isNotEmpty()) {
            com.pace.app.ui.components.PaceSegmentedControl(
                options = listOf(
                    com.pace.app.ui.components.PaceSegment(true, stringResource(R.string.pace_share_map)),
                    com.pace.app.ui.components.PaceSegment(false, stringResource(R.string.pace_share_photo_mode)),
                ),
                selected = mapMode,
                onSelected = { if (!mapExportBusy) mapMode = it },
            )
        }
        if (mapMode && points.isNotEmpty()) {
            com.pace.app.ui.maps.PaceMapPanel(
                darkTheme = false,
                isOnline = isOnline,
                routePoints = points,
                fitRoute = true,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                snapshotKey = snapshotKey,
                onSnapshot = ::finishMapExport,
            )
            Text("$distance  ·  $duration", style = MaterialTheme.typography.titleLarge)
        } else {
            preview?.let {
                Image(it.asImageBitmap(), stringResource(R.string.pace_share_preview), Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height))
            }
            PaceSecondaryButton(
                stringResource(R.string.pace_share_photo),
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraOpen = true
                    } else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !photoBusy,
                icon = PaceAppIcons.Add,
            )
        }
        PacePrimaryButton(
            label = stringResource(if (mapMode && points.isNotEmpty()) R.string.pace_share_map_image else R.string.pace_share_image),
            onClick = {
                if (mapMode && points.isNotEmpty()) {
                    mapExportBusy = true
                    snapshotKey++
                } else preview?.let { card -> sendImage { card } }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !photoBusy && !mapExportBusy && (preview != null || mapMode && points.isNotEmpty()),
            icon = PaceAppIcons.Export,
        )
        if (points.isNotEmpty()) {
            PaceSecondaryButton(
                stringResource(R.string.pace_share_route_file),
                onClick = {
                    scope.launch {
                        runCatching {
                            val file = withContext(Dispatchers.IO) {
                                File.createTempFile("pace-route-", ".gpx", directory).apply {
                                    bufferedWriter().use { writer ->
                                        writer.append("<gpx version=\"1.1\" creator=\"Pace\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>")
                                        points.forEachIndexed { index, point ->
                                            if (index > 0 && points[index - 1].segmentIndex != point.segmentIndex) writer.append("</trkseg><trkseg>")
                                            writer.append("<trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\"><time>")
                                            writer.append(Instant.ofEpochMilli(point.timestampEpochMillis).toString())
                                            writer.append("</time></trkpt>")
                                        }
                                        writer.append("</trkseg></trk></gpx>")
                                    }
                                }
                            }
                            share(file, "application/gpx+xml")
                        }.onFailure { failed = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (failed) Text(stringResource(R.string.pace_share_failed), color = MaterialTheme.colorScheme.error)
    }
    if (cameraOpen) PaceShareCamera(distance, duration, onDismiss = { cameraOpen = false }, onCapture = { image ->
        cameraOpen = false
        photoBusy = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    File.createTempFile("pace-camera-", ".jpg", directory).also { file ->
                        file.outputStream().use { check(image.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
                    }.name
                }
            }
            photo = result.getOrNull() ?: photo
            failed = result.isFailure
            photoBusy = false
        }
    })
}

private fun decodeSharePhoto(file: File): Bitmap? {
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        return runCatching {
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val sample = (maxOf(info.size.width, info.size.height) / 1_200).coerceAtLeast(1)
                decoder.setTargetSampleSize(sample)
            }
        }.getOrNull()
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
        inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1_200).coerceAtLeast(1)
    })
}

private fun renderShareCard(
    points: List<TrackPointEntity>,
    distance: String,
    duration: String,
    photo: Bitmap?,
    basemap: Boolean = false,
): Bitmap {
    val width = 960
    val imageHeight = photo?.let { (width.toDouble() * it.height / it.width).toInt().coerceIn(550, 1800) } ?: 960
    val height = if (basemap) imageHeight + 212 else imageHeight
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawColor(0xFFF4ECFF.toInt())
    if (photo != null) {
        canvas.drawBitmap(photo, null, Rect(0, 0, width, imageHeight), paint)
        if (!basemap) {
            paint.color = 0xD9000010.toInt()
            canvas.drawRect(0f, (height - 215).toFloat(), width.toFloat(), height.toFloat(), paint)
        }
    }
    if (!basemap && points.isNotEmpty()) {
        val first = points.first()
        val longitudeScale = kotlin.math.cos(Math.toRadians(first.latitude))
        val coordinates = points.map { point ->
            val longitude = (point.longitude - first.longitude + 540) % 360 - 180
            longitude * longitudeScale to point.latitude
        }
        val minX = coordinates.minOf { it.first }
        val maxX = coordinates.maxOf { it.first }
        val minY = coordinates.minOf { it.second }
        val maxY = coordinates.maxOf { it.second }
        val routeHeight = (height - 280).coerceAtLeast(150)
        val scale = minOf(710 / (maxX - minX).coerceAtLeast(0.000001), routeHeight / (maxY - minY).coerceAtLeast(0.000001))
        val projected = coordinates.map { (x, y) ->
            (480 + (x - (minX + maxX) / 2) * scale).toFloat() to
                ((height - 215) / 2 - (y - (minY + maxY) / 2) * scale).toFloat()
        }
        val path = Path()
        projected.forEachIndexed { index, (x, y) ->
            if (index == 0 || points[index - 1].segmentIndex != points[index].segmentIndex) path.moveTo(x, y)
            else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = android.graphics.Color.WHITE
        paint.strokeWidth = 26f
        canvas.drawPath(path, paint)
        paint.color = 0xFF7739D2.toInt()
        paint.strokeWidth = 15f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        listOf(projected.first(), projected.last()).distinct().forEach { (x, y) ->
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(x, y, 15f, paint)
            paint.color = 0xFF7739D2.toInt()
            canvas.drawCircle(x, y, 9f, paint)
        }
    }
    paint.color = if (photo == null || basemap) 0xFF2A1749.toInt() else android.graphics.Color.WHITE
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 43f
    canvas.drawText("Pace", 64f, (height - 133).toFloat(), paint)
    paint.textSize = 64f
    canvas.drawText("$distance  ·  $duration", 64f, (height - 54).toFloat(), paint)
    if (basemap) {
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 18f
        canvas.drawText("© OpenStreetMap contributors · OpenFreeMap", 64f, (height - 21).toFloat(), paint)
    }
    return output
}
