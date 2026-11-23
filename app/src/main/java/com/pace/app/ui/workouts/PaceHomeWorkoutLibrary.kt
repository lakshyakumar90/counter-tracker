package com.pace.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pace.app.PaceAppIcons
import com.pace.app.R
import com.pace.app.data.local.entity.ActivityEntity
import com.pace.app.data.local.entity.HomeWorkoutDraftEntity
import com.pace.app.domain.repository.ActivityRepository
import com.pace.app.domain.repository.HomeWorkoutDraftRepository
import com.pace.app.domain.repository.FinalizedActivity
import com.pace.app.domain.workouts.HomeWorkoutActivityCodec
import com.pace.app.domain.workouts.HomeWorkoutActivityDetails
import com.pace.app.domain.workouts.HomeWorkoutExerciseResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

private enum class ExerciseCategory(val label: Int) {
    Mobility(R.string.pace_workout_category_mobility),
    Strength(R.string.pace_workout_category_strength),
    Core(R.string.pace_workout_category_core),
    Hiit(R.string.pace_workout_category_hiit),
    Stretching(R.string.pace_workout_category_stretching),
}

private data class ExerciseDefinition(
    val id: String,
    val name: Int,
    val category: ExerciseCategory,
    val instructions: List<Int>,
    val illustration: Int,
    val format: Int,
    val caution: Int,
    val durationSeconds: Int?,
    val repetitions: Int?,
)

private data class WorkoutBlock(
    val id: String,
    val exerciseId: String,
    val durationSeconds: Int?,
    val repetitions: Int?,
    val completedRepRounds: List<Int> = emptyList(),
    val completedDurationRounds: List<Int> = emptyList(),
)

