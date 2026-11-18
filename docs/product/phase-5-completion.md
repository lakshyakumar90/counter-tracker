# P05: Local data model and repository layer

## Delivered

- Room v2 database and exported schema for activity summaries, ordered route
  points, splits, heart-rate/cadence/power samples, workout templates and
  intervals, planned sessions, goals, personal records, map-pack metadata,
  and sync/tombstone operations.
- Explicit indexes for owner/time and sport/time activity history, route and
  split ordering, sensor timelines, calendar dates, goals, records, map-pack
  lookup, and ordered sync work.
- A v1-to-v2 Room migration that preserves existing activity summary values
  and supplies documented defaults for newly introduced fields. The database
  builder registers the migration and has no destructive fallback.
- DAO and repository interfaces plus Room implementations for activities,
  workouts/calendar, goals/records, map metadata, and sync work.
- Atomic activity finalization: summary, route points, splits, optional sensor
  samples, and a supplied sync operation commit in one database transaction.
- Stable cursor-style history pagination, search, sport filtering, ordered
  batch point reads/writes, calendar range queries, and goal/record aggregates.
- Soft deletion and durable activity tombstones. Reusing a deleted ID is
  rejected; retention purge waits until its tombstone is marked done.
- Preferences DataStore and one-time migration of P04 `pace_local_settings`.
  Invalid enum/tab values and incompatible preference types recover to safe
  defaults; a corrupt DataStore file is replaced with empty preferences.
- Android Auto Backup stays disabled. Legacy and Android 12+ extraction rules
  explicitly exclude private databases, preferences, and files.
- Deterministic synthetic route fixture generator for long tracks and mixed
  sensor history.
- At-rest, backup, and entity relationship decisions in
  [ADR 0002](../architecture/adr/0002-local-data-protection.md).

## Roadmap mapping

| Tasks | Evidence |
| --- | --- |
| T001–T002, T022–T023 | ADR 0002, exported Room schema, explicit v1→v2 migration and migration test |
| T003–T013 | `data/local/entity/Entities.kt` |
| T014–T017 | `data/local/dao/Daos.kt`; indexed history/calendar/aggregate queries |
| T018–T019 | `domain/repository/Repositories.kt` and `data/local/repository/RoomRepositories.kt` |
| T020 | `PaceSettingsRepository` and DataStore settings migration test |
| T021 | `RoomActivityRepository.finalizeActivity` transaction |
| T024 | tombstone-aware repository deletion and purge test |
| T025 | `PaceFixtureGenerator` long-route persistence test |
| T101–T102 | backup/data-extraction XML and ADR 0002 |

## Verification and remaining gate evidence

- Kotlin main-source and Android-test-source compilation passed; Room KSP
  generated `app/schemas/com.pace.app.data.local.PaceDatabase/2.json`.
- Database instrumentation source covers atomic rollback, migration data
  retention, history pagination/deletion, workout intervals/calendar, goals,
  records, map metadata, and a deterministic 2,000-point route.
- The instrumentation tests have **not** been run for P05. Running them would
  package a new debug app APK and test APK. Per `AGENTS.md`, this requires the
  owner's explicit APK-build instruction. No emulator/system image was
  created.
- The P05 code has not yet been verified by an on-device migration run. P05's
  Go gate remains pending until the owner authorizes that APK/test run.

## Capacity and cut-line checkpoint

The HTML roadmap assigns P05 78 points across 27 tasks. At P00's provisional
rate of 4 points/week, that is roughly 19.5 focused weeks. This is a planning
calculation, not measured velocity: no actual-hours or accepted-points log was
available for P05. The V1 cut line therefore remains provisional and must be
reviewed before dates or scope for P07–P16 are committed.
