# P03 Completion: Compose Component Foundation

## Outcome

P03 provides a reusable Compose component layer on top of the P02 theme. The
app is still a foundation project; these components are ready to be assembled
into product screens in later phases. Feature-specific navigation remains
caller-controlled, including Plan's P00 scope gate.

## Roadmap task evidence

| Task | Evidence |
| --- | --- |
| T001–T006 theme, light/dark schemes, typography, color access, spacing | `app/src/main/java/com/pace/app/ui/theme/` (theme package was introduced in P02 and aligned here to the visual-system palette) |
| T007–T010 primary, secondary/text, destructive, and text-input controls | `ui/components/PaceActions.kt` |
| T011–T013 segmented control, bottom navigation, four sport tiles | `ui/components/PaceActions.kt`, `ui/components/PaceNavigation.kt` |
| T014–T015 large metric and comparison states | `ui/components/PaceMetrics.kt` |
| T016–T021 loading, empty, offline, network retry, permission, dialog/snackbar patterns | `ui/components/PaceStates.kt`, `ui/components/PaceActions.kt` |
| T022–T026 chart tooltip, map controls, interval timer chip, calendar chips, bottom sheet | `ui/components/PaceStates.kt` |
| T027–T028 numeric transition and system animation-scale handling | `PaceNumericTransition` in `ui/components/PaceStates.kt` |
| T029 light/dark component previews | `ui/components/PaceComponentGallery.kt` |

Focused Compose instrumentation coverage is in
`app/src/androidTest/java/com/pace/app/ui/components/PaceComponentsTest.kt`.
The tests cover enabled/loading/disabled button state, selection semantics,
offline and missing-data copy, and destructive confirmation.

## Component behavior and implementation choices

- All display copy and accessibility labels are supplied by callers or Android
  string resources. Reusable components do not own product-specific wording.
- Loading and unavailable states retain labels and semantics; loading actions
  cannot be activated twice.
- Permission UI provides separate request and Settings hand-off callbacks. The
  caller decides whether the platform should prompt or Settings should open.
- Destructive actions require a deliberate confirmation step.
- Map controls accept caller-provided vector icons and accessible labels, with
  48 dp icon-button targets.
- Chart tooltips expose a merged textual summary. The chart owns hit testing,
  point selection, and keyboard/screen-reader navigation.
- Bottom sheets use Material 3 modal sheet behavior for drag dismissal,
  keyboard insets, and nested scrolling. The caller owns visibility and content.
- Numeric transitions use a short fade and read Android's animator duration
  scale; a zero scale produces immediate changes.
- Calendar and workout components represent supplied state only; this does not
  add calendar persistence or a workout player.

## Validation and gate status

The P03 gate is defined as reusable components meeting visual, accessibility,
string, and test standards. Build validation must include the debug APK,
instrumented test APK, JVM tests, lint, and formatting. Instrumented tests are
compiled as part of the test APK build, but they have not been run because no
physical Android device is connected and no emulator is to be installed.

Therefore implementation evidence is complete, while final P03 gate status is
**Hold for physical-device UI test execution and target-device visual review**.
This preserves the device constraint and avoids claiming runtime validation
that has not happened.
