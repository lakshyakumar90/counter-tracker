# Pace Android design

Open [the interactive prototype](index.html) directly in a desktop browser. Choose a screen, theme or device from the review controls. The component catalog is under **Screen → Components**. Routes, readings, dates, backups and account interactions are illustrative fixtures. The prototype runs locally and sends no data.

[Stageshow activity-detail still](activity-detail-stage.png) shows the map, route markers and first read of the stats in an Android frame.

## Art direction

Keep Pace’s lavender identity. Think muted park maps, bold stopwatch numerals and a private training notebook. Use quiet violet-gray surfaces and crisp, rounded linework. Give the activity map the strongest visual weight. During recording, make elapsed time the first number the eye finds and put Pause inside thumb reach. Use tone and spacing to group information. Reserve stronger color for routes, action and information that changes a decision.

- **Record** uses a large map, a compact readout and a single primary control. A GPS gap has both a dashed segment and an explicit status.
- **Review** starts at the route. Scroll into metrics, scrub charts, then review splits and signal quality.
- **Plan** uses a calendar, an intensity strip and an ordered list of blocks.
- **Privacy** uses short, explicit labels for local storage, account backup and optional AI access. Keep services opt-in and provide a local exit from onboarding and restore.

The reference study drew on Strava’s map-led review, Nike Run Club’s training hierarchy, Apple Fitness’s goal rings, Garmin Connect’s metric grouping, Komoot and AllTrails’ map-led route browsing, Runna’s workout scheduling and Gentler Streak’s measured tone. Pace uses its own visual language and private account model. [Strava](https://www.strava.com/) · [Running app reference overview](https://apps.apple.com/us/iphone/story/id1674662196) · [Runna](https://www.runna.com/)

## Tokens

| Role | Dark | Light |
| --- | --- | --- |
| Canvas | #14131A | #FBF8FF |
| Surface | #211F29 | #F0EBF7 |
| Raised | #302B3B | #E8DFF4 |
| Rule | #4C4559 | #ABA1B9 |
| Ink | #F3EFFA | #211A2A |
| Secondary ink | #C5BDD1 | #53485F |
| Lavender | #D0B9FF | #633CAC |
| On lavender | #231337 | #FFFFFF |
| Cycling | #62D8DC | #006970 |
| Running | #D0B9FF | #633CAC |
| Jogging | #FFBA78 | #924700 |
| Walking and success | #A9DF91 | #33651F |
| Warning | #FFD084 | #805000 |
| Error | #FFB4B2 | #A7192A |
| Map land | #202830 | #E4E7DF |
| Map water | #263949 | #CBDCE9 |
| Map green space | #283B37 | #CDDDC9 |

**Type** uses Roboto Flex for native handoff, with one family and explicit weight and width settings. The preview follows the local font stack. Use Hero Metric 56–72sp at 760 weight and 96 width, Large Metric 32–40sp at 720, Title 20sp at 700, Body 16sp at 450, Label 14sp at 600 and Caption 12sp at 500. Enable tabular figures. Render units at label size with a visible space.

**Spacing and shape** use 8dp intervals, 16dp content gutters, 48dp action targets and 64–72dp recording controls. Radius roles are 12dp for standard containers, 20dp for sheets and selections, and 28dp for major surfaces. Separate surfaces with tone. Allow one elevation level for modal sheets. At 200% font scale, wrap or stack metrics and let the content scroll while keeping Pause and Finish reachable.

**Motion** uses 150ms for selection and token updates, 220ms for sheets, and 280ms for a mild spring on transition to the activity map. Roll only the changed live digits. Preserve focus and data position across transitions. With reduced motion, update digits in place and replace shared transitions with a direct swap. Haptics mark pause, completed finish, kilometre split and interval change. Use light, selectable feedback and honor the system haptic setting.

**Charts and maps** use the sport palette for a single active series. Use labels, line patterns, positions and markers to identify series under grayscale or red-green deficiency. Maintain a contrasting 1.5dp route casing against muted land and water, a 5–6dp stroke for fixes and a thin dashed stroke for the missing segment. Mark the start with a dot and finish with a flag or square. Give GPS status a label plus icon. A forecast uses a band between upper and lower bounds. Elevation uses an area trace. Pace uses an inverted time axis so faster is higher. Provide the actual values and a summary to TalkBack. Map licenses and attribution must follow the selected tile provider. The schematic preview uses authored geometry.

**High contrast** raises recording text and control contrast. Keep route and chart colors fixed when dynamic color is enabled. Keep larger type and TalkBack labels usable on compact phones. Tablet adds a left navigation rail and side-by-side map and metrics. Record landscape retains access to Pause, Resume and Finish.

## Component states

The catalogue shows default, pressed, disabled, loading and error samples. In Compose, give each control its correct semantics. Passive components have no press surface unless the container has an action. When a value is loading, keep the height. Use a dash and reason for missing data, and keep existing local content visible when a refresh fails.

| Specimens | Compose implementation |
| --- | --- |
| Metric tile, hero metric and stat delta | Custom composables with M3 Text, tabular typography and explicit semantics |
| Sport chip and tile | FilterChip and selectable Surface with vector sport icons |
| Segmented control | SingleChoiceSegmentedButtonRow and SegmentedButton |
| Navigation | NavigationBar and NavigationBarItem; NavigationRail and NavigationRailItem on expanded width; custom Record affordance |
| Bottom sheet | ModalBottomSheet with persistent state where needed; verify API opt-ins against the pin |
| Map controls, permission, offline and GPS | FilledTonalIconButton, Surface, AssistChip where actionable; custom map provider and signal semantics |
| Progress ring, goal and personal record | Canvas ring with progress semantics; Card or Surface for the container |
| Activity card, split row and heatmap | Keyed LazyColumn and sticky month headers; Canvas route thumbnail, split bar and heat cells with alternative text |
| Chart card and scrub tooltip | Canvas path and axis layout, pointer input and accessible adjustable semantics, shared distance cursor with the map |
| Interval chip | FilterChip or Surface; custom proportional timeline and reorder controls |
| Empty, skeleton and undo | Surface and Button as appropriate; shape-matched skeleton; SnackbarHost and SnackbarResult |
| Finish interaction | Custom hold progress or swipe semantics with a confirm dialog as an accessible alternative |
| Forms and menus | OutlinedTextField, DropdownMenu, DatePicker and inline supporting text |

### Expressive dependency

The repository locks Material 3 **1.4.0** through Compose BOM **2025.12.00**. AndroidX released 1.4.0 on 24 September 2025. It contains the revised motion scheme behavior and stabilized expressive navigation variants such as WideNavigationRail and ShortNavigationBar. The Expressive opt-in APIs removed before the 1.4.0 stable release require moving beyond that pin. For this project, the Expressive SplitButton, ButtonGroup, morphing LoadingIndicator and wavy progress indicators are excluded from the 1.4.0 build. Use ordinary buttons, segmented controls, skeletons and a custom ring. The screens require no 1.5 pre-release API. [AndroidX Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) · [Compose December release](https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-december-release)

## Prototype flows

1. **First run** → Use without an account → Choose sports → Units and theme → Today.
2. **Record → save → detail** → Choose a sport → Continue → Start → Pause → Hold to finish or confirm by keyboard → Save activity → scrub the chart.
3. **Download a map** → Downloaded regions → Download a map → Download → Simulate completion → Open maps.
4. **Create and start a plan** → Create workout → reorder blocks or set a date → Schedule workout → Start → Record.
5. **Restore after reinstall** → Sign in → Backup preview → Merge backup → History. The production service must validate records and reconcile updates without silently overwriting newer local data.

## Copy removed

- Intro paragraphs from Today, performance summaries and charts. Retain the date, units, primary value and a short chart caption.
- Repeated descriptions from sport choice, permission prompts and account status. Use labelled icons and one action.
- Map storage explanations from the map itself. Show the area, estimated size and download progress above the action.
- Split tables with redundant headings. Use one unit heading, a numbered row, a bar and a time.
- Social activity, streak prompts and badge copy. Keep a compact record marker directly on the relevant route.

## Scope and device verification

This review includes future roadmap concepts. Saved routes, the planner, home workouts, forecasts and intelligence follow the release cut line and decision gates in the existing product baseline. Cloud restore and downloads are simulated. This proposal does not change the Android application or close a roadmap gate. Native acceptance still needs screenshots at supported Android window widths, TalkBack and 200% font scale, outdoor readability, reduced-motion and haptic checks, map-source attribution, large-list performance, and physical-phone recording verification.

The browser preview reuses generic loading, error and partial-data specimens across several screens. It represents long history with a short fixture, and models coverage and waypoint controls without a full map editor. Motion and haptics are specified for Compose handoff. The Stageshow deliverable is a still framed with the kit’s Android Phone, using the Phone Touch example to review cropping at desktop and compact widths.

Token contrast calculations give at least 7.29:1 for the primary text, secondary text and primary button combinations sampled across both themes, and at least 5.16:1 for sport colors on base map land. Street labels, overlays and real provider tiles need their own measurements.
