# V13.1 Verification / 0.13.1

Current source version: `0.13.1` (version code `16`).

Status: automated verification and local APK generation passed on **2026-10-11**, after the user authorized testing/building the initially source-only patch. Production source remains the 0.13.1 UI update; this verification step adds test coverage and documentation, not game-rule changes. Historical [0.13.0 evidence](verification-v13.md) stays unchanged.

## Portable And Tool Checks

`scripts/test.ps1` passed **974,317 assertions** across 18 groups and completed Java2D preview rendering:

| Group | Assertions |
| --- | ---: |
| Model, saves, controls and 60-map simulations | 207,270 |
| Current release metadata | 15 |
| V13 layout, onboarding, caches and tablets | 10,866 |
| New illustrated screens and coverage spacing | 38,179 |
| Retained V12 screen coverage | 23,136 |
| V12.1 event semantics | 124 |
| V11 model | 94,219 |
| Objective/progress records | 3,001 |
| V11 scene | 182 |
| Localization / 572 complete keys per language | 10,507 |
| Balance Lab | 5,574 |
| Run state | 1,828 |
| Run scene | 2,224 |
| Local log | 653 |
| Routing factories | 194,984 |
| Logistics records | 10,461 |
| Logistics model | 369,503 |
| Logistics scene | 1,591 |

Focused illustrated tests cover all tabs at logical heights 620/780/1100 and minimum touch sizes 64/96 in all three languages; fixed actions, bounded scrolling, minimum/non-overlapping targets, interception replay, all deployment counts, king gain/loss limits, tutorial Skip return, replacement/cancel and byte-exact retained model/progress. Every mission's objective appears on the initial briefing tab. Daily retains its date/seed and Normal difficulty after manual/practice navigation. Lab-origin rules remain route-specific. Home's 14-unit coverage-to-action gap is checked at five heights and four target sizes without enabling Home scroll.

The Python CSV summary tool passed **36 unit tests**. Automated simulation samples and preview fixtures are not human playtest sessions or evidence of enjoyable balance.

## Native Android

`scripts/test-android-v13.ps1` passed **518,711 native assertions**, plus **one final preference-recovery check**, on the dedicated Android 15 emulator. Every configuration exercises English, Indonesian and Hindi:

| Requested display / density | Captured native View | Assertions |
| --- | --- | ---: |
| Small phone: 480x800 / 240dpi | 480x800 | 88,077 |
| Redmi-style: 720x1600 / 320dpi | 720x1600 | 77,661 |
| Enlarged display: 720x1600 / 420dpi | 720x1600 | 93,859 |
| Portrait tablet: 1200x1920 / 320dpi | 1200x1920 | 90,704 |
| Wide tablet: 1920x1200 / 240dpi | 1920x1200 | 81,443 |
| Larger wide tablet: 2560x1600 / 240dpi | 1920x1600 | 86,967 |

The larger override again produced a 1920x1600 app View, not a full-width 2560x1600 capture. The portable viewport suite covers the wider transform; real hardware behavior and physical inch sizes are not established by emulator overrides.

New native coverage exercises every Attempt/Manual tab, fully visible/partly clipped control registration, fixed actions, scrolling, actual font/ink bounds, accessibility labels and at-least-48dp non-overlapping targets. It taps interception/deployment/king examples, replays the tutorial from the manual, verifies return/replacement flow and exact battle/progress preservation. Retained coverage checks Home/progress, all campaign nodes, Settings/local log warnings, cached previews, countdown callback cleanup, feedback persistence, first-mode tutorial gating, wide battle swipes and shifted Pause input. Exact V7 `12345` unlock behavior is retained and checked.

Selected actual Canvas captures were visually inspected and copied to `docs/screenshots/v13-1/`. Full staged captures and native View/text reports remain under ignored `build/device/v13-native/`. The harness restored original preferences and display overrides, removed its separate instrumentation package, and the dedicated emulator was stopped.

## Execution Corrections

- A new portable test initially tried to open Lab rules from Pause, which has no such button. It was corrected to use the actual Lab Rules entry; the full suite then passed.
- A historical native assertion assumed Frontier Lab always appeared directly on compact Home. The added coverage spacing can correctly select the existing More fallback on short landscape views. The test now checks that fallback and Lab reachability; the complete six-configuration suite was rerun successfully.
- Windows sandbox restrictions initially blocked Java packaging and Python temporary-file access. Approved reruns completed successfully. No production-code fix was required by this verification pass.

## APK

- Local testing APK: `build/Frontline-0.13.1.apk`, identical to `build/Frontline-debug.apk`.
- Package: `com.frontline.offline`; version `0.13.1`, code `16`; minimum API 24, target 35.
- APK v2/v3 signature verification passed. Certificate SHA-256: `33ea82b84a5fa09e52fbaa37769ec18539cc1adf5b80fa22246147b1bfe7996d`, matching the preserved 0.13.0 APK's original prototype key.
- APK SHA-256: `1cf8a9937aaf216d3cb3ca3652eb55e694f876f4a5d84f6c959b9de947b98eb5`.
- The manifest declares no permissions, including no network permission. This is a debug-signed local testing APK, not a Play Store build or new public GitHub release asset.

## Remaining Human Checks

Physical Redmi 13C performance/touch feel, hardware tablet sizing/rotation, fluent translation review, full TalkBack usability, real previous-APK upgrade flows, document-picker export/cancellation and perceived music quality remain device/human checks. Older native suites were not all rerun. Automated native captures and desktop checks do not prove lag-free physical hardware or enjoyable gameplay.
