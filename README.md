# Frontline

An original offline Android territory-conquest game. **V6 / 0.6.0** has 60 sectors across ten story chapters, up to five computer opponents, local scores/progression, music, an interactive tutorial and saved battles. No login, backend, network permission, ads or telemetry.

## Install

[Download Frontline V6 APK](https://github.com/rohitashmathur/Frontline/releases/tag/v0.6.0) from the private repository's release assets. Android 7.0 or newer is required. Local build output is `build/Frontline-debug.apk`; the versioned distribution copy is `build/FrontlineV6.apk`.

Install over V5 to retain local progress; do not uninstall first. The package and original prototype signing key are unchanged. Existing overfilled V5 garrisons are clamped to the new 100/125 limits, while armies already in flight retain their units.

## V6 Changes

- Five-step How to Play includes an interactive Capture King/Lose King booster demo, previous-step navigation and tappable 25%/50%/100% deployment examples.
- A compact boost strip remains visible above the battlefield; king changes update and pulse it without moving the board.
- Held enemy king bonuses: 1 = 1.2x, 2 = 1.44x, 3 = 1.728x, 4 = 3x, 5 = 3.6x. Your original king is excluded. Bonuses apply team-wide only while held.
- Thirty new sectors, five new chapters, Iron Dominion and Frost Union factions, and larger battlefields with zoom, pan, pinch and fit controls.
- Normal/Hard opponents prioritize recovering their original king, including coordinated attacks, while keeping defensive reserves.
- Fixed maximum garrisons: ordinary 100, king 125. Team saturation no longer enables overflow growth. Reinforcements respect the same caps.
- Above 90% player coverage for ten sustained simulation seconds, hopeless rivals can resign after a conservative check of all remaining garrisons and convoys. Their tiles become yours; a rival with a recovery opportunity continues fighting.
- A thicker coverage bar with whole-number team percentages, no decimals, and 100% shown at full control.

## Documentation

- [Application Architecture and Flow Diagrams](docs/architecture.md)
- [Complete V6 Game Logic and Balance Rules](docs/game-rules-v6.md)
- [Native Android Screenshots](docs/screenshots.md)

<img src="docs/screenshots/battle-five-opponents.png" width="240" alt="Frontline V6 with five opponents" /> <img src="docs/screenshots/tutorial-boost.png" width="240" alt="Interactive king booster tutorial" />

## Play

Drag from a green tile to attack or reinforce another tile. Choose 25%, 50% or 100% deployment. King tiles grow faster; captured enemy kings boost your team's production. Hostile armies meeting in flight cancel one-for-one, while friendly armies pass through. Eliminate every rival territory/army or force hopeless rivals to resign.

On large maps, +/- changes zoom and the fit icon restores the whole board. Two-finger pinch/drag pans and zooms; when zoomed, dragging from a neutral/enemy/empty area pans instead of deploying. One-finger drags starting on your own tile still send troops. Long-press tool icons for their names.

Use the main menu to Play, Resume, choose a sector, replay How to Play or change settings. Scores, stars, best times, unlocks, settings and unfinished battles are saved locally. Winning unlocks the next sector. Pause/Main Menu freezes and retains the round; Play/Restart begins a fresh attempt. Uninstalling or clearing app data removes saves.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `app/` and root Gradle files | Native Android app, campaign, portable battle rules and assets |
| `scripts/` | Windows setup, build and Android/portable verification |
| `tests/` | Core and V6 regression tests |
| `android-tests/` | Separate test-only Android multi-touch instrumentation |
| `tools/` | Rendering adapters, deterministic test saves and original music generation |
| `docs/` | Architecture, diagrams, V6 rules and native screenshots |
| `ios/` | Future iOS placeholder; not implemented |
| `backend/` | Future optional service placeholder; not implemented |

The working Android project stays at the root of this monorepo. Assets, campaign content and specifications can inform an iOS port, but native Java does not directly build for iOS. The offline game does not require a backend. No open-source license has been added.

## Build on Windows

Run PowerShell from the repository root:

```powershell
# Review Google's Android SDK terms before using the acceptance switch.
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-android.ps1 -AcceptSdkLicense
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-android.ps1
```

Setup downloads a checksummed Temurin Java 17 runtime and Google's Android SDK tools into `.toolchain/`, without changing global environment variables. The local build compiles Java, generates the original 20-second WAV loop, packages resources/DEX, aligns and signs the APK, and verifies its signature.

Alternatively, open the Gradle project in Android Studio using Java 17, Android SDK 35 and Gradle 8.11.1. The PowerShell build does not require Android Studio or Gradle.

**Signing:** SDKs, signing keys, credentials and generated packages are excluded from Git. Back up the original prototype debug key privately. A fresh clone creates a different debug key, so its APK cannot update an installation signed by the original key. Publication needs a separately managed release key; never commit it.

## Verification

The portable suite runs all 60 maps on all three difficulties and checks dispatch, production/caps, capture/reinforcement, swept interception, packet accounting, corruption recovery, V4/V5 migration, six-team saves, held-king milestones, guarded resignation, AI reserves/recovery/coordination, progression, tutorial and camera input. Java2D renders the same scene commands at compact, tall and tablet sizes and rejects text exceeding the viewport.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-emulator.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-test-device.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\device-smoke-test.ps1
```

The native smoke test uses Android 15, airplane mode and the named `emulator-5580`. It verifies the five-step tutorial, expanded quarter swipe, music/lifecycle, saves, ten chapters, five rivals, zoom/pan/fit, king bonuses, caps, resignation, restart, campaign ending and V5 upgrade. It then calls `test-android-gestures.ps1` to test real multi-pointer events, pinch/pan drag cancellation and nonblank native battle pixels at phone/tablet sizes using a separate test-only APK, which is removed afterwards. Put the actual previous `FrontlineV5.apk` in `build/` for the real binary upgrade check; otherwise legacy save fixtures still verify migration. Captures go to `build/device/`; test fixtures/tools are not packaged in the game APK. Stop the test emulator after use.

See [Verification Results](docs/verification-v6.md) for the final V6 test count, native results and distribution checksum.

## Prototype Limits

- AI uses heuristics; factions currently share the same rules/AI rather than unique abilities.
- Later-map balance and resignation feel still need human playtesting.
- Troops fly directly between any two territories without terrain obstruction.
- Canvas menus do not yet provide full TalkBack navigation.
- Difficulty can change mid-round, so scores are personal rather than competitive rankings.
- Headless tests verify music playback state, not perceived loudness or manufacturer-specific audio/touch behavior.
- This is a debug prototype, not a Play Store release.
