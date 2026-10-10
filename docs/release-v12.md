# V12 Step 1 / 0.12.0

Historical release snapshot. Current code-only changes are documented in [Step 2 / 0.12.2](release-v12-step2.md); the verification and APK described below apply only to 0.12.0.

This release implements only the finalized dark Home, Campaign and Settings design. It is the first step of V12, not authorization to begin subsequent features.

## Changes

- Command Deck: original game icon/brand, top-right gear, Daily below it, real saved-battle preview and factual coverage/time, Continue/New Attempt, Classic Run/Missions, Frontier Lab and familiar navigation icons.
- A fresh installation displays the selected map and actual three-star target, not the mockup's sample coverage or elapsed time. A retained mission, Run or Logistics round is represented and resumed as that actual mode.
- Campaign: connected six-sector chapter path with cleared/current/locked states, real difficulty-specific or explicitly Legacy/Historical records, ten-chapter navigation and a separate Play/Continue action. Selecting a node does not erase or replace a retained round.
- Settings: full-page audio/haptics, next-battle difficulty, expandable offline language picker, unchanged Enter Code, collapsed Playtest Tools and correct Back context from Home, Campaign, pause and battlefield.
- Content scrolling is separate from gameplay/camera gestures. A scroll that starts on a button cancels activation; fully visible controls have non-overlapping targets. Android accessibility supports screen scrolling.
- All three footers show exactly `0.12.0`; their old footer summary is removed.

## Retained Behavior

V7's exact `12345` code still unlocks all sixty sectors without marking them cleared. All V11 campaign/mission/Daily/Run/Logistics rules, saved armies, local records, progress, audio controls and offline languages remain. Battle-rule/config/save versions stay at their existing values: UI release `0.12.0` does not invalidate V11 records or save formats.

## Testing Build

Local APK: `build/FrontlineV12.apk`. Android package `com.frontline.offline`, version code 12, minimum API 24, target API 35. Signed with the unchanged prototype key; install over the existing app and do not uninstall first. No Internet permission. This remains a debug-signed testing build, not a Store release.

Code, native screenshots and verification are committed/pushed for this step. No new GitHub release or APK asset is published in this step. Further implementation waits for the next user-approved step; update the release number after each subsequent step/release.

Evidence: [verification](verification-v12.md), [screenshots](screenshots.md), [architecture and flow](architecture.md).
