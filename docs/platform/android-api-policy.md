# Android API Policy and Android 16 Review

**Reviewed:** 2026-11-17
**Current app baseline:** min SDK 26, compile SDK 36, target SDK 36.

## Play submission baseline

Google Play's published requirement says new apps and updates must target Android 16 (API 36) from August 31, 2026. Pace targets API 36, so its current target meets that published baseline. Recheck the Play policy before each release; P00 decision D23 treats this as a dated baseline, not a permanent target.

Android 17 is API 37. The Android 17 SDK setup guidance documents compile/target SDK 37 and requires AGP 8.9.0-rc01 or higher. The current project uses AGP 8.13.2, but the owner should move compile and target SDK together only after reviewing the final Android 17 behavior changes, library compatibility, and current Play policy. No Android 17 SDK or system image is installed as part of this review.

## Android 16 behavior review for this scaffold

- **Large screens:** apps targeting API 36 must adapt on displays with smallest width of at least 600 dp; orientation, resizability, and aspect-ratio restrictions are ignored. The manifest sets no fixed orientation or aspect-ratio restriction. Keep future screens responsive and test them on large physical displays where available.
- **Background jobs:** Android 16 adjusts JobScheduler/WorkManager quotas. The current app has no scheduled work. Recheck quotas when background sync or reminders are introduced, and do not use a job as a substitute for an active workout recording service.
- **Health sensors:** API 36 uses granular `android.permission.health` permissions for heart-rate sensor access instead of legacy body-sensor permissions. Pace currently requests no health-sensor permission and heart rate is outside the declared V1 scope.
- **Intents and media:** safer intent resolution and per-app `MediaStore` version behavior have no current call sites in this scaffold. Recheck before adding cross-app intents or MediaStore-based import/export.
- **Location:** the app has not implemented location permission or recording yet. Before that phase, review current Android location, foreground-service, background location, notification, and Play declaration requirements together.

## Sources

- [Android 16 behavior changes for all apps](https://developer.android.com/about/versions/16/behavior-changes-all)
- [Android 16 behavior changes for apps targeting API 36](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Set up the Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk)
- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
