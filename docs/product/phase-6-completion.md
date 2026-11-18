# P06: Supabase bootstrap and authentication

## Implemented

- Added the `supabase-kt` Auth client (`3.2.3`) with Ktor Android engine
  (`3.2.3`), aligned to the repository's Kotlin `2.2.20` baseline.
- Account configuration comes from user-level Gradle properties. If either
  setting is absent, Pace remains in local-only mode and makes no auth requests.
  No project key or token is checked into the repository.
- Added email sign-in, registration, verification messaging, password-reset
  email, reset callback handling, authenticated password update, sign-out, and
  visible session/reauthentication status.
- Session JSON is encrypted with AES-GCM using an Android Keystore key. A bad
  or unreadable stored session is discarded and returns the user to signed-out
  state; it does not clear the Room database or local preferences.
- Added the `pace://auth-callback` PKCE redirect and documented the Supabase
  dashboard setup in [`supabase/README.md`](../../supabase/README.md).
- Added a versioned SQL migration for the account profile table, account-create
  trigger, and row-level policies that constrain profile reads and updates to
  `auth.uid()`. Anonymous access is revoked.
- Signing in does not upload activities, GPS tracks, settings, or workouts.
  Local data stays usable while offline and after sign-out; backup consent and
  sync belong to P17.
- Added unit coverage for email/password validation and local-only behavior,
  plus instrumentation coverage for encrypted session save/reload/delete.

## Roadmap mapping

| Tasks | Evidence |
| --- | --- |
| T001–T003 | Development setup guide and committed SQL migration; no remote project is provisioned |
| T004–T005 | `supabase/migrations/20261118000000_profiles.sql` |
| T006–T010 | Profile account card, Supabase Auth email flows, and PKCE callback |
| T011–T012 | Sign-out flow and `AndroidEncryptedSessionManager` |
| T013, T016 | Account remains optional; local profile and Room data remain device-local/offline |
| T017–T018 | SDK auto-refresh with explicit reauthentication state and safe generic error copy |
| T019 | Profile account status card |
| T021 | Auth input unit tests and secure-session instrumentation test source |

Google sign-in (T015) and a biometric local app lock (T020) remain optional and
are not enabled without provider credentials and a separate lock policy. The
P06 cloud account is not a local database partition; data upload and multi-device
merge are intentionally left to the later backup/sync phase.

## Verification and remaining gate evidence

- Kotlin main-source compilation passed against the pinned Supabase client.
- The unit and Android instrumentation test sources cover validation, local-only
  behavior, and encrypted session storage. Five JVM unit tests passed, and both
  app and Android-test Kotlin sources compiled. Lint and formatting checks passed.
- Instrumentation tests have not been run as a P06 device suite.
- No P06 APK/AAB was built. Per `AGENTS.md`, instrumentation execution would
  package app and test APKs; the owner has asked for code checks without APK
  builds unless explicitly requested.
- No Supabase development project credentials were configured. The SQL has not
  been applied remotely, and account confirmation, password recovery, refresh,
  multi-device sign-in, and two-user RLS isolation have not been verified online.
- P06's Go gate remains pending until those online checks pass. P05's separate
  physical-device migration gate also remains open.

## Sequencing note

On 2026-11-18 the owner explicitly directed work to proceed to P06 while the
P05 device gate was open. That exception is recorded in
[`planning-and-gates.md`](planning-and-gates.md); it does not close or waive the
P05 evidence requirement.

The HTML roadmap assigns P06 57 points across 21 tasks, about 14.25 focused
weeks at P00's provisional 4 accepted points/week. This is an estimate, not
measured velocity. Remote project setup and online validation are still outside
the verified implementation evidence.
