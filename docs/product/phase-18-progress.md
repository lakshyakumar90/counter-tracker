# Phase 18 — Settings and privacy progress

## Implemented in source

- Existing profile settings include local profile name, metric/imperial units,
  system/light/dark theme, preferred sports, and Supabase account status.
- Added a local data export action in Profile. The Android document picker saves
  a versioned JSON snapshot of local profile preferences, activity summaries,
  track points, splits, sensor samples, plans, goals, records, routes, and
  map-region metadata, plus in-progress home workout drafts. Export reads rows
  incrementally. The UI warns that route
  coordinates are private.
- Added a confirmed local fitness-data erase action. It clears Room tables and
  pending sync operations, and explicitly states it does not delete cloud data.
- Added an account restore action to Profile with confirmation, row progress,
  retry messaging, and a persisted restore-owner gate that blocks a different
  signed-in account from opening the app shell. Fitness tables and exports now
  filter by account owner; signing out returns to a separate local-only owner.

## Gate remains open

Profile now contains optional backup controls, reminder preferences, GPS
permission/provider diagnostics, tracked map-storage metadata, privacy details,
and account deletion. The Supabase deletion function removes private account
objects then deletes the authenticated account; local account rows are erased
scoped to that UUID. A web deletion page template is ready for project values
and static hosting. The AI opt-in is separate and sends only aggregate metrics;
its cached result and usage log are included in data export, and account deletion
cascades server logs. Local export remains available if the cloud usage query
is unavailable.

Physical accessibility checks at large font sizes, high contrast, reduced
motion, TalkBack, keyboard, and switch access remain open. The web deletion URL
still needs publishing, and remote deletion requires deployment/credential
acceptance. P17 live RLS and multi-account checks remain upstream Hold evidence.

The current Android source and instrumentation source compile, formatting and
lint pass, and all 52 JVM unit tests pass (2026-11-19). No physical-device
runtime acceptance has been performed for the document picker, privacy
controls, accessibility, or account delete.
