# V10 Acceptance and Verification

Scope: A1-A6 then B1-B4 from `Frontline_Product_Priorities_Codex.md`, based on repository V6 `e4e837a15ea704ed12b45377d2a8f54d63940fc6`. All local V7 unlock-code work is included. A7 balance/human playtesting and C1-C3 are explicitly excluded. No human enjoyment, retention or physical-phone accuracy claim is made.

## Reviewable Items

| ID | Implementation | Acceptance Evidence | Pending |
| --- | --- | --- | --- |
| A1 | Continue primary, briefing/New Attempt, confirmation on active restart/replacement, selection-only preservation | Scene tests compare exact serialized model on cancel/menu/resume; native touch/lifecycle flows | Physical tester observation of accidental replacements |
| A2 | Existing five-step tutorial extended with capture/reinforcement, percentage and king gain/loss practical gates; replay/skip; opening-sector prompts; optional advanced Rules | Tutorial tests cover gating, back, all percentages, staged state isolation, skip and retained completion; compact/tall renders | Uncoached first-capture and explanation checks with players |
| A3 | Actual aggregated events, sent/remaining previews, MAX, king-loss distinction and cap-loss reporting | Model event tests for arrival/interception/transfers/surrender/caps; selected preview and native Canvas captures | Player comprehension of feedback |
| A4 | First-large-map guide, fixed faction symbols, selected count in footer, pinch/pan cancellation | Actual multi-pointer Android tests at 480x800, 720x1280 and 1200x1920; nonblank canvas pixels; fit/zoom previews | Required physical-phone touch accuracy check |
| A5 | Pre-run target/PB, result time versus target and improvement; difficulty fixed per attempt; separate records; Legacy unchanged; one factual insight | Progress migration/records tests; Scene difficulty/briefing/result tests; V6 binary upgrade | Human assessment of goals/replay motivation |
| A6 | Default-off local 500-event log; explicit decisions, active elapsed and seed/rules/config snapshots; manual CSV | Log bounds/escaping/corruption tests, pause/no-abandon checks, native persistence/export integration | Stakeholder review of exported playtest samples |
| B1 | Separate nine missions across three configurable objective types; explicit success/failure and saved active counters | Hold resets, home-loss failure, budget boundary and save/resume tests; challenge navigation; campaign independence | Timer/budget tuning by players |
| B2 | Pressure/Guardian decision styles, independently gated from difficulty; legacy classic retained | Reproducible targeting, reserves and coordinated recovery scenarios; classic regression fingerprints; before-run style names | Readability and strategic variety with players |
| B3 | Date/version deterministic curated selection, Normal only, 00:00 UTC reset, bounded local bests | Calendar/seed/setup determinism, invalid date checks, midnight restoration and scene/native daily flow | Human return-play measurement; device-clock manipulation is not prevented |
| B4 | Crown Keeper, Normal Chapter, Objective Specialist, persisted once with visible progress and earned themes | Awards/history eligibility, three-type progress, theme gating and persistence; cosmetic model invariance | Replay interest before expanding catalogue |
| V7 | Settings code `12345`, native input/error/cancel/keyboard/button, persistent all-sector unlock | Portable V7 regression and real V6-to-V10 upgrade/native dialog at phone sizes | None identified in automated checks |

## Runners

```powershell
.\scripts\test.ps1
.\scripts\build-android.ps1
.\scripts\start-test-device.ps1
.\scripts\test-unlock-code.ps1
.\scripts\test-android-gestures.ps1
.\scripts\test-android-v10.ps1
```

The native runners modify only `emulator-5580`, not a connected physical phone. The V6 upgrade runner uses the actual previous APK plus deterministic overflow/legacy data. Binary formats FL01/FL02/FL03 migrate; FL04 adds validated metadata and exact RNG continuation. Tests/tools/instrumentation are not distributed inside the game.

Portable Java2D captures are layout previews, not Android screenshots. Native Canvas captures are labelled separately and published under `docs/screenshots/`. Debug builds require the original private signing key for an in-place update; keys and generated build artifacts remain ignored by Git.

## Human Checks Still Required

1. Install over the prior build on an actual Android phone without uninstalling. Confirm progress/settings and code unlock are retained.
2. On a dense map at fit and zoom, select edge tiles, deploy, pinch, pan and fit. Watch specifically for accidental dispatch, missed touch targets and readable selected counts.
3. New-player tutorial: observe capture/reinforcement, percentage tradeoffs and king loss without coaching; record confusion, skip and replay.
4. Play each challenge type and both AI styles. Collect whether objectives/styles are understandable, and whether mission timers/budgets allow meaningful decisions.
5. Review stars, records and badges; measure voluntary next/retry and later return choices with opt-in logs.

These are acceptance follow-ups, not completed playtests. The requested A7 balance study will be a separate task; V10 does not adjust king multipliers, caps, scoring or surrender based on theoretical concerns.

## Build Evidence

Verified 6 October 2026:

- `scripts/test.ps1`: **207,231 checks passed**, including all legacy regressions, model/objective/event/RNG checks, daily determinism, progress/log corruption, scene flows and 60-map simulations. Compact/tall/tablet preview rendering passed.
- `scripts/test-unlock-code.ps1`: actual V6 binary upgraded in place; historical settings/records retained and migrated battle state compared exactly. Empty/wrong/Cancel, keyboard/button, repeat/persistence, Sector 60 and small-phone code-dialog checks passed in airplane mode.
- `scripts/test-android-gestures.ps1`: native pinch/pan, drag cancellation and nonblank battlefield at 480x800, 720x1280 and 1200x1920 passed.
- Final `scripts/test-android-v10.ps1`: **334 checks per viewport, 1,002 total** at 480x800, 720x1600 and 1200x1920. Actual native attempt/challenge/daily/settings controls, lifecycle freeze/save, activity recreation, independently corrupted battle/progress/log, dense selected counts at fit/zoom and external CSV byte equality passed. Original emulator preferences and viewport settings were restored. The test-only package was removed.
- Native main/briefing/challenges/daily/mastery/settings/result and dense selection captures were visually reviewed. The duplicate floating aim count is suppressed on dense maps to avoid hiding MAX; the selected count and sent/remaining footer remain readable.
- APK: `build/FrontlineV10.apk`, **959,093 bytes**, package `com.frontline.offline`, version code **10**, version **0.10.0**, minimum API **24**, target API **35**, no Internet/storage permission. APK signing verification passed and the certificate matches V5/V6/V7: `33ea82b84a5fa09e52fbaa37769ec18539cc1adf5b80fa22246147b1bfe7996d`.

```text
SHA-256: A2FD951AACA1BE05295D87BC8E3809DC6CB81C8FF50E26541127A3A2FDE657EC
```

This is a debug-signed prototype for review, not a Play Store release. Physical-phone touch, manufacturer-specific keyboard/audio behavior, older Android versions, challenge tuning and human replay interest remain unverified. No automated check establishes enjoyment, balance or retention.
