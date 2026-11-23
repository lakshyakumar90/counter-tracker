# Backup and recovery runbook

Pace backup is a convenience copy; the Room database remains the source of
truth for recording and local analytics. This runbook must be exercised on a
staging project before enabling backup for real users.

## Preflight

1. Apply migrations in order to a disposable project and run `supabase test db`.
2. Create two disposable verified accounts. With each access token, insert and
   read one synthetic activity and one saved route. Confirm the other account
   cannot select, mutate, or delete those rows or list/download either archive.
   Confirm unauthenticated requests fail.
3. Confirm the two buckets are private, their object limit and allowed MIME
   types match the archive codec, and RLS scopes every object by the first path
   component (the authenticated user's UUID).
4. Review the project plan and choose the per-account private Storage cap. Set
   `public.pace_backup_quota_settings.per_user_bytes` in the SQL editor; the
   migration deliberately leaves it `NULL` until this review. Verify the app's
   preview reports usage and warning state, and that an upload above the cap is
   rejected both by the preflight RPC and Storage RLS. Exercise two concurrent
   uploads near the limit to confirm serialized quota enforcement.
5. Configure a non-production Android build with the project URL and publishable
   key in user-level Gradle properties. Never use a service-role key in Android.
6. Enable backup from Profile on a test device. Exercise Wi-Fi-only and mobile
   data settings, pause/resume, a revoked session, a network interruption, a
   deleted activity, and restore cancellation/retry. Verify local activity
   recording remains usable with the network disabled.

## Database and object recovery

- Before production, the project owner must check the active Supabase plan's
  database backup/PITR window, project pause behavior, Storage durability and
  recovery options, egress, and current included limits. Record the plan name,
  retention, estimated stored bytes, and monthly cost in the release evidence.
- Database and Storage objects are separate recovery units. A database restore
  alone can leave `track_object_path` references without files; a bucket restore
  alone can leave orphaned files. Preserve a timestamped database backup and an
  inventory/copy of both private buckets as one recovery point, then restore
  both to a non-production project and verify archive checksums.
- Do not promise a retention period until the plan and backup process establish
  one. Do not delete object versions or bucket contents as a cleanup shortcut.
- After recovery, run RLS and two-user isolation checks, restore synthetic
  activity and route records into a clean app install, compare row counts and
  hashes, and record the database/object recovery-point timestamps.

## Incident response

If a user reports missing backup data, stop destructive cleanup, keep the
device's Room data, and capture only account UUID, app version, operation ID,
safe HTTP error code, and timestamps. Never request raw GPS archives or print
access tokens. Pause sync if a policy or ownership issue is suspected. Resume
only after the cause is identified and the owner approves the recovery action.
