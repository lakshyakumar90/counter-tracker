# Phase 13 — Saved routes implementation progress (2026-11-18)

P13 was started at the owner's direction while P10 device acceptance remains
open. It remains a V1.1 feature under the P00 cut line; the Maps entry point is
debug-only and this code does not change the V1 release decision.

## Implemented in this step

- Added normalized `saved_routes` and ordered `saved_route_points` Room entities,
  repository APIs, transactional save/delete operations, and the v4→v5 migration.
- Added a debug preview screen from Maps: saved route list/details, manual
  coordinate waypoints with point editing, saving a recorded activity as a
  separate route, metadata editing, and confirmed route-only deletion.
- Added bounded GPX import with secure SAX parsing, 15 MB and 100,000-point
  limits, validation, preview, and error reporting. Added GPX document export
  and explicit share-sheet export using a read-only FileProvider URI.
- Added offline route preview and follow mode. Location permission is requested
  only when the user starts following. Current location is compared against
  route segments locally; a 50 m deviation threshold shows an off-route warning.
  The follow view includes an explicit finish summary. It provides no
  turn-by-turn engine or remote map-tile request.
- Added GPX codec and route-distance JVM tests, plus Room persistence and
  migration instrumentation source coverage.

## Code checks

- `:app:compileDebugKotlin` — passed.
- `:app:testDebugUnitTest` — passed (30 tests, including GPX codec and route deviation coverage).
- `:app:compileDebugAndroidTestKotlin` — passed; Room runtime instrumentation has not been executed.
- `:app:lintDebug` — passed with warnings only.
- APK packaging, USB debugging, and device testing were not performed.

## Remaining phase work and gates

Automatic turn-by-turn routing remains explicitly excluded by D06. Manual route
creation and waypoint editing accept ordered coordinate text and direct map
taps, with add-last, undo-last, clear-all, and a bounded 100,000 point limit.
The coordinate editor remains usable without map interaction or location
permission. P13 still needs runtime acceptance of
document-provider import/export, route following, permission denial/recovery,
off-route warnings, and route preview/edit/delete.
The owner prohibited APK/device checks. P10 and P12 acceptance gates also
remain open; this progress note does not pass P13 or change its V1.1 release
classification.

## Map waypoint follow-up (2026-11-19)

The route editor now accepts taps from the MapLibre map and appends ordered
latitude/longitude points. Users can still edit coordinates in a text field,
remove the last point, or clear the list. The map flow does not request GPS
permission. The shared parser rejects malformed and out-of-range values and
enforces the GPX point limit; focused JVM tests cover these cases. Map tap
behavior still requires physical-phone acceptance.

The 2026-11-19 follow-up verification passed `:app:compileDebugKotlin`,
`:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest` (52 tests),
`checkFormatting`, and `:app:lintDebug`. The map-tap interaction itself remains
unverified on a physical device.
