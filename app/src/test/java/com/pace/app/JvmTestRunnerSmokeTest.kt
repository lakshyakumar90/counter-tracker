package com.pace.app

import org.junit.Assert.assertEquals
import org.junit.Test

class JvmTestRunnerSmokeTest {
    @Test
    fun junitRunnerExecutesJvmTest() {
        assertEquals(4, 2 + 2)
    }
}
