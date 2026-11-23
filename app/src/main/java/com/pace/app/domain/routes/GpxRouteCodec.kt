package com.pace.app.domain.routes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

data class GpxPoint(val latitude: Double, val longitude: Double, val altitudeMeters: Double? = null, val segmentIndex: Int = 0)

object GpxRouteCodec {
    const val MAX_IMPORT_BYTES = 15 * 1024 * 1024
    const val MAX_POINTS = 100_000

    fun parse(input: InputStream): List<GpxPoint> {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_IMPORT_BYTES) { "GPX file is larger than the 15 MB import limit." }
            output.write(buffer, 0, count)
        }
        val parserFactory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        val handler = RouteHandler()
        parserFactory.newSAXParser().parse(ByteArrayInputStream(output.toByteArray()), handler)
        require(handler.points.size >= 2) { "GPX must contain at least two valid track or route points." }
        return handler.points
    }

    fun distanceMeters(points: List<GpxPoint>): Double = points.zipWithNext().sumOf { (a, b) ->
        if (a.segmentIndex != b.segmentIndex) 0.0 else haversine(a.latitude, a.longitude, b.latitude, b.longitude)
    }

    fun write(title: String, points: List<GpxPoint>): ByteArray {
        require(points.size in 2..MAX_POINTS)
        val safeTitle = escapeXml(title)
        val output = ByteArrayOutputStream()
        output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Pace\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>$safeTitle</name>".toByteArray(StandardCharsets.UTF_8))
        points.groupBy { it.segmentIndex }.toSortedMap().values.forEach { segment ->
            output.write("<trkseg>".toByteArray(StandardCharsets.UTF_8))
            segment.forEach { point ->
                val elevation = point.altitudeMeters?.takeIf(Double::isFinite)?.let { "<ele>$it</ele>" }.orEmpty()
                output.write("<trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">$elevation</trkpt>".toByteArray(StandardCharsets.UTF_8))
            }
            output.write("</trkseg>".toByteArray(StandardCharsets.UTF_8))
        }
        output.write("</trk></gpx>".toByteArray(StandardCharsets.UTF_8))
        return output.toByteArray()
    }

    private class RouteHandler : DefaultHandler() {
        val points = mutableListOf<GpxPoint>()
        private var segment = 0
        private var seenPoints = 0
        private var readingElevation = false
        private var current: GpxPoint? = null
        private var elevation = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
            when (localName ?: qName?.substringAfter(':')) {
                "trkseg" -> if (points.isNotEmpty()) segment++
                "trkpt", "rtept" -> {
                    seenPoints++
                    require(seenPoints <= MAX_POINTS) { "GPX contains more than 100,000 points." }
                    val latitude = attrs.getValue("lat")?.toDoubleOrNull()
                    val longitude = attrs.getValue("lon")?.toDoubleOrNull()
                    require(latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                        "GPX contains a point with invalid coordinates."
                    }
                    current = GpxPoint(latitude, longitude, segmentIndex = segment)
                }
                "ele" -> { readingElevation = true; elevation = StringBuilder() }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) { if (readingElevation) elevation.append(ch, start, length) }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (localName ?: qName?.substringAfter(':')) {
                "ele" -> { current = current?.copy(altitudeMeters = elevation.toString().toDoubleOrNull()?.takeIf(Double::isFinite)); readingElevation = false }
                "trkpt", "rtept" -> { current?.let(points::add); current = null }
            }
        }
    }

    private fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun haversine(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val lat1 = Math.toRadians(aLat); val lat2 = Math.toRadians(bLat)
        val dLat = lat2 - lat1; val dLon = Math.toRadians(bLon - aLon)
        val x = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) + kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 6_371_008.8 * 2 * kotlin.math.atan2(kotlin.math.sqrt(x), kotlin.math.sqrt(1 - x))
    }
}
