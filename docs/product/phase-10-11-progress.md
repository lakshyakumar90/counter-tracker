# Phase 10–11 implementation and gate evidence (2026-11-18)

## Phase 10 — History/detail

Implemented the complete local history path in `PaceHistoryScreen`:

- Home entry and saved activity list, sport chips, title/notes search, local-calendar date range, and database sort by newest, oldest, distance, or elapsed duration.
- Stable 100-row offset pages and lazy list rendering, month section headers, a loading placeholder, retryable list/detail errors, and a record CTA for empty results.
- Compact route thumbnails; possible-duplicate hints based on nearby start times and similar distances; left-swipe delete with confirmation and explicit detail actions.
- Detail screen with title, sport, local timestamp, notes, route map, visible start/finish markers, quality state, unit-aware distance/time/pace/speed/elevation metrics, sampled speed/pace/elevation charts, and split table.
- Metadata edits update Room immediately and add an escaped JSON sync payload. Delete is confirmation-backed and tombstoned transactionally. Explicit GPX sharing is user initiated and does not add a feed.

Room `sortedPage` applies owner, status, sport, title/notes, date, and sort filters before offset paging. Existing keyset `page` remains available for callers that need it.

## Phase 11 — Deterministic analytics

`ActivityMetrics` is an offline pure Kotlin calculation library. It now covers:

- SI/imperial conversion; elapsed time; moving time as elapsed minus explicit pause time per P00 (GPS stationary time is not subtracted); valid sampled distance and covered sample duration.
- Pace, speed, 3-point median maximum speed, kilometer/mile interpolated completed splits, and quality-filtered elevation ascent/descent.
- Daily, locale-week, and monthly frequency; weekly sport distance and moving/elapsed durations; 7/28-day load; longest activity; rolling 1K/5K/10K pace records; comparable sport-only speed/pace trends; consistency streaks; goal progress; workout completion; and period comparison with a zero prior period reported unavailable.
- Incomplete, deleted, invalid, and zero-distance summaries are excluded from aggregate operations. GPS segment breaks are never bridged; an incomplete final split is omitted.

A rebuildable Room weekly-by-sport aggregate table was added at schema version 4. Save/finalize and delete refresh only the affected locale/time-zone week and sport inside the activity transaction. Full recomputation replaces the cache from canonical Room summaries. The Insights overview reads cached totals and rebuilds if the locale/time-zone cache signature is stale.

## Tests and checks

- JVM fixtures cover conversions and zero inputs, segments and spikes, elevation quality, kilometer/mile split interpolation, eligibility exclusions, locale weeks/months, sport totals, load, trends, personal best windows, goals, streaks, workout completion, and 10,000-session aggregation under a 5-second budget.
- Android database test source covers stable paging, date/sport/sort filtering, immediate metadata/outbox persistence, incremental cache updates, full cache rebuild, tombstones, and the v3→v4 migration. These tests compile but require an Android runtime to execute.
- `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:lintDebug` pass.

## Gate status

Implementation and code checks are complete for P10 and P11. Phase 10's roadmap gate also requires manual history/detail acceptance on physical devices. The owner explicitly prohibited APK packaging and USB/device checks for this work, so that device-only evidence is not claimed and the P10 gate remains Hold. Android database instrumentation was compiled but not executed for the same no-device constraint; P11's Room migration/cache runtime evidence remains pending. No APK was built.
