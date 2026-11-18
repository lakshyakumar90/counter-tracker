# P08 map provider and storage decision

**Decision date:** 2026-11-18  
**Status:** Selected for the initial implementation; recheck terms before release.

## Provider and renderer

- **Renderer:** MapLibre Native Android, using its offline region support.
- **Light style:** OpenFreeMap Liberty (`https://tiles.openfreemap.org/styles/liberty`).
- **Dark style:** OpenFreeMap Dark (`https://tiles.openfreemap.org/styles/dark`). The provider's style repository documents this endpoint; Dark is still described as an unmodified upstream style, so review the rendered result before release.
- **Cost/account:** the OpenFreeMap public service currently advertises free commercial use with no API key or registration.
- **Visible credit:** keep MapLibre's attribution control visible. The source lists `OpenFreeMap © OpenMapTiles Data from OpenStreetMap` and links the OpenStreetMap copyright page.

OpenFreeMap's current [Terms of Service](https://openfreemap.org/tos/) permit app offline downloads up to 3,000 tiles or 50 MB per download, with no more than four parallel requests and a unique identifying User-Agent. Larger downloads require contacting the provider. The [service FAQ](https://openfreemap.org/) says commercial use is allowed and identifies the public service as free, but also says it has no SLA. The Terms permit discontinuation without notice. Downloaded regions therefore remain local and usable offline, while new downloads and refreshes may become unavailable if the public service changes.

The standard [OpenStreetMap Foundation tile service policy](https://operations.osmfoundation.org/policies/tiles/) is not the offline pack source: it prohibits bulk tile downloads and prefetch. Do not point MapLibre's pack downloader at `tile.openstreetmap.org`.

MapTiler's [Android Offline Pack API](https://docs.maptiler.com/mobile-sdk/android/examples/offline-get-started/) offers another managed option, but its documented packs expire after 30 days and must be refreshed. It would also change the planned MapLibre renderer to the MapTiler SDK or require a separate MapTiler-compatible data license; it is not selected for the first implementation.

## Download and storage limits

P08 begins with these conservative, **provisional** app limits:

- At most 3,000 tiles and an estimated 50 MB for one user-requested region pack, following the stricter interpretation of OpenFreeMap's published limits.
- At most four parallel map resource requests.
- A 150 MB aggregate app-managed offline-map budget across saved regions. This keeps map packs bounded on the owner's low-storage phone; revisit after measuring real region sizes and available device storage.
- Estimate tile count and approximate bytes before download. Stop before starting if either single-pack provider limit would be exceeded. Because actual style, glyph, and sprite sizes vary, surface progress and final bytes rather than presenting the estimate as exact.
- Never delete an existing pack automatically to make room. Explain the budget and let the user delete a named pack. Keep packs in app-private storage and remove their database metadata and files together.

## Privacy and fallback

The initial map opens at a neutral default view and does not request the device location. If the user explicitly enables follow-my-location, the app may show the puck locally; it must not send GPS coordinates to the tile server. Tile requests necessarily reveal the requested tile coordinates and the network address to the public provider. Use a unique app User-Agent and do not include activity identifiers, account data, or route samples in tile requests.

First launch without a saved pack, provider failure, and panning beyond a saved region must show an explicit local fallback. A missing tile or map provider never blocks GPS recording. The map destination stays useful offline with the fallback and whatever local packs remain available.

## Recheck before distribution

Before a public release, re-read the provider terms, verify their offline limits and attribution in the actual Android UI, and decide whether an SLA/self-hosted source is needed. If OpenFreeMap changes or withdraws its public service, keep existing packs usable and replace the source behind the MapLibre provider boundary; do not fall back to the OSM Foundation's standard tile endpoint for bulk downloads.

Style reference: [OpenFreeMap styles repository](https://github.com/hyperknot/openfreemap-styles/).
