# Pace Architecture

## Overview

Pace is an Android-first fitness application built with Kotlin and Jetpack Compose. Its core is offline-first: local Room storage is authoritative for fitness records, while network services provide optional backup and AI features. The app supports walking, running, jogging, and cycling.

## Layers and responsibilities

```text
Compose screens and navigation
          │ user actions / UI state
          ▼
Application and domain services
          │ repository interfaces
          ▼
Repository implementations ───── optional remote adapters
          │                              Supabase / AI gateway
          ▼
Room database, DataStore, files
```

- **Presentation:** Jetpack Compose screens, navigation, reusable components, and app theme. UI observes state and forwards user intent; it should not own persistence or network policy.
- **Application/domain:** activity tracking, distance splits, metrics, planning, route, and recommendation logic. Repository interfaces keep feature logic independent from storage implementations.
- **Data:** Room entities, DAOs, database migrations, and repository implementations store activities and related local data. DataStore holds preferences. Export/import and owner-data deletion are explicit operations.
- **Tracking:** Android location services feed the tracking engine and foreground recording service. Recording remains usable without an account; permissions are requested in context.
- **Maps:** MapLibre renders maps and supports offline region packs. Mapping is isolated behind domain/provider abstractions so map implementation details do not leak into feature logic.
- **Optional services:** Supabase Auth/Storage support account-scoped, opt-in backup. Server-side AI is opt-in and can be disabled independently. Neither is required for core recording or local analytics.

## Key architectural decisions

1. **Local-first source of truth:** Room is canonical; cloud synchronization is an optional copy. This preserves core functionality offline and makes privacy boundaries clearer.
2. **Repository boundaries:** Features depend on domain repository contracts, allowing local persistence and remote adapters to evolve independently.
3. **Explicit consent for network features:** Backup and AI are opt-in. Remote failure must not prevent local tracking, history, or analytics.
4. **Foreground location recording:** Long-running GPS activity uses an Android foreground service and visible notification, with lifecycle and permission handling kept in tracking components.
5. **Compose presentation:** Kotlin/Compose provides declarative UI and resource-backed strings; UI state is derived from application and repository state.
6. **Schema and dependency control:** Room schemas are versioned in source control, and Gradle dependency locking plus CI vulnerability scanning support reproducible dependency resolution.
7. **Release secrets stay external:** signing keystores/passwords and backend secrets are supplied locally or through protected CI secrets, never committed.

## Data flow

During an activity, location updates enter the tracking engine, which filters/aggregates samples and calculates distance and splits. The recording service keeps the session alive in the background. Saving persists the completed activity and route through local repositories into Room. Screens observe repository-backed data for history and analytics. If the owner has enabled backup and authenticated, a separate sync/outbox path can transfer eligible data to the private remote backend; local persistence remains successful independently.

## Build and release

The Android application module uses Gradle Kotlin DSL and a version catalog. `debug` enables development sample data; `release` disables it. Configure `PACE_RELEASE_STORE_FILE`, `PACE_RELEASE_STORE_PASSWORD`, `PACE_RELEASE_KEY_ALIAS`, and `PACE_RELEASE_KEY_PASSWORD` outside source control to sign a release. See [`docs/build-and-signing.md`](docs/build-and-signing.md). Do not commit signing credentials or keystores.

## Related references

- [`DESIGN.md`](DESIGN.md) — visual system and UI guidance.
- [`docs/architecture/0001-core-libraries.md`](docs/architecture/0001-core-libraries.md) — core library decision record.
- [`docs/platform/offline-map-provider.md`](docs/platform/offline-map-provider.md) — offline maps.
- [`docs/build-and-signing.md`](docs/build-and-signing.md) — release signing and artifact workflow.
