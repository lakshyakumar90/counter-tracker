package com.pace.app.domain.workouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeWorkoutActivityCodecTest {
    @Test
    fun roundTripsUserNamedExerciseWithPunctuation() {
        val name = "Push-ups, slow"
        val id = HomeWorkoutActivityCodec.customExerciseId(name)
        val details = HomeWorkoutActivityDetails(1, listOf(HomeWorkoutExerciseResult(id, 8, 8, null, 0)))
        assertEquals(details, HomeWorkoutActivityCodec.decode(HomeWorkoutActivityCodec.encode(details)))
        assertEquals(name, HomeWorkoutActivityCodec.customExerciseName(id))
    }
    @Test
    fun encodesCompletedRepsDurationAndNotesForHistoryEditing() {
        val details = HomeWorkoutActivityDetails(
            rounds = 2,
            exercises = listOf(
                HomeWorkoutExerciseResult("chair-squat", 10, 18, null, 0, "Stopped early on round two"),
                HomeWorkoutExerciseResult("march", null, 0, 30, 45, "Extra seconds on final round"),
            ),
            notes = "Used a chair for balance",
        )

        assertEquals(details, HomeWorkoutActivityCodec.decode(HomeWorkoutActivityCodec.encode(details)))
    }

    @Test
    fun rejectsMalformedOrUnsupportedSessionData() {
        assertNull(HomeWorkoutActivityCodec.decode("PACEHW99|broken"))
        assertNull(HomeWorkoutActivityCodec.decode("PACEHW1|0||chair-squat,10,10,-1,0,"))
        assertNull(HomeWorkoutActivityCodec.decode("PACEHW1|1||unknown,10,10,-1,0,"))
    }

    @Test
    fun enforcesCloudNotesSizeLimit() {
        val details = HomeWorkoutActivityDetails(
            rounds = 1,
            exercises = List(HomeWorkoutActivityCodec.MAX_EXERCISES) {
                HomeWorkoutExerciseResult("chair-squat", 10, 10, null, 0, "A".repeat(80))
            },
            notes = "B".repeat(200),
        )

        assertThrows(IllegalArgumentException::class.java) { HomeWorkoutActivityCodec.encode(details) }
    }
}
