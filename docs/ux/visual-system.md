# Pace Visual System

**Design direction:** a field instrument for personal training. The interface
uses quiet mineral surfaces, an ink-like type color, a river route trace, and a
small amber signal for the one action currently in focus. It communicates
conditions and measurements directly. It uses no gamification, social proof,
leaderboard language, decorative gradients, or motion unrelated to a user
action.

## Color tokens

### Foundation palette

| Token | Light | Dark | Primary use |
| --- | --- | --- | --- |
| Field paper | `#F2F5F2` | `#112124` | App canvas |
| Surface | `#FFFFFF` | `#1B2E32` | Sheets, controls, content surfaces |
| Ink | `#18343A` | `#E7EEEB` | Main text and icons |
| Quiet text | `#52696D` | `#B1C0BE` | Supporting labels and secondary values |
| River | `#1E6674` | `#7DC5D0` | Primary navigation, route line, links |
| Signal amber | `#F1C56B` | `#F0C56A` | Active recording and selected emphasis; pair with Ink |

### Semantic tokens

| Meaning | Light foreground | Light container | Dark foreground | Dark container |
| --- | --- | --- | --- | --- |
| Success | `#286B4C` | `#E2F1E8` | `#8DD4A9` | `#183C2D` |
| Warning | `#754B0B` | `#FFF1CE` | `#F0C56A` | `#49330F` |
| Error | `#A3332D` | `#FCE8E6` | `#FFB4AB` | `#521D1A` |
| Information/location | `#245C9A` | `#E7F0FC` | `#A8C9FF` | `#1B3559` |
| Divider/outline | `#C8D2CE` | — | `#43575B` | — |

Body text and interactive labels must reach at least 4.5:1 contrast with their
actual surface; large text and essential non-text indicators must reach at
least 3:1. Use the approved foreground/container pairs above, and check any new
pair in both themes. Amber is a filled focus surface with Ink content; it is
not small amber text on white. A status always includes a text label or icon
shape so hue is not its only meaning.

### Route and chart palette

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| Route | `#176875` | `#8BD0D8` | Primary GPS trace; 4 dp line with a light/dark casing on map tiles |
| Route comparison | `#785C9E` | `#C6A7F0` | A second series; dashed when overlaid |
| Pace/speed series | `#A65C22` | `#F4B574` | Alternate metric series and selected data point |
| Location marker | `#245C9A` | `#A8C9FF` | Current position; blue ring and distinct pointer shape |
| Start | `#286B4C` | `#8DD4A9` | Start marker with triangle/flag silhouette |
| Finish | `#A3332D` | `#FFB4AB` | Finish marker with square/flag silhouette |
| GPS accuracy | `#5683B6` at 18% | `#8AB5E8` at 22% | Translucent radius; retain a solid center marker |
| POI | `#754B0B` | `#F0C56A` | Named point of interest with a recognizable icon |

Charts use labeled axes, explicit units, a visible legend, and a persistent
selected value. Patterns, line styles, and markers distinguish multiple
series. Route markers keep different shapes for start, finish, and current
position. Map overlays use a neutral halo when the underlying tile color makes
their line hard to see.

## Type scale

Use Android's system sans family for UI copy; no bundled typeface is required.
Use tabular numerals for live metrics so digits do not shift while counting.
The display scale is measured and calm, not condensed or italic.

| Role | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| Live metric | 56 / 64 sp | Medium | Recording elapsed time or primary distance |
| Page title | 28 / 36 sp | Semibold | Home and destination titles |
| Section title | 20 / 28 sp | Semibold | Screen sections |
| Card title | 16 / 24 sp | Medium | Activity or metric item |
| Body | 16 / 24 sp | Normal | Instructions and supporting copy |
| Secondary | 14 / 20 sp | Normal | Units, dates, secondary values |
| Compact label | 12 / 16 sp | Medium | Axis labels and short supporting labels |

Respect Android font scaling. Do not set fixed heights around text. If a
metric wraps or becomes crowded, reflow its row before reducing the user's
selected font scale. Captions do not carry required instructions by themselves.

## Spacing, shapes, surfaces

Spacing follows a 4 dp base: `4`, `8`, `12`, `16`, `24`, `32`, and `48 dp`.
Use 16 dp for compact screen gutters, 24 dp for large phone/tablet gutters,
and 8 dp between related controls. Keep at least 24 dp between unrelated
sections.

| Component | Corner radius |
| --- | ---: |
| Small label container | 8 dp |
| Button and compact control | 12 dp |
| Content card | 16 dp |
| Bottom sheet and large panel | 24 dp |
| Pill/status chip | Fully rounded |

