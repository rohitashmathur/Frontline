# V13 Verification / 0.13.0

Current source version: `0.13.0` (version code `15`).

Status: automated verification and local APK build passed on 2026-10-11. This report covers the final V13 source and APK; historical V12 counts are not counted again as V13 evidence.

## Portable And Tool Checks

`scripts/test.ps1` passed **935,983 assertions** across 17 groups:

| Group | Assertions |
| --- | ---: |
| Game model, saves, controls and 60-map simulations | 207,270 |
| Current release metadata | 15 |
| V13 layouts, onboarding, caches and tablets | 11,337 |
| Retained V12 screen coverage | 23,436 |
| V12.1 event semantics | 124 |
| V11 model | 94,219 |
| Objective/progress records | 3,001 |
| V11 scene | 182 |
| Localization / 509 complete keys per language | 9,581 |
| Balance Lab | 5,574 |
| Run state | 1,828 |
| Run scene | 2,224 |
| Local log | 653 |
| Routing factories | 194,984 |
| Logistics records | 10,461 |
| Logistics model | 369,503 |
| Logistics scene | 1,591 |

Home was checked at logical heights 620, 680, 780, 900 and 1120, with minimum target sizes 64, 70, 82 and 96, in all three languages, both fresh and retained-battle states. Checks cover initial best total/progress visibility, no Home scrolling, non-overlapping targets, minimum sizes, reachable compact More actions, truthful Run/Daily status and unchanged model/record bytes. Tablet transforms were also checked at 1200x1920, 1920x1200, 1600x2560 and 2560x1600, including real-model troop dispatch and shifted Pause input.

First-mode tests cover Campaign, Run, Missions, Challenges, Daily and Lab; Skip/Complete continuation uses the existing seen flag, duplicate Skip is inert, Back does not mark completion, and a retained battle resumes without changed model bytes. Recognition caches are exact, bounded and language-separated; repeated static battle labels cause no additional recognition misses after warmup.

The independent Python CSV tool passed **36 unit tests**. Coverage includes real CSV quoting/multiline fields, exact scoped deduplication, collisions, build/rule segmentation, incomplete attempts, pauses/abandonments/results, timing/ratings, first-mode tutorial intent, malformed/legacy context, storage-cap warnings and input/output safety. A CLI smoke check also processed an existing Java-generated V10 export: 24 rows preserved, zero identifiable attempt segments; missing attempt identity was not invented. These are software fixtures, not observed player sessions.

## Native Android

`scripts/test-android-v13.ps1` passed **217,565 native assertions**, plus one final preference-recovery check, on the dedicated Android 15 emulator. Each configuration covers English, Indonesian and Hindi:

| Requested display / density | Captured native View | Assertions |
| --- | --- | ---: |
| Small phone: 480x800 / 240dpi | 480x800 | 38,664 |
| Redmi-style phone: 720x1600 / 320dpi | 720x1600 | 26,187 |
| Enlarged display: 720x1600 / 420dpi | 720x1600 | 41,109 |
| Portrait tablet: 1200x1920 / 320dpi | 1200x1920 | 38,123 |
| Wide tablet: 1920x1200 / 240dpi | 1920x1200 | 37,207 |
| Larger wide tablet: 2560x1600 / 240dpi | 1920x1600 | 36,275 |

The larger override produced a 1920x1600 app View in this emulator, not a full-width 2560x1600 native capture. The portable transform covers 2560x1600; exact full-width native hardware behavior still needs checking. The tablet labels describe intended display classes, not measured physical inch sizes.

Native checks include actual Canvas rendering/glyphs/text bounds, visible Home branding, at-least-48dp targets, no overlapping controls, campaign selection, settings/log warnings, inactive-preview caching, timer cleanup, feedback persistence, first-mode Lab tutorial/Skip persistence, expanded-board troop swipes, shifted Pause and bounded text-width cache. Screenshots were visually inspected and selected captures copied to `docs/screenshots/v13/`. Full captures and View-size reports remain in ignored `build/device/v13-native/`.

The harness restores original preferences and display overrides and removes its separate test package. It never targets a physical phone. Other historical native runners, old-APK upgrade flows and real document-picker interactions were not all rerun for V13.

## Baseline Performance Probe

`RenderBenchmark` measured the prior 0.12.2 compiled presentation with a no-op graphics adapter, 80 warmup frames and 400 measured renders per language/screen. Microseconds per render: English Home 212.64, Settings 13.68, Battle 53.07; Indonesian 764.22, 169.90, 282.19; Hindi 635.94, 170.43, 225.67. This is desktop render CPU, not Redmi frame rate or Android shaping time. The probe does not update simulation.

The same probe on final V13 source returned:

| Language | Home us | Settings us | Battle us |
| --- | ---: | ---: | ---: |
| English | 227.06 | 13.97 | 47.73 |
| Indonesian | 119.30 | 11.40 | 43.05 |
| Hindi | 91.33 | 11.11 | 38.07 |

The localized battle path was about six times faster in this limited probe. Home composition also changed, and timings include JVM warmup/order/noise, so these numbers are not a general speedup promise or proof that Redmi gameplay lag is eliminated. Native shaping, drawing and simulation require a physical-device performance check.

## Local Build And Delivery

- APK: `build/FrontlineV13.apk`, also produced as `build/Frontline-debug.apk`.
- Identity: `com.frontline.offline`, `0.13.0`, code `15`; minimum Android API 24, target 35.
- Signature verification: APK v2/v3 passed. Debug certificate SHA-256: `33ea82b84a5fa09e52fbaa37769ec18539cc1adf5b80fa22246147b1bfe7996d`.
- APK SHA-256: `2badcbca6fef0e2e4f0fdd82cfbe77c948cb1927a48daffec5e89b9c98762fd3`.
- Manifest declares no permissions, including no network permission. English, Indonesian and Hindi use the bundled Java catalogue rather than Android resource locale folders.
- GitHub About now describes the current offline Android scope and clearly labels iOS/backend as future placeholders. Current screenshots, architecture, playtest kit and tool documentation are included in source delivery. No new public APK release asset is created.

## Remaining Human Work

Physical Redmi 13C performance/touch feel, 8-13-inch hardware/rotation, fluent language review, TalkBack usability, old-APK upgrade, document-picker export/cancellation, music quality and observed enjoyment remain device/human checks. Automated Canvas captures and desktop timing are not substitutes.
