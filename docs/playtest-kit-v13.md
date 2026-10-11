# V13 Observed Playtest Kit

Status: session protocol, not evidence that human playtests have happened.
Budget: 20-30 minutes per anonymous session. Use a small exploratory sample;
include readers of the tested language and different device sizes where possible.
The observer watches and records; do not coach before the tester tries an action.

## Permission And Opt-In

Suggested plain-language wording, not a legal consent form:

"Would you like to try this test build for about 25 minutes? You can stop or skip
any task. With your permission, I can enable a local gameplay event log and ask
you to share its CSV afterward. It records game actions, timestamps, build and
battle settings, not your name. Logging and sharing are separate choices. We can
do the session without the log. Please do not put personal information in notes."

Record yes/no separately for participation, local logging and CSV sharing. Ask
separately before any recording or quotation; neither is required. Say who will
receive the files and the planned deletion date. Use an anonymous ID such as
S01-D01, not names, email, account IDs, serial numbers or advertising IDs.

Enable logging only after permission: Settings > Playtest Tools > Local Playtest Log.
Do not assume installing the build enables logging. Turning it off does not erase
old events. Clear only after a verified export, or when the tester asks to remove
the local history. Sharing/export is manual and controlled by the tester.

## Before The Session

- Record the footer's build name and the build code from exported context when
  available, Android version, device class, approximate screen
  size/orientation, language and chosen difficulty. Keep device labels anonymous.
- Identify fresh-install versus upgrade versus restored-battle test. For upgrades,
  install over the existing app without uninstalling or clearing app data. Note
  the old build and whether progress, language, tutorial-seen state and active
  battle survive. Do not change a tester's real device state without permission.
- Use a separate fresh test profile/device for first-launch testing. Clearing the
  event log does NOT reset tutorial-seen state. Export any existing log before
  clearing it; never erase the only copy of earlier sessions.
- Verify the system document picker can save a CSV and the observer can read the
  saved file. Give each session a distinct anonymous filename, for example
  S01-D01-final.csv. Note that export captures the log when Export CSV is tapped,
  not later when the document picker finishes.
- Check Settings > Playtest Tools entry count. At 450/500, export a checkpoint immediately.
  At 500/500, oldest events are replaced; an export may already lack starts or
  tutorial events. Retain the warning in notes, never claim a complete session.
- Clear verified prior-session history between sessions, ideally with no active
  battle. If exporting overlapping checkpoints during the same session, preserve
  them all and use the same explicit analysis scope. Clearing mid-battle can
  produce a result-only attempt; record it and do not invent the missing start.

## Session Script

1. Minutes 0-3: permission, anonymous device/build record, opt-in and log check.
   Ask: "Start playing as you normally would." Record the entry path they choose.
2. Minutes 3-8: first-launch tutorial observation. V13 acceptance requirement:
   before the first live dispatch on a fresh profile, the tutorial must appear
   through every first-battle entry path, including Run and Logistics. Record
   actual behavior as pass/fail; automated gate checks do not establish usability.
   Let the tester choose completion or Skip Tutorial. In the current event
   contract both choices set tutorial-seen; only tutorial_complete means completed.
   tutorial_skip means skipped, then V13 returns to the chosen mode or briefing.
   Starting a battle still requires that mode's normal start action.
   Check that skip is available, does not pretend to complete practice, and does
   not immediately force the tutorial again. Upgrade profiles may already be seen.
   Ask: "What do you expect your first action to do?" Observe the first dispatch
   and deployment fraction choice. Record tutorial assistance separately from
   battle assistance. Test the alternative skip path in a separate fresh session,
   not by resetting the same participant midway.
3. Minutes 8-15: play one campaign battle and one short mission if time permits.
   Ask the player to explain the objective before starting and the result after.
   For Hold/Keep King, distinguish completing the timed objective from conquering
   the board. For Troop Budget, check understanding that friendly reinforcements
   spend budget and excessive release can lose. Do not manufacture a defeat.
   Offer the optional Too easy / About right / Too hard rating only when available;
   Not now is valid missing feedback.
