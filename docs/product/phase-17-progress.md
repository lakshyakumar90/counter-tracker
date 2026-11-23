# Phase 17 — Backup, sync, and restore progress

## Implemented in source

- Added a versioned gzip activity-track archive with coordinate/sample validation,
  ordering, bounded sizes, and SHA-256 support.
- Added Room schema v9 owner columns for templates, planned sessions, goals,
  personal records, and saved routes; activities were already owner-scoped.
  Existing records retain the `local` owner during migration. Repository reads,
  writes, deletes, analytics, recording, reminders, and local export now use the
  active account owner. Added an instrumentation case for cross-account reads.
- Added account identity to outbox rows and enqueue operations for activities,
  templates, plans, goals, personal records, and saved routes.
- Added a network-constrained WorkManager upload worker with bounded retry
  backoff and credential-safe error codes.
- Added explicit Profile opt-in, future-only or local-data linking choices,
  Wi-Fi-only default/metered-network control, manual sync, queue/error status,
  persisted last-success time, and an app observer that schedules opted-in work.
  The worker rechecks signed-in account ownership and consent before reading the
  outbox. Disabling upload cancels pending WorkManager work without deleting the
  user's local queue.
- Added authenticated PostgREST upsert/tombstone and private Storage upload
  transport, paged metadata reads, bounded private track downloads, and
  idempotent deletion of private activity track objects. Activity and route
  archives now use the Supabase Kotlin SDK's resumable TUS uploader with a
  stable content/path fingerprint and private on-device upload cache so an
  interrupted transfer can continue after WorkManager retries. GPS track bytes
  are stored separately from activity metadata.
- Saved-route geometry now uses the same versioned private archive format under
  an account-scoped `routes/` object path. Restore validates the checksum before
  rebuilding route points.
- Added a Supabase migration for per-user backup metadata tables, RLS policies,
  revision triggers, and private track/backup buckets.

## Gate remains open

The worker does not run automatically until the user opts in. The following
live-service, recovery, and device behavior remains unverified:

- Consent-gated upload and the explicit local-record linking/backfill path are
  implemented in source. Upload remains off by default, and cloud acceptance
  has not been run.
- A Room restore coordinator now pages all six cloud metadata tables, applies
  metadata by last-updated timestamp, validates user ownership, downloads and
  checksum-validates immutable activity track archives, restores child samples
  transactionally, and rebuilds the local analytics cache. The coordinator
  reports progress and preserves newer local rows.
- Profile has a confirmed restore action, row progress, and retry messaging.
  The account marker is saved before import; a different signed-in account is
  gated from the shell until the user signs out or erases local fitness data.
- Restore runs as cancellable unique WorkManager work with connected-network
  constraints, visible row progress, retries, and timestamp-merge idempotency.
  The worker receives only the account UUID and rechecks account ownership at
  execution time; it loads credentials from the app's encrypted auth session,
  never from WorkManager input. Profile now offers a signed-in,
  user-triggered cloud size estimate: metadata bytes are estimated from the
  owner's rows and route archive bytes are exact from lengths saved at upload.
  Older archives without recorded lengths contribute zero until uploaded again.
- Queue, last-success, failure, metered-network, and manual-sync controls are
  implemented. The per-account storage quota is configurable but starts unset;
  the owner must review the Supabase plan and set the byte ceiling before cloud
  backup acceptance.
- A manual Sync now immediately retries failed or delayed operations only for
  the signed-in account. Automatic retryable operations use their persisted
  next-attempt time, and WorkManager schedules a continuation after the current
  batch so retries and batches larger than the per-worker limit do not stall.
  Last-success time only advances after a non-empty batch completes without
  operation failures.
- Supabase migration execution, RLS tests with two real test users, Storage
  policy checks, plan-limit review, database/object backup runbooks, and
  uninstall/reinstall restore evidence.

## Source checks

On 2026-11-19, Android app and instrumentation sources compiled,
`checkFormatting` and `:app:lintDebug` passed, and all 52 JVM tests passed
after adding the durable restore worker, map waypoint editor, and workout
history result codec.
Deno type checks and lint pass for all three Supabase Edge Functions. The SQL
policy test now checks the owner-only backup preview function and archive size
columns. `supabase test db --local` was attempted but could not connect because
no local Postgres is listening at `127.0.0.1:54322`. The linking and
cross-account isolation instrumentation cases have not run because no
authorized physical device is connected. No project credentials were available;
no live migration, RLS, Storage, sync, or restore test has run. P17 remains Hold
pending those external checks and physical-device migration/restore evidence.

## Resumable transfer and retry follow-up (2026-11-19)

Track and saved-route object writes now use the Supabase Kotlin Storage SDK's
resumable TUS uploader. Resume identity is a SHA-256 fingerprint of the exact
account-scoped object path and archive bytes; the SDK's private disk cache
retains resumable state across worker retries. `Sync now` immediately requeues
failed or delayed operations for only the signed-in account. Automatic delayed
retries and remaining batches are scheduled after the current WorkManager
worker finishes. The old whole-object REST upload methods were removed.

The Android app and instrumentation sources compile, 53 JVM tests pass,
formatting and lint pass. A JVM fixture verifies the upload fingerprint is
stable for identical data and changes when the object path or archive changes.
Live Storage RLS, interruption/resume, and process-death transfer behavior
remain unverified until a development project and physical device are available.

## Configurable storage quota follow-up (2026-11-19)

P17-T033 now has a server-side per-account quota setting, seeded `NULL` so the
repository does not assume a Supabase capacity plan. The owner-scoped preview
reports actual private Storage bytes, warns at 80%, and flags usage above a
later-lowered limit. The sync worker checks a path- and size-scoped RPC before
TUS transfer; a Storage RLS helper performs the final atomic cap check,
including concurrent writes, without altering Supabase's managed Storage
schema. Profile shows the used/limit state and a readable quota
failure. The owner must configure `per_user_bytes` after reviewing the target
project. The migration and policy test source are not yet exercised against a
database; live RLS, quota race, UI, and upload checks remain open.

Because account isolation and restore are not yet validated against a live
project, do not turn on automatic upload yet. The migration/client code is a
source-complete foundation, not a validated live backup service.
