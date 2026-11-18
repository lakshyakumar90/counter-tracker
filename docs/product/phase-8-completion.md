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

## Remaining work

P08-T001 provider research, the initial T003 online map, and T005's packaged no-tile fallback are implemented. T002 budget is documented provisionally. Light/dark style switching and retry are implemented; T004's offline style-rendering acceptance still depends on offline packs. T006–T028 remain open: location puck/accuracy circle, segmented route drawing, camera controls, visible attribution verification, region selection and size estimation, background download progress/pause/resume, checksum/completeness checks, saved-region listing/deletion/update, and physical tests for offline rendering, storage, and recording independence.

## APK and device checks

An APK is required now for the first native runtime checkpoint: verify that the map surface renders on the target phone, OpenFreeMap style/tiles load, attribution remains visible, map gestures work, lifecycle transitions do not blank/crash, the 12-second fallback appears without network, and recording remains independent of map loading. JVM tests and lint cannot establish those device behaviors. ADB reported no attached/authorized device on 2026-11-18, so APK packaging/installation is deferred until the physical phone is available; no emulator is to be installed.

P08's offline-rendering gate remains Hold until an installed region renders with networking disabled, the no-pack fallback is visible, downloads respect the source/storage limits, and recording continues without map data.

## Sequencing note

The owner explicitly asked to continue with P08 while the P07 on-device gate is open. This is a recorded sequencing exception; P07 device evidence remains pending.
