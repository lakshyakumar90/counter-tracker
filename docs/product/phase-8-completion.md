# P08: Offline maps and region management

## Completed groundwork

- Selected MapLibre Native Android with OpenFreeMap Liberty for the first map implementation, with no API key or service fee under the currently published public-service terms.
- Recorded attribution, offline download limits, parallel-request limit, unique User-Agent requirement, public-service availability risk, privacy boundaries, and an explicit provider recheck before release.
- Set a provisional 150 MB aggregate map storage budget for this low-storage device, plus 3,000-tile/50 MB per-pack ceilings. Downloads must be estimated, report actual bytes, and never evict a saved map automatically.
- Preserved a local fallback requirement so network tiles never block activity recording.
- Integrated MapLibre Native Android 13.7.1 into the Maps screen with OpenFreeMap Liberty, lifecycle forwarding, a 12-second load fallback, default map gestures/attribution, and no automatic location request.
- Added OpenFreeMap's documented Dark style for the app's dark theme, a retry action for style-load failures, and a locally drawn fallback illustration that is clearly not a geographic map.
- Added localized loading, map error, offline-download availability, and recording-independence copy.
- Updated `AGENTS.md` to call for occasional APK builds at device/runtime checkpoints, explain why packaging is needed, install debug APKs to an authorized physical phone, and avoid all emulator/system-image setup.

See [the provider and storage decision](../platform/offline-map-provider.md).

## Additional source implementation

- Maps now uses MapLibre `OfflineManager`/`OfflineRegion` for app-private saved
  areas. The Maps screen accepts named latitude/longitude bounds, estimates
  tile count and size for zoom levels 10–15, checks free space, and enforces the
  documented 3,000-tile/approximately-50-MB per-area and 150-MB total limits.
- Downloaded bytes, bounds, native pack ID, status, and style version are
  persisted in Room. The user can pause, resume, and confirm deletion; progress
  shows completed tiles, resources, and actual bytes. Existing packs are never
  evicted automatically. Native MapLibre stores the resources in its private
  offline database.
- The app identifies map requests with a Pace Android User-Agent and limits the
  shared MapLibre HTTP client to four in-flight requests. When offline, the map
  tries the cached Liberty style and leaves the non-geographic fallback visible
  when the style is not available. Recording remains independent from tile data.
- Added deterministic boundary/size-policy JVM tests.

The region selector currently uses coordinate fields instead of a drawn map
rectangle. Pack estimates are approximations; actual bytes are displayed and
downloads pause on observed size-limit overrun. Process-death/resume behavior,
provider attribution, offline labels/sprites, corruption handling, and pack
deletion/space recovery still need phone verification. No custom checksum over
MapLibre's native offline database is claimed.

## APK and device checks

An APK is required for native runtime acceptance: verify online and cached offline
map rendering, attribution, gestures, lifecycle, downloads, progress, pause/resume,
storage limits, delete, and recording independence. JVM tests and lint cannot
establish those behaviors. ADB reported no attached/authorized device on
2026-11-18, so APK installation waits for the owner's physical phone; no
emulator is to be installed.

P08's offline-rendering gate remains Hold until an installed region renders with networking disabled, the no-pack fallback is visible, downloads respect the source/storage limits, and recording continues without map data.

## Sequencing note

The owner explicitly asked to continue with P08 while the P07 on-device gate is open. This is a recorded sequencing exception; P07 device evidence remains pending.