private val exerciseLibrary = listOf(
    ExerciseDefinition("chair-squat", R.string.pace_exercise_chair_squat, ExerciseCategory.Strength, listOf(R.string.pace_exercise_chair_squat_step_1, R.string.pace_exercise_chair_squat_step_2), R.drawable.exercise_chair_squat, R.string.pace_exercise_reps_10, R.string.pace_exercise_caution_balance, null, 10),
    ExerciseDefinition("wall-pushup", R.string.pace_exercise_wall_pushup, ExerciseCategory.Strength, listOf(R.string.pace_exercise_wall_pushup_step_1, R.string.pace_exercise_wall_pushup_step_2), R.drawable.exercise_wall_pushup, R.string.pace_exercise_reps_8, R.string.pace_exercise_caution_shoulder, null, 8),
    ExerciseDefinition("glute-bridge", R.string.pace_exercise_glute_bridge, ExerciseCategory.Core, listOf(R.string.pace_exercise_glute_bridge_step_1, R.string.pace_exercise_glute_bridge_step_2), R.drawable.exercise_glute_bridge, R.string.pace_exercise_reps_10, R.string.pace_exercise_caution_back, null, 10),
    ExerciseDefinition("march", R.string.pace_exercise_march, ExerciseCategory.Hiit, listOf(R.string.pace_exercise_march_step_1, R.string.pace_exercise_march_step_2), R.drawable.exercise_march, R.string.pace_exercise_seconds_30, R.string.pace_exercise_caution_balance, 30, null),
    ExerciseDefinition("dead-bug", R.string.pace_exercise_dead_bug, ExerciseCategory.Core, listOf(R.string.pace_exercise_dead_bug_step_1, R.string.pace_exercise_dead_bug_step_2), R.drawable.exercise_dead_bug, R.string.pace_exercise_reps_8_each, R.string.pace_exercise_caution_back, null, 8),
    ExerciseDefinition("cat-cow", R.string.pace_exercise_cat_cow, ExerciseCategory.Mobility, listOf(R.string.pace_exercise_cat_cow_step_1, R.string.pace_exercise_cat_cow_step_2), R.drawable.exercise_cat_cow, R.string.pace_exercise_seconds_30, R.string.pace_exercise_caution_wrist, 30, null),
    ExerciseDefinition("hamstring-stretch", R.string.pace_exercise_hamstring_stretch, ExerciseCategory.Stretching, listOf(R.string.pace_exercise_hamstring_step_1, R.string.pace_exercise_hamstring_step_2), R.drawable.exercise_hamstring_stretch, R.string.pace_exercise_seconds_30_each, R.string.pace_exercise_caution_pain, 30, null),
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PaceHomeWorkoutLibrary(
    activityRepository: ActivityRepository,
    draftRepository: HomeWorkoutDraftRepository,
    ownerId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var builderOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var workoutTitle by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var exerciseName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var repetitionsText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var durationMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var roundsText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("1") }
    var restText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("30") }
    var workoutBlocks by remember { mutableStateOf(emptyList<WorkoutBlock>()) }
    var circuitRepeats by remember { mutableStateOf(1) }
    var restSeconds by remember { mutableStateOf(30) }
    var playerOpen by remember { mutableStateOf(false) }
    var sessionStartedAt by remember { mutableStateOf(0L) }
    var saveMessage by remember { mutableStateOf<Int?>(null) }
    var recoveryDraft by remember { mutableStateOf<HomeWorkoutDraftEntity?>(null) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    val defaultWorkoutTitle = stringResource(R.string.pace_workout_library_title)
    val scope = rememberCoroutineScope()
    val totalSeconds = workoutBlocks.sumOf { block ->
        block.durationSeconds ?: (block.repetitions ?: 0) * 3
    } * circuitRepeats + restSeconds * (workoutBlocks.size - 1).coerceAtLeast(0) * circuitRepeats
    androidx.activity.compose.BackHandler(enabled = builderOpen && !playerOpen) { builderOpen = false }
    if (!builderOpen && !playerOpen) {
        Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.pace_workout_library_title), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text(stringResource(R.string.pace_action_back)) }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.pace_workout_builder_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.pace_custom_workout_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { builderOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pace_custom_workout_open))
                    }
                }
            }
            if (recoveryDraft != null) {
                OutlinedButton(onClick = { builderOpen = true; showRecoveryDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pace_workout_recovery_resume))
                }
            }
        }
    }
    LaunchedEffect(ownerId) {
        playerOpen = false
        recoveryDraft = null
        workoutBlocks = emptyList()
        val saved = draftRepository.get(ownerId) ?: return@LaunchedEffect
        val recoveredBlocks = runCatching { decodeWorkoutBlocks(saved.blocksJson) }.getOrNull()
        if (recoveredBlocks.isNullOrEmpty() || saved.circuitRepeats !in 1..20 || saved.restSeconds !in 0..600 ||
            saved.stepIndex !in 0 until workoutStepCount(recoveredBlocks, saved.circuitRepeats, saved.restSeconds) ||
            saved.remainingStepMillis < 0 || saved.elapsedActiveMillis < 0 || saved.sessionStartedAtEpochMillis <= 0
        ) {
            draftRepository.delete(ownerId)
            return@LaunchedEffect
        }
        workoutBlocks = recoveredBlocks
        workoutTitle = workoutTitleFromDraft(saved.blocksJson).orEmpty()
        circuitRepeats = saved.circuitRepeats.coerceIn(1, 20)
        restSeconds = saved.restSeconds.coerceIn(0, 600)
        roundsText = circuitRepeats.toString()
        restText = restSeconds.toString()
        sessionStartedAt = saved.sessionStartedAtEpochMillis
        recoveryDraft = saved.copy(paused = true)
        showRecoveryDialog = true
    }
    if (builderOpen && !playerOpen) Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.pace_workout_builder_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { builderOpen = false }) { Text(stringResource(R.string.pace_action_back)) }
        }
        androidx.compose.material3.OutlinedTextField(
            workoutTitle, { workoutTitle = it.take(60) }, singleLine = true,
            label = { Text(stringResource(R.string.pace_custom_workout_title)) }, modifier = Modifier.fillMaxWidth(),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.pace_custom_workout_exercise), style = MaterialTheme.typography.titleMedium)
                androidx.compose.material3.OutlinedTextField(
                    exerciseName, { exerciseName = it.take(24) }, singleLine = true,
                    label = { Text(stringResource(R.string.pace_custom_workout_name)) }, modifier = Modifier.fillMaxWidth(),
                )
                com.pace.app.ui.components.PaceSegmentedControl(
                    options = listOf(
                        com.pace.app.ui.components.PaceSegment(false, stringResource(R.string.pace_custom_workout_reps)),
                        com.pace.app.ui.components.PaceSegment(true, stringResource(R.string.pace_custom_workout_seconds)),
                    ),
                    selected = durationMode,
                    onSelected = { durationMode = it },
                )
                androidx.compose.material3.OutlinedTextField(
                    repetitionsText, { repetitionsText = it.filter(Char::isDigit).take(4) }, singleLine = true,
                    label = { Text(stringResource(if (durationMode) R.string.pace_custom_workout_seconds else R.string.pace_custom_workout_reps)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val amount = repetitionsText.toIntOrNull()?.takeIf { it in 1..(if (durationMode) 3600 else 500) }
                OutlinedButton(
                    enabled = amount != null && exerciseName.isNotBlank() && workoutBlocks.size < HomeWorkoutActivityCodec.MAX_EXERCISES,
                    onClick = {
                        if (amount != null) {
                            workoutBlocks += WorkoutBlock(
                                UUID.randomUUID().toString(), HomeWorkoutActivityCodec.customExerciseId(exerciseName),
                                amount.takeIf { durationMode }, amount.takeUnless { durationMode },
                            )
                            exerciseName = ""
                            repetitionsText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.pace_workout_add_block)) }
            }
        }
        Text(stringResource(R.string.pace_custom_workout_sequence), style = MaterialTheme.typography.titleLarge)
        workoutBlocks.forEachIndexed { index, block ->
            val exercise = workoutExerciseName(block.exerciseId)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pace_workout_block_title, index + 1, exercise), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (block.durationSeconds != null) R.string.pace_workout_block_seconds else R.string.pace_workout_block_reps, block.durationSeconds ?: block.repetitions ?: 0), style = MaterialTheme.typography.bodySmall)
                    }
                    Column {
                        TextButton(onClick = {
                            if (index > 0) workoutBlocks = workoutBlocks.toMutableList().also { list ->
                                val previous = list[index - 1]
                                list[index - 1] = list[index]
                                list[index] = previous
                            }
                        }) { Text(stringResource(R.string.pace_workout_move_up)) }
                        TextButton(onClick = { workoutBlocks = workoutBlocks.filterNot { it.id == block.id } }) { Text(stringResource(R.string.pace_workout_remove)) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedTextField(
                roundsText, { input ->
                    roundsText = input.filter(Char::isDigit).take(2)
                    roundsText.toIntOrNull()?.takeIf { it in 1..20 }?.let { circuitRepeats = it }
                }, modifier = Modifier.weight(1f), singleLine = true,
                label = { Text(stringResource(R.string.pace_custom_workout_rounds)) },
                isError = (roundsText.toIntOrNull() ?: 0) !in 1..20,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            )
            androidx.compose.material3.OutlinedTextField(
                restText, { input ->
                    restText = input.filter(Char::isDigit).take(3)
                    restText.toIntOrNull()?.takeIf { it in 0..600 }?.let { restSeconds = it }
                }, modifier = Modifier.weight(1f), singleLine = true,
                label = { Text(stringResource(R.string.pace_custom_workout_rest)) },
                isError = (restText.toIntOrNull() ?: -1) !in 0..600,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            )
        }
        if (workoutBlocks.isNotEmpty()) Text(stringResource(R.string.pace_workout_estimated_duration, totalSeconds / 60, totalSeconds % 60), style = MaterialTheme.typography.titleMedium)
        recoveryDraft?.let {
            Text(stringResource(R.string.pace_workout_recovered_banner), color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = { showRecoveryDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pace_workout_recovery_resume))
            }
        }
        Button(
            enabled = workoutBlocks.isNotEmpty() && (roundsText.toIntOrNull() ?: 0) in 1..20 && (restText.toIntOrNull() ?: -1) in 0..600,
            onClick = {
                val startedAt = System.currentTimeMillis()
                val firstStep = if (workoutBlocks.first().durationSeconds != null) workoutBlocks.first().durationSeconds!! * 1_000L else 0L
                val draft = HomeWorkoutDraftEntity(
                    ownerId = ownerId,
                    blocksJson = encodeWorkoutBlocks(workoutBlocks, workoutTitle),
                    circuitRepeats = circuitRepeats,
                    restSeconds = restSeconds,
                    sessionStartedAtEpochMillis = startedAt,
                    stepIndex = 0,
                    remainingStepMillis = firstStep,
                    elapsedActiveMillis = 0,
                    paused = false,
                    finished = false,
                    updatedAtEpochMillis = startedAt,
                )
                scope.launch {
                    draftRepository.save(draft)
                    recoveryDraft = draft
                    sessionStartedAt = startedAt
                    playerOpen = true
                    saveMessage = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.pace_workout_start)) }
        saveMessage?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
    }
    if (showRecoveryDialog && recoveryDraft != null) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text(stringResource(R.string.pace_workout_recovery_title)) },
            text = { Text(stringResource(R.string.pace_workout_recovery_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val pausedDraft = recoveryDraft!!.copy(paused = true, updatedAtEpochMillis = System.currentTimeMillis())
                    scope.launch {
                        draftRepository.save(pausedDraft)
                        recoveryDraft = pausedDraft
                        showRecoveryDialog = false
                        playerOpen = true
                    }
                }) { Text(stringResource(R.string.pace_workout_recovery_resume)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showRecoveryDialog = false }) { Text(stringResource(R.string.pace_cancel)) }
                    TextButton(onClick = {
                        scope.launch {
                            draftRepository.delete(ownerId)
                            recoveryDraft = null
                            workoutBlocks = emptyList()
                            showRecoveryDialog = false
                        }
                    }) { Text(stringResource(R.string.pace_workout_recovery_discard)) }
                }
            },
        )
    }
    if (playerOpen && workoutBlocks.isNotEmpty() && recoveryDraft != null) {
        Dialog(onDismissRequest = {
            scope.launch {
                val pausedDraft = recoveryDraft!!.copy(paused = true, updatedAtEpochMillis = System.currentTimeMillis())
                draftRepository.save(pausedDraft)
                recoveryDraft = pausedDraft
                playerOpen = false
            }
        }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            PaceWorkoutPlayer(
                blocks = workoutBlocks,
                rounds = circuitRepeats,
                restSeconds = restSeconds,
                initialDraft = recoveryDraft!!,
                onCheckpoint = { draft ->
                    draftRepository.save(draft)
                    recoveryDraft = draft
                },
                onClose = {
                    val pausedDraft = recoveryDraft!!.copy(paused = true, updatedAtEpochMillis = System.currentTimeMillis())
                    draftRepository.save(pausedDraft)
                    recoveryDraft = pausedDraft
                    playerOpen = false
                },
                onSave = { elapsedSeconds, completedBlocks ->
                    val now = System.currentTimeMillis()
                    // Keep retries idempotent if process death occurs after the activity is saved.
                    val id = UUID.nameUUIDFromBytes(
                        "home-workout:$ownerId:$sessionStartedAt".toByteArray(),
                    ).toString()
                    activityRepository.finalizeActivity(FinalizedActivity(ActivityEntity(
                        id = id,
                        sport = "HOME_WORKOUT",
                        startedAtEpochMillis = sessionStartedAt,
                        endedAtEpochMillis = now,
                        elapsedSeconds = elapsedSeconds,
                        movingSeconds = elapsedSeconds,
                        title = workoutTitle.trim().ifBlank { defaultWorkoutTitle },
                        notes = runCatching {
                            HomeWorkoutActivityCodec.encode(
                                HomeWorkoutActivityDetails(
                                    rounds = circuitRepeats,
                                    exercises = completedBlocks.map { block ->
                                        HomeWorkoutExerciseResult(
                                            exerciseId = block.exerciseId,
                                            plannedRepetitions = block.repetitions,
                                            completedRepetitions = block.completedRepRounds.sum(),
                                            durationSeconds = block.durationSeconds,
                                            completedDurationSeconds = block.completedDurationRounds.sum(),
                                        )
                                    },
                                ),
                            )
                        }.getOrElse {
                            ("${workoutBlocks.size} exercises · $circuitRepeats rounds\n" + completedBlocks.joinToString("\n") { block ->
                                val total = block.completedRepRounds.sum()
                                "${HomeWorkoutActivityCodec.customExerciseName(block.exerciseId) ?: block.exerciseId} · $total reps"
                            }).take(1_000)
                        },
                        dataQualityFlags = "HOME_WORKOUT;NO_GPS",
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )))
                    draftRepository.delete(ownerId)
                    recoveryDraft = null
                    saveMessage = R.string.pace_workout_saved
                    playerOpen = false
                },
            )
        }
    }
}

