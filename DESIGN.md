# PACE design system

## Visual direction

Use the supplied Strava screens as references for generous spacing, neutral sans serif type, large workout numbers, and route maps. PACE is a private training log. Use icons and route geometry for illustrations. Keep one primary action visible and use dividers for related rows.

## Color

Light primary: #6340A5 on white. Dark primary: #D3BCFF on #112124. Use pale lavender for selected surfaces. Use green for routes and successful states, with labels and marker shapes so color is never the only signal. Keep purple and green in separate roles on neutral surfaces. Preserve system, light, dark, and high contrast preferences.

## Layout and typography

Use Android system sans with tabular numbers for timing and pace. Use 20–24 dp page gutters, 48 dp touch targets, and safe drawing and keyboard insets. Give the location, timing, and primary recording controls the strongest hierarchy. Use simple sport icons and restrained motion that respects reduced motion.

## Onboarding and accounts

Three steps: welcome and local use, activity preferences, and units. Keep actions below scrolling content. Allow back and skip without undoing choices already made. Profile provides optional name, theme, authentication, and backup controls. Ask for location at recording time. Local recording and saved activities work offline. When opted in, cloud backup sends queued changes for the signed-in owner when connectivity allows.

## Stitch reference

The supplied Luminous Lavender PACE export guides the light palette, white metric panels, stacked sport controls, and summary charts. Render live telemetry and local history from repositories and the recorder. Omit sample sensor values and routes from the export.

## Android interface refinement

Place Record in the center of the five-tab bar. Keep the Start, Pause, Resume and Finish controls in a fixed action area, including when the map and workout metrics scroll. Keep Finish confirmation and place title editing, GPS accuracy and Discard under Activity options. Use the same vector icon style for navigation and prominent actions. Keep accessible names on icon-only controls.

Home opens with a seven-day distance chart from local activities, with each day identified for TalkBack. Show the map first in Maps and in activity detail. Collapse History search and filters into one control and show when filters are active. Use short state labels such as Offline, while retaining explanations where a person needs them to grant a permission or make a data decision.

Use short transitions for sport selection and disclosures. Honor Pace’s reduced-motion preference and Android’s animator setting.

The Android app uses the light lavender palette and light system-bar appearance even when Android is in dark mode. Profile exposes sign-in and registration directly, groups location and notification access under Permissions, and ends with Privacy and your data. Home workouts opens a separate builder for user-named exercises. Insights shows frequency in month groups with one small cell per day.

Home presents one workout recommendation and a daily steps card. The recording picker always offers all supported outdoor sports. The saved summary opens with the recorded route and offers an explicit share flow. The share image is composed on the device from the track geometry, distance, and time. The camera preview displays the stats before capture. An optional GPX share contains the original coordinates.

Step tracking is opt-in and uses the phone's step counter independently of GPS, with a foreground notification and a device-local Room total. The resizable Pace Steps widget can be added through Home or the Android widget picker. The tracker is sensor-dependent and resumes after normal reboot where Android permits it. A force-stop, withdrawn permission, or unsupported sensor prevents tracking until the app can resume it.
