# Phase 22 — Operations and growth progress

## Implemented in source

- Added a readable changelog and Profile help/support entry. Its report includes
  app and Android versions only and is shared only after the user chooses to
  share; it does not attach route or activity data.
- Documented triage, privacy-safe support handling, forward-only migrations,
  Android/backend rollback, recovery drills, maintenance, AI budget checks, and
  deferred integrations in `docs/operations/README.md` and
  `supabase/OPERATIONS.md`.
- AI usage is scoped to account-owned request metadata and included in export;
  account deletion cascades server consent and usage. No product analytics or
  crash-reporting SDK is installed.

## Owner and service gates

P22 remains **Hold**. A public support/privacy contact, monitoring provider,
uptime monitor, cloud cost/capacity alerts, OpenAI spend alerts, and retention
schedule require owner selection and configured accounts. Database and object
storage recovery must be rehearsed in staging; documentation alone is not
recovery evidence. Performance/crash trend collection requires a separate
privacy review and approved telemetry design. No vendor, account, alert
recipient, paid service, or retention period was selected by this source work.

Offline graph routing, Wear OS, and Health Connect remain deferred pending a
separate approved roadmap and privacy review.
