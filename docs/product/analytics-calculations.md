# P11 calculation contract

All domain values use SI units. Formatting and conversion happen at the UI
boundary. Results are estimates from accepted GPS samples, not laboratory
measurements.

| Metric | Rule |
| --- | --- |
| Distance | Sum Haversine distance between ordered, valid adjacent points in the same segment. Reject non-positive time, gaps over 30 seconds, invalid coordinates/accuracy, and inferred speed over 15 m/s. Never bridge a pause or route segment. |
| Elapsed time | End minus start when valid; otherwise the stored elapsed value. It includes pauses. |
| Moving time | Elapsed time less explicit paused time, clamped to zero. Stationary GPS samples do not reduce moving time. |
| Average speed / pace | Distance divided by stored moving time; unavailable for zero distance or moving time. Pace is presented for running, jogging, and walking. |
| Maximum speed | Maximum rolling median of three adjacent accepted sample speeds; with fewer than three accepted speeds, use the highest accepted sample. |
| Distance split | Interpolate elapsed sample time at each configured distance boundary. Persist completed splits only; an incomplete final distance is not a full split. |
| Elevation gain/loss | Sum adjacent altitude changes of at least 3 m when both fixes have horizontal accuracy at most 25 m. Smaller changes are treated as jitter. |
| Weekly frequency/totals | Convert timestamps into the supplied device time zone and locale week fields. Cache buckets use the locale's first weekday and local zone; a locale/zone signature prevents reuse after either changes. |
| Rolling load | Sum eligible moving seconds in the inclusive current 7-day or 28-day local-date window. |
| Personal best | Fastest rolling target-distance time (1 km, 5 km, or 10 km) over valid continuous route samples; interpolate both window endpoints. No route means no route-derived record. |
| Consistency streak | Consecutive local dates ending today, or yesterday when today has no activity. |
| Goal progress | `current / target * 100`; invalid/non-positive targets are unavailable and overachievement can exceed 100%. |
| Period change | `(current - previous) / previous * 100`; a zero/non-positive previous period is unavailable. |
| Workout completion | Completed non-deleted planned sessions divided by planned, completed, and skipped non-deleted sessions. Empty denominator is unavailable. |

Aggregate inputs include only saved, non-deleted summaries with positive
distance and elapsed time and moving time between zero and elapsed time. Room
activities remain canonical; the aggregate cache can be deleted and rebuilt.
