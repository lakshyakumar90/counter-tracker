# P00 Planning, Issue Policy, Risks, and Phase Gates

## Capacity and velocity baseline (provisional)

This is a solo project. Until measured, plan with **8 focused hours per week** and a planning baseline of **4 accepted points per week**. These are scheduling assumptions, not a commitment. The plan’s 46-week estimate is not yet validated against that capacity. Track actual focused hours and accepted points weekly; calculate a rolling four-week rate; re-forecast after P05 before committing to P07–P16 dates. Keep one active implementation task at a time except independent planning/documentation work. Do not increase estimates or silently stretch hours to preserve the original date.

## Issue priorities and severity

### Priority

- **P0 — release/blocker:** data loss/corruption, unsafe or privacy-invasive behavior, core recording failure, security boundary failure, build/release blocker, or a phase-gate dependency. Stop dependent work until mitigated.
- **P1 — important:** a user journey or planned acceptance item is materially degraded, with a safe workaround and no critical data/security impact.
- **P2 — later:** polish, convenience, or non-release enhancement that does not violate accepted scope or accessibility baseline.

### Bug severity

- **Critical:** active or likely personal-data exposure/loss, security compromise, or app unusable for most users; immediate triage and release halt.
- **High:** core recording/recovery broken or major flow unusable for a supported device/OS group; fix before release candidate.
- **Medium:** important feature impaired with workaround; schedule before relevant phase gate.
- **Low:** minor visual/edge issue with low user impact; backlog with priority and owner.

Each issue records roadmap task/phase, reproducible steps, expected/actual behavior, device/OS, offline/permission state, privacy/data impact, severity, priority, owner, and acceptance evidence. Do not attach real GPS traces; use synthetic or sanitized fixtures.

## Risk register

Owner for all initial risks: **Pace project owner**. Review weekly during active work and at every phase gate; add dated status/evidence in the issue tracker or this table.

| ID | Risk | Likelihood / impact | Mitigation and trigger |
| --- | --- | --- | --- |
| R1 | GPS variance, background restrictions, or process death loses/warps a workout | High / Critical | Build deterministic engine and recovery first; golden tracks plus outdoor tests on two physical devices (one battery-restrictive); trigger: missing, duplicate, or materially wrong saved route. |
| R2 | Battery drain or vendor task-killing makes screen-off capture unreliable | High / High | Measure per-device sessions, foreground service compliance, battery guidance; trigger: interrupted field run or unacceptable measured drain. |
| R3 | Offline map licensing, attribution, or tile cost invalidates distribution/download model | Medium / High | Resolve provider, offline rights, attribution, regions, storage and cost before P08 map implementation; no unlicensed production tiles. |
| R4 | Offline sync/restore causes duplicates, overwrites, or missing private track objects | Medium / Critical | Backup-first immutable track policy, idempotent operations, tombstones, restore drills for DB and Storage, no silent overwrite. |
| R5 | Sensitive GPS leaks through cloud policy, logs, backups, analytics, or AI egress | Medium / Critical | Threat model, access tests, log redaction, private object policy, opt-in minimum payload, privacy review before cloud/AI launch. |
| R6 | Solo capacity cannot support 1,780 points/46 weeks | High / High | Establish 8h/4pt provisional baseline, track weekly actuals, cut scope before expanding hours, reforecast after P05. |
| R7 | Unsupported SDK/build tooling or OS behavior blocks reproducible builds/Play submission | Medium / High | Pin and document JDK/Gradle/AGP/SDK; CI build; monitor target API policy and re-check before release. |
| R8 | Privacy or store policy changes delay distribution | Medium / High | Confirm launch jurisdictions/account type, deletion path, data disclosures, location declarations and closed-test duties before beta. |
| R9 | Permission denial or revocation leaves recording state ambiguous | Medium / High | Explicit state machine, permission rationale, recovery/error UX and revocation instrumentation tests. |
| R10 | Analytics appear precise despite noisy/missing data | Medium / High | Glossary, quality flags, synthetic golden tracks, tolerance bounds, no unsupported elevation/forecast metrics. |

## Phase gate checklist

A phase is **Go** only when its named gate is evidenced, relevant P0 dependencies are closed, required checks are green, risks are reviewed, and no unresolved decision blocks the next phase. Otherwise status is **Hold** with an owner and next action. Proposed decisions are not approvals.

### P00 exit gate

- Vision, supported sports, V1 non-goals, users, journeys, terminology, units, offline contract, restore contract, and AI boundary approved.
- MVP cut line, capacity baseline, Definition of Done, privacy/threat baseline, error policy, issue taxonomy, release channels, calculation glossary, risk owner, and privacy review checkpoints recorded.
- MVP matrix has binary evidence criteria; product owner accepts provisional assumptions or records replacements.

### Subsequent gates (from roadmap)

