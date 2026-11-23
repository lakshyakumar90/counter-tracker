# Phase 15 — Home workouts progress

## Implemented offline

- Added mobility, strength, core, HIIT, and stretching categories with seven
  packaged bodyweight movements, localized steps, repetition or duration
  guidance, and comfort/safety cues.
- Added category filters, expandable exercise instructions, and a circuit
  builder with block add/remove/reorder, round count, rest duration, and an
  estimated-time/exercise preflight summary.
- Added a full-screen circuit player with timed countdowns based on Android's
  monotonic elapsed clock, manual rep completion, inter-exercise rests,
  circuit rounds, next-step preview, pause/resume, previous, and skip.
- Added optional transition haptics and an active-session keep-screen-on
  switch. Paused time is excluded from the saved elapsed duration.
- Completed sessions are saved locally through the activity repository and
  appear in history as `HOME_WORKOUT` activities with their exercise plan in
  editable notes. No location permission, network, or account is needed.
- User-facing text is in Android string resources, including the `en-rIN`
  resource set.
- Packaged seven lightweight vector diagrams for the starter exercises. The
  library shows each diagram beside its movement name, and expanded steps show
  a larger version. The assets are available offline, have accessible labels,
  and do not animate or replace written instructions.
- In-progress circuits are now checkpointed to Room by account owner. On
  process restart the app offers resume or discard and resumes in a paused
  state, so time away is not counted. Saving uses a stable session-derived ID
  to avoid duplicate history entries if the process stops between activity
  persistence and draft cleanup. Draft data is included in local export and
  account-scoped local erase.
- The player checkpoints completed reps and timed seconds for each round. The
  saved activity stores a bounded, versioned structured result, and History
  displays and edits completed reps, duration, exercise notes, and the overall
  note without requiring GPS or a cloud connection. Old plain-text home
  workout entries remain readable.

## Remaining acceptance

- Runtime acceptance on a physical phone is still needed for screen lock,
  background timing, haptic behavior, display scaling, and history review.
- Runtime visual review remains on a physical phone for diagram legibility at
  common display sizes and accessibility scale settings.
- Runtime acceptance on a physical phone remains for process-death recovery,
  export, and account erase, alongside the player checks above.

## Checks

On 2026-11-19, after the workout result editor, `:app:compileDebugKotlin`,
`:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest` (52 tests),
`checkFormatting`, and `:app:lintDebug` passed.
The physical-phone and Room instrumentation checks remain unrun; no APK was
packaged and no authorized physical device was connected.

## Illustration follow-up (2026-11-19)

Added seven packaged vector movement diagrams for the offline exercise library,
with thumbnail and expanded instruction views plus localized accessibility
labels. On this revision, Android app and instrumentation sources compile,
53 JVM tests pass, formatting passes, and lint passes. Diagram legibility and
screen-reader review remain part of physical-device acceptance.
