# Contributor instructions

## Build in small, ordered steps

- Follow the phases and dependencies in `Pace · Build Control Center.html` one step at a time. Finish and review the current step before starting dependent work.
- Keep pull requests and changes focused on one roadmap task or one prerequisite at a time. Update the roadmap only when scope or sequencing actually changes.
- Do not build a later phase on an unresolved gate. Record blockers and decisions in the relevant project documentation instead of silently choosing.
- Before changing a platform or product decision, check the plan and its decision register. Decisions marked Proposed are not approved.

## Product constraints

- Android is the first platform. Use Kotlin and Jetpack Compose.
- The core experience is offline-first. Room is the canonical local store; cloud services and AI must not be required for recording or local analytics.
- Treat GPS and fitness data as private. Request permissions only when needed and avoid logging raw location data.
- V1 supports cycling, running, jogging, and walking. Do not add social feeds, leaderboards, subscriptions, swimming, or AI to the core release without an approved scope change.

## Engineering workflow

- Keep UI strings in Android resources and dependencies in the Gradle version catalog.
- Add or update focused tests with behavior changes. Run the relevant unit tests, lint, and formatting checks before calling a task complete when the toolchain is available.
- Preserve accessibility, lifecycle safety, and screen-off recording requirements when their phases are reached.
- Document setup changes and decisions in `README.md` or a short architecture decision record.