Cards sit on tonal surfaces with a one-pixel outline when adjacent surfaces
would otherwise blend. Default elevation is zero. Menus and modal sheets may
use the Material tonal/elevation treatment needed to establish their layer.
Avoid stacking outlines, drop shadows, and tinted backgrounds on the same
component.

## Icons

Use AndroidX Compose Material Icons Rounded with outlined icons for ordinary
navigation and filled icons for the selected destination. Keep standard icons
at 24 dp in a 48 dp interactive area. Use custom vector symbols only for
activity route markers (start triangle, finish square) or a Pace-specific
recording waveform that Material does not express clearly. Use one style per
control family, always pair an icon-only action with an accessible label, and
do not use emoji as an icon.

## Responsive layouts

| Window width | Navigation | Content layout |
| --- | --- | --- |
| Compact, under 600 dp | Five-item bottom bar | One column; full-width primary action; sheets for short secondary choices |
| Medium, 600–839 dp | Navigation rail | Centered content column up to 720 dp; list/detail can use two panes |
| Expanded, 840 dp and above | Permanent rail | Two-pane history/detail or analytics/chart; cap reading width and preserve whitespace |

The live recorder keeps its primary metric and pause/finish controls visible in
portrait, landscape, split-screen, and resizable windows. Secondary metrics
reflow to a second row; the primary control order stays unchanged. Do not lock
orientation. Insets reserve system bars, display cutouts, and gesture areas.
Use window size classes instead of device model names. When a second pane does
not fit, return to the compact single-pane pattern.

## Touch, keyboard, and screen reader behavior

- Every actionable item reserves at least a 48 × 48 dp target. Space neighboring targets so their expanded touch bounds do not overlap.
- All actions have a concise accessible name and role. Decorative route art is excluded from traversal; route meaning is also available in a textual summary.
- Focus order follows the visual reading order. Dialog focus starts at the title or first meaningful control and returns to its trigger when dismissed.
- Selected, recording, paused, offline, warning, and error states are announced with a text label. Color is a supporting cue.
- Charts expose the same values through labels and a selectable data summary. A user can navigate individual points without relying on a drag-only gesture.
- Support large system font sizes, switch access, TalkBack, and keyboard/D-pad focus for every primary flow.

## Charts and maps

Charts begin with Compose Canvas. Each chart includes a title, period, unit,
axes, legend, selected value, data coverage and a textual summary. Missing
values create gaps instead of zeroes. Partial splits are marked partial and
are not compared as complete splits. A selected point links to its source
activity only when that source exists.

Map colors use the route/chart palette above. Provide labels for all markers,
let route content fit the screen without covering attribution or controls,
and keep map gestures from trapping TalkBack or nested-scroll users. A map may
show an empty/offline fallback while local recording remains available.

## Motion and haptics

| Interaction | Motion/haptic |
| --- | --- |
| Destination change | 160 ms crossfade; no travel animation |
| Expand/collapse details | 180 ms size/fade transition tied to the user's tap |
| Bottom sheet open/close | 220 ms eased transition; drag remains available |
| Start/pause/resume recording | Immediate state label and one light confirmation haptic |
| Finish recording | One medium confirmation haptic after the durable save succeeds |
| Failed action | No success haptic; show the reason and retry action |
| Reduced motion/system animation disabled | Remove nonessential transitions; keep state changes immediate and legible |

Do not pulse a GPS indicator continuously, animate a metric on each sample, or
vibrate for every split/location update. An approved interval workout may use
one light haptic at a work/rest boundary. Haptics are supplemental and can be
disabled from Android settings; text and visual states remain sufficient.

## Data states

Every screen that reads data defines these outcomes:

| State | Presentation rule |
| --- | --- |
| Loading | Use only for real local work; keep controls stable and announce loading when it lasts. |
| Content | Show current local data and its period/unit/quality context. |
| Empty | Say what belongs here and provide one next action, such as recording an activity. |
| Offline | Keep local content visible; identify only the network-dependent action that is unavailable. |
| Error | State what failed, whether saved local data is safe, and provide retry/recovery. |

An offline state is not a full-screen error when local data is usable. Never
replace an existing local record with an empty response after a failed sync.

## References

- [Compose accessibility defaults and 48 dp touch targets](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Android adaptive layouts and window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)
- [W3C WCAG 2.2 contrast minimum](https://www.w3.org/TR/WCAG22/#contrast-minimum)
