# Phase 20 — Beta and release progress

## Source preparation

- A Pace launcher mark and app label are present. The app uses a system-masked
  adaptive icon with a monochrome layer and vector splash artwork, so assets
  scale without density-specific bitmaps. The manifest disables platform Auto
  Backup for private local fitness data.
- The auth callback deep link is declared as `pace://auth-callback`; the
  development Supabase project must allowlist it.
- Debug and release variants have version code/name values. Release signing
  reads owner-supplied environment variables and fails on partial
  configuration. No signing secret or upload key is stored in the repository.
- Store-readiness, privacy, account-deletion, and signing checklists are
  documented.

## Owner and runtime gates

P20 remains **Hold**. The final application ID, publisher identity, listing
name, launch regions, support/privacy contact, Play account type, target group,
store declarations, and public privacy/deletion URLs require the owner.
Production signing and Play credentials, release artifact builds, and tester
enrollment are intentionally not part of this source-only checkpoint.

Before beta, complete applicable developer verification and closed-test
requirements, approve privacy/data-safety and precise-location disclosures,
configure a signed distribution path, and provide screenshots and a feedback
process. Debug installation, airplane-mode ride/run, app upgrade, release
shrinking, auth deep-link, and signed-update checks remain runtime acceptance.
Recheck current Play requirements at release time.
