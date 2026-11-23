package com.pace.app.domain.routes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMetricsTest {
    @Test fun measuresDistanceToSegmentAndFlagsDeviation() {
        val route = listOf(GpxPoint(12.0, 77.0), GpxPoint(12.0, 77.002))
        val onPath = RouteMetrics.distanceToRouteMeters(12.0, 77.001, route)
        val away = RouteMetrics.distanceToRouteMeters(12.001, 77.001, route)
        assertNotNull(onPath)
        assertTrue(onPath!! < 1.0)
        assertTrue(RouteMetrics.isOffRoute(away))
        assertFalse(RouteMetrics.isOffRoute(onPath))
    }

    @Test fun doesNotBridgeSegmentsOrAcceptInvalidFixes() {
        val route = listOf(GpxPoint(12.0, 77.0, segmentIndex = 0), GpxPoint(12.0, 77.001, segmentIndex = 1))
        assertNull(RouteMetrics.distanceToRouteMeters(12.0, 77.0005, route))
        assertNull(RouteMetrics.distanceToRouteMeters(91.0, 77.0, route))
    }
}
