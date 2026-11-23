---
name: Luminous Lavender PACE
colors:
  surface: '#faf9fe'
  surface-dim: '#dad9de'
  surface-bright: '#faf9fe'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f3f8'
  surface-container: '#eeedf2'
  surface-container-high: '#e8e7ec'
  surface-container-highest: '#e3e2e7'
  on-surface: '#1a1c1f'
  on-surface-variant: '#4a4455'
  inverse-surface: '#2f3034'
  inverse-on-surface: '#f1f0f5'
  outline: '#7b7487'
  outline-variant: '#ccc3d8'
  surface-tint: '#732ee4'
  primary: '#630ed4'
  on-primary: '#ffffff'
  primary-container: '#7c3aed'
  on-primary-container: '#ede0ff'
  inverse-primary: '#d2bbff'
  secondary: '#6b38d4'
  on-secondary: '#ffffff'
  secondary-container: '#8455ef'
  on-secondary-container: '#fffbff'
  tertiary: '#4f4b6c'
  on-tertiary: '#ffffff'
  tertiary-container: '#686385'
  on-tertiary-container: '#e9e2ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#eaddff'
  primary-fixed-dim: '#d2bbff'
  on-primary-fixed: '#25005a'
  on-primary-fixed-variant: '#5a00c6'
  secondary-fixed: '#e9ddff'
  secondary-fixed-dim: '#d0bcff'
  on-secondary-fixed: '#23005c'
  on-secondary-fixed-variant: '#5516be'
  tertiary-fixed: '#e5deff'
  tertiary-fixed-dim: '#c8c2e9'
  on-tertiary-fixed: '#1b1735'
  on-tertiary-fixed-variant: '#474363'
  background: '#faf9fe'
  on-background: '#1a1c1f'
  surface-variant: '#e3e2e7'
typography:
  display-lg:
    fontFamily: Space Grotesk
    fontSize: 56px
    fontWeight: '700'
    lineHeight: 60px
    letterSpacing: -0.03em
  display-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 44px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-md:
    fontFamily: Space Grotesk
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Outfit
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Outfit
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: 0em
  headline-sm:
    fontFamily: Outfit
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  body-lg:
    fontFamily: Outfit
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Outfit
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: Space Grotesk
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.04em
  label-md:
    fontFamily: Space Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.06em
  label-sm:
    fontFamily: Space Grotesk
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.08em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.25rem
  gutter-desktop: 1.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system expresses an energized, light-suffused athletic environment engineered for high-performance pacing, endurance tracking, and mindful recovery. Designed natively for modern Android (Material 3 architecture), the visual narrative departs from aggressive dark-mode fitness dashboards by introducing luminous pastel lavenders paired with striking electric violet energy accents.

### Personality & Emotional Tenor
- **Luminous & Uplifting:** Airy daylight feel that reduces fatigue during intense sessions.
- **Electric Precision:** High-contrast sports typography, hyper-legible telemetry figures, and tactical micro-glows indicating live physiological states.
- **Mindful Athleticism:** Calm, restorative tonal backdrops coupled with vibrant velocity markers.

### Design Style Movement
- **Modern Android Material 3 with Fluid Tactility:** Heavy reliance on dynamic surface containers, pill forms, generous 20–24dp elevation radii, and subtle ambient optical blooms that emulate backlit LED digital displays on athletic hardware.

## Colors

The palette establishes an open, daylight-readable visual hierarchy built upon three distinct lavender ground layers, layered with elevated crisp white surfaces, sharp deep violet text, and concentrated electric purple focal points.

### Palette Architecture
- **Primary Accent (`#7C3AED`):** High-energy electric purple for active workouts, primary CTAs, GPS paths, and real-time pace splits.
- **Secondary Accent (`#8B5CF6`):** Vibrancy bridge used for secondary actions, interactive toggles, chart gradients, and milestone badges.
- **Tertiary Accent (`#DDD6FE`):** Soft luminous lilac for active states, selected metric pill fills, and metric bar track backgrounds.
- **Neutral Grounds:**
  - Base Canvas: `#F8F7FC` (Ultra-soft ambient lavender-white).
  - Surface Mid: `#F3EEFA` (Grouped section background and navigation bar baseline).
  - Surface Low: `#EBE5F7` (Recessed wells, inactive input tracks, and segment dividers).
