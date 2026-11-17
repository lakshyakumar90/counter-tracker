# P00 Product Vision and Scope

## One-page vision

Pace is a private, offline-first Android fitness companion for people who want to record outdoor activity, understand their training, and manage simple workouts without depending on a social network or a continuous internet connection. The first release prioritizes reliable cycling, running, jogging, and walking capture; trustworthy local history and analytics; and clear control over personal data. A user can install Pace, choose local-only use, and complete the core activity workflow without creating an account.

Pace is native Android, built with Kotlin and Jetpack Compose. Room is the canonical store for user data on the device. Background recording uses a location foreground service. Maps, cloud backup, and optional intelligence support the core experience but do not gate recording or local analysis. Network services are explicit extensions of the local product.

### Target users

- **New local user:** wants to try Pace and record an activity without registration.
- **Returning local user:** keeps activities and preferences on the same device and may remain offline indefinitely.
- **Returning cloud user:** signs in to back up or restore personal data across reinstalls/devices.
- **Outdoor exerciser:** records cycling, running, jogging, or walking with screen-off continuity and sees understandable GPS limitations.
- **Home-workout user:** follows bodyweight, timer, HIIT, or mobility sessions; this is in the product direction, but its V1 inclusion is governed by the MVP cut line below.

### Product principles

1. Local-first: recording, saved history, plans included in the shipped scope, and core analytics remain useful without a connection.
2. Private by default: precise location and fitness data are personal data, not social content.
3. Honest measurements: label estimates and incomplete data; define every displayed metric.
4. Reliable capture before breadth: protect the recording state machine, battery behavior, recovery, and data integrity.
5. User-controlled services: sign-in, cloud backup, map downloads, and AI are optional and explained when invoked.

## V1 scope

### Included product capabilities

- Android app with local-only onboarding and profile/settings.
- Outdoor recording for cycling, running, jogging, and walking, including pause/resume, finish, recovery, and screen-off operation.
- Local activity history, detail, edit/delete controls, local analytics, and export of user data.
- Limited offline map fallback; region downloads only where licensing and storage behavior are approved.
- Optional account and backup/restore path, subject to the backup-first contract.
- Accessibility, privacy controls, error reporting, and release-quality checks are part of each feature, not a final polish phase.

### Explicitly out of V1

- Social feed, followers, public profiles, leaderboards, or community features.
- Subscription or feature paywall.
- Swimming and sports beyond the four named outdoor types.
- Turn-by-turn routing or a full offline route-planning engine.
- AI-generated coaching, predictions, or workout generation in the core release.
- Mandatory account creation, mandatory cloud connectivity, or mandatory telemetry.

### V1 cut line for later roadmap phases (provisional)

To keep the first release focused on safe capture and a coherent local product, use this initial cut:

| Phase | Initial release decision | Rationale |
| --- | --- | --- |
| P13 Saved routes, import/export and navigation | V1.1, except basic activity GPX export required for user data portability | Route following/import adds map and file edge cases after core capture is proven. |
| P14 Outdoor training plan and calendar | V1.1 | A usable planner is a separate product surface; first validate recording and history. |
| P15 Home workout library and interval player | V1.1 | Distinct indoor experience that should not delay outdoor recording reliability. |
| P16 Rules engine, goals and performance forecasts | V1.1, except deterministic descriptive analytics already in P11/P12 | Avoid presenting forecasts before their inputs and limitations are validated. |

This cut line is a planning default, not a claim that these capabilities are unwanted. Review it after P05 (stable local model/repository) and before committing P07–P16 estimates. A V1.1 capability may be pulled into V1 only if P00 acceptance remains intact and the release estimate is updated.

## Supported activity types

V1 outdoor recorder choices are **Cycling**, **Running**, **Jogging**, and **Walking**. Running and jogging may share recording and analysis engines while preserving the user-selected activity label. Home workouts are a separate future activity mode and do not appear in the outdoor recorder until P15 is approved for a release.

## User journeys

1. **First launch, offline:** open app → choose local-only mode → set units and optional profile details → see the home screen and packaged fallback map → no sign-in prompt blocks use.
2. **Record:** choose activity → review location permission explanation → grant required permission → acquire location with visible status → start → record while app is foreground or screen is locked → pause/resume as needed → finish → review saved activity and data quality.
3. **GPS unavailable or degraded:** choose activity → see a clear location state → allow retry or continue only if product policy permits a non-GPS session → never imply a complete route when fixes are missing.
4. **Review activity:** open history offline → filter/select an activity → inspect summary, splits, route and metric provenance → edit allowed metadata or delete with confirmation → export when requested.
5. **Plan/analyze:** open local analytics → choose period/activity/unit → inspect values and charts with definitions and empty states → data is derived from local records and works offline.
6. **Restore:** reinstall/open → select restore/sign-in explicitly → authenticate online → show available backup and timestamp → user chooses restore → download metadata and private track files → validate and merge without silently replacing newer local records → report partial failures and retry options.
7. **Cloud unavailable:** continue local use → queue eligible backup operations → show sync state and last success → retry when online; never block recording or local review.

## Terminology and metric rules

- **Distance:** estimated horizontal path length from accepted location samples, reported in the selected distance unit and marked approximate where GPS quality warrants.
- **Elapsed time:** finish timestamp minus start timestamp, including pauses and interruptions.
- **Paused time:** sum of intervals in the explicit Paused state.
- **Moving time:** elapsed time minus explicit paused time. Auto-detected stationary time is not removed in V1 unless separately specified and tested.
- **Average speed:** distance divided by moving time; show unavailable when moving time is zero or distance is invalid.
- **Pace:** moving time divided by distance, displayed for running, jogging, and walking in time per distance; unavailable at zero/invalid distance.
- **Split:** distance/time segment at each configured unit-distance boundary; show a final incomplete segment as “partial” and do not compare it as a full split.
- **Route/track:** ordered accepted location samples associated with an activity. It is sensitive personal data.
- **Estimate:** a derived value that may vary with GPS sampling, filtering, and device conditions; do not present as laboratory precision.
- **Workout:** a saved outdoor activity or a future structured home workout; UI and data model must distinguish them.

## Units and locale

Metric is the default for the provisional India-first launch. Imperial is a user-selectable alternative. Store canonical quantities in SI units and convert at presentation boundaries. Distance uses kilometres/metres or miles/feet as appropriate; pace uses min/km or min/mi; speed uses km/h or mph. Locale controls date/time and number formatting, while the selected unit preference controls measurement units. Tests must cover conversions, rounding, zero values, and partial splits. Do not infer a user’s preferred units solely from location.

## AI boundaries

AI is excluded from recording, safety, persistence, restore, and deterministic analytics. No AI call runs automatically. If added after the core release candidate, it must be opt-in, explain what data leaves the device, send the minimum necessary payload through an authenticated server-side function, validate structured output, apply user-visible cost/rate limits, and remain unavailable without reducing local functionality. The vendor choice is deferred to the AI phase; no API key may ship in the Android binary.

## Open product checkpoints

- Confirm provisional MVP cut line after P05.
- Confirm capacity/velocity baseline after the first measured work period and re-forecast after P05.
- Confirm actual Play account type, launch regions, privacy obligations, and licensed map coverage before beta/release.
- Confirm whether route import/following moves from V1.1 into V1 only through an explicit scope decision.
