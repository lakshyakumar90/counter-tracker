# Pace

Pace is a planned offline-first Android fitness app for cycling, running, jogging, and walking. The roadmap and build control center are in [Pace · Build Control Center.html](./Pace%20%C2%B7%20Build%20Control%20Center.html).

The starter app is Kotlin + Jetpack Compose. The intended architecture keeps activity data on-device first (Room); Supabase backup and optional AI are later capabilities. GPS and fitness data are private by default.

## Current status

This repository contains the initial Android project foundation. It is not yet a workout recorder. Follow the roadmap one step at a time and respect each phase gate. Product scope decisions are recorded in the HTML plan; items marked Proposed remain unapproved.

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
.\gradlew.bat :app:installDebug
```

On macOS/Linux:

```sh
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:installDebug
```

Open the project directory in Android Studio to use an emulator. The initial screen is a simple Compose welcome screen; no network connection is needed to launch it.

## Repository workflow

1. Read `AGENTS.md` and the relevant roadmap phase/task.
2. Work on one task at a time, including its acceptance criteria.
3. Run the checks for the change and record any unavailable toolchain or device checks.
4. Update the roadmap or decision register only when an approved change warrants it.

Git has been initialized locally. No remote or initial commit is configured.
