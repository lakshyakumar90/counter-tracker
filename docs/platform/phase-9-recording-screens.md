# Phase 9: Activity recording screens and controls

## Implementation status

The Phase 9 recording flow is implemented in code. The phase gate remains open until it is exercised on the owner's physical phone. Phase 9 was started at the owner's explicit request while the preceding Phase 8 map gate was still awaiting device review; this records the sequencing exception rather than treating Phase 8 as passed.

## Delivered

- A recording landing and preflight flow for cycling, running, jogging, and walking. The last used sport is stored in DataStore; location and notification permissions are requested only when recording starts.
- A live recording screen with elapsed time, distance, running pace or cycling speed, elevation, accuracy, GPS-loss feedback, route and current-position display, follow/recenter, and map/metrics ordering controls. Recorded route segments are drawn on MapLibre when online and on the local map illustration when map tiles are unavailable.
- Large pause/resume, finish-confirmation, and discard-confirmation actions. An interrupted recording is surfaced for explicit resume, finish, or discard. Activity data and title changes remain local.
- A finish summary with distance, duration, primary metric, route fit, split list, editable activity type/title, and return-to-dashboard action.
- Completed 1 km splits are calculated from saved GPS points, shown live as they complete, and persisted atomically with the finalized activity. Segment gaps on resume are excluded. Incomplete final distance is not presented as a completed split.
- Recording-screen state that survives Compose recreation and reads active work from Room, including orientation changes.

## Code checks

- `:app:compileDebugKotlin` passed on the final source state.
- `:app:lintDebug` passed on the final source state (29 warnings, 0 errors).
- `git diff --check` passed after the final implementation changes.
- No APK was built. `adb devices -l` showed no connected physical device; emulator or system-image setup is prohibited for this project.

## Physical-device gate still required

On the owner's phone, verify each supported sport, denied and approximate location permission, GPS acquisition and signal loss, screen-off recording, rotation, map/metrics switching, live and saved splits, pause/resume, finish confirmation, discard, interrupted-session recovery, offline recording, and summary metadata edits. Check one-handed reach, talkback labels, and reduced-motion behavior. Build and install a debug APK only for this device checkpoint, then record the device/Android version and non-sensitive results in `physical-device-testing.md`.

## Known scope boundaries

- Split summaries currently use completed 1 km intervals. Custom split distance and a partial final split are not part of this screen implementation.
- The preflight reports that cycling speed falls back to GPS when no sensor is connected; external sensor pairing and target zones are separate work.
- GPS background reliability, battery use, permission changes, and recovery after force-stop/reboot cannot be signed off by compilation or lint.
