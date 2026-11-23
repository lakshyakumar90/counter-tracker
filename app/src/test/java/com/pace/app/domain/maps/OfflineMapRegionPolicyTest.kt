package com.pace.app.domain.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapRegionPolicyTest {
    @Test fun estimatesCoverageFromRadiusAndRejectsUnsupportedEdges() {
        val small = OfflineMapRegionPolicy.around(12.97, 77.59, 10)!!
        val large = OfflineMapRegionPolicy.around(12.97, 77.59, 30)!!
        assertTrue(large.south < small.south && large.east > small.east)
        assertTrue((10..15).any { zoom ->
            OfflineMapRegionPolicy.estimate(large.south, large.west, large.north, large.east, zoom)?.withinLimits == true
        })
        assertNull(OfflineMapRegionPolicy.around(0.0, 179.99, 30))
        assertNull(OfflineMapRegionPolicy.around(12.97, 77.59, 15))
    }
    @Test fun rejectsInvalidBoundsAndAntimeridianCrossing() {
        assertNull(OfflineMapRegionPolicy.estimate(1.0, 2.0, 1.0, 3.0))
        assertNull(OfflineMapRegionPolicy.estimate(-1.0, 170.0, 1.0, -170.0))
        assertNull(OfflineMapRegionPolicy.estimate(Double.NaN, 0.0, 1.0, 1.0))
    }

    @Test fun estimatesAValidNeighborhoodWithinProviderLimits() {
        val result = OfflineMapRegionPolicy.estimate(40.70, -74.02, 40.72, -74.00)
        assertNotNull(result)
        assertTrue(result!!.tileCount > 0)
        assertTrue(result.withinLimits)
        assertEquals(result.tileCount * 18L * 1024, result.estimatedBytes)
    }

    @Test fun rejectsRegionsThatExceedTileOrByteBudget() {
        val result = OfflineMapRegionPolicy.estimate(-20.0, -20.0, 20.0, 20.0)
        assertNotNull(result)
        assertFalse(result!!.withinLimits)
        assertTrue(result.tileCount > OfflineMapRegionPolicy.TILE_LIMIT)
    }
}
