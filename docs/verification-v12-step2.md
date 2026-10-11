# V12 Step 2 Verification / 0.12.2

Current source version: `0.12.2` (version code `14`).

Status: **PASSED within the automated scope below**, verified on 2026-10-11. The initial source pushes deliberately omitted tests/builds; this separate user-authorized verification step compiled the current code, ran the portable and expanded native V12 suites, generated previews/screenshots and created a signed local testing APK. No production gameplay code needed changing.

## Portable Regression

`scripts/test.ps1` passed **931,470 checks** across sixteen suites/check groups, and portable preview rendering completed.

| Suite | Checks |
| --- | ---: |
| GameModelTest, including shared/V6/V7/V10 regressions | 207,266 |
| DocsVersionTest | 15 |
| V12ScreensTest | 30,276 |
| V12PlaytestTest | 124 |
| V11ModelTest | 94,219 |
| V11ProgressTest | 3,001 |
| V11SceneTest | 182 |
| LocalizationTest | 9,569 |
| V11BalanceTest | 5,574 |
| RunStateTest | 1,828 |
| V11RunSceneTest | 2,224 |
| V11LogTest | 653 |
| LogisticsRoutesTest | 194,984 |
| LogisticsRecordsTest | 10,461 |
| V11LogisticsModelTest | 369,503 |
| V11LogisticsSceneTest | 1,591 |

All three offline languages have 508 complete keys. Coverage includes combat/caps/interception, sixty maps, legacy saves/progress, V7 unlock behavior, protected replacement, objectives/Daily, Run and Logistics. Current screen tests cover 100 consecutive preview redraws, sector/difficulty cache replacement, active-model immutability, earned progress versus code unlocks, UTC Daily boundaries, Run details, shared trail positions and log sizes 0/449/450/500 with logging disabled. The 124 playtest checks cover event boundaries, first dispatch, opt-in/restore/feedback guards, Run milestones and refused routes. Current metadata is checked exactly; historical documents are excluded from the version guard.

The first run found a stale `Cleared / Legacy` expectation. It was updated to the intentionally approved `Cleared / previous rules` wording, then the full suite passed. No game rule or record policy was changed to satisfy the test.

## Native Android

The final expanded `scripts/test-android-v12.ps1` passed **153,977 checks** on dedicated Android 15 `emulator-5580`:

| Display Configuration | Density | Checks |
| --- | --- | ---: |
| Small phone, 480x800 | 240 dpi | 43,030 |
| Tall phone, 720x1600 | 320 dpi | 25,317 |
| Tablet, 1200x1920 | 320 dpi | 43,087 |
| Wide display, 1200x800 | 320 dpi | 42,543 |

Each configuration exercises English, Indonesian and Hindi. The app retains portrait orientation; the wide configuration covers Android's constrained portrait window, not a new landscape layout. Checks cover native text ink/non-overlap, nonblank Canvas captures, localized accessibility nodes, visible targets of at least 48dp, native touch/scroll cancellation, settings return context, all sixty reachable sector nodes, ribbon navigation and byte-identical retained battle/record data.

Expanded checks cover Sector 46/60 previews with 100 native redraws each and immutable cached/retained models, full Frontier Lab reachability on the compact configuration, warnings at 450/500 with logging off and manual clear preserving opt-out, Home callback scheduling/removal on navigation and stop, and native feedback submission in three languages. Ratings, duplicate/stale rejection, exact terminal battle bytes and persistent guards are checked after Activity restart. This is not a physical-phone lifecycle/stress test or a fresh-process rating test.

The new harness initially needed its reflection call to include the localization varargs array; it was corrected and the expanded suite rerun successfully. Original emulator preferences/display overrides were restored, the separate test-only package removed and the emulator stopped. Fixtures never target a physical phone. Full captures/reports are in ignored `build/device/v12-native/`; selected current captures are committed in the [gallery](screenshots.md).

## APK Identity

| Field | Value |
| --- | --- |
| Local testing APK | `build/FrontlineV12_2.apk` |
| Build-script output | `build/Frontline-debug.apk` (identical copy) |
| Size | 1,041,013 bytes |
| Package | `com.frontline.offline` |
| Version | `0.12.2` / code `14` |
| Minimum / target SDK | 24 / 35 |
| Network permissions | None |
| Signature | Verified v2 and v3; original prototype debug signer |

APK SHA-256:

```text
242fc41414e9eb5af5fa9efd5d11caf9c38ae3b73fad1ab12391182b1d021fa0
```

Signer certificate SHA-256:

```text
33ea82b84a5fa09e52fbaa37769ec18539cc1adf5b80fa22246147b1bfe7996d
```

Packaging required an out-of-sandbox retry after the native Android packaging tool crashed in the sandbox. The successful build compiled app sources, verified the APK signature and installed on the emulator. Install over an original-key prototype; do not uninstall first. Toolchains, keys and APKs remain excluded from Git. No GitHub release asset is published.

## Remaining Checks and Deferrals

- Physical-phone touch/scroll feel, TalkBack usability, fluent-speaker review, music quality, OEM-specific behavior and longer-session playtesting remain human work.
- Dedicated older-APK upgrade, external document-picker export/cancellation and fresh-process rating scenarios were not rerun in this step. Existing portable migration/CSV tests pass; that does not replace those native scenarios.
- UTC completion/reset boundaries are tested with an injected clock in portable checks; the native callback is checked for scheduling/cleanup, not by waiting for a real midnight.
- Daily streaks and the proposed Linux runner remain deferred. No later V12 feature phase was started.
- [Step 1 verification](verification-v12.md), `build/FrontlineV12.apk` and the old V12 gallery remain unchanged 0.12.0 evidence. The original 0.12.1 APK was never built; current evidence applies to its retained code in 0.12.2, not a historical binary.
