package com.pace.app.ui.maps

import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pace.app.PaceAppContainer
import com.pace.app.R
import com.pace.app.data.local.entity.MapPackMetadataEntity
import com.pace.app.domain.maps.OfflineMapRegionPolicy
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val MAP_STYLE_VERSION = "openfreemap-liberty-v1"

private data class PackProgress(val completedTiles: Long, val totalResources: Long, val actualBytes: Long)

@Composable
fun PaceOfflineMapPackControls(
    container: PaceAppContainer,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    previewCamera: org.maplibre.android.camera.CameraPosition? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { OfflineManager.getInstance(context.applicationContext) }
    val packs by container.mapPackRepository.observeMapPacks().collectAsState(initial = emptyList())
    val regions = remember { mutableStateMapOf<String, OfflineRegion>() }
    val progress = remember { mutableStateMapOf<String, PackProgress>() }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MapPackMetadataEntity?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var longitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var radiusKm by rememberSaveable { mutableStateOf(10) }
    var uiError by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val area = remember(latitude, longitude, radiusKm) {
        OfflineMapRegionPolicy.around(latitude ?: Double.NaN, longitude ?: Double.NaN, radiusKm)
    }
    val coverage = remember(area) {
        area?.let { bounds ->
            (OfflineMapRegionPolicy.MAX_ZOOM downTo OfflineMapRegionPolicy.MIN_ZOOM).firstNotNullOfOrNull { zoom ->
                OfflineMapRegionPolicy.estimate(bounds.south, bounds.west, bounds.north, bounds.east, zoom)
                    ?.takeIf { it.withinLimits }?.let { zoom to it }
            }
        }
    }
    val estimate = coverage?.second
    val mapPacksTitle = stringResource(R.string.pace_map_packs_title)
    val mapPackLimitError = stringResource(R.string.pace_map_pack_limit_error)
    val mapStorageLimitError = stringResource(R.string.pace_map_storage_limit_error)
    val mapFreeSpaceError = stringResource(R.string.pace_map_free_space_error)
    val mapNameRequired = stringResource(R.string.pace_map_name_required)
    val mapDownloadFailed = stringResource(R.string.pace_map_download_failed)
    val mapInvalidBounds = stringResource(R.string.pace_map_pack_invalid_bounds)

    fun attachObserver(metadata: MapPackMetadataEntity, region: OfflineRegion) {
        regions[metadata.id] = region
        region.setDeliverInactiveMessages(true)
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val exceedsPackLimit = status.completedResourceSize > OfflineMapRegionPolicy.PACK_BYTE_LIMIT
                if (exceedsPackLimit) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                val current = when {
                    exceedsPackLimit -> "ERROR"
                    status.isComplete -> "AVAILABLE"
                    status.downloadState == OfflineRegion.STATE_ACTIVE -> "DOWNLOADING"
                    else -> "PAUSED"
                }
                progress[metadata.id] = PackProgress(status.completedTileCount, status.requiredResourceCount, status.completedResourceSize)
                scope.launch {
                    val otherBytes = container.mapPackRepository.getAll().asSequence()
                        .filterNot { it.id == metadata.id }.sumOf { it.downloadedBytes.coerceAtLeast(0) }
                    val exceedsSavedMapLimit = status.completedResourceSize + otherBytes > OfflineMapRegionPolicy.TOTAL_BYTE_LIMIT
                    if (exceedsSavedMapLimit) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    container.mapPackRepository.save(metadata.copy(
                        status = if (exceedsSavedMapLimit) "ERROR" else current,
                        downloadedBytes = status.completedResourceSize.coerceAtLeast(0),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ))
                }
            }

            override fun onError(error: org.maplibre.android.offline.OfflineRegionError) {
                scope.launch {
                    container.mapPackRepository.save(metadata.copy(status = "ERROR", updatedAtEpochMillis = System.currentTimeMillis()))
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                scope.launch {
                    container.mapPackRepository.save(metadata.copy(status = "ERROR", updatedAtEpochMillis = System.currentTimeMillis()))
                }
            }
        })
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                status ?: return
                progress[metadata.id] = PackProgress(status.completedTileCount, status.requiredResourceCount, status.completedResourceSize)
                scope.launch {
                    val state = if (status.isComplete) "AVAILABLE" else if (status.downloadState == OfflineRegion.STATE_ACTIVE) "DOWNLOADING" else "PAUSED"
                    container.mapPackRepository.save(metadata.copy(status = state, downloadedBytes = status.completedResourceSize.coerceAtLeast(0), updatedAtEpochMillis = System.currentTimeMillis()))
                }
            }
            override fun onError(error: String?) { progress.remove(metadata.id) }
        })
    }

    LaunchedEffect(manager) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions ?: return
                offlineRegions.forEach { region ->
                    val metadataJson = runCatching { JSONObject(region.metadata.toString(Charsets.UTF_8)) }.getOrNull() ?: return@forEach
                    val regionId = metadataJson.optString("regionId").takeIf(String::isNotBlank) ?: return@forEach
                    scope.launch {
                        val existing = container.mapPackRepository.getByRegion(regionId)
                        val metadata = existing ?: MapPackMetadataEntity(
                            id = region.id.toString(), regionId = regionId,
                            name = metadataJson.optString("name", "Offline map"),
                            southLatitude = metadataJson.optDouble("south"), westLongitude = metadataJson.optDouble("west"),
                            northLatitude = metadataJson.optDouble("north"), eastLongitude = metadataJson.optDouble("east"),
                            version = MAP_STYLE_VERSION, status = "PAUSED", updatedAtEpochMillis = System.currentTimeMillis(),
                        )
                        if (metadata.version != MAP_STYLE_VERSION) {
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            container.mapPackRepository.save(metadata.copy(status = "OUTDATED", updatedAtEpochMillis = System.currentTimeMillis()))
                            regions[metadata.id] = region
                        } else {
                            container.mapPackRepository.save(metadata)
                            attachObserver(metadata, region)
                        }
                    }
                }
            }
            override fun onError(error: String) { uiError = mapDownloadFailed }
        })
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(mapPacksTitle, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pace_map_packs_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showCreate = true; uiError = null }) { Text(stringResource(R.string.pace_map_pack_add)) }
        }
        if (packs.isEmpty()) {
            Text(stringResource(R.string.pace_map_packs_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        packs.forEach { pack ->
            val status = pack.status
            val details = progress[pack.id]
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pack.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.pace_map_pack_status, stringResource(statusResource(status)),
                            android.text.format.Formatter.formatShortFileSize(context, pack.downloadedBytes),
                            details?.completedTiles ?: 0L, details?.totalResources ?: 0L),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (status == "ERROR") Text(stringResource(R.string.pace_map_pack_attention_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == "OUTDATED") {
                            Button(enabled = isOnline, onClick = {
                                regions[pack.id]?.invalidate(object : OfflineRegion.OfflineRegionInvalidateCallback {
                                    override fun onInvalidate() {
                                        val updated = pack.copy(version = MAP_STYLE_VERSION, status = "DOWNLOADING", updatedAtEpochMillis = System.currentTimeMillis())
                                        scope.launch { container.mapPackRepository.save(updated) }
                                        regions[pack.id]?.setDownloadState(OfflineRegion.STATE_ACTIVE)
                                    }
                                    override fun onError(error: String) { uiError = mapDownloadFailed }
                                })
                            }) { Text(stringResource(R.string.pace_map_pack_update)) }
                        }
                        if (status == "DOWNLOADING" || status == "ERROR") {
                            OutlinedButton(onClick = { regions[pack.id]?.setDownloadState(OfflineRegion.STATE_INACTIVE) }) { Text(stringResource(R.string.pace_map_pack_pause)) }
                        } else if (status != "AVAILABLE") {
                            Button(enabled = isOnline && regions.containsKey(pack.id), onClick = { regions[pack.id]?.setDownloadState(OfflineRegion.STATE_ACTIVE) }) { Text(stringResource(R.string.pace_map_pack_resume)) }
                        }
                        TextButton(onClick = { deleteTarget = pack }) { Text(stringResource(R.string.pace_map_pack_delete)) }
                    }
                }
            }
        }
    }

    if (showCreate) {
        val aggregateBytes = packs.sumOf { it.downloadedBytes.coerceAtLeast(0) }
        val estimatedBytes = estimate?.estimatedBytes ?: 0L
        val enoughSpace = runCatching { StatFs(context.filesDir.absolutePath).availableBytes >= estimatedBytes + 25L * 1024 * 1024 }.getOrDefault(false)
        AlertDialog(
            onDismissRequest = { if (!busy) showCreate = false },
            title = { Text(stringResource(R.string.pace_map_pack_add_title)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(stringResource(R.string.pace_map_pick_center), style = MaterialTheme.typography.bodyMedium) }
                    item {
                        PaceMapPanel(
                            darkTheme = false,
                            isOnline = isOnline,
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            initialCamera = previewCamera,
                            onMapTap = { lat, lng ->
                                latitude = lat
                                longitude = lng
                                uiError = null
                            },
                            selection = latitude?.let { lat -> longitude?.let { lng -> LatLng(lat, lng) } },
                            selectionRadiusKm = radiusKm,
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(10, 20, 30).forEach { radius ->
                                androidx.compose.material3.FilterChip(
                                    selected = radiusKm == radius,
                                    onClick = { radiusKm = radius; uiError = null },
                                    label = { Text(stringResource(R.string.pace_map_radius_km, radius)) },
                                )
                            }
                        }
                    }
                    item { OutlinedTextField(name, { name = it.take(50) }, label = { Text(stringResource(R.string.pace_map_pack_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item {
                        val currentEstimate = estimate
                        if (currentEstimate == null && latitude != null) Text(stringResource(R.string.pace_map_pack_invalid_bounds), color = MaterialTheme.colorScheme.error)
                        else if (currentEstimate != null) Text(stringResource(R.string.pace_map_pack_estimate, currentEstimate.tileCount, android.text.format.Formatter.formatShortFileSize(context, currentEstimate.estimatedBytes)))
                    }
                    if (uiError != null) item { Text(uiError!!, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && isOnline && coverage != null, onClick = {
                    val box = estimate
                    if (name.isBlank()) { uiError = mapNameRequired; return@TextButton }
                    if (box == null) { uiError = mapInvalidBounds; return@TextButton }
                    if (!box.withinLimits) { uiError = mapPackLimitError; return@TextButton }
                    if (aggregateBytes + box.estimatedBytes > OfflineMapRegionPolicy.TOTAL_BYTE_LIMIT) { uiError = mapStorageLimitError; return@TextButton }
                    if (!enoughSpace) { uiError = mapFreeSpaceError; return@TextButton }
                    val (s, w, n, e) = area ?: return@TextButton
                    val zoom = coverage?.first ?: return@TextButton
                    val regionId = UUID.randomUUID().toString()
                    val metadataJson = JSONObject().put("regionId", regionId).put("name", name.trim())
                        .put("south", s).put("west", w).put("north", n).put("east", e)
                    val bounds = LatLngBounds.from(n, e, s, w)
                    val definition = OfflineTilePyramidRegionDefinition(
                        MAP_STYLE, bounds, OfflineMapRegionPolicy.MIN_ZOOM.toDouble(), zoom.toDouble(),
                        context.resources.displayMetrics.density,
                    )
                    busy = true
                    manager.createOfflineRegion(definition, metadataJson.toString().toByteArray(Charsets.UTF_8), object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(offlineRegion: OfflineRegion) {
                            scope.launch {
                                val entity = MapPackMetadataEntity(
                                    id = offlineRegion.id.toString(), regionId = regionId, name = name.trim(),
                                    southLatitude = s, westLongitude = w, northLatitude = n, eastLongitude = e,
                                    version = MAP_STYLE_VERSION, downloadedBytes = 0, status = "DOWNLOADING",
                                    updatedAtEpochMillis = System.currentTimeMillis(),
                                )
                                container.mapPackRepository.save(entity)
                                attachObserver(entity, offlineRegion)
                                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                                busy = false
                                showCreate = false
                                name = ""; latitude = null; longitude = null; radiusKm = 10
                            }
                        }

                        override fun onError(error: String) {
                            busy = false
                            uiError = mapDownloadFailed
                        }
                    })
                }) { Text(if (busy) stringResource(R.string.pace_map_pack_starting) else stringResource(R.string.pace_map_pack_download)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showCreate = false }) { Text(stringResource(R.string.pace_cancel)) } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.pace_map_pack_delete_title)) },
            text = { Text(stringResource(R.string.pace_map_pack_delete_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val region = regions[target.id]
                    if (region == null) {
                        scope.launch { container.mapPackRepository.delete(target.id); deleteTarget = null }
                    } else region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            regions.remove(target.id); progress.remove(target.id)
                            scope.launch { container.mapPackRepository.delete(target.id); deleteTarget = null }
                        }
                        override fun onError(error: String) { deleteTarget = null }
                    })
                }) { Text(stringResource(R.string.pace_map_pack_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.pace_cancel)) } },
        )
    }
}

private fun statusResource(value: String): Int = when (value) {
    "AVAILABLE" -> R.string.pace_map_pack_available
    "DOWNLOADING" -> R.string.pace_map_pack_downloading
    "PAUSED" -> R.string.pace_map_pack_paused
    "ERROR" -> R.string.pace_map_pack_attention
    "OUTDATED" -> R.string.pace_map_pack_outdated
    else -> R.string.pace_map_pack_paused
}
