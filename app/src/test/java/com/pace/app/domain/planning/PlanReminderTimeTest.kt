package com.pace.app.domain.planning

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PlanReminderTimeTest {
    @Test
    fun reminderUsesEightAmOnThePlannedLocalDateInEachTimezone() {
        val date = LocalDate.parse("2026-03-08")
        val india = planReminderTime(date.toString(), ZoneId.of("Asia/Kolkata"))
        val pacific = planReminderTime(date.toString(), ZoneId.of("America/Los_Angeles"))

        assertEquals(date, india.toLocalDate())
        assertEquals(date, pacific.toLocalDate())
        assertEquals(LocalTime.of(8, 0), india.toLocalTime())
        assertEquals(LocalTime.of(8, 0), pacific.toLocalTime())
        assertEquals(ZoneId.of("Asia/Kolkata"), india.zone)
        assertEquals(ZoneId.of("America/Los_Angeles"), pacific.zone)
    }
}