fun homeWorkoutExerciseNameResource(exerciseId: String): Int? = exerciseLibrary.firstOrNull { it.id == exerciseId }?.name

@Composable
fun workoutExerciseName(id: String): String =
    homeWorkoutExerciseNameResource(id)?.let { stringResource(it) }
        ?: HomeWorkoutActivityCodec.customExerciseName(id)
        ?: stringResource(R.string.pace_custom_workout_exercise)

private data class PlayerStep(val block: WorkoutBlock?, val blockIndex: Int?, val round: Int, val roundIndex: Int, val isRest: Boolean)

private fun encodeWorkoutBlocks(blocks: List<WorkoutBlock>, title: String): String = JSONArray().apply {
    blocks.forEachIndexed { index, block ->
        put(JSONObject().put("id", block.id).put("exerciseId", block.exerciseId)
            .put("durationSeconds", block.durationSeconds ?: JSONObject.NULL)
            .put("repetitions", block.repetitions ?: JSONObject.NULL)
            .put("completedRepRounds", JSONArray(block.completedRepRounds))
            .put("completedDurationRounds", JSONArray(block.completedDurationRounds))
            .apply { if (index == 0) put("workoutTitle", title.trim().take(60)) })
    }
}.toString()

private fun workoutTitleFromDraft(value: String): String? = runCatching {
    JSONArray(value).getJSONObject(0).optString("workoutTitle").trim().take(60)
}.getOrNull()?.takeIf(String::isNotBlank)

