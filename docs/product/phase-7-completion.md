# P07: GPS engine and workout recording service

## Implemented in this source pass

- Added a pure recording state transition model and GPS sample validation for
  coordinate bounds, timestamps, accuracy, and sport-specific implausible jumps.
- Added Haversine distance for the recorder engine. P11 must consume this shared
  function and add its golden metric fixtures rather than implementing a second
  distance formula.
- Added route segment indexes and a schema migration so a location gap over
  30 seconds or an explicit pause/resume starts a new line; distance is not
  added between those segments.
- Added a swappable location source boundary, Google Play fused high-accuracy
  source, and Android `LocationManager` fallback for devices without a working
  fused provider. Sampling is configured per activity type (2 seconds cycling,
  3 seconds walk/run/jog); no network is required.
- Added precise/approximate location and notification permission requests at
  recording start, plus Android location foreground-service declarations.
- Added a visible-start foreground service, persistent session notification,
  pause/resume and finish actions, and Room checkpoint writes pairing each
  accepted point with the updated activity summary in a transaction.
- Recording state and track points are stored locally. Saved history excludes
  in-progress activity rows. A session found after process loss is presented as
  interrupted and requires an explicit resume; elapsed recording time resumes
  from the last durable checkpoint and does not count the process gap.
- Location permission loss marks the activity recoverable. Invalid/outlier
  samples and storage failures do not overwrite earlier committed track data.
- Added core unit cases for state transitions, invalid transitions, sample
  rejection, valid sprint tolerance, and a known Haversine coordinate delta.

## Roadmap mapping and remaining work

| Tasks | Status / evidence |
| --- | --- |
| T001–T002 | State model and injectable `LocationSource` interface implemented. |
| T003–T004 | Fused provider plus platform fallback implemented; no device/GMS compatibility run yet. |
| T005–T009 | Permission flow, location FGS, visible start, and persistent notification implemented. |
| T010–T012 | Notification pause/resume and save actions implemented; in-app finish asks for confirmation. |
| T013–T014 | Provider-unavailable and no-fix states are visible; accepted fix accuracy is shown in the active recording UI. |
| T015–T018 | Sport sampling, validation, jump filtering, segment gap handling, and shared Haversine distance implemented. |
| T019–T026 | Explicit pause time is handled; pace/speed/elevation calculations remain for P11. Auto-pause is intentionally not implemented: P00 says V1 moving time excludes only explicit pauses and prohibits silent auto-pause. Manual pause/resume is implemented. |
| T027–T030 | Room checkpoint and explicit recovery path implemented; permission loss and GPS reacquisition handling are source-covered but need fault-injection/device validation. |
| T031–T034 | Battery measurement, airplane-mode, screen-lock, calls, and multi-app field runs remain device evidence; no emulator is used. |
| T101 | Added a secure XML GPX parser and Handler-based replay `LocationSource`; the synthetic golden GPX fixture is covered by a JVM test. |
| T102 | Play location declaration text/storyboard remains open pending release distribution details. |
| T103 | OEM battery restriction detection/guidance remains open pending device matrix and OEM behavior. |

## Verification and gate

- APK/AAB packaging was not run. Per `AGENTS.md`, only code checks are allowed
  unless the owner explicitly asks for a build.
- Main and Android-test Kotlin compilation passed. Eleven JVM tests passed with no
  failures. Lint and formatting checks passed; lint reports existing warnings
  for older pinned SDK/dependency versions and the API-33 locale attribute.
- This phase is **Hold** until a real-device run confirms continuous screen-off
  capture, airplane-mode recording, pause/resume, process-death recovery,
  notification actions, and battery impact. The roadmap specifically requires
  device evidence for its P07 gate.

## Sequencing note

The owner explicitly asked to proceed with P07 while P06 online authentication
validation remains pending (and while the earlier P05 migration/device gate also
remains pending). This exception is recorded in
[`planning-and-gates.md`](planning-and-gates.md); it does not close those gates.

## Product-rule reconciliation

The P07 roadmap asks for auto-pause (T024), while the accepted P00 calculation
contract explicitly says V1 moving time excludes only user-requested pauses and
must not silently auto-pause. P00 governs implementation, so auto-pause stays
off unless the product owner changes that decision. The P07 filter accepts fixes
with accuracy at most 100 m, requires strictly increasing timestamps, rejects
apparent speed above 15 m/s for walk/run/jog and 45 m/s for cycling, and begins a
new route segment after a gap longer than 30 seconds. These are implementation
thresholds and still need calibration against more synthetic golden tracks and
physical-device runs before release.

Android platform references: [foreground services](https://developer.android.com/develop/background-work/services/foreground-services),
[location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions),
and [location updates](https://developer.android.com/develop/sensors-and-location/location/request-updates).