- **Surface Elevation:**
  - `#FFFFFF` (Crisp floating metric cards, elevated bottom sheets, and high-visibility cards).
- **Typography & Hierarchy:**
  - Deep Violet High-Contrast (`#1E1135`): Primary telemetry readouts, header typography, and numerical readouts.
  - Deep Violet Mid (`#2E1A4E`): Body text, graph axes legends, and secondary values.
  - Muted Lavender-Purple (`#6B5E87`): Metric unit labels (e.g., `BPM`, `KM/H`, `/KM`), captions, and inactive icons.

## Typography

The typographic pairing balances technical performance instruments with human-centric clarity. 

### Font Roles
- **Headlines & Telemetry (`Space Grotesk`):** Serves display figures, heart rates, split times, and metrics. Features tabular figure alignment (`font-variant-numeric: tabular-nums`) to prevent horizontal jitter during rapid live sensor updates.
- **Body & Editorial Context (`Outfit`):** Handles narrative insights, recovery coach notes, and general navigational text with a contemporary geometric balance.
- **Labels & Micro-data (`Space Grotesk`):** All uppercase, tracked telemetry units (`BPM`, `PACE`, `ZONE 4`, `KCAL`) rendered with structural rigidity.

## Layout & Spacing

The layout is built on an adaptive Android Material 3 fluid structure with an 8dp baseline grid (subdivided into 4dp for tight micro-telemetry cards).

### Layout System
- **Mobile Handheld (Up to 599dp):** Single-column layout with 4-column interior card grids. Screen edge margins are `16dp` (`1rem`). Gaps between telemetry widgets use `12dp` to `16dp` to preserve dashboard density without crowding touch zones.
- **Large Handhelds & Tablets (600dp - 1023dp):** 8-column layout with `20dp` (`1.25rem`) gutters, allowing side-by-side metric charts, route maps, and split feeds.
- **Expanded Screens & Desktop (1024dp+):** 12-column layout centered with max content constraints (1200dp), maintaining `24dp` (`1.5rem`) gutters and generous outer breathing room.

### Spacing Philosophy
- White space is intentional: Dense data metrics are encapsulated within compact, rounded tiles, balanced by spacious margins between metric groupings to reduce cognitive load during high-exertion workouts.

## Elevation & Depth

Visual hierarchy leverages tonal stratification layered with targeted optical luminescence rather than muddy dark shadows.

### Tonal Stratification
- **Ground Floor (`#F8F7FC`):** Application background.
- **Container Tiers:** Recessed interactive areas and inactive tracks utilize `#EBE5F7` or `#EDE9FE`.
- **Raised Surfaces (`#FFFFFF`):** Primary tracking cards, telemetry blocks, and navigation elements sit atop backgrounds with 0–1dp edge separation.

### Ambient Micro-Glows & Shadows
- **Card Shadow:** Subtle, high-dispersion lavender shadow: `box-shadow: 0 8px 24px -4px rgba(124, 58, 237, 0.07), 0 2px 6px -1px rgba(30, 17, 53, 0.04);`
- **Active State Micro-Glow:** Live indicators, workout recording buttons, and peak metric highlights cast a calibrated electric violet aura: `box-shadow: 0 0 16px 2px rgba(124, 58, 237, 0.35);`
- **Ghost Borders:** Floating white cards use a subtle boundary outline (`1px solid rgba(221, 214, 254, 0.5)`) ensuring sharp separation when lighting conditions shift outdoors.

## Shapes

The design system incorporates continuous rounded corners that reflect modern hardware silhouettes and smooth aerodynamic gear.

### Corner Radius System
- **Metric Cards & Floating Panels:** Set strictly to `20dp` or `24dp` (`rounded-xl` to custom `1.5rem`), creating frictionless, tactile tiles that feel natural under thumb navigation.
- **Pill System (Interactive Elements):** Buttons, chips, search bars, filter switches, and active state badges employ full pill styling (`rounded-full` / `9999px`).
- **Inner Containers & Secondary Visuals:** Nested items inside cards (e.g., small heart-rate bar charts, pace icons) use soft `8dp` to `12dp` radii to maintain nested geometric rhythm.