private fun decodeWorkoutBlocks(value: String): List<WorkoutBlock> {
    val json = JSONArray(value)
    require(json.length() in 1..100)
    return (0 until json.length()).map { index ->
        val item = json.getJSONObject(index)
        val exerciseId = item.getString("exerciseId")
        require(homeWorkoutExerciseNameResource(exerciseId) != null || HomeWorkoutActivityCodec.customExerciseName(exerciseId) != null)
        val duration = item.optInt("durationSeconds").takeIf { !item.isNull("durationSeconds") }
        val repetitions = item.optInt("repetitions").takeIf { !item.isNull("repetitions") }
        val completedRepRounds = item.optJSONArray("completedRepRounds")?.let { values ->
            (0 until values.length()).map { values.getInt(it) }
        }.orEmpty()
        val completedDurationRounds = item.optJSONArray("completedDurationRounds")?.let { values ->
            (0 until values.length()).map { values.getInt(it) }
        }.orEmpty()
        require((duration != null) xor (repetitions != null))
        require(duration == null || duration in 1..3600)
        require(repetitions == null || repetitions in 1..500)
        require(completedRepRounds.size <= 20 && completedRepRounds.all { it in 0..(repetitions ?: 0) })
        require(completedDurationRounds.size <= 20 && completedDurationRounds.all { it in 0..(duration ?: 0) })
        WorkoutBlock(item.getString("id"), exerciseId, duration, repetitions, completedRepRounds, completedDurationRounds)
    }
}

