# Phase 14 — Plans and calendar implementation progress

The Room model and repository layer for workout templates and planned sessions
were already present. This implementation enables the Plan destination and
adds a local month agenda that observes Room data, creates a named sport
session on a selected ISO local date, and tombstones planned sessions on delete.
The date is stored with the device time-zone identifier so later synchronization
can preserve the intended local day.

The continuation adds a Monday-to-Sunday week agenda with previous/next week
navigation, using the same Room calendar query and ISO dates. Saved templates
can now be scheduled on the selected date or edited by name and sport while
preserving their interval rows and creation time. Editing a template does not
rewrite a planned session's date or status.

The template form also supports ordered timed warm-up, work, rest, and cool-down
blocks. Durations must be positive seconds. Existing blocks load into the
editor and remain attached when template metadata is changed.
Repeat counts are editable per block and must also be positive.
Each block can also carry a positive bounded pace (seconds/kilometre), speed
(metres/second), or distance (metres) target. The Room repository rejects
incomplete, unsupported, or inverted target ranges.

Workout templates now include optional duration (seconds) and distance (metres)
targets. A version 5→6 Room migration adds nullable columns so existing
templates upgrade without losing data. The planner validates positive values.

Two packaged starter templates (easy run and recovery walk) are available
offline. Choosing one fills the local editor and leaves the plan editable
before the template and scheduled session are saved.

Users can enable an 8:00 local-time reminder for a planned session. The app
requests notification permission only when the user turns a reminder on.
WorkManager stores unique per-session work and reschedules it across reboot.
The app also recomputes enabled reminder times on reboot and timezone or clock
changes, using the session's local date in the device's current timezone. No
exact-alarm permission is used. Completed, skipped, missed, and deleted
sessions cancel or suppress their reminder.

Planned sessions can be rescheduled from the agenda by selecting a new ISO
local date. The update preserves the session ID, status, notes, completion
link, and original time-zone identifier. Room instrumentation source coverage
checks those fields across a reschedule. The agenda offers an undo action, and
the repository updates only the date and timestamp so a concurrent status
change is retained.

Planned sessions can be marked skipped and restored to planned. The calendar
record stays present, distinct from a user deletion. Past sessions can be
marked missed and restored to planned.

After an activity is saved, its completion view offers same-day, same-sport
planned sessions for explicit linking. Linking transactionally marks the
selected session completed and associates the activity ID. A Room test source
checks the completed status and activity association.

The week agenda now summarizes planned, completed, and skipped sessions for
the displayed ISO week.

## Additional planning flows

- Session cards open a detail dialog with the scheduled date, sport, status,
  target summary, and interval count. Planned sessions can start outdoor
  recording from the dialog or list, with the planned sport preselected.
- Home displays the next upcoming planned workout and offers a one-tap start.
- Templates have confirmation-backed soft deletion. Existing planned sessions
  remain in the calendar and clearly show when their template is unavailable.
- The week view copies upcoming sessions to the following week, gives copied
  sessions new IDs, clears reminders, and skips same-template/same-day entries
  already present next week.
- A long press and horizontal drag on a planned-session card shifts it by one
  local calendar day. The existing undo action restores its previous date and
  reminder schedule. The explicit date editor remains available to assistive
  technology and for larger date changes.
- The offline home workout library labels its bodyweight movements as needing
  no equipment.

## Remaining P14 acceptance

The month view is an agenda rather than a traditional grid. Core scheduling,
editing, reminders, copy, and rescheduling code is implemented. Manual phone
checks remain for timezone changes, reminder delivery through reboot, drag and
undo, planned workout start, recording completion linking, and history review.
Room instrumentation sources compile but have not been run on a device. The
roadmap gate remains pending those runtime checks.

## Verification

The combined P14/P15 code received passing `:app:compileDebugKotlin`,
`:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and
`:app:lintDebug` checks after the additional planner flows. A JVM test checks
the local-time reminder calculation. Room instrumentation and physical-device
runtime acceptance remain pending. No APK was packaged.

The reminder design follows [Android persistent background work guidance](https://developer.android.com/develop/background-work/background-tasks/persistent)
and [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).
