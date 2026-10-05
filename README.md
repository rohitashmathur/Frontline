# Frontline

An original, offline Android territory-conquest prototype. Version 0.5.0 includes 30 hex maps across five story chapters, one to three computer opponents, a main menu, interactive tutorial, sector selection, three difficulty levels, local scores and stars, music/sound/vibration settings, and saved battle state.

## Repository layout

Frontline is a monorepo for the game and its future platforms. The current implementation is Android-only and runs entirely offline; an iOS app and backend have not been implemented.

| Path | Purpose |
| --- | --- |
| `app/` and root Gradle files | Native Android application, game rules, campaign, and assets |
| `scripts/` | Local Android setup, build, and verification commands |
| `tests/` | Portable Java regression tests |
| `tools/` | Test fixtures, rendering previews, and original music generation |
| `ios/` | Reserved for a future iOS application |
| `backend/` | Reserved for optional future online services |

The Android project stays at the repository root so existing build commands continue to work. A future iOS implementation can reuse campaign definitions, assets, and rule specifications, but the native Java application is not directly an iOS build. The offline game does not require a backend.

Downloaded SDKs, local signing keys, credentials, generated APKs, and test output are excluded from Git. Keep the current debug signing key backed up privately: a fresh clone generates a different key, so its APK cannot update an existing installation signed with the original key. Use a separately managed release key for publication. No open-source license has been added.

## V5 changes

- Original twenty-second instrumental background loop, enabled by default. Settings has separate Music and Sound Effects switches. Music pauses when the app goes into the background, resumes from its position, and respects Android audio focus.
- How to Play has a visible previous-step arrow. The 25%, 50%, and 100% demonstration selectors show sent and remaining counts. The offline-score sentence was removed.
- Hostile armies that meet in flight cancel one-for-one, including intersecting routes at the same time. Merely crossing the same location at different times does not cause a clash. Friendly armies pass through each other. A brief spark marks collisions.
- The circular arrow beside Pause restarts the current round immediately, retaining campaign progress and settings.
- Troop growth can exceed 99 while every territory currently owned by that team has at least 99 troops. When any owned tile falls below 99, growth above 99 pauses until the condition is met again; existing excess troops are not deleted.
- Each enemy king territory held grants its team a 1.5x production multiplier. Two held enemy kings give 2.25x, three give 3.375x. Losing one removes its bonus; recapturing it does not stack another bonus. Retaking your own original king does not grant this bonus. Rival teams follow the same rule.
- Capturing a king shows an animated growth notice above the battlefield without shifting the board. If it ends the round, the notice plays before the result screen. Notices do not replay on save restoration.

Armies larger than the visual particle budget travel as grouped packets, preserving all sent units, their owner, and their timing. Up to 900 convoy particles can be in flight, with a safety ceiling of one million troops per territory. Larger counts fit inside their tile. V4-format saves are still accepted; an extended format stores over-99 counts and grouped convoys when needed.

## Campaign

You lead the green Frontier Alliance to reunite a divided homeland. Rival factions retain their colours even when the enemy lineup changes:

- Ember Pact (coral), led by Marshal Kael Voss.
- Auric League (gold), led by Chancellor Mira Sol.
- Vesper Order (violet), led by Warden Ilyra Veil.

The five chapters each contain six sectors: Border Sparks (1-6), Ember Reach (7-12), Auric Divide (13-18), Vesper Veil (19-24), and Last Accord (25-30). Browse chapters using the arrow controls in Select Sector. Each page includes a ruler, crest, short story briefing, and cleared/unlocked/locked status. The final chapter brings all three rivals together. Chapter-ending victories announce the next chapter; winning Sector 30 completes the campaign.

The original six sectors retain their indexes, map layouts, and starting garrisons. An existing clear of Last Stand automatically unlocks Sector 7, while preserving old scores, stars, best times, settings, tutorial completion, selection, and saved battle. Later maps vary missing tiles, starting positions, faction lineups, and initial troop counts. All 30 maps share the same battle rules and AI; factions do not yet have special abilities or distinct AI personalities.

## AI update

The AI sends estimated capture forces instead of almost emptying its source. Budgets include defender growth during travel and the arriving volley, plus reinforcements already in flight. It retains percentage-based garrisons, increases reserves near stronger enemies and incoming attacks, and prioritizes timely reinforcements for threatened friendly territories. Coordinated attacks share the required force between sources without draining them and wait when combined forces are insufficient. Crowns receive a modest target bonus; weaker or closer ordinary territories can take priority. All rival owners are considered equally.

Base production remains 2.2 troops per simulation second for king territories and 1.35 for ordinary owned territories. Held enemy kings multiply these rates for the whole team. AI growth estimates and incoming-army accounting use the new rates and grouped troop counts.

## Install and play

The installable debug build is `build/Frontline-debug.apk`; the V5 distribution copy is `build/FrontlineV5.apk`. Transfer it to an Android phone running Android 7.0 or newer, open it, and allow installation from the app used to open the APK when Android prompts.

Install this APK over the previous version to keep scores and progress. The package name and signing key are unchanged. Do not uninstall the old app first.

