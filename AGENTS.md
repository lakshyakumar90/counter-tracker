# Contributor instructions

## Build in small, ordered steps

- Follow the phases and dependencies in `Pace · Build Control Center.html` one step at a time. Finish and review the current step before starting dependent work.
- Keep pull requests and changes focused on one roadmap task or one prerequisite at a time. Update the roadmap only when scope or sequencing actually changes.
- Do not build a later phase on an unresolved gate. Record blockers and decisions in the relevant project documentation instead of silently choosing.
- Before changing a platform or product decision, check the plan and its decision register. Decisions marked Proposed are not approved.
- Treat `docs/product/` as the readable P00 product baseline. If it conflicts with the HTML build control center, flag and reconcile the mismatch before implementing affected behavior.
- P00 defaults marked provisional (capacity, MVP cut line, and launch regions) need review at their stated checkpoints; do not turn them into permanent commitments without review.

## Product constraints

- Android is the first platform. Use Kotlin and Jetpack Compose.
- The core experience is offline-first. Room is the canonical local store; cloud services and AI must not be required for recording or local analytics.
- Treat GPS and fitness data as private. Request permissions only when needed and avoid logging raw location data.
- V1 supports cycling, running, jogging, and walking. Do not add social feeds, leaderboards, subscriptions, swimming, or AI to the core release without an approved scope change.

## Engineering workflow

- Keep UI strings in Android resources and dependencies in the Gradle version catalog.
- Add or update focused tests with behavior changes. Run the relevant unit tests, lint, and formatting checks before calling a task complete when the toolchain is available.
- Prefer code-only checks (`compile`, unit tests, lint, and formatting) after coherent implementation steps. Do not package after every edit. At planned runtime/device checkpoints and phase gates, build the APK/AAB needed for the acceptance work every now and then, and explain before the build why packaging is needed and what runtime behavior it allows us to test.
- When an APK build is part of the planned device verification or the owner asks for one, install the resulting debug APK on the owner's connected, authorized physical Android phone, then run the relevant manual/device checks and report the result. If ADB shows no authorized physical device, do not claim installation or device validation; continue code-only checks and report that APK installation is waiting for the phone. Do not install or create Android emulators or system images.
- Do not build a release AAB unless a roadmap release task or the owner explicitly requests it. Never use emulator/system-image setup as a substitute for a missing physical phone.
- Preserve accessibility, lifecycle safety, and screen-off recording requirements when their phases are reached.
- Document setup changes and decisions in `README.md` or a short architecture decision record.
