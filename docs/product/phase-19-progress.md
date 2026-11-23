# Phase 19 — Reliability and security progress

## Implemented in source

- Android runtime dependencies are locked in `app/gradle.lockfile` for debug,
  Android-test, and release configurations.
- GitHub Actions resolves the graphs and runs the pinned OSV Scanner action for
  known vulnerable dependencies on each workflow run. Gitleaks also scans
  repository history for exposed secrets.
- Deterministic tracking, analytics, planner, route, archive, sync, and policy
  test sources exist. CI runs JVM unit tests and compiles the instrumentation
  sources; it does not claim a physical-device run.
- Profile offers high contrast and reduced motion. Shared numeric transitions
  and map camera movement honor reduced motion. Screen-reader and large-text
  behavior still needs manual assessment.
- Recording and local analytics do not depend on cloud or AI availability.

## Verification and gate

On 2026-11-19, `:app:compileDebugKotlin`,
`:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest` (52 tests),
`checkFormatting`, and `:app:lintDebug` passed. The OSV action has not run in
GitHub Actions because this checkout has no configured remote or CI run.
Supabase migrations and two-user RLS/Storage tests have not run because no
local database or configured development project is available.

P19 remains **Hold**. Roadmap QA-001–QA-064 and the reliability/security matrix
need runtime evidence, including screen-off recording and recovery, battery,
storage pressure, large histories/tracks, performance, TalkBack, 200% text,
permission revocation, offline map packs, live multi-account access, restore,
and supported-device/API coverage. No critical-vulnerability conclusion is
claimed before CI scans the pinned dependency graph.
