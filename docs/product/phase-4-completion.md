# P04 Completion: Shell and Local Onboarding

## Delivered

- A single-activity application root and explicit `PaceAppContainer` composition
  root without a dependency-injection framework.
- Android's launch splash on API 31+ and a themed launch window on older
  versions. Startup performs no network request.
- Four-entry compact bottom navigation and medium/expanded navigation rail for
  Home, Record, Maps, and Insights. The calendar/Plan destination remains
  excluded from default navigation under the P00 V1.1 cut line.
- A local-only first-run flow with skip, preferred activities, metric/imperial
  preference, system/light/dark theme, and optional profile name.
- Settings were initially persisted locally in SharedPreferences. P05 migrates
  these preferences once into DataStore and adds Room for fitness data; these
  are independent of cloud account state.
- Last selected tab persists across process/activity recreation. Back from
  profile/settings returns to the prior tab. No external deep-link filters or
  privileged deep-link routes are registered; unexpected data/action intents
  are reduced to the safe Home entry.
- Local profile and account-offline state; theme, units, preferred activities,
  and profile name are editable from settings.
- App-wide connectivity status based on validated network capability. Offline
  status is informational and never blocks local screens.
- Honest initial empty states for Home and Insights, a Record preflight, and a
  Maps coverage-unavailable state. The UI does not invent activities or show
  unlicensed map tiles.
- Corrupt preference types recover to safe defaults without crashing.

## Roadmap mapping

| Tasks | Evidence/status |
| --- | --- |
| T001–T003 root, launch splash, offline startup | `PaceApplication.kt`, `PaceAppRoot.kt`, `values-v31/styles.xml`; startup requires no network |
| T004–T009 Home, Record, Maps, Plan gate, Insights, profile/settings | `PaceAppRoot.kt`; Plan remains hidden per P00; maps and insights show truthful empty/unavailable content until P08/P11/P12 data/features exist |
| T010–T011 last-tab persistence and back policy | `PaceSettingsRepository` and `PaceShell` |
| T012–T017 onboarding, activities, units, theme, skip, completion | `PaceOnboarding` and persisted `PacePreferences` |
| T018–T020 local profile, offline account, corrupt settings recovery | `PaceProfileScreen`, `PaceSettingsRepository` |
| T021 offline indicator | `rememberPaceConnectivity` and `PaceOfflineBanner` |
| T022 deep-link safety | Launcher-only manifest filter and safe-intent handling in `MainActivity` |

Focused instrumentation coverage is in
`app/src/androidTest/java/com/pace/app/PaceShellOnboardingTest.kt`. It covers
first-run/skip, preference selection and persistence, tab recreation, default
Plan exclusion, corrupt-settings recovery, offline state, and shell destinations.

## Product and phase gates

- P00 keeps Plan/calendar in V1.1 pending the post-P05 cut-line review. P04 does
  not silently move it into V1.
- Map coverage and Insights remain empty/unavailable until their data and
  licensing phases supply real content. No synthetic production totals or
  unlicensed map assets are included.
- P03's physical-device UI tests passed on a Motorola Edge 60 Pro running
  Android 17. A separate manual visual review remains outstanding.
- P04's code checks passed (Kotlin compilation, test-source compilation, unit
  tests, lint, and formatting), and all 11 instrumented UI tests passed on the
  same physical device. The debug app APK remained up to date; only the changed
  Android test APK was repackaged. No emulator or system image is part of this
  workflow.
