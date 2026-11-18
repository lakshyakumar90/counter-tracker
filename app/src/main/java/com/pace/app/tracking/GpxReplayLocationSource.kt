package com.pace.app.tracking

import android.os.Handler
import android.os.Looper
import org.w3c.dom.Element
import java.io.InputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

/** Offline GPX-backed source for deterministic manual/replay checks; never writes the fixture anywhere. */
class GpxReplayLocationSource(private val gpx: InputStream) : LocationSource {
    private val handler = Handler(Looper.getMainLooper())
    private var replay: Runnable? = null

    override fun start(intervalMillis: Long, onSample: (TrackSample) -> Unit, onUnavailable: () -> Unit) {
        stop()
        val points = try {
            parseGpx(gpx)
        } catch (_: Exception) {
            onUnavailable()
            return
        }
        if (points.isEmpty()) { onUnavailable(); return }
        var index = 0
        val task = object : Runnable {
            override fun run() {
                if (index >= points.size) return
                onSample(points[index++])
                if (index < points.size) handler.postDelayed(this, intervalMillis.coerceAtLeast(1L))
            }
        }
        replay = task
        handler.post(task)
    }

    override fun stop() {
        replay?.let(handler::removeCallbacks)
        replay = null
        runCatching { gpx.close() }
    }

}

internal fun parseGpx(input: InputStream): List<TrackSample> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    val builder = factory.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> InputSource(StringReader("")) }
    }
    val document = builder.parse(input)
    val nodes = document.getElementsByTagName("trkpt")
    return (0 until nodes.length).mapNotNull { index ->
        val element = nodes.item(index) as? Element ?: return@mapNotNull null
        val latitude = element.getAttribute("lat").toDoubleOrNull() ?: return@mapNotNull null
        val longitude = element.getAttribute("lon").toDoubleOrNull() ?: return@mapNotNull null
        val timeText = element.getElementsByTagName("time").item(0)?.textContent ?: return@mapNotNull null
        val timestamp = runCatching { Instant.parse(timeText).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
        val altitude = element.getElementsByTagName("ele").item(0)?.textContent?.toDoubleOrNull()
        TrackSample(timestamp, latitude, longitude, altitude, null, null, null)
    }
}
