# ADR 0001: Core Library Choices

- **Status:** Accepted as the initial platform baseline; recheck at the implementation phase named below.
- **Date:** 2026-11-17

## Context

Pace is an offline-first Android app. Recording and local analytics must not depend on a network service. Keep dependencies small and centrally versioned, and do not add libraries before the feature phase needs them.

## Decisions

| Concern | Choice | Version/baseline | Use point |
| --- | --- | --- | --- |
| Dependency injection | Constructor injection and explicit composition; no DI framework | No extra dependency | P02/P04; revisit only if object graph complexity warrants it |
| Serialization | kotlinx.serialization JSON and its Kotlin compiler plugin | Runtime `1.9.0`; plugin aligned to Kotlin `2.2.20` | P02 persistence and P17 backup contracts |
| HTTP | Ktor Client with the Android engine | `3.2.3` as the Kotlin `2.2.20`-compatible baseline | P17 cloud backup; recheck compatibility before adding |
| Supabase integration | `supabase-kt` Auth and PostgREST modules; no service-role key in the app | `3.2.3`, paired with Ktor `3.2.3` | P17; authenticate as the user and enforce row-level security |
| Charts | Start with Compose Canvas and an accessible textual/table summary; no chart dependency | Current Compose BOM `2025.12.00` | P11/P12; evaluate a library if chart interaction becomes too complex |
| Local persistence | AndroidX Room remains the canonical local store | Select and compatibility-check the pinned Room version in P02 | P02 |

The Supabase Kotlin quickstart uses the Kotlin client with Ktor. The project's `3.2.3` client release records Kotlin `2.2.20` compatibility. Newer client versions may require newer Kotlin/Ktor versions, so do not upgrade these independently. Before P17, compare the latest stable Supabase client, Ktor, and Kotlin requirements and update them together only if the full build checks pass.

The chart library Vico is not selected for the initial baseline. Its current Compose release is `3.3.1`, and its published metadata depends on Kotlin stdlib `2.4.10`, while this repository pins Kotlin `2.2.20`. Avoid introducing that mismatch; reevaluate after a coordinated Kotlin upgrade if custom charts are insufficient.

No dependency is added by this ADR. The versions above are explicit candidates for their implementation phases, not permission to add unused packages to the starter app.

## Consequences

- The core activity and analytics code remains local and testable without Supabase.
- Authenticated REST access and row-level security need dedicated review before P17; a service-role key must never ship in the client.
- Dependency updates must follow [`dependency-updates.md`](../platform/dependency-updates.md).
- Refresh the version compatibility evidence when each integration phase begins; versions can change before then.

## References

- [Supabase Kotlin quickstart](https://supabase.com/docs/guides/getting-started/quickstarts/kotlin)
- [supabase-kt 3.2.3 release notes](https://github.com/supabase-community/supabase-kt/releases/tag/3.2.3)
- [Kotlin serialization 1.9.0 release notes](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.9.0)
- [Vico Compose setup guide](https://guide.vico.patrykandpatrick.com/getting-started)
- [Vico 3.3.1 release notes](https://github.com/patrykandpatrick/vico/releases/tag/v3.3.1)
