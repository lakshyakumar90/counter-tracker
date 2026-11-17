# Contributing to Pace

## Branches

Use short, task-focused branches:

- `feature/<task-or-feature>` for product work
- `fix/<issue>` for corrections
- `docs/<topic>` for documentation
- `chore/<topic>` for tooling and dependency maintenance

Keep one roadmap task or one prerequisite per pull request. Use an imperative commit subject that describes the result, for example `Add Android CI workflow`.

## Pull requests

Open a pull request into the repository's default branch. Before requesting review:

- Link the roadmap task or issue and state its acceptance criteria.
- Explain behavior, compatibility, privacy, and dependency changes.
- Include screenshots for visible UI changes and note device/Android versions used.
- Run the relevant Gradle build, JVM tests, lint, and formatting check.
- Build instrumented tests and run them on a physical phone for UI/runtime changes; record the phone and Android version.
- Confirm generated files, signing material, local properties, and secrets are not included.
- Record any check that needs hardware, credentials, or a configured remote and is still pending.

Merge only after CI is green and review feedback is resolved. Do not merge a blocked phase gate silently; document the blocker and its owner.