## Components

### Buttons & Interactive Controls
- **Primary Action (Start Workout / Finish):** Full pill (`rounded-full`), `#7C3AED` solid fill with `#FFFFFF` text. Accompanied by a resting `0 4px 14px rgba(124, 58, 237, 0.3)` micro-glow. Height: 56dp for immediate hit confidence.
- **Secondary Action:** Pill-shaped, container background `#EDE9FE`, text `#7C3AED`, zero shadow. Transitions to `#DDD6FE` on press.
- **Floating Action Button (FAB):** 56x56dp rounded-full pill in `#7C3AED` with pure white iconography and continuous active ambient aura.

### Chips & Filters
- **Pace / Zone Selectors:** Pill-shaped, minimal height (36dp). Inactive state: `#FFFFFF` surface with `1px solid #DDD6FE` and text `#6B5E87`. Active state: `#7C3AED` fill with `#FFFFFF` text and subtle glow.

### Cards & Metric Displays
- **Telemetry Tile:** `#FFFFFF` background, `24dp` corner radius, interior padding `16dp` or `20dp`. Top row features uppercase tracked label (`#6B5E87`) paired with a micro-icon. Middle section showcases massive `Space Grotesk` tabular figures (`#1E1135`). Bottom row displays dynamic mini sparklines or trend deltas (`+2.4%`).

### Lists & Activity Feeds
- **Segment Lists:** Surface `#FFFFFF` separated by `8dp` canvas margins rather than hairline rules. Each list row is an isolated pill or soft rounded card with an icon container (`#F3EEFA`), primary title (`#1E1135`), and right-aligned performance number.

### Selection Controls (Checkboxes & Radios)
- **Checkboxes & Radios:** `20dp` pill-rounded squares for checkboxes, fully circular for radios. Inactive: `#DDD6FE` border on white. Active: `#7C3AED` background with crisp white check or center dot.

### Input Fields
- **Data Input:** Enclosed pill or 16dp rounded box with `#F3EEFA` background and transparent border. Focused state: Surface transitions to `#FFFFFF` with a `2px solid #7C3AED` outline and soft electric purple perimeter glow.

### Specialized Athletic Components
- **Pace Arc / Circular Gauges:** 12dp thick stroke using a gradient from `#DDD6FE` to `#7C3AED`, with a glowing end-cap marker.
- **Live State Badge:** Pill container `#EDE9FE` housing a pulsing `#7C3AED` recording dot alongside `Space Grotesk` timer numerals.

## Android handoff

Open the [screen library](../index.html) to browse the 81 handset frames, dark variants, adaptive layouts, component sheet and state variants. Each frame directory contains its own entry HTML and PNG. Keep the shared assets folder beside the frame folders. The web export uses local Outfit and Space Grotesk font files with their Open Font Licenses, bundled vector paths and synthetic readings. The PNGs show the initial viewport. Open the HTML to scroll and try the controls.

### Themes, maps, charts and status

| Role | Light | Dark |
| --- | --- | --- |
| Canvas | #F8F7FC | #171124 |
| Raised surface | #FFFFFF | #251D36 |
| Recessed surface | #EDE9FE | #352844 |
| Ink | #1E1135 | #F7F0FF |
| Primary action | #630ED4 on white-text buttons | #D2BBFF with #25005A text |
| Running route | #7C3AED, white casing | #D2BBFF, indigo casing |
| Cycling | #006970 | #62D8DC |
| Jogging | #994C00 | #FFBA78 |
| Walking | #376126 | #A9DF91 |
| Basemap | #EEEAF1 land, white roads, #DCE7D9 parks, #DCE5F3 water | #211B32 land, #483C58 roads, #293B39 parks, #2A334E water |

Use sport color in filters, compact charts and route thumbnails. Pair color with a sport name, symbol or line pattern. Use violet as the main chart trace, muted dashed rules and direct values. Pace reads faster toward the top. Provide a focusable distance cursor and readable chart summary. Scrubbing an activity chart updates the route marker. A forecast range is a band with a plain uncertainty note.