| Phase | Go evidence |
| --- | --- |
| P01 Repository/environment | Reproducible native build, documented pinned toolchain, lint/format checks and CI green. |
| P02 UX/design | Approved flows, interaction specs, design tokens and accessibility baseline. |
| P03 Compose foundation | Reusable UI components meet visual, accessibility, string, and test standards. |
| P04 Shell/onboarding | Navigation and local-only onboarding usable without network. |
| P05 Local data/repositories | Room schema, migrations, repository contract and backup/data-extraction policy stable; reforecast capacity/cut line. |
| P06 Auth bootstrap | Accounts work online; existing local profiles continue offline; per-user isolation verified. |
| P07 GPS engine | Reliable complete recording, screen-off and offline, with recovery evidence on device. |
| P08 Offline maps | Licensed offline package renders; recording remains independent of tiles. |
| P09 Recording UI | Full activity flow polished and manually verified on devices. |
| P10 History/detail | Discover, inspect, edit and delete work offline with data integrity. |
| P11 Analytics math | Deterministic tested metrics independent of internet/AI; golden fixtures pass. |
| P12 Dashboard/charts | Accurate accessible interactive charts, linked route behavior if included. |
| P13 Routes | If in release: GPX/import/export/follow works offline and licensing/scope accepted. |
| P14 Plans/calendar | If in release: create, schedule, complete offline with timezone behavior tested. |
| P15 Home workout | If in release: library/player work offline with safe interval transitions. |
| P16 Rules/goals | If in release: deterministic recommendations and transparent forecasts validated. |
| P17 Backup/sync/restore | Private per-user restore works for metadata and track objects with failure/retry evidence. |
| P18 Settings/privacy/accessibility | User data controls, deletion/export and accessibility pass. |
| P19 Reliability/security | Release reliability, performance, security and accessibility matrix passes. |
| P20 Beta/deployment | Signed installable beta, offline reliability evidence, current store requirements and disclosures complete. |
| P21 Optional intelligence | Opt-in validated AI; no offline-core regression; privacy/cost controls approved. |
| P22 Operations/growth | Support, monitoring, retention, recovery and next-version scope ready. |

## P05 checkpoint (2026-11-18)

The local Room/DataStore source and migration test source are implemented. Main
and Android-test code compilation has passed. The physical-device migration and
repository instrumentation suite has not run because it requires packaging a
new debug APK; that packaging requires the owner's explicit instruction per
`AGENTS.md`. See [P05 completion evidence](phase-5-completion.md). The roadmap's
78-point P05 estimate is 19.5 weeks at the provisional 4-point weekly rate; no
actual P05 velocity log is available, so this is not a validated forecast.
Review the provisional MVP cut line and measured capacity before committing
P07–P16 dates.

## P06 checkpoint (2026-11-18)

The owner explicitly directed implementation of P06 while P05's physical-device
gate remained open. This is a recorded sequencing exception; P05's migration and
instrumentation evidence remains pending. P06 now has an optional Supabase Auth
client, encrypted session storage, email flows, and a profile/RLS migration.
No Supabase project credentials were present, so online account and two-user RLS
checks have not run. P06 remains Hold until its account flow and profile isolation
are verified against a configured development project. See
[P06 completion evidence](phase-6-completion.md).

## P07 checkpoint (2026-11-18)

The owner explicitly requested P07 while P06 online validation is still pending.
This is the second recorded sequencing exception; it does not waive the open P05
or P06 evidence requirements. P07 source now includes an offline Room-backed
recorder, location foreground service, fused and platform location sources,
sample filtering, notification controls, and interrupted-session recovery. The
P07 device gate remains Hold until screen-off, airplane-mode, recovery, and
battery behavior are evidenced on physical devices. See
[P07 completion evidence](phase-7-completion.md).

## P08 checkpoint (2026-11-18)

The owner asked to proceed with P08 while the P07 device gate remains open. This
sequencing exception is recorded; it does not mark P07 complete. P08-T001/T002
now select OpenFreeMap's public MapLibre vector style for the initial prototype,
define provisional per-pack and aggregate storage limits, and render light/dark
online styles with retry and a packaged, non-geographic no-tile illustration.
The provider's public service has no SLA and may be discontinued, so P08's
saved-region support remains required. The phone is currently absent from ADB; P08 APK
installation and offline runtime checks are pending until it is connected. See
[P08 progress](phase-8-completion.md) and the
[provider/storage decision](../platform/offline-map-provider.md).

## Release channels

1. **Developer/debug APK:** local and CI validation only; test data; not a public promise.
2. **Internal APK / Play internal testing:** invited testers for rapid device/field feedback; retain crash/privacy safeguards and use synthetic data where possible.
3. **Closed beta:** staged cohort only after P19 beta criteria, privacy/store declarations, support contact, and recovery path are ready. Verify current account-specific tester duration/count requirements immediately before enrollment.
4. **Production Play AAB:** only after closed-beta exit, signed reproducible release, accessibility/privacy review, support and rollback plan, and current Play policy checks.

Direct APK distribution is for controlled testing unless a separately reviewed distribution and update/security policy is established. No launch date is committed by this phase.

## Decision dispositions for P00 defaults

These dispositions close the P00 planning questions while retaining explicit review points:

- **Sequencing (D21): accept.** Move auth work to just before cloud backup/sync; local profile and recording do not wait for sign-in.
- **Sync policy (D22): accept.** V1 cloud is backup-first; saved GPS tracks are immutable after save, while allowed metadata merges and deletes tombstone. Revisit only with demonstrated multi-device editing need.
- **Target SDK baseline (D23): accept as a dated baseline.** Use API 36 for the initial scaffold and re-check current Google Play requirements before every release; it is not a permanent promise.
- **AI transport (D24): accept.** Keep a server-side vendor-neutral gateway boundary; OpenAI may be an initial provider later, but no vendor key or direct provider call ships in the app.
- **Continuous quality (D25): accept.** Apply the Definition of Done to every task, not only P19.

Update the HTML decision register from Proposed to the dispositions above when the roadmap file is next edited; this companion document records the P00 baseline in the meantime.
