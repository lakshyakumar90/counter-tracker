# P00 Outdoor Activity State Machine

## States

- **Idle:** no active session; user may choose an activity or open history.
- **Acquiring:** activity selected; permission and location provider are checked and a usable fix is sought. No active elapsed timer until the user starts.
- **Recording:** session is active; accepted samples and timing state are durably recorded; user can pause or finish.
- **Paused:** session remains active but paused duration advances and new distance samples are not added. User can resume or finish.
- **Finishing:** user has requested completion; stop location collection, finalize duration/metrics, persist idempotently, and transition to a saved result. Repeated finish requests must not create duplicate activities.
- **Recovered:** a prior active session was found after process/device interruption. Show its timestamp and data quality; user may resume, finish/save, or discard with confirmation. Never silently discard or silently resume tracking.

## Allowed transitions

| From | Event | To | Required behavior |
| --- | --- | --- | --- |
| Idle | choose outdoor sport | Acquiring | Preserve selected sport; explain/request only required permission. |
| Acquiring | user starts and service is ready | Recording | Establish start time and durable active-session record before relying on background sampling. |
| Acquiring | user cancels | Idle | Stop pending location work and retain no phantom activity. |
| Acquiring | permission denied/provider unavailable | Acquiring | Show actionable error/status; permit retry or cancel; do not claim recording. |
| Recording | pause | Paused | Persist pause boundary before UI reports paused. |
| Recording | finish | Finishing | Lock session against duplicate finish and stop future sample intake. |
| Recording | process/device interruption | Recovered on next launch | Recover last durable checkpoint and identify any missing interval. |
| Paused | resume | Recording | Persist resume boundary; moving time resumes; preserve original sport/session identity. |
| Paused | finish | Finishing | Close pause interval and finalize without counting it as moving time. |
| Paused | process/device interruption | Recovered on next launch | Recover as paused; require explicit user choice. |
| Recovered | resume | Recording | Resume existing session only after explicit action and service readiness. |
| Recovered | finish/save | Finishing | Finalize saved samples and flag interruption/gap. |
| Recovered | discard | Idle | Explain irreversible loss and confirm before deletion. |
| Finishing | finalize success | Idle/result | Save once, show final metrics and quality notes. |
| Finishing | persistence failure | Recovered | Preserve recoverable session data and offer retry; do not report successful save. |

## Invariants

- A session has one stable ID and one selected sport from start through finalization.
- No location sample is accepted before Recording or after Finishing begins.
- Elapsed time includes paused time; moving time excludes explicit pause intervals only.
- Recording state is durable before confirming start, pause, resume, or finish to the user.
- A saved session is immutable for its GPS track; metadata edits do not rewrite the route.
- Permission revocation, service loss, provider loss, process death, and storage failure produce visible, recoverable states.
- The map and network are never prerequisites for entering or staying in Recording.
