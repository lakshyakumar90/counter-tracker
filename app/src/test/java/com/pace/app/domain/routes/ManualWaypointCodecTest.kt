package com.pace.app.domain.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualWaypointCodecTest {
    @Test
    fun parsesOrderedCoordinatesAndSupportsWhitespaceSeparators() {
        val result = ManualWaypointCodec.parse("12.5,77.6\n-10.25 120.75")

        assertEquals(listOf(GpxPoint(12.5, 77.6), GpxPoint(-10.25, 120.75)), result)
    }

    @Test
    fun rejectsMalformedOrOutOfRangeWaypointLines() {
        assertNull(ManualWaypointCodec.parse("12,77\nnot-a-coordinate"))
        assertNull(ManualWaypointCodec.parse("90.01,0"))
        assertNull(ManualWaypointCodec.parse("0,180.01"))
    }

    @Test
    fun enforcesGpxPointLimit() {
        val input = List(GpxRouteCodec.MAX_POINTS + 1) { "0,0" }.joinToString("\n")

        assertNull(ManualWaypointCodec.parse(input))
    }
}
