# P00 Acceptance, Metrics, and Quality Baseline

## MVP acceptance matrix

All release-critical rows are binary: pass only when evidence is attached to a reproducible test/device run. Values below are initial product thresholds and must be tuned only through a recorded decision.

| Area | Pass criteria |
| --- | --- |
| Install/launch | Debug and release variants install and launch on the documented supported Android range; first launch completes with network disabled and no account. |
| Outdoor recording | User can start, pause, resume, and finish each supported outdoor type; active recording continues with screen locked; saved activity survives process death/device restart recovery without duplicate finalization. |
| Location quality | Permission and GPS states are visible; poor/no fix never produces a falsely complete route; sample timestamps are ordered and invalid points are excluded by documented rules. |
| Local persistence | Saved activity, settings, and included feature data survive force-stop/relaunch and work in airplane mode. |
| Data loss | A kill/reboot recovery scenario is exercised on at least two physical devices, including one with aggressive battery management; no silent loss of a finalized activity. |
| Metrics | Golden fixtures produce expected distance, time, speed/pace, and splits within declared rounding/tolerance; zero distance/time and partial split cases have explicit states. |
| Offline maps | Installed package and fallback render without network; missing tile/region is explicit; recording still works with no map data. |
| Privacy/security | Local-only path needs no account; cloud data is private per user; cross-user access tests fail closed; raw GPS is absent from logs/diagnostics. |
| Backup/restore | Opt-in backup is idempotent; restore previews destination and merges without silent destructive overwrite; both metadata and track objects restore; partial failure is visible/retryable. |
| Accessibility | Core flows are usable with TalkBack, large text, adequate contrast, and non-color-only status; semantics and touch targets are checked. |
| Reliability/performance | No crash, ANR, or unbounded battery/storage regression in the agreed field-test matrix; thresholds and device matrix are recorded before beta. |
| Release | Signed internal APK and Play AAB can be produced reproducibly; privacy disclosures and current Play requirements reviewed before distribution. |

## Analytics calculation glossary

All stored canonical distances are metres, durations are milliseconds/seconds with an explicit unit, and derived values are recomputable from accepted samples. The exact filter for accepted GPS samples and distance tolerance must be specified with P07 golden-track fixtures before release.

| Metric | Calculation / source | Notes |
| --- | --- | --- |
| Elapsed time | `finishTime - startTime` | Includes pauses and recovery gaps; use monotonic elapsed time while an activity is active, with wall-clock timestamps for display. |
| Paused time | Sum of intervals in explicit Paused state | Does not infer pause from zero speed. |
| Moving time | `elapsedTime - pausedTime` | V1 excludes only explicit pauses; do not silently auto-pause. |
| Distance | Sum geodesic horizontal distances between consecutive accepted GPS samples | Apply documented accuracy/outlier/gap filters; mark estimate and quality. Never bridge a rejected long gap as if observed. |
| Average speed | `distance / movingTime` | Undefined if moving time is zero; display as unavailable. |
| Average pace | `movingTime / distance` | For running/jogging/walking only; undefined if distance is zero. Format min/km or min/mi. |
| Split duration | Time between interpolated crossing of consecutive configured distance boundaries | Clearly identify partial final segment; use same accepted track/filter as total distance. |
| Elevation gain | Sum of positive changes in accepted, filtered elevation samples, only when source quality is adequate | Do not show until filtering/source and uncertainty are documented; otherwise omit. |
| Personal record | Best qualifying value among valid saved activities of matching sport and distance/rule | Rule, minimum data quality, and tie behavior must be visible and tested before enabling. |
| Weekly/monthly totals | Sum valid activity values in the user’s local calendar period | State timezone/calendar boundaries; exclude deleted/invalid records; avoid mixing units. |

## Definition of Done (applies to every task)

A task is complete only when:

1. Its acceptance criteria are satisfied and evidence is recorded in the roadmap/PR.
2. Relevant unit, integration, Compose UI, or instrumentation tests pass; calculations and state transitions have focused tests.
3. Android lint and the project formatting check pass, or an explicit toolchain blocker is recorded.
4. UI changes have accessibility semantics, TalkBack/large-text review, contrast check, and no hard-coded user-facing strings.
5. Offline behavior, loading/empty/error states, and permission/privacy effects are reviewed for affected features.
6. No secrets, raw GPS, or unnecessary personal data are added to code, logs, fixtures, or diagnostics.
7. Public behavior, data model/migration, setup, and architecture decisions are documented where affected.
8. CI is green for required checks; if CI is not yet configured, local equivalent checks and the missing CI task are recorded.
9. The change is reviewed against dependencies and phase gates; a task cannot close a gate by itself without the named phase evidence.

## Error and recovery policy

- Never fail silently for GPS, permission, storage, network, authentication, map download, restore, or export errors.
- Explain what happened, whether user data was saved, what the app will retry, and the next safe action. Preserve entered data and retryable work.
- Distinguish recoverable, retryable, and terminal errors. Do not display raw exceptions or secrets.
- Recording failures show current recording state and last durable save. Any uncertainty about whether a workout is saved must be explicit.
- Offline errors are contextual: local work remains available; cloud-only actions explain that connectivity is required.
- Destructive actions (finish/discard/delete/overwrite/restore) require clear consequence and confirmation where data loss is possible.
- Log stable error codes and non-sensitive diagnostics; never include precise coordinates, tokens, or full user content.
