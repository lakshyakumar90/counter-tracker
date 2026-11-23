# Phase 16 — Local recommendations and forecasts

## Implemented

- Added a deterministic Kotlin recommendation engine separated from Compose.
  It uses saved local activity summaries, recent training load, days since the
  last session, selected sport, training goal, available minutes, and saved
  templates. High recent load or consecutive-day activity suppresses
  hard/interval suggestions. Candidates outside the time limit are omitted.
- Added conservative offline starter options for people without saved workout
  templates, with readable explanations for each suggestion.
- Home exposes the Instant Workout inputs and result cards. An outdoor choice
  writes its template and a same-day planned session to Room, then opens the
  recorder with its sport preselected. A home-workout suggestion opens the
  packaged bodyweight library. Completion linking remains explicit.
- Added a local performance prediction screen for 1K, 5K, 10K, and half
  marathon targets. The standard Riegel formula starts from the best eligible
  saved running/jogging activity near 5K in the last 90 days. Confidence is
  labelled from the number of efforts in the last 30 days; longer-distance
  forecasts include a higher-uncertainty notice.
- Added an informational target-pace estimator and a non-medical, non-guarantee
  disclaimer. Predictions use no network or AI and are linked from Insights.
- Added JVM coverage for the Riegel calculation, invalid-input rejection,
  eligible efforts, confidence levels, conservative fallback, time filtering,
  sport selection, and recovery-load behavior.

## Remaining acceptance

The code checks are complete. Visual and interaction acceptance for the Home
recommendation panel and Predictions screen remains for a physical Android
phone. Forecast estimates are summary-based average efforts near 5K, not
race-grade route segment analysis.

## Checks

`:app:compileDebugKotlin`, `:app:testDebugUnitTest` (37 tests),
`:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`, and `checkFormatting`
passed. No APK or device runtime checks were performed.
