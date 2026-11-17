# Annotated Screen Specifications

These specifications define layout and behavior for the later Compose screen
implementation. The companion [screen board](assets/p02-screen-board.svg)
shows the compact-phone composition; it is a static design artifact, not a
release screenshot.

## Home

**Owner:** Android + product/design. **Purpose:** resume local use and make the
next useful action obvious. **Primary action:** Record. **Secondary actions:**
open history, Insights, Maps, local profile/settings. The current week summary
uses local records and labels its period and unit.

```text
┌ Pace                         Profile ┐
│ Good afternoon                      │
│ Your week            24.6 km        │
│ 3 activities         since Monday   │
│                                      │
│ [ Record a ride ]                    │
│                                      │
│ Recent activity                      │
│ Walk · Today         4.8 km     42m  │
│ Run · Tuesday        5.0 km     31m  │
│                                      │
│ Home  Record  Maps  Plan  Insights   │
└──────────────────────────────────────┘
```

The Profile action opens settings without a gesture. On first use, replace the
weekly summary with a direct invitation to record; do not show fake totals.
When the device is offline, local activities remain visible and only cloud
status changes.

## Record

The setup screen offers the four supported outdoor activities. Location
explanation precedes the Android prompt. Acquiring displays accuracy and
actionable recovery guidance. Active recording keeps one live metric dominant,
two secondary values, and Pause/Finish reachable without scrolling.

```text
┌ Close                    Cycling   GPS ┐
│                                        │
│                00:00                   │
│              elapsed                  │
│                                        │
│   0.00 km              —:— /km         │
│   distance             avg pace        │
│                                        │
│            [ Start ]                  │
│                                        │
│ GPS status · waiting for a clear fix   │
└────────────────────────────────────────┘
```

The active state replaces Start with adjacent 48 dp Pause and Finish controls.
Pause changes the main status immediately and freezes distance sampling.
Finish opens the save confirmation; successful durable save opens Activity
detail. During recording, the normal destination bar is suppressed. A
foreground notification returns to the current activity.

## Maps

The map surface explains local coverage. When a licensed region package is
available, its card includes size, free-space requirement, network choice, and
progress. If coverage is unavailable, show the limited fallback or a clear
empty state; never imply an offline region is present when it is not.

```text
┌ Maps                         Download ┐
│ Offline coverage                     │
│ [ map surface / route if present ]   │
│                                      │
│ Saved regions                        │
│ Central Park · 84 MB      Available  │
│                                      │
│ No saved regions?                   │
│ Browse licensed map areas            │
└──────────────────────────────────────┘
```

The map's own controls sit in a safe inset and do not cover attribution. A
download failure preserves any previously valid package. A map remains
optional to activity recording.

## Plan (V1.1 scope gate)

Plan list, edit, workout detail, start, and completion use local data. This
screen remains a design target only until the product owner approves the P00
cut line after P05. The V1 build omits this destination if the gate remains
closed; it does not render the example content below as a placeholder.

```text
┌ Plan                         Add plan ┐
│ This week                             │
│                                      │
│ Easy run                      30 min │
│ Wednesday · 3 steps                  │
│                                      │
│ Mobility                     15 min  │
│ Saturday · 5 steps                   │
│                                      │
│ A workout is distinct from a saved   │
│ outdoor activity.                    │
└──────────────────────────────────────┘
```

Empty state invites “Create a workout” only after the scope gate is approved.
Deleting a plan asks for confirmation. Starting one explains exactly how it
will link to an outdoor activity or timed session.

## Insights

Overview shows a named period, selected activity types, defined summary
metrics, one chart, and recent records. Metric detail contains the glossary
definition, unit, coverage/quality note, and links to source activities.

```text
┌ Insights                 [This month⌄] ┐
│ [All activities⌄]                    │
│                                      │
│ Distance               Moving time  │
│ 86.4 km                7 h 20 min    │
│                                      │
│ Distance by week                     │
│  ┃    ┃                              │
│  ┃ ┃  ┃   ┃                          │
│  W1 W2 W3 W4                         │
│                                      │
│ Select a bar for its activities      │
└──────────────────────────────────────┘
```

Chart selection exposes the selected date/value/unit to touch, screen reader,
and keyboard navigation. Missing values produce a gap. The linked map opens
only when the selected activity has route data. The empty state points to
Record and retains the same layout structure when data appears.

## Activity detail and analytics link

1. Activity title and date, selected sport, data-quality state.
2. Route map when route samples exist, with start, finish, and GPS-quality
   markers.
3. Distance, elapsed time, moving time, average speed or pace, and splits with
   unit labels and glossary access.
4. Actions for permitted metadata editing, export, and deletion.
5. A tapped Insight chart point opens the matching activity and focuses its
   date/metric; Back returns to the same chart period and selection.

No route samples: replace the map with an explicit “Route unavailable” state
and keep all valid summary metrics. Partial recording: label the gap and mark
the final partial split; do not imply a complete route.

## Design review checklist

- [ ] The primary action and current screen are clear at a glance.
- [ ] Every state in the flow has content, empty, offline, and error behavior.
- [ ] Text, icon, map, and chart contrasts meet the visual-system thresholds.
- [ ] No required instruction is below 12 sp or clipped at large font scale.
- [ ] Every action has a 48 dp target and a screen-reader name.
- [ ] Recording controls remain visible in portrait and landscape.
- [ ] Map/chart content has a textual accessible alternative.
- [ ] P00 local-first, privacy, terminology, and V1 cut-line rules are respected.
- [ ] A reviewer has recorded layout, readability, and navigation feedback.
