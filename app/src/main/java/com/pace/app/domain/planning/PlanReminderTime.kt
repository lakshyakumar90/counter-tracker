package com.pace.app.domain.planning

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun planReminderTime(date: String, zone: ZoneId): ZonedDateTime =
    LocalDate.parse(date).atTime(LocalTime.of(8, 0)).atZone(zone)
