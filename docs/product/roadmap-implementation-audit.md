# Roadmap implementation audit — 2026-11-19

This audit tracks implementation evidence for the remaining roadmap phases.
“Source implemented” means repository code/docs exist and the stated code checks
passed. It does not mean the live service, physical-device, release, or
operations acceptance gate passed.

## P14 — Planning

- **Source implemented:** P14-T001–T027, including the Room planner, week/month
  agendas, day/session details, templates, targets, intervals, reminders,
  completion linking, copy, skip/missed states, drag/date rescheduling, and
  Home entry points. Evidence: [P14 progress](phase-14-progress.md),
  `app/src/main/java/com/pace/app/ui/planning/PacePlanScreen.kt`, and
  `PlanReminderWork.kt`.
- **Source tests:** P14-T028 has JVM reminder-time coverage and Room
  instrumentation sources. Time-zone/clock changes, reboot reminders, drag and
  undo, planned start, completion linking, and History still need a phone.
- **Gate:** Hold for physical-device acceptance; month is an agenda, not a
  traditional grid.

## P15–P16 — Home workouts and local recommendations

- **P15 source implemented:** P15-T001–T022, including packaged offline
  movement diagrams. Circuits, monotonic
  timers, rounds, rests, pause/resume, player controls, checkpoint recovery,
  saved activity details, and History editing work offline. Evidence:
  [P15 progress](phase-15-progress.md),
  `app/src/main/java/com/pace/app/ui/workouts/PaceHomeWorkoutLibrary.kt`, and
  `HomeWorkoutActivityCodec.kt`.
- **P15 acceptance:** T023–T024 need screen-lock/background timing acceptance
  on a phone; diagram legibility also needs device review.
- **P16 source implemented:** P16-T001–T020 for deterministic local workout
  recommendations, fallback choices, time/load constraints, predictions, and
  JVM regression coverage. Evidence: [P16 progress](phase-16-progress.md).
  Visual and interaction acceptance still needs a phone.

## P17 — Cloud backup, sync, and restore

- **Source implemented:** P17-T001–T027 and T030–T035, including backup
  quota configuration and runbooks.
  This includes account-scoped Room data/outbox, bounded versioned archives,
  private Storage, owner RLS policies, resumable TUS uploads, WorkManager
  transfer/restore, idempotent retry, tombstones, restore merge rules, manual
  sync, metered-network control, and backup-size preview. A manual retry
  requeues only the active account's failed/retry rows. Evidence:
  [P17 progress](phase-17-progress.md),
  `PaceSyncWorker.kt`, `PaceRestoreCoordinator.kt`, and Supabase migrations.
- **Source implemented:** P17-T033 now has a server-configurable per-user byte
  quota, actual private-object usage preview, 80% warning, oversized-upload
  preflight, and atomic Storage RLS enforcement. The quota is seeded unset; the
  owner must choose and configure its value after reviewing the Supabase plan.
  T101's backup-first/single-writer contract is documented; concurrent editing
  is deferred.
- **Live acceptance:** T028–T029 and T036–T038 require a project, test users,
  and device(s). Migrations and SQL policy tests have not executed: local
  Postgres is absent and Docker Desktop's engine did not respond to the Supabase
  CLI. No live RLS, Storage, sync, or reinstall/restore claim is made.

## P18–P20 — Privacy, reliability, and release

- **P18 source implemented:** P18-T001–T017, T020–T021, and T024 for settings,
  export, local erase, account separation/deletion, encrypted auth session,
  privacy disclosures, high contrast, and reduced motion. Evidence:
  [P18 progress](phase-18-progress.md). T018–T019 and T022–T023 require manual
  screen-reader, large-text, keyboard, and switch-access checks. T101's web
  deletion page is a template; public hosting and URL remain open.
- **P19 source implemented:** tracking/analytics/planner/sync/policy test
  sources, Gradle locking, and CI OSV/Gitleaks configuration (including
  P19-T101). The 53 JVM tests pass and Android instrumentation sources compile.
  T001–T040 are not all runtime-verified; the full device, scale, battery,
  accessibility, API/OEM, live RLS, and recovery matrix is still Hold. The OSV
  workflow has not run in CI.
- **P20 source prepared:** P20-T001–T002, T004, T009–T010, T020–T022 have source or
  documented preparation. Final identity, signing, Play listing/forms,
  screenshots, tester enrollment, distribution, and release builds depend on
  owner decisions and authorized accounts. No release AAB is authorized.

## P21 — Optional AI

- **Source implemented:** P21-T001, T005–T033, T035, and the T034 model
  comparison harness; P21-T101's
  vendor-neutral server boundary; T102 local-only eligibility; and T104 export
  and deletion coverage. Payloads are aggregate-only. Prompts are versioned and
  that version is logged. The server enforces per-user daily/monthly request
  caps, a global monthly request ceiling, and a global cost budget, and has a
  remotely controlled, default-off kill switch. Eight Deno tests cover privacy
  minimization, validation, hallucinated numeric claims, and synthetic novice/
  intermediate fixtures. Evidence: [P21 progress](phase-21-progress.md),
  `supabase/functions/ai-insights/`, and the AI migrations.
- **Live/owner acceptance:** P21-T004 and a run of T034 require OpenRouter and
  Gemini project keys, currently eligible free model IDs, and an explicit
  evaluation request ceiling. Provider pricing/account billing state must be
  verified. T036–T037 need staging privacy/network verification.
  T101's on-device evaluation is documented: no second generative runtime for
  this release because AICore/Gemini Nano coverage varies by device; local
  deterministic recommendations remain the offline path. T103 store/privacy
  approval and T105's kill-switch drill remain open. Only synthetic smoke
  requests were sent; a comparative model evaluation has not run.

## P22 — Operations

- **Source implemented:** P22-T001, T004–T005, T009–T010, T012–T013, and
  T015–T016 have a changelog, private support flow, FAQ, rollback/migration,
  maintenance, triage, and deferred-integration documentation. Evidence:
  [P22 progress](phase-22-progress.md) and `docs/operations/`.
- **Owner/service gates:** T002–T003, T006–T008, T011, and T014 require chosen
  monitoring/cost vendors, alert recipients, approved telemetry, and a staged
  recovery rehearsal. No account/provider or retention decision is assumed.

## Cross-phase verification snapshot

- Android: `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`,
  `:app:testDebugUnitTest` (53 JVM tests), `checkFormatting`, and
  `:app:lintDebug` passed after the recent workout and account-scoped manual
  retry changes.
- Backend: five Deno policy/evaluation tests, Edge Function type checks, and
  lint passed. SQL migrations/RLS have not been run against a database.
- Device/package: `adb devices -l` returned no devices. No final configured APK
  was built or installed; native runtime behavior remains unverified.