After the logo splash, Play starts the selected sector. Your first Play opens a four-step tutorial with a practice swipe; it can be skipped or replayed through How to Play. Select Sector opens the selected sector's chapter and lists cleared sectors with scores and stars, available sectors, and locked sectors with their unlock requirement. You can browse locked chapters, but their sectors cannot be selected. Selecting a sector updates the menu; Play begins it.

Drag from a green territory to another territory to attack or reinforce it. Touch targets extend beyond tile edges, and the destination is highlighted while dragging. The deployment selector sends 25%, 50%, or 100% of the source's troops, rounded down. A zero-unit send does nothing. Owned territories produce troops; crown-marked bases produce them faster. Neutral territories do not produce troops. Reinforcements and hostile armies continue moving even after their starting territory is captured.

Eliminate all rival territories and armies to win. Your armies in transit can recapture a territory after you lose your last base. Faster wins earn more stars. Winning a sector unlocks the next one. Pause opens restart, sector selection, settings, and Main Menu. Returning to the menu freezes and retains unfinished battles; Resume continues them. Play starts a new attempt and replaces the retained battle. The result screen also offers Main Menu. Android's Back pauses/resumes battles, navigates out of submenus, and quits the app from the main menu.

Scores, progress, selected sector, tutorial completion, preferences, and the current battle are stored locally in Android SharedPreferences. The app saves on backgrounding, menu actions, and every 15 seconds during play. Restoring the app opens the main menu with Resume for unfinished battles. There are no network permissions, advertising SDKs, accounts, telemetry, purchases, cloud backups, or external game assets. Uninstalling or clearing app data removes saves.

## Build on Windows

Use PowerShell from this project directory:

```powershell
# Downloads a checksummed Temurin Java 17 runtime and Google's SDK tools locally.
# Review the Android SDK license before using the acceptance switch.
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-android.ps1 -AcceptSdkLicense
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-android.ps1
```

Tools are installed under `.toolchain/` without changing global environment variables. The build script compiles Java, packages Android resources, builds DEX, aligns the APK, and signs it with a local debug key. Debug signing is for prototype distribution; use a separate release key for publication.

The included Gradle project can also be opened in Android Studio with Java 17, Android SDK 35, and Gradle 8.11.1. The command-line build above does not require Gradle or Android Studio.

## Verification

`scripts/test.ps1` exercises dispatch accounting, generation and caps, capture and reinforcement, end conditions with armies still in transit, corrupt-save recovery, expanded touch input, quarter sends, tutorial practice and skipping, menu/resume flows, cleared/locked sector status, selection, progression, chapter navigation, faction colour consistency, and simulations of all 30 maps at each difficulty. It renders every map, chapter page, and chapter-ending screen at compact and tall sizes, plus phone/tablet and tutorial previews, using the exact same scene layout and drawing commands as Android with a Java2D adapter. Preview text is checked against the viewport width.

The Android canvas adapter and activity are in `MainActivity.java`; the portable rules and view/controller are in `GameModel.java` and `GameScene.java`. Device testing is still necessary for real touch feel, audio, battery usage, and manufacturer-specific behavior.

The portable suite has 1,020 checks, including AI attack budgets, defensive reserves, timely/late reinforcement, target selection, coordination, king bonuses, saturation/overflow, large-army conservation, interception across several tick sizes, true and false route crossings, tutorial Previous and percentage demos, restart, and extended-save corruption handling. The music samples are checked for non-silence, clipping headroom, and smooth loop endpoints.

The Android 15 emulator smoke test covers the offline campaign, actual expanded-area 25% dispatch, exact battle restoration, settings/tutorial persistence, app exit, sector selection/unlocking, AI reserves, all five chapter pages, and the ending. V5 checks cover tutorial Previous/percentage controls, native music start/stop and background pause, toolbar restart, actual 20-versus-30 interception, king capture with 1.5x team growth, counts beyond 99, and a V4-to-V5 upgrade with unchanged saved preferences/battle and music enabled by default. The real previous-APK checks require `build/FrontlineV3.apk` and `build/FrontlineV4.apk`; fixtures can still exercise migration when they are unavailable. An AndroidRuntime crash-log check completes the smoke test. Native screenshots are in `build/device/`. Navigation-bar safe areas are included. Test-only fixtures and music-generation tools are not packaged in the APK. The headless emulator verifies media playback state, but music loudness, feel, and audio-focus interruptions still need testing on a physical phone.

To repeat the emulator checks, first run `scripts/setup-emulator.ps1`, then `scripts/start-test-device.ps1`, then `scripts/device-smoke-test.ps1`. The smoke test uses the named `emulator-5580` test device, clears only its Frontline test app data, and expects the startup script's 720x1280 display configuration. Stop the test emulator after use.

## Prototype limits

- Computer opponents use tactical heuristics, not machine learning.
- Thirty handcrafted map setups; neutral troop counts vary between attempts. Later-sector difficulty still needs human playtesting and tuning.
- Story is conveyed through briefings, names, and chapter-ending text, without cutscenes or voice acting.
- Armies travel directly between any two territories, without terrain obstacles. Hostile armies can now clash in flight.
- Menus are drawn on the game canvas; full TalkBack navigation is not implemented.
- Difficulty can be changed mid-battle and affects the score bonus, so local scores are not competitive rankings.
- This is a debug prototype, not a Play Store release.
