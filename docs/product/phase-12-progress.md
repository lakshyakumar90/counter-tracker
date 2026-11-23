# Phase 12 — Insights dashboard implementation (2026-11-18)

## Implemented

- Replaced the weekly cache placeholder with local Room history summaries and calendar-aligned week, month, and year selectors. The selected period is saved in app preferences; sport filters cover cycling, running, jogging, walking, and all sports.
- Added distance, activity count, active days, moving duration, distance by period, monthly duration by period, calendar-week distance bars, selectable values, and a selected-period activity list. Selecting an activity opens an on-device route scrubber that highlights the recorded GPS point at the chosen timestamp. The frequency calendar exposes day counts to accessibility services and opens the selected day’s activity list.
- Added local sport distance proportions and per-period pace, cycling-speed, and elevation trend lines. Missing elevation remains a chart gap. Also added existing personal-record summaries with source activity context, active and archived goals, goal creation and editing, recalculated goal progress, archive behavior, and a user-initiated text summary share action.
- Chart output preserves empty calendar buckets as zero activity; it does not invent heart-rate or power values. Pace and speed are shown only for applicable selected sports, with lower pace/higher speed direction stated. A chart point can be selected by tap and its exact value is exposed in text and semantics. The full-screen view has zoom and horizontal pan controls.
- The charts are static and use the existing river/amber theme; no animated transitions are used, so reduced-motion settings do not trigger motion.
- Added pure analytics for calendar period ranges and gap-preserving buckets. Added tests for empty, single, 1,000-activity, sport-filter, period-boundary, and annual summaries.

## Checks

- `:app:compileDebugKotlin` — passed after adding route timeline scrubbing.
- `:app:testDebugUnitTest` — passed (25 tests, including the new Insights analytics coverage).
- `:app:compileDebugAndroidTestKotlin` — passed.
- `:app:lintDebug` — passed after replacing Compose `LocalContext.getString` usage with resource-backed strings and adding the `en-rIN` translations; warnings only.
- No APK/AAB was built, and no USB debugging or device checks were run, per owner instruction.

## Gate and remaining acceptance

Phase 12 code is in place, but its roadmap gate is not marked passed. The roadmap depends on P10 and P11; P10 physical-device history/detail acceptance and P11 Room instrumentation remain open per [Phase 10–11 evidence](phase-10-11-progress.md). The owner requested proceeding, so Phase 12 implementation was started as a recorded sequencing exception. Runtime touch/accessibility review remains. Do not treat this note as closing the P10–P12 gates.
