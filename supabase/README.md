# Supabase development setup

Pace supports an optional account. Local recording, settings, and the Room
database continue to work without a Supabase project or internet access. P06
creates only the account and its profile row; it does not upload fitness data.
Backup starts for account-owned records when a user signs in. Activity recorded while signed out stays separate until the user explicitly links it. The migration `20261118010000_private_backup_sync.sql`
defines private metadata tables, user-scoped row-level policies, and private
Storage buckets. The Android client now has explicit consent for linking signed-out records,
account-scoped upload queues, immutable activity and saved-route archives,
tombstones, resumable TUS Storage transfers, retry controls, and a cancellable
restore path. A source build does not validate live project policies, plan
limits, auth redirects, or Storage.

Large activity and route archives use the Supabase Kotlin SDK's resumable
Storage uploader. Its local upload cache preserves resumable-upload state across
worker retries, and objects use the same account-scoped archive path for
idempotent retries. See the [official resumable upload guide](https://supabase.com/docs/guides/storage/uploads/resumable-uploads).

## Create a development project

1. Create a Supabase development project in the Supabase dashboard.
2. In **Authentication → URL Configuration**, allow the callback URL
   `pace://auth-callback`.
   Pace requests this exact URL for new confirmation and recovery emails.
   Supabase falls back to the project's Site URL when it rejects a destination.
   Use the deployed site as the production Site URL and keep the callback in
   the redirect allowlist. After changing the settings, request a fresh email.
3. In **Authentication → Providers → Email**, enable email signup and
   verification. Configure password recovery to return to the same callback.
4. Apply `migrations/20261118000000_profiles.sql`, then
   `migrations/20261118010000_private_backup_sync.sql`,
   `migrations/20261118020000_ai_gateway.sql`,
   `migrations/20261119010000_backup_preview.sql`,
   `migrations/20261119020000_ai_kill_switch.sql`, and
   `migrations/20261119030000_backup_quota.sql` in timestamp order in the
   project's SQL editor.
5. Copy the project URL and publishable key from the dashboard's Connect panel.
   These are client identifiers, not service-role credentials. Never put a
   service-role or secret key in the Android app.
6. Put these Gradle properties in the user's `%USERPROFILE%\.gradle\gradle.properties`
   (or the equivalent user-level Gradle properties file):

   ```properties
   PACE_SUPABASE_URL=https://<project-ref>.supabase.co
   PACE_SUPABASE_PUBLISHABLE_KEY=<publishable-key>
   ```

7. Run the app's normal code checks. An app built without both properties stays
   in local-only mode and displays that account setup is unavailable.

## Backup migration review

- Apply migrations in timestamp order to a disposable development project first.
- Use two disposable accounts. Verify each account can read and write only its
  own rows in all six `pace_*` data tables and only objects beneath its own
  user-ID folder in both private buckets. Verify anonymous requests fail.
- Keep the service-role key only in trusted server-side deployment secrets. It
  is never a client key and must not be added to Gradle properties or the APK.
- Upload defaults off. The user can enable it in Profile, choose whether to link
  pre-existing local-only records, select Wi-Fi-only or metered transfer, and
  request Sync now. Disabling uploads leaves pending work on-device. Restored
  data stays bound to its restoring account until local fitness data is erased.
- The worker checks the signed-in UUID and persisted opt-in owner before reading
  the outbox. Room remains the source of truth. Track and saved-route archives
  are immutable, compressed, checksummed, and size-bounded. Live collaborative
  editing is out of scope under the accepted backup-first policy.
- Supabase plan limits, project pausing behavior, point-in-time database
  backups, object-storage backup/recovery, and expected monthly cost must be
  checked in the target project before production use. This cannot be verified
  from source code.
- `pace_backup_quota_settings.per_user_bytes` is deliberately seeded as `NULL`.
  After checking the selected plan and capacity policy, set a positive byte
  limit in the dashboard SQL editor, for example:

  ```sql
  update public.pace_backup_quota_settings
  set per_user_bytes = 52428800, updated_at = now()
  where singleton = true;
  ```

  The example is 50 MiB and is illustrative, not an approved Pace limit. The
  table is inaccessible to app roles. The owner-scoped preview reports actual
  private-object bytes and warns at 80%; an authenticated preflight explains
  an oversized upload, and Storage RLS policies enforce the configured limit
  atomically, including concurrent uploads. With the limit unset, app-level
  per-account enforcement is off; Supabase's per-object limit still applies.
  This uses Storage's supported RLS path and leaves the managed `storage`
  schema unmodified, following Supabase's [Storage schema guidance](https://supabase.com/docs/guides/storage/schema/design).

## Local track archive

`ActivityTrackArchiveCodec` writes gzip-compressed binary archives with a
versioned header, stable sequence ordering, summary split data, sensor samples,
bounded row/size checks, and SHA-256 support. Track bytes contain private
coordinates. Activity objects use
`<auth-user-id>/<activity-id>.pace.gz`; saved route objects use
`<auth-user-id>/routes/<route-id>.pace.gz`. Never make these buckets public or
log object bytes/coordinates.

## Operations

See [backup and recovery runbook](OPERATIONS.md) for staging checks, project
backup limitations, and the restore drill. Production use is blocked until the
project owner verifies actual database and Storage recovery capabilities.

Do not commit project keys, access tokens, or account data. Create a distinct
production project only when production setup is explicitly scheduled. Before
the P06 gate can close, verify signup, email confirmation, sign-in, password
recovery, session refresh, and profile RLS with two separate test accounts.

## Server functions and secrets

Deploy the functions in `functions/` from a non-production project. The
`delete-account`, `ai-consent`, and `ai-insights` functions disable the gateway
JWT check only to allow browser preflight; each function verifies the bearer
token with Supabase Auth before acting. `SUPABASE_SERVICE_ROLE_KEY` is read only
in the function runtime. Never put it in Android, web HTML, Gradle properties,
CI logs, or exported configuration.

AI remains unavailable until its owner opts in on the device and the server is
configured with `OPENROUTER_API_KEY`, `PACE_AI_OPENROUTER_MODELS`,
`GEMINI_API_KEY`, `PACE_AI_GEMINI_MODEL`, and the explicit
`PACE_AI_GEMINI_FREE_TIER_ONLY=true` confirmation, `PACE_AI_MONTHLY_BUDGET_USD`, and
`PACE_AI_MONTHLY_REQUEST_LIMIT`, and `PACE_AI_USER_MONTHLY_REQUEST_LIMIT`.
`PACE_AI_DAILY_REQUEST_LIMIT` defaults to 3 and accepts 1–10. The global monthly
request cap and budget are separate from the per-user daily and monthly request
caps. The default route is OpenRouter's `openrouter/free` router, which selects
a compatible free model. Set up to two OpenRouter models, each either
`openrouter/free` or a model ID ending in `:free`; they are tried in order, then
the configured Gemini model is attempted. The Gemini API project must have
free-tier-only billing if the owner wants a hard zero-cost ceiling; provider
account billing settings are outside the app's quota enforcement, and the
required `PACE_AI_GEMINI_FREE_TIER_ONLY=true` is an operator assertion rather
than a billing-tier API check. Verify the Google AI Studio project remains on
its Free tier. Server-side
per-token prices default to zero for this free-model configuration and accept
zero. AI sends only a validated aggregate summary and requests JSON Schema
output from OpenRouter or Gemini; all parsed responses pass the same local
schema and policy validation. A server prompt version is included in usage
metadata and export. Provider keys and model IDs never ship in the APK.

### Compare model configurations with synthetic cases

The credential-gated evaluation command is `functions/ai-insights/evaluate.ts`.
It reuses the production provider adapter, strict response schemas, prompt, and
result validators. It uses synthetic fixtures only. To run it locally after
reviewing the current model IDs and prices, set these in a secure local
environment, never in source control:

- `OPENROUTER_API_KEY` or `GEMINI_API_KEY`
- `PACE_AI_EVAL_MODELS`: comma-separated list of 2–6 model IDs
- `PACE_AI_EVAL_PRICING_JSON`: JSON object keyed by model ID, with
  `inputUsdPer1M` and `outputUsdPer1M` prices for each model
- `PACE_AI_EVAL_MAX_COST_USD`: positive maximum reservation for the whole run

Set `PACE_AI_EVAL_PROVIDER` to `openrouter` or `gemini` and use only free model
IDs if the price rows are all zero. Then run:

```powershell
npx --yes deno run --allow-env=OPENROUTER_API_KEY,GEMINI_API_KEY,PACE_AI_EVAL_PROVIDER,PACE_AI_EVAL_MODELS,PACE_AI_EVAL_PRICING_JSON,PACE_AI_EVAL_MAX_COST_USD --allow-net=openrouter.ai,generativelanguage.googleapis.com supabase/functions/ai-insights/evaluate.ts
```

The command makes at most 36 requests (six cases across up to six models),
sequentially. Before each request it reserves a conservative estimate of
10,000 input tokens and 900 output tokens against the configured ceiling. The
report contains per-case schema/policy status, latency, token counts, and cost,
but not generated response text. The ceiling depends on supplied prices being
current; it is a staging evaluation tool, not a provider billing limit. Provider
keys, free-tier eligibility, and project billing status must be reviewed before
running it.

The `pace_ai_control` row starts disabled. After staging privacy, policy, budget,
and safety acceptance, an operator can enable it by setting its singleton row's
`enabled` value to true; setting it false disables AI immediately without
affecting recording or local recommendations. Missing control data or a query
error fails closed. Do not enable it in production until the kill-switch drill
has been recorded.

The external account deletion page is a source template at
`docs/privacy/delete-account.html`. Before hosting it, replace the project URL
and publishable key placeholders, deploy the `delete-account` Edge Function,
and use its final HTTPS URL in the app's public privacy disclosures. The page
contains no service-role secret.
