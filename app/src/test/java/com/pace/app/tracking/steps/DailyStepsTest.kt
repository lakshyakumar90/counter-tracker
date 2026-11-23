package com.pace.app.tracking.steps

import com.pace.app.data.local.dao.DailySteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStepsTest {
    private val prior = DailySteps(day = "2026-11-19", count = 100, sensorCount = 440, bootCount = 5)

    @Test fun countsOnlyTheDeltaWithinTheSameBoot() {
        assertEquals(112L, updateDailySteps(prior, prior.day, 452, 5, false).count)
        assertEquals(100L, updateDailySteps(prior, prior.day, 452, 6, false).count)
        assertEquals(100L, updateDailySteps(prior, prior.day, 452, 5, true).count)
    }

    @Test fun resetsTheDisplayedTotalAtMidnightAndKeepsNewSteps() {
        assertEquals(3L, updateDailySteps(prior, "2026-11-20", 443, 5, false).count)
        assertEquals(0L, updateDailySteps(prior, "2026-11-20", 30, 6, false).count)
    }

    @Test fun preservesAnExistingDayAfterAClockChange() {
        assertEquals(203L, updateDailySteps(prior, "2026-11-18", 443, 5, false, previousToday = 200).count)
    }

    @Test fun producesClearlyUnitLabeledStepEstimates() {
        assertEquals(40L, estimatedCalories(1000))
        assertEquals(0L, estimatedCalories(-1))
        assertTrue(estimatedDistance(1_000, imperial = false).endsWith(" km"))
        assertTrue(estimatedDistance(1_000, imperial = true).endsWith(" mi"))
    }
}
