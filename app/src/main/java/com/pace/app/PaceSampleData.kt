package com.pace.app

internal enum class SampleActivityKind {
    Run,
    Cycle,
}

internal data class SampleActivity(
    val kind: SampleActivityKind,
    val distanceMeters: Int,
    val durationMinutes: Int,
)

internal object PaceSampleData {
    val activities = listOf(
        SampleActivity(SampleActivityKind.Run, distanceMeters = 5_000, durationMinutes = 31),
        SampleActivity(SampleActivityKind.Cycle, distanceMeters = 18_200, durationMinutes = 48),
    )
}