4. Minutes 15-22: choose ONE experimental task, Run or Logistics. In Run observe
   one battle and a council choice if reached; do not require five battles in the
   time budget. In Logistics ask for a connected friendly reinforcement, an
   impossible route and an explanation of any interruption. Note actual decisions,
   route refusal and confusion. Record unfinished work as observer cutoff, not loss.
5. Minutes 22-25: ask the tester to find Settings and Daily, change to a language
   they read, return to the battle, pause/resume and reopen the app if comfortable.
   Note assistance, clipping, mistaken taps and whether saved state survives.
   Do not infer successful navigation merely from home_tap or language_change.
6. Minutes 25-30: ask "What was unclear?", "How did the difficulty feel?" and
   "Would you play again, and why?" Stop at the agreed budget. Record final state
   as victory, objective success, explicit loss, confirmed abandon, paused or
   unfinished/cutoff. An observer time limit is never a telemetry loss.

If the session must be 20 minutes, shorten free play and use one mission or one
experimental task; do not rush permission, first-launch observation or export.

## Anonymous Sheet (Copy Per Session)

| Field | Entry |
| --- | --- |
| Session / device scope | S__-D__; fresh/upgrade/reset epoch; export filenames |
| Permission | Participate Y/N; local log Y/N; share CSV Y/N; recording/quote Y/N |
| Build / upgrade | Visible name/code; prior build; migration observations |
| Device / language | Android; device class; screen/orientation; readable language |
| Log coverage | Before/after row counts; 450/500 warning; checkpoint/clear/opt-out |
| First-launch entry / gate | Campaign/mission/daily/run/logistics; gate pass/fail/not tested |
| Tutorial | First/replay; complete/skip/back-out/unknown; assistance; observed minutes |
| Battle context | Mode; map/sector/mission; difficulty; objective in player's words |
| First dispatch | Observed wall time; successful command; assistance; fraction choice |
| Final state | Victory/objective success/loss/abandon/paused/unfinished; explicit reason |
| Difficulty (optional) | Too easy / About right / Too hard / Not now / not offered |
| Navigation / migration | Task reached? pause/reopen state; wrong taps; language issues |
| Replay intent | Yes/no/unsure; short anonymous paraphrase or permitted quote |
| Evidence / follow-up | Observation separate from interpretation; exact export files |

Score each observed task (objective explanation, first dispatch, reinforcement,
navigation and result explanation): 0 = could not do/explain, 1 = succeeded with
help, 2 = independent, N/T = not tested. N/T is not zero. For each score record
one concrete observation and whether the tutorial or observer supplied help.
Do not average this ordinal rubric into a balance or usability claim.

## After The Session

1. Ask again before sharing. Export via Settings > Playtest Tools > Export CSV. Save with
   the anonymous session/device label; verify the CSV has a header and expected
   event count. Keep checkpoints, original exports and observer sheets unchanged.
2. Export EVERY consenting logged session, even when unfinished or unenjoyable.
   If no log was enabled, record that fact; do not fabricate events. Export before
   clearing, reinstalling, resetting the device or starting the next session.
3. Assign a new scope for each device/reset epoch and independent session. A raw
   attempt_id is not a global participant/device identity. A resumed battle across
   checkpoint exports needs the same scope; unrelated sessions or restored/cloned
   devices do not. See playtest-summary-v13.md for exact deduplication rules.
4. Run the summary tool into a separate output directory. Review missing starts,
   missing ends, malformed/legacy rows, collisions and the cap warning FIRST.
5. Compare first_dispatch_active_seconds with observed wall time, tutorial
   complete/skip by observed tutorial starts, mode starts/results, optional rating
   coverage and navigation counts. Keep build/rules/mode/difficulty buckets apart.
   Report numerators, denominators and untested tasks alongside any interpretation.
6. Keep files only for the disclosed period and access group. Record findings as
   exploratory human feedback; automated checks and this kit are not human results.
