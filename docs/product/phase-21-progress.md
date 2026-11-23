# Phase 21 — Optional AI progress

## Implemented in source

- Added a vendor-neutral Android gateway client and authenticated Supabase Edge
  Functions for account consent and AI generation. OpenRouter is the preferred
  route for free models; Gemini is the direct provider fallback. Provider keys,
  model order, quota, budget, and pricing values stay in server secrets.
- Server-side opt-in is required in `pace_ai_preferences`; unauthenticated,
  expired-session, unconfigured, and non-opted-in requests are rejected.
- The app sends only a 28-day aggregate: activity count, distance, moving time,
  sport categories, and a fixed focus. GPS coordinates, routes, titles, notes,
  sensors, and email are not included.
- The AI card shows the 28-day source window and the local activity count,
  distance, and moving-time totals beside generated output.
- AI insight and workout responses use strict JSON Schemas, size/range checks,
  explicit refusal/incomplete-response handling, timeouts, and conservative
  training language. AI generated workouts are shown for review, editable, and
  saved locally only after user action.
- Successful output is cached per account in DataStore for offline viewing;
  opting out clears the local cache and blocks new app requests. Server usage
  logs record only kind, token counts, approximate cost, status, and timestamps.
- Export includes the local AI cache and fetches up to 1,000 account-owned AI
  usage rows when online. Authenticated account deletion cascades server usage
  and consent records.
- Added a fail-closed server feature switch in `pace_ai_control`; it is seeded
  disabled and is checked for each generation request. Operators can disable
  generation without affecting local recording or recommendations.
- AI reservations enforce per-user daily and monthly request limits, a separate
  global monthly request ceiling, and a serialized developer-wide dollar
  budget before a provider call is made. The SQL policy check verifies the
  service-only reservation signature and the server-only feature switch;
  executing the quota logic awaits a local or hosted database.
- System prompts are isolated in a versioned server module; the prompt version
  is stored with private usage metadata and included in user data export.
- Added three provider-free synthetic evaluation fixtures for novice return to
  running, intermediate distance training, and recovery-focused walking. The
  fixtures exercise only the source/output policy and do not claim live model
  quality acceptance.
- Added a credential-gated model comparison command that runs the same synthetic
  cases through the production provider adapter for insight and workout
  outputs. It reports schema/policy pass rate, latency, token use, and cost for
  each model, logs no generated fitness text, requires current per-model
  pricing and an explicit maximum evaluation cost, and reserves a conservative
  per-request token budget before sending. Provider-backed model comparisons
  have not run yet. Synthetic-only live smoke requests reached both OpenRouter
  and Gemini and passed schema/policy validation; an end-to-end request also
  completed through Gemini fallback. Free provider availability has varied
  during later probes.
- Evaluated on-device GenAI for P21-T101. Android's current ML Kit GenAI APIs
  run Gemini Nano through AICore, but availability depends on supported device
  hardware, Android/AICore service state, and model readiness; Android describes
  on-device models as requiring more premium devices. Decision: do not add a
  second generative runtime to this release. The app's deterministic local
  recommendations remain available to every supported device offline, while
  optional generated insights use the vendor-neutral, opt-in server gateway.
  Revisit an on-device adapter if the supported-device matrix and a concrete
  offline AI use case justify the APK/runtime cost. References:
  [Gemini Nano and AICore](https://developer.android.com/ai/gemini-nano) and
  [Android AI solution guidance](https://developer.android.com/ai/overview).
- Extracted the AI request/result policy validators and added Deno tests for
  aggregate-field minimization, prompt-injection-shaped extra fields, input
  bounds, unsupported sports, unsupported numerical claims, oversized claims,
  and workout consistency.

## Configuration and acceptance still open

- On 2026-11-22, the Android publishable key was accepted by Supabase. The
  deployed gateway passed authenticated, opted-in synthetic insight and workout
  requests using Gemini. An authenticated request returned `AI_DISABLED` with
  the server switch off. The switch was re-enabled after the drill, and the
  synthetic account was deleted.
- OpenRouter's free router reached its generation limit without returning a
  valid Pace response in a direct probe. The deployed gateway is currently
  configured with an empty `PACE_AI_OPENROUTER_MODELS` list so it goes directly
  to Gemini. Groq is pending provider integration.

- Source checks run: Deno type-check and lint for all Edge Functions and policy
  tests, plus the policy unit tests. Supabase migrations and RLS behavior still
  cannot be executed because no local Postgres service or configured project is
  available.
- OpenRouter and Gemini keys are configured as Supabase function secrets. The
  hosted Gemini configuration retains the existing operator assertion that it
  uses the free tier. The account's billing tier was not independently verified.
- Review current model eligibility, provider retention and pricing, privacy
  disclosures, broader semantic hallucination fixtures/evaluation cases,
  quota and cost enforcement against a live database, and a remote kill-switch
  drill in staging before release. The T101 architecture evaluation is
  documented; actual hardware coverage for any future on-device adapter is not
  part of this release decision.
- Only synthetic summaries were sent during provider smoke checks.
  On-device acceptance remains open.
- ChatGPT sign-in and plan-sharing is not implemented. OpenAI documents a
  participating-app flow; it requires app participation and an approved client
  setup. Revisit only after that access is available.