private fun workoutStepCount(blocks: List<WorkoutBlock>, rounds: Int, restSeconds: Int): Int =
    blocks.size * rounds + if (restSeconds > 0) ((blocks.size - 1).coerceAtLeast(0) * rounds + (rounds - 1).coerceAtLeast(0)) else 0

@Composable
private fun PaceWorkoutPlayer(
    blocks: List<WorkoutBlock>,
    rounds: Int,
    restSeconds: Int,
    initialDraft: HomeWorkoutDraftEntity,
    onCheckpoint: suspend (HomeWorkoutDraftEntity) -> Unit,
    onClose: suspend () -> Unit,
    onSave: suspend (Long, List<WorkoutBlock>) -> Unit,
) {
    val steps = buildList {
        repeat(rounds) { round ->
            blocks.forEachIndexed { index, block ->
                add(PlayerStep(block, index, round + 1, round, false))
                if (restSeconds > 0 && (index < blocks.lastIndex || round < rounds - 1)) add(PlayerStep(null, null, round + 1, round, true))
            }
        }
    }
    val sessionKey = "${initialDraft.ownerId}:${initialDraft.sessionStartedAtEpochMillis}"
    var playerBlocks by remember(sessionKey) {
        mutableStateOf(blocks.map { block ->
            block.copy(
                completedRepRounds = List(rounds) { block.completedRepRounds.getOrElse(it) { 0 } },
                completedDurationRounds = List(rounds) { block.completedDurationRounds.getOrElse(it) { 0 } },
            )
        })
    }
    val nowElapsed = SystemClock.elapsedRealtime()
    var stepIndex by remember(sessionKey) { mutableStateOf(initialDraft.stepIndex.coerceIn(0, steps.lastIndex)) }
    var paused by remember(sessionKey) { mutableStateOf(initialDraft.paused) }
    var deadlineMillis by remember(sessionKey) {
        mutableStateOf(if (!initialDraft.paused && !initialDraft.finished && initialDraft.remainingStepMillis > 0) nowElapsed + initialDraft.remainingStepMillis else 0L)
    }
    var remainingMillis by remember(sessionKey) { mutableStateOf(initialDraft.remainingStepMillis.coerceAtLeast(0)) }
    var activeElapsedBase by remember(sessionKey) { mutableStateOf(initialDraft.elapsedActiveMillis.coerceAtLeast(0)) }
    var activeSegmentStarted by remember(sessionKey) {
        mutableStateOf(if (!initialDraft.paused && !initialDraft.finished) nowElapsed else null)
    }
    var finished by remember(sessionKey) { mutableStateOf(initialDraft.finished) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var hapticCues by remember { mutableStateOf(true) }
    val playerScope = rememberCoroutineScope()
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    DisposableEffect(view, keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    val step = steps.getOrNull(stepIndex)
    val duration = if (step?.isRest == true) restSeconds else step?.block?.durationSeconds
    fun currentActiveMillis(): Long = (activeElapsedBase + (activeSegmentStarted?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0) } ?: 0L)).coerceAtLeast(0)
    fun currentRemainingMillis(): Long = if (!paused && duration != null && deadlineMillis > 0) {
        (deadlineMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    } else remainingMillis
    suspend fun checkpoint(pausedValue: Boolean = paused, finishedValue: Boolean = finished) {
        val timeLeft = currentRemainingMillis()
        remainingMillis = timeLeft
        onCheckpoint(initialDraft.copy(
            blocksJson = encodeWorkoutBlocks(playerBlocks, workoutTitleFromDraft(initialDraft.blocksJson).orEmpty()),
            stepIndex = stepIndex,
            remainingStepMillis = timeLeft,
            elapsedActiveMillis = currentActiveMillis(),
            paused = pausedValue,
            finished = finishedValue,
            updatedAtEpochMillis = System.currentTimeMillis(),
        ))
    }
    fun checkpointSoon() { playerScope.launch { checkpoint() } }
    fun beginStep(index: Int) {
        activeElapsedBase = currentActiveMillis()
        activeSegmentStarted = SystemClock.elapsedRealtime()
        stepIndex = index.coerceIn(0, steps.lastIndex)
        paused = false
        val seconds = if (steps[stepIndex].isRest) restSeconds else steps[stepIndex].block?.durationSeconds
        if (seconds != null) {
            remainingMillis = seconds * 1_000L
            deadlineMillis = SystemClock.elapsedRealtime() + remainingMillis
        } else {
            remainingMillis = 0L
            deadlineMillis = 0L
        }
    }
    fun advance(completed: Boolean = true) {
        steps.getOrNull(stepIndex)?.let { current ->
            val blockIndex = current.blockIndex
            val block = current.block
            if (blockIndex != null && block != null) {
                val existing = playerBlocks[blockIndex]
                if (block.repetitions != null) {
                    val values = existing.completedRepRounds.toMutableList()
                    values[current.roundIndex] = if (completed) block.repetitions else 0
                    playerBlocks = playerBlocks.toMutableList().also { it[blockIndex] = existing.copy(completedRepRounds = values) }
                } else if (block.durationSeconds != null) {
                    val values = existing.completedDurationRounds.toMutableList()
                    values[current.roundIndex] = if (completed) block.durationSeconds else 0
                    playerBlocks = playerBlocks.toMutableList().also { it[blockIndex] = existing.copy(completedDurationRounds = values) }
                }
            }
        }
        if (stepIndex >= steps.lastIndex) {
            activeElapsedBase = currentActiveMillis()
            activeSegmentStarted = null
            remainingMillis = 0
            deadlineMillis = 0
            finished = true
            checkpointSoon()
        } else {
            beginStep(stepIndex + 1)
            checkpointSoon()
        }
    }
    fun togglePause() {
        val now = SystemClock.elapsedRealtime()
        if (paused) {
            paused = false
            activeSegmentStarted = now
            if (duration != null) deadlineMillis = now + remainingMillis
        } else {
            remainingMillis = currentRemainingMillis()
            activeElapsedBase = currentActiveMillis()
            activeSegmentStarted = null
            paused = true
            deadlineMillis = 0
        }
        checkpointSoon()
    }
    LaunchedEffect(deadlineMillis, paused, finished) {
        while (deadlineMillis > 0 && !paused && !finished) {
            val left = (deadlineMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMillis = left
            if (left == 0L) { advance(completed = true); break }
            delay(minOf(left, 250L))
        }
    }
    LaunchedEffect(stepIndex, hapticCues, finished) {
        if (stepIndex > 0 && hapticCues && !finished) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(stepIndex, paused, finished) {
        while (!finished && !paused) {
            delay(1_000L)
            remainingMillis = currentRemainingMillis()
            checkpoint()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 560.dp, max = 760.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(if (finished) R.string.pace_workout_finished else R.string.pace_workout_playing), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            TextButton(onClick = {
                playerScope.launch {
                    if (!paused && !finished) togglePause()
                    checkpoint(pausedValue = true)
                    onClose()
                }
            }) { Text(stringResource(R.string.pace_action_back)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.pace_workout_keep_screen_on), modifier = Modifier.weight(1f))
            Switch(checked = keepScreenOn, onCheckedChange = { keepScreenOn = it })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.pace_workout_haptic_cues), modifier = Modifier.weight(1f))
            Switch(checked = hapticCues, onCheckedChange = { hapticCues = it })
        }
        if (finished) {
            val completedMillis = currentActiveMillis()
            Text(stringResource(R.string.pace_workout_finished_summary, (completedMillis / 60_000).toInt(), blocks.size, rounds), style = MaterialTheme.typography.titleMedium)
            if (saveError) Text(stringResource(R.string.pace_workout_saved_error), color = MaterialTheme.colorScheme.error)
            Button(enabled = !saving, onClick = { saving = true; saveError = false }) { Text(stringResource(R.string.pace_workout_save_history)) }
            LaunchedEffect(saving) {
                if (saving) {
                    try { onSave((currentActiveMillis() / 1_000).coerceAtLeast(1), playerBlocks); saving = false }
                    catch (_: Exception) { saving = false; saveError = true }
                }
            }
        } else if (step != null) {
            Text(stringResource(R.string.pace_workout_round, step.round, rounds), style = MaterialTheme.typography.labelLarge)
            Text(if (step.isRest) stringResource(R.string.pace_plan_rest) else workoutExerciseName(step.block?.exerciseId.orEmpty()), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.pace_workout_exercise_progress, stepIndex + 1, steps.size), style = MaterialTheme.typography.bodyMedium)
            val next = steps.getOrNull(stepIndex + 1)
            next?.let { nextStep -> Text(stringResource(R.string.pace_workout_up_next, if (nextStep.isRest) stringResource(R.string.pace_plan_rest) else workoutExerciseName(nextStep.block?.exerciseId.orEmpty())) ) }
            val reps = step.block?.repetitions
            if (duration != null) Text("${(remainingMillis / 1_000).toInt()}", style = MaterialTheme.typography.displayLarge)
            else if (reps != null) Text(stringResource(R.string.pace_workout_block_reps, reps), style = MaterialTheme.typography.headlineSmall)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    enabled = stepIndex > 0,
                    onClick = { beginStep(stepIndex - 1); checkpointSoon() },
                    modifier = Modifier.weight(0.7f).height(56.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Icon(PaceAppIcons.Back, stringResource(R.string.pace_workout_previous), Modifier.size(22.dp)) }
                Button(
                    onClick = { togglePause() },
                    modifier = Modifier.weight(1.5f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        contentColor = if (paused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Icon(
                        if (paused) PaceAppIcons.Play else PaceAppIcons.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(if (paused) R.string.pace_workout_resume else R.string.pace_workout_pause),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                OutlinedButton(
                    onClick = { advance(completed = duration == null) },
                    modifier = Modifier.weight(0.9f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        stringResource(if (duration == null) R.string.pace_workout_done else R.string.pace_workout_skip),
                        maxLines = 1,
                    )
                }
            }
        }
        }
    }
}
