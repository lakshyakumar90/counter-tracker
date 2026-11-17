# P00 Offline, Privacy, Permissions, and Recovery Contracts

## Offline-first contract

The core app must remain usable with networking disabled. Local Room data is canonical until a user explicitly enables cloud backup. A network outage never prevents recording, pause/resume, finish, local save, history/detail, local analytics, settings, or user data export.

| Capability | Offline behavior |
| --- | --- |
| First launch/onboarding | Local-only path is available; account creation is optional. |
| Recording | Location service records locally; no network call is on the recording critical path. |
| Maps | Show an installed region or packaged low-detail fallback; identify unavailable areas and never block recording. |
| History/details | Read local records and track samples. |
| Analytics | Compute locally from local records; distinguish missing/insufficient data. |
| Plans/settings | Features included in release scope persist locally. |
| Export/delete | Local export and local deletion work without network. Cloud deletion may remain pending and visible. |
| Sign-in/backup/restore | Require connectivity only for those explicit cloud actions. Queue eligible backup work and expose status. |
| AI | Optional future service only; show unavailable offline and preserve local app behavior. |

## Cloud backup and restore contract

- Cloud is an optional backup/restore destination, not the primary write path.
- A new local user can remain unsigned indefinitely. Sign-in is requested only when the user invokes backup/restore or another approved cloud capability.
- Outdoor tracks are immutable after save in the initial backup-first model. Editable summary metadata may be updated; deletes use explicit tombstones so they can propagate.
- Uploads are queued durably, retried idempotently, and visible as pending/synced/failed with last successful sync time. No duplicate activity is created by retry.
- Restore requires an explicit user action and preview of account/backup timestamp. It must not silently wipe local data. Merge records by stable identity; surface conflicts and partial restore failures.
- A restore includes relational data and private track objects. A database-only backup is insufficient. Export remains available as user-controlled recovery.
- Account deletion explains what is removed locally and remotely, confirms destructive action, and records pending remote deletion until acknowledged. Provide a web deletion path before Play distribution if required by then-current policy.
- Retention, deletion propagation, account recovery, and backup retention periods require an approved operational policy before cloud launch.

## Permission strategy

- Ask only when the user starts a capability that needs the permission; precede the system prompt with a concise explanation tied to that action.
- Request foreground location for active outdoor recording. Request background location only if the final Android behavior and supported OS versions require it for screen-off recording, and explain the value before prompting.
- Do not request contacts, photos, microphone, or unrelated permissions for the V1 core.
- Handle denied, approximate-only, revoked, and “don’t ask again” states with a useful explanation and a route to Settings where appropriate. Never repeatedly prompt or block non-location features.
- Notifications/foreground-service disclosures are shown before recording as required by the supported Android versions. Stop active location work when the user finishes or cancels.
- Re-evaluate exact permission declarations against the target SDK and Play policy immediately before release.

## Data sensitivity and handling baseline

| Data | Classification | Default handling |
| --- | --- | --- |
| GPS samples, route geometry, home/work inferences | Highly sensitive personal/location data | Private, local-first, least access, no public sharing by default, never write raw coordinates to routine logs. |
| Activity summaries, pace, goals, plans | Sensitive fitness data | Private to the user; minimize cloud and diagnostic copies. |
| Account identifiers/tokens | Secret/authentication data | Secure platform storage; never log tokens; enforce server-side user isolation. |
| Diagnostics | Operational data | Minimize, redact identifiers, avoid precise coordinates and activity titles. Optional collection must be disclosed and controllable. |
| AI request content | External disclosure | Explicit opt-in, minimum payload, server-mediated, explain retention/use before first request. |

### Required controls

- Local data is scoped to the app sandbox; use Android backup/data-extraction rules deliberately so private tracks are not copied to an unintended backup destination.
- Protect cloud records and objects with per-user authorization and private storage. Use HTTPS and secure token storage.
- Define access, export, correction, deletion, retention, and incident response before cloud release.
- Avoid precise GPS in analytics/telemetry/crash breadcrumbs. Scrub request bodies and user-entered text from crash reports.
- Threat model and data inventory are in `threat-model.md`; implementation tasks must trace back to them.

## Threat model summary

| Threat | Impact | Minimum mitigation / acceptance evidence |
| --- | --- | --- |
| Lost or stolen unlocked/locked phone | Exposure of routes and routines | App-sandbox storage; device credential protections; no sensitive lock-screen content; evaluate local DB encryption against threat and performance; document residual risk. |
| Shared device or account switch | One person sees another’s records | Explicit profile/account state; sign-out clears credentials and isolates local records; tests cover account switching and local-only data. |
| Cloud account/object breach or authorization bug | Bulk disclosure of routes | Per-user row/object policies, private buckets, least privilege, security tests for cross-user reads/writes, backup access controls. |
| Compromised/misconfigured backup | Persistent disclosure or loss | Separate database and object backup policy, access audit, restore drills, retention and deletion verification. |
| Malicious/curious diagnostic tooling | Coordinates escape in reports/logs | Redaction by default; test logs/crash payloads with fixture coordinates; no raw GPS in routine telemetry. |
| AI data egress | User’s activity/location reaches third party | Off by default; explicit opt-in and payload preview; strip exact route unless essential; server-side secret; quota and deletion/retention review. |
| Corrupt process/device shutdown during recording | Lost or malformed activity | Durable state/sample writes, recovered state, idempotent finalization, power-kill/reboot recovery tests on devices. |
| Permission revocation or GPS spoof/poor signal | Missing or misleading route/statistics | Visible accuracy/fix state; quality flags; pause/recovery/error path; no false claim of complete route. |

This is a product-level baseline, not a penetration test or legal assessment. P19 must validate controls technically.

## Provisional legal/privacy applicability

**Working launch assumption:** India is the first launch market because the project owner is based there; do not assume other markets are covered. The Digital Personal Data Protection Act, 2023 and DPDP Rules, 2025 have phased commencement. As of 17 November 2026, MeitY’s 13 November 2025 commencement notification schedules core Data Fiduciary duties in sections 3–5, 6(1)–(8),(10), 7–10, 11–17 and related provisions for 18 months after publication (13 May 2027); the government’s November 2026 update says data-principal rights, fiduciary obligations, and Board inquiry/penalty provisions are scheduled for that date. Consent Manager registration became effective 13 November 2026. Confirm the operative provisions, amendments, and rules again before launch; design now for clear notice/consent, purpose limitation, minimization, security, user rights, deletion, retention, and breach response.

If the app or its cloud services are offered to people in the EU/EEA or monitor their behavior there, GDPR may apply even when Pace is established outside the EU. Do not intentionally target or accept those regions until a separate applicability and operational review is complete. The launch jurisdiction, audience, data flows, controller/processor roles, lawful basis, children’s use, retention, deletion, cross-border transfers, and Play disclosures must be reviewed with qualified counsel before public release. This is a product planning assessment, not legal advice or a compliance claim. Record the reviewed regions, reviewer, date, and resulting actions in a release ADR.

### Primary sources reviewed (17 November 2026)

- [MeitY Gazette notification on phased commencement of the DPDP Act](https://www.meity.gov.in/static/uploads/2025/11/c56ceae6c383460ca69577428d36828b.pdf), 13 November 2025.
- [MeitY Digital Personal Data Protection Rules 2025](https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa).
- [Press Information Bureau: Consent Manager framework and phased DPDP implementation](https://www.pib.gov.in/PressReleasePage.aspx?PRID=2336151&lang=1&reg=48), 14 November 2026.
- [European Commission: application of the GDPR](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/application-gdpr_en).
