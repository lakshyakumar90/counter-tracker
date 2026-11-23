package com.pace.app.domain.routes

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxRouteCodecTest {
    @Test fun parsesSegmentsAndKeepsMissingAltitude() {
        val xml = """<gpx xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg><trkpt lat="12.0" lon="77.0"><ele>910</ele></trkpt><trkpt lat="12.001" lon="77.001"/></trkseg><trkseg><trkpt lat="12.2" lon="77.2"/><trkpt lat="12.201" lon="77.201"/></trkseg></trk></gpx>"""
        val points = GpxRouteCodec.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(4, points.size)
        assertEquals(910.0, points.first().altitudeMeters!!, 0.0)
        assertEquals(null, points[1].altitudeMeters)
        assertEquals(0, points[0].segmentIndex)
        assertEquals(1, points[2].segmentIndex)
        assertTrue(GpxRouteCodec.distanceMeters(points) > 200.0)
    }

    @Test fun exportEscapesNamesAndCanBeReadBack() {
        val points = listOf(GpxPoint(12.0, 77.0, 900.0), GpxPoint(12.001, 77.001, 901.0))
        val bytes = GpxRouteCodec.write("R&D <Ride>", points)
        val text = String(bytes, StandardCharsets.UTF_8)
        assertTrue(text.contains("R&amp;D &lt;Ride&gt;"))
        assertEquals(points, GpxRouteCodec.parse(ByteArrayInputStream(bytes)))
    }

    @Test fun rejectsExternalEntitiesAndInvalidCoordinates() {
        val entity = """<!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]><gpx><trk><trkseg><trkpt lat="1" lon="2"/><trkpt lat="2" lon="3"/></trkseg></trk></gpx>"""
        assertTrue(runCatching { GpxRouteCodec.parse(ByteArrayInputStream(entity.toByteArray())) }.isFailure)
        val invalid = """<gpx><trk><trkseg><trkpt lat="91" lon="2"/><trkpt lat="2" lon="3"/></trkseg></trk></gpx>"""
        assertTrue(runCatching { GpxRouteCodec.parse(ByteArrayInputStream(invalid.toByteArray())) }.isFailure)
    }
}
