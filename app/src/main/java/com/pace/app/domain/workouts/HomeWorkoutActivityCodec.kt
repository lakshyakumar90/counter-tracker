package com.pace.app.domain.workouts

import java.nio.charset.StandardCharsets
import java.util.Base64

data class HomeWorkoutExerciseResult(
    val exerciseId: String,
    val plannedRepetitions: Int?,
    val completedRepetitions: Int,
    val durationSeconds: Int?,
    val completedDurationSeconds: Int,
    val notes: String = "",
)

data class HomeWorkoutActivityDetails(
    val rounds: Int,
    val exercises: List<HomeWorkoutExerciseResult>,
    val notes: String = "",
)

/** Compact, versioned notes payload for offline editing of completed home workouts. */
object HomeWorkoutActivityCodec {
    private const val HEADER = "PACEHW1"
    const val MAX_EXERCISES = 12
    const val MAX_NOTES_LENGTH = 80
    const val MAX_SUMMARY_NOTES_LENGTH = 200
    const val MAX_ENCODED_LENGTH = 1_000
    private val SAFE_ID = Regex("[a-z0-9-]{1,48}")
    private val SUPPORTED_EXERCISES = setOf(
        "chair-squat", "wall-pushup", "glute-bridge", "march", "dead-bug", "cat-cow", "hamstring-stretch",
    )

    fun customExerciseId(name: String): String = "custom-" + encodeText(name.trim().take(24))

    fun customExerciseName(id: String): String? = id.takeIf { it.startsWith("custom-") }
        ?.removePrefix("custom-")
        ?.let { runCatching { decodeText(it) }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it.length <= 24 && customExerciseId(it) == id }

    fun encode(details: HomeWorkoutActivityDetails): String {
        require(details.rounds in 1..20)
        require(details.exercises.size in 1..MAX_EXERCISES)
        require(details.notes.length <= MAX_SUMMARY_NOTES_LENGTH)
        details.exercises.forEach(::validateExercise)
        val body = details.exercises.joinToString(";") { exercise ->
            listOf(
                exercise.exerciseId,
                exercise.plannedRepetitions ?: -1,
                exercise.completedRepetitions,
                exercise.durationSeconds ?: -1,
                exercise.completedDurationSeconds,
                encodeText(exercise.notes),
            ).joinToString(",")
        }
        val encoded = "$HEADER|${details.rounds}|${encodeText(details.notes)}|$body"
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Workout details exceed the supported size." }
        return encoded
    }

    fun decode(value: String?): HomeWorkoutActivityDetails? = runCatching {
        require(value != null && value.length <= MAX_ENCODED_LENGTH)
        val parts = value.split('|', limit = 4)
        require(parts.size == 4 && parts[0] == HEADER)
        val rounds = parts[1].toInt()
        val notes = decodeText(parts[2])
        val exercises = parts[3].split(';').map { line ->
            val columns = line.split(',', limit = 6)
            require(columns.size == 6)
            HomeWorkoutExerciseResult(
                exerciseId = columns[0],
                plannedRepetitions = columns[1].toInt().takeUnless { it == -1 },
                completedRepetitions = columns[2].toInt(),
                durationSeconds = columns[3].toInt().takeUnless { it == -1 },
                completedDurationSeconds = columns[4].toInt(),
                notes = decodeText(columns[5]),
            )
        }
        HomeWorkoutActivityDetails(rounds, exercises, notes).also { details ->
            require(details.rounds in 1..20 && details.exercises.size in 1..MAX_EXERCISES)
            require(details.notes.length <= MAX_SUMMARY_NOTES_LENGTH)
            details.exercises.forEach(::validateExercise)
        }
    }.getOrNull()

    private fun validateExercise(exercise: HomeWorkoutExerciseResult) {
        require((SAFE_ID.matches(exercise.exerciseId) && exercise.exerciseId in SUPPORTED_EXERCISES) ||
            customExerciseName(exercise.exerciseId) != null)
        require((exercise.plannedRepetitions != null) xor (exercise.durationSeconds != null))
        require(exercise.plannedRepetitions == null || exercise.plannedRepetitions in 1..500)
        require(exercise.completedRepetitions in 0..10_000)
        require(exercise.durationSeconds == null || exercise.durationSeconds in 1..3_600)
        require(exercise.completedDurationSeconds in 0..72_000)
        require(exercise.notes.length <= MAX_NOTES_LENGTH)
    }

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