Use one status chip. **Offline ready** indicates the app is working locally without connectivity. **Syncing** indicates work in progress. **Backed up** requires a confirmed successful backup with an available last-run timestamp. **Backup off** describes the disabled or local-only setting. **Needs attention** offers details and a retry where appropriate. Keep failed sync outside the recording path.

Use the same muted vector map style across maps, route review and recording. Draw route-only thumbnails from saved geometry, without looking up tiles. Outside downloaded coverage, retain the route over a hatched base and offer downloading when online. Include the provider’s required attribution in production. The export labels its drawn map as illustrative.

### Batch decisions and corrections to the seven supplied figures

1. **Recording.** Add four distinct sport preflights, permission rationale, metrics mode, pause, recovery, finish, save success and battery guidance. UI-003 removes calibration claims and unsupported sensor metadata. UI-015 makes the map and GPS status the preflight focus. UI-019 replaces the Preflight title, removes the back arrow during the active session and makes Pause the largest control. UI-025 leads with the route and uses actual recordable metric slots.
2. **Review.** Keep a short activity list with route line thumbnails, move filtering into its own sheet, and give the activity map the strongest visual weight. Move edit and destructive operations into their own tasks. Show the fastest split with a bar and a readable pace label.
3. **Maps and routes.** UI-033 drops photography and leads with consistent map geometry. Download and management show region size and progress. Following keeps the chosen route and off-route status in view. Use local GPX selection and waypoint geometry in Compose.
4. **Insights and goals.** UI-011 uses one status and a weekly goal ring. UI-042 starts with distance, then frequency and pace. Remove unsupported calories and sensor placeholders. Give elevation, trends, goals and records their own focused pages.
5. **Planning and workouts.** Use a month grid, day agenda and interval timeline. Keep the duration and top action visible. The player centers the countdown ring and gives Pause a large target.
6. **Account and settings.** Group actions by data scope. Put local use first, and keep credentials, backup and deletion in explicit tasks. Use a local initials avatar. Keep explanatory text for consent and data removal.
7. **Optional AI.** Use an explicit consent review. Show the source figures alongside a suggestion. Give Edit, Schedule and Reject separate actions. Release depends on the existing roadmap approval.

### Native Compose mapping

| Recurring element | Compose implementation |
| --- | --- |
| Compact bottom navigation, expanded rail | Material 3 NavigationBar, NavigationBarItem, NavigationRail, NavigationRailItem, emphasis on Record |
| Top bars and profile | TopAppBar, IconButton, initials in a round Surface |
| Primary and secondary action | Button and FilledTonalButton, custom sizes and pill shapes |
| Sport tile, list row and status chip | Selectable Surface, ListItem or Card; AssistChip for interactive status and Surface for passive status |
| Telemetry and progress rings | Text with tabular figures, custom metric layout, Canvas ring with progress semantics |
| Charts, splits, heatmap and timeline | Canvas or layout with keyboard and adjustable accessibility actions as applicable; keyed LazyColumn for long lists |
| Maps and thumbnails | Existing Android map provider, custom route overlay, Canvas thumbnails |
| Sheets, confirmations and undo | ModalBottomSheet, AlertDialog and SnackbarHost |
| Empty, skeleton, permission and offline states | Shaped Surface variants with semantics; private data stays visible on recoverable sync failure |
| Finish recording | Custom hold or swipe, with an accessible confirmation action |
| Motion and haptics | AnimatedVisibility, animateColorAsState and Android haptic feedback; respect system animator scale and Pace’s reduced-motion setting |

These designs use the app’s locked Material 3 1.4.0 as the baseline. No alpha Material 3 dependency is required for the specified implementation. Morphing loading indicators, wavy progress and other opt-in Expressive APIs excluded from that pin are unnecessary. [AndroidX Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)

The browser library uses a shared state sheet with example variants and synthetic data. Downloads, account changes, GPS, and consent are demonstrations. The code export uses shared CSS classes and inline vectors, including the existing Pace sport glyphs. Full screen-specific error handling, device haptics, genuine offline map tiles, and native assistive technology need Android verification.
