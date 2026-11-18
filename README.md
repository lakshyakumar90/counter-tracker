# Pace

Pace is a planned offline-first Android fitness app for cycling, running, jogging, and walking. The roadmap and build control center are in [Pace · Build Control Center.html](./Pace%20%C2%B7%20Build%20Control%20Center.html).

The starter app is Kotlin + Jetpack Compose. The intended architecture keeps activity data on-device first (Room); Supabase backup and optional AI are later capabilities. GPS and fitness data are private by default.

## Product definition

Phase P00 scope, user journeys, terminology, offline and restore contracts, acceptance criteria, privacy baseline, risks, and phase-gate checklist are documented in [`docs/product/`](docs/product/). The P00 task-to-evidence mapping is in [`docs/product/phase-0-completion.md`](docs/product/phase-0-completion.md). Defaults that need product-owner confirmation are called out in those documents.

## Current status

This repository contains the Android foundation, P02 UX specification, P03 Compose component library, P04 local-first shell/onboarding, P05 local data/repository layer, and P06 optional account/authentication foundation. GPS recording, history UI, maps, and analytics are not implemented yet. Follow the roadmap one step at a time and respect each phase gate. Product scope decisions are recorded in the HTML plan; items marked Proposed remain unapproved.

## Product UX and visual system

- [Navigation and screen inventory](docs/ux/navigation-and-screen-inventory.md)
- [User flows](docs/ux/user-flows.md)
- [Visual design system](docs/ux/visual-system.md)
- [Annotated screen specifications](docs/ux/screen-specifications.md)
- [Screen concept board](docs/ux/assets/p02-screen-board.svg)
- [P02 completion and acceptance record](docs/ux/phase-2-completion.md)
- [P03 Compose component foundation and gate status](docs/ux/phase-3-component-foundation.md)
- [P04 shell and onboarding implementation record](docs/product/phase-4-completion.md)
- [P05 local model and repository implementation record](docs/product/phase-5-completion.md)
- [P06 account setup and gate status](docs/product/phase-6-completion.md)
- [Supabase development setup](supabase/README.md)

## Toolchain

- Android Studio with Android SDK Platform 36 and Build Tools 36.0.0
- JDK 17
- Gradle 8.13 (via the checked-in wrapper)
- Android Gradle Plugin 8.13.2
- Kotlin 2.2.20 and the matching Compose compiler plugin
- Min SDK 26; target and compile SDK 36

Versions live in `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`. The application ID `com.pace.app` is provisional; choose a permanent publisher-owned ID before distribution.

## Build and run

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat checkFormatting
.\gradlew.bat :app:installDebug
```

On macOS/Linux:

```sh
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew checkFormatting
./gradlew :app:installDebug
```

The initial screen is a simple Compose welcome screen; no network connection is needed to launch it. For device testing, connect an Android phone with USB debugging enabled and run `.\gradlew.bat :app:installDebug` (Windows) or `./gradlew :app:installDebug` (macOS/Linux). An emulator is not required. Instrumented UI tests also run on that connected phone with `:app:connectedDebugAndroidTest`.

GitHub Actions runs the debug build, assembles the instrumented test APK, and runs JVM unit tests, lint, and formatting checks on pushes and pull requests. CI does not start an emulator; run the instrumented UI test on a connected physical phone when one is available.

## Release signing

Release APK/AAB builds use a private keystore supplied through environment variables. See [`docs/build-and-signing.md`](docs/build-and-signing.md). With no signing credentials configured, Gradle produces unsigned release artifacts.

## Platform workflow

- [Dependency update procedure](docs/platform/dependency-updates.md)
- [Physical-device test routine](docs/platform/physical-device-testing.md)
- [Android API policy and Android 16 review](docs/platform/android-api-policy.md)
- [Localization baseline](docs/platform/localization.md)
- [Secrets and source control](docs/platform/secrets-and-source-control.md)
- [Play Console readiness](docs/platform/play-console-readiness.md)
- [Core library choices ADR](docs/architecture/0001-core-libraries.md)
- [P01 completion and owner-dependent follow-ups](docs/platform/phase-1-completion.md)
- [Contributing and pull request conventions](CONTRIBUTING.md)

## Repository workflow

1. Read `AGENTS.md` and the relevant roadmap phase/task.
2. Work on one task at a time, including its acceptance criteria.
3. Run the checks for the change and record any unavailable toolchain or device checks.
4. Update the roadmap or decision register only when an approved change warrants it.

Git has been initialized locally. No remote or initial commit is configured.
