# P01 Platform Foundation Completion

P01 repository work is implemented and the available local checks pass. The phase gate is **not closed yet** because several acceptance criteria require owner-controlled hardware, publisher identity, Play Console access, or a Git remote. No emulator or system image was installed for this phase.

## Task evidence

| Task | Result | Evidence or remaining action |
| --- | --- | --- |
| P01-T001 Create Git repository | Complete | Local Git repository and Android project; commits recorded in Git history. |
| P01-T002 README setup commands | Complete | `README.md` documents toolchain, builds, tests, phone install, and workflow. |
| P01-T003 Pin Android toolchain | Complete | Wrapper, JDK, AGP, Kotlin/Compose, and SDK versions are recorded. |
| P01-T004 Create Android Studio project | Pending phone check | Debug APK builds. Launch verification must happen on an owner-supplied physical phone. |
| P01-T005 Gradle version catalog | Complete | Dependencies and plugin versions use `gradle/libs.versions.toml`. |
| P01-T006 Debug/release build types | Complete | Debug APK, unsigned release APK, and unsigned release AAB build. |
| P01-T007 Package identifiers | Needs owner choice | Current `com.pace.app` is documented as provisional. Choose a publisher-controlled unique reverse-DNS identifier before distribution. |
| P01-T008 SDK policy | Complete | Min SDK 26 and target/compile SDK 36 are documented; Android 16 review is linked. |
| P01-T009 Kotlin/Compose compiler | Complete | Compose screen compiles in debug and release variants. |
| P01-T010 Lint/static analysis | Complete | `:app:lintDebug` passes locally and is configured in CI. Existing non-fatal SDK/library and scaffold warnings remain in the lint report. |
| P01-T011 Formatting rules | Complete | `checkFormatting` passes. |
| P01-T012 Unit test runner | Complete | `:app:testDebugUnitTest` passes. |
| P01-T013 Instrumented test runner | Pending phone check | Test APK assembles. Run `:app:connectedDebugAndroidTest` on a connected physical phone to verify the UI assertions. |
| P01-T014 GitHub Actions | Pending remote | Workflow is configured for push and pull request. No Git remote is configured, so GitHub has not run it. |
| P01-T015 Signing secrets strategy | Complete | Signing reads environment variables; secrets and keystores are ignored and not stored in source. |
| P01-T016 Ignore/secrets scanning | Configured | `.gitignore` and Gitleaks CI scan cover generated outputs and repository history. GitHub scan awaits a push; organization-owned repositories must configure the Gitleaks license secret. |
| P01-T017 Build information screen | Complete | App shows version name/code and build variant. |
| P01-T018 Sample-data developer switch | Complete | Debug-only switch displays fixed deterministic run and ride examples; release builds disable it. |
| P01-T019 Dependency update procedure | Complete | `docs/platform/dependency-updates.md` records compatibility and verification steps. |
| P01-T020 Branch/PR conventions | Complete | `CONTRIBUTING.md` and the pull request template define review expectations. |
| P01-T101 Physical devices/field routine | Pending owner devices | Two physical phones and a weekly outdoor test log are required. The device matrix and privacy-safe log template are ready in `physical-device-testing.md`. |
| P01-T102 API 36/Android 16 review | Complete | Target SDK 36 is set; Android 16 behavior and API 37 follow-up are recorded in `android-api-policy.md`. |
| P01-T103 Strings/locales | Complete | UI text is in resources, lint checks translation coverage, and `en-IN` is configured provisionally. |
| P01-T104 Core library ADR | Complete | `docs/architecture/0001-core-libraries.md` records choices and version compatibility gates. |
| P01-T105 Play Console account | Pending owner access | Follow the checklist in `play-console-readiness.md`; account verification and account type cannot be recorded without owner access. |

## Local verification

The following combined Gradle invocation succeeded:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug checkFormatting
```

The instrumented UI test was compiled and packaged, but not run because `adb devices -l` showed no attached device. `git diff --check` is also required before committing.
