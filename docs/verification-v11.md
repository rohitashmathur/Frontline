# Frontline V11 Delivery Report

Release: **V11 / 0.11.0**, version code 11, Android 7.0+ (API 24), target API 35. Review date: 9 October 2026. The `v0.11.0` release tag identifies the complete implemented revision. Historical baseline: `f05d81c`; phase 1: `6fc1b66` plus isolated-test repair `f8b56db`; phase 2: `4af4d9c`; phase 3: `b5748a8`; phase 4 is the release revision containing this report. Reports generated before commits explicitly use working-tree labels rather than invented commit hashes.

## Completion

| Requirement | Status / changed components | Evidence |
| --- | --- | --- |
| P1-03 tutorial logging | Implemented: exclusive terminal events and duplicate guards in GameScene | Skip/replay/complete/pause/disabled-log scene tests |
| P1-04 failure feedback | Implemented: GameModel terminal reasons, shared integer deployment preview and localized warnings | Budget boundaries, friendly reinforcement, invalid actions, restored causes, native warnings/results |
| P1-02 objective records | Implemented: ObjectiveResult and versioned Progress policies | Hold elapsed PB; Keep completion-only; Budget troops/time tie-break; Daily uses same policies |
| P1-01 active defence | Implemented: versioned mission-local Home/Province pressure in Challenge/GameModel | Zero passive-gate violations; both legal active approaches succeed on representative Normal seeds |
| P2-01 player copy | Implemented: GameScene/Localization remove engineering placeholders/raw Daily tokens | Native Challenges, Daily, briefing and result fixtures |
| P2-03 Settings gear | Implemented: top-right home control and native accessible label/48 dp target | Three-size bounds, hit tests and virtual accessibility nodes |
| P2-05 Daily placement | Implemented: separate entry immediately beneath Settings | Native available/completed states; protected attempt and UTC reset checks |
| P2-04 languages | Implemented: 460 complete keys each in English, Indonesian and Hindi; immediate persisted selection | Catalog/placeholders, native glyph/layout checks, separate-process language recovery |
| P2-02 documentation | Implemented: README, current rules, architecture/flow diagrams, screenshots and phased reports | Historical reports remain labelled as historical |
| Additional equal army request | Implemented for new battles across every map/difficulty/faction | Entire initial armies are equal; existing saved troops are not rewritten |
| Phase 2 Balance Lab | Implemented with actual model, raw results, matched inputs and checklist | 720 Classic attempts; reproducible output; human sessions pending |
| Phase 3 Classic Run | Implemented: five frozen battles, four councils, five non-stacking perks, one retry and resumable transitions | 1,828 state / 2,224 scene checks; 14,625 native checks, including three process checkpoints |
| Phase 4 Logistics | Implemented as separate experiment: three maps, legal paths/ETA, transit combat and isolated records | 194,984 route / 369,503 model / 10,461 record / 1,591 scene checks; 16,421 native checks |

Details: [phase 1](verification-v11-phase1.md), [phase 2](verification-v11-phase2.md), [phase 3](verification-v11-phase3.md), [phase 4](verification-v11-phase4.md).

## Final Verification

- `scripts/test.ps1`: 900,359 assertions across 13 suites passed. The shared renderer also passed campaign, chapter, tutorial, camera, localized mission, Run and Logistics fixtures. Assertion counts include repeated simulation/round-trip checks; they are not counts of human sessions.
- `scripts/build-android.ps1`: the final integrated APK compiled, aligned, signed and verified. Only expected Java 8 bootstrap/deprecated-API warnings were emitted.
- `scripts/test-android-v11.ps1`: final APK passed 24,013 suite assertions and 612 combined language/process-relaunch assertions per viewport: 73,875 total, excluding cleanup.
- `scripts/test-android-v11-run.ps1`: final integrated APK passed 14,625 assertions at all three viewports, preserving its earlier phase-3-only outcomes.
- `scripts/test-android-v11-logistics.ps1 -RunDevice`: final APK passed 16,421 assertions at all three viewports, including exact mid-route process recovery and isolated corruption handling.
- All native tests used only Android 15 emulator-5580 at 480x800/240 dpi, 720x1600/320 dpi and 1200x1920/320 dpi. English, Indonesian and Hindi were exercised. Preferences/display overrides were restored. Outcomes are explicitly staged fixtures, not organically won games.
- `DefenseBalance` was rerun against final model sources; its summary was byte-identical. All 720 rows of the final Classic lab rerun matched the committed phase-2 CSV after excluding revision labels. The routing comparison verified byte-identical reruns. Timeouts are never inferred wins.

The final native groups total **104,921 assertions**, excluding recovery cleanup. Native Hindi ink/shaping checks passed; this is not fluent-speaker approval. The old V6 APK installation-upgrade script and manufacturer-specific audio/performance tests were not rerun for this release. Legacy binary/profile migrations, exact continuation and preserved unlock behavior have focused portable/native coverage.

## Balance and Remaining Work

Home and Province passive Normal/Hard wins are now 0/20 independently in development seeds 0-19 and holdout seeds 1000-1019. Before, development Normal was 16/20 and 17/20; Hard was 20/20 for both. Easy remains 20/20. Veiled is reported separately and not pressure-tuned. Fortification and counterattack both succeed on Home seed 0 and Province seed 13. Province's simple counterattack controller wins only 2/20 development and 1/20 holdout attempts; reasonable human difficulty remains unresolved. Full denominators: [defence report](../tools/balance-reports/v11-defense/summary.md).

The Classic lab recorded 256 natural completions and 464 timeouts in 720 attempts, without resignation-enabled completions. The routed comparison recorded 324 attempts / 162 matched pairs and verified a byte-identical rerun. Routing lengthened both-completed pairs and increased timeouts on the larger maps: Crown Circuit routed 46/72 versus Classic 27/72. See [routing evidence](verification-v11-phase4.md). These results do not demonstrate enjoyment or justify migrating Classic.

Human sessions, fluent Indonesian/Hindi review, physical-phone testing, native performance/audio measurements, Run's proposed 10-15 minute length and routing comprehension/enjoyment remain pending. [Playtest checklist](playtest-v11.md) is ready. No backend, login, cloud, multiplayer, advertising, monetization or automatic Classic migration was added.

## Compatibility and Package

V7's exact `12345` setting remains: all 60 sectors unlock without being marked completed. Existing campaign/mission/mastery/settings remain; old attempt rules, army counts, timers, difficulty, seed/RNG and Daily date stay frozen. Run and Logistics rewards/records do not leak into campaign. Classic keeps direct targeting and FL01-FL05 support; routed saves alone use FL06.

Testing APK: `build/FrontlineV11.apk`, package `com.frontline.offline`, 1,024,629 bytes. APK SHA-256: `63bb5e6193a8ae84077c8bb5eb6f71bacb5ada044f93cf10fb28cc0aa18f8632`. Original prototype certificate SHA-256: `33ea82b84a5fa09e52fbaa37769ec18539cc1adf5b80fa22246147b1bfe7996d`. APK v2/v3 signature verification passed; the manifest has no Internet permission.

Install over the existing prototype to retain local data; do not uninstall first. This is debug-signed and experimental, not a Play Store production release. Signing keys/toolchains/generated packages remain excluded from Git.
