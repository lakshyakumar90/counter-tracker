# Dependency Update Procedure

Dependencies are pinned in `gradle/libs.versions.toml`; the Gradle distribution is pinned in `gradle/wrapper/gradle-wrapper.properties`. Do not use dynamic versions such as `1.+`.

## Update steps

1. Create a focused branch such as `chore/update-compose-bom`.
2. Read the official release notes and compatibility requirements for every version being changed. Check the Android Gradle Plugin/Gradle/JDK matrix, Kotlin plugin and Compose compiler plugin alignment, Compose BOM compatibility, and Android SDK requirements.
3. Update the version catalog and wrapper together when required. Keep `compileSdk`, `targetSdk`, CI SDK packages, and README toolchain notes aligned.
4. Review the dependency graph for new transitive libraries and license or privacy implications. Do not add a dependency just to make a version update easier.
5. Run `:app:assembleDebug`, `:app:assembleRelease`, `:app:testDebugUnitTest`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, and `checkFormatting`.
6. Install the debug APK on a physical phone and run `:app:connectedDebugAndroidTest` when the change affects runtime behavior. Do not install an emulator for this project.
7. Record the versions, compatibility evidence, checks, and any device-only follow-up in the pull request.

Framework and SDK updates should be deliberate changes, not routine unattended version bumps. Recheck Play target API requirements before every release.
