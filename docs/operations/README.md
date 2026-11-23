# Pace operations and support

This document records implementation-independent operational work that needs
an owner-controlled staging or production project. Do not use real GPS data in
support tickets or dashboard examples.

## Monitoring and privacy

- The Android app currently ships without a crash-reporting SDK or product
  analytics. Do not add one until the owner selects a provider, approves its
  privacy/retention terms, and verifies that breadcrumbs, request bodies, user
  email, route geometry, and fitness records are excluded.
- Before a beta, set up privacy-safe crash grouping and uptime checks for Auth,
  the account deletion function, backup PostgREST/Storage, and AI only if enabled.
  Use synthetic accounts and no GPS payloads. Keep alert payloads to service,
  status, time, and request/error identifiers.
- Supabase capacity, database/storage use, egress, and project-pause alerts must
  be enabled in the owner’s dashboard after the plan and budget are confirmed.
  AI provider alerts must use the configured project budget and the same model prices
  as `PACE_AI_*_USD_PER_1M`; the database reservation is a second enforcement
  layer, not a substitute for provider-side billing limits.

## Support and triage

- Profile → Help and support offers offline troubleshooting and prepares a
  report with app/Android version only. The user chooses whether to share it;
  Pace never attaches routes, activity history, screenshots, tokens, or email
  automatically.
- Assign one owner to incoming issues. Record severity, priority, phase/task,
  reproduction steps, device/API, network and permission state, and synthetic
  evidence. Stop release immediately for privacy exposure, data loss, or
  account-isolation failures.
- The public support address and privacy contact are not configured yet. Add
  owner-approved contact details before beta distribution.

## Rollback and recovery

- For a bad Android build, disable optional AI and backup through the server or
  user settings where possible; keep local Room data intact and distribute the
  last known-good signed build through the approved channel. Do not overwrite a
  user database as a rollback shortcut.
- Database migrations are forward-first. Do not ship a destructive rollback;
  create a compatible corrective migration, back up database and objects as one
  recovery point, and rehearse the restore in staging. Follow
  [`supabase/OPERATIONS.md`](../../supabase/OPERATIONS.md).
- Record every incident’s cause, affected versions/accounts (UUID only), safe
  error codes, recovery point, customer action, and follow-up. Never log tokens,
  email, prompts, routes, or health/fitness details.

## Maintenance checklist

- Review Gradle, Kotlin, Android Gradle Plugin, maps SDK, Supabase client, and
  Edge Function dependencies for security updates.
- Run unit tests, lint, formatting, migration upgrade checks, and synthetic
  RLS/Storage ownership tests.
- Confirm the current Android target API and distribution rules before every
  release; review location and account-deletion disclosures.
- Verify AI model availability, current token pricing, server budget, opt-out,
  and kill-switch if AI is enabled.
- Verify database and object backup recovery, project capacity, and support
  coverage before beta or production.

## Deferred product evaluations

Offline graph routing and turn-by-turn guidance remain out of the approved
scope. Wear OS and Health Connect require a separate roadmap and privacy review;
do not start those integrations as part of the current Android V1/V1.1 source
work.
