# Frontline Architecture (V10)

Frontline 0.10.0 is an offline, single-activity Android game. The production app is native Java and Android Canvas; simulation, scene, progress, challenge selection and measurement also run without Android for regression tests. There is no server, login, network permission, advertising, remote telemetry or cloud save. Local measurement is explicit opt-in.

## Components

```mermaid
flowchart LR
    Activity[MainActivity / Android lifecycle]
    View[BattleView / Canvas and touch adapter]
    Scene[GameScene / screens, gestures, camera and HUD]
    Model[GameModel / battle simulation and AI]
    Campaign[Campaign / factions and chapter story]
    Profile[Profile / scores, unlocks and settings]
    Progress[Progress / per-difficulty records and mastery]
    Challenges[Challenge / objectives and UTC daily seed]
    Log[PlaytestLog / bounded opt-in snapshots]
    Export[Android document picker / manual CSV]
    Storage[(SharedPreferences: frontline-v1)]
    Music[BackgroundMusic / MediaPlayer and audio focus]
    Assets[Resources / theme, icons and original soundtrack]
    Tests[Portable Java tests and RenderPreview]
    Future[ios and backend / future placeholders]

    Activity --> View
    View --> Scene
    Scene --> Model
    Scene --> Campaign
    Scene --> Profile
    Profile --> Progress
    Profile --> Log
    Scene --> Challenges
    Challenges --> Model
    View --> Export
    Export --> Log
    View <-->|load and save| Storage
    View --> Music
    Music --> Assets
    View --> Assets
    Tests --> Scene
    Tests --> Model
    Tests --> Campaign
```

| Component | Responsibility | Source |
| --- | --- | --- |
| Android adapter | Lifecycle, safe-area layout, Canvas, one/two-finger input, audio/haptics, storage, native code dialog and manual document export | `app/src/main/java/com/frontline/offline/MainActivity.java` |
| Scene controller | Protected attempt flow, practical tutorial, campaign/missions/daily/mastery/help screens, results, camera and event-based HUD | `GameScene.java` |
| Rules | Generation/caps, dispatch/interception/arrivals, objective counters, AI styles, resignation, history/events and exact binary saves | `GameModel.java` |
| Challenge catalogue | Nine validated objective presets, configurable objectives, deterministic UTC date/version seeds and reset countdown | `Challenge.java` |
| New progress | Difficulty-specific campaign/challenge records, bounded daily bests, mastery progress/awards and cosmetic selection | `Progress.java` |
| Local measurement | Opt-in bounded event snapshots, checksummed persistence and escaped CSV, no Android/network dependency | `PlaytestLog.java` |
| Campaign | Ten story chapters, six named factions, rulers and short HUD names | `Campaign.java` |
| Audio | Default-on looping music, independent enable flag, lifecycle pause/resume, Android audio focus | `BackgroundMusic.java` |
| Test adapters | Java2D rendering, rule tests, Android fixture generation and preference probes | `tests/`, `tools/`, `scripts/` |

`GameModel.LEVELS` holds the 60 map definitions. Owner IDs are local team slots (player 0; rivals 1-5), while a level maps each slot to a persistent faction ID and colour. Original king ownership is derived from the unchanged initial map setup, not current control. This keeps king bonuses deterministic across saves and faction reorderings.

## Application Flow

```mermaid
flowchart TD
    Launch[Launch activity] --> Load[Load profile and validate saved battle]
    Load --> Splash[Logo splash]
    Splash --> Menu[Main menu]
    Menu --> Select[Chapter and sector selection]
    Select --> Menu
    Menu --> Settings[Settings]
    Settings --> Menu
    Menu --> How[How to Play / five interactive steps]
    How --> Menu
    Menu --> Play[New Attempt / campaign, challenge or daily]
    Play --> Brief[Briefing / objective, star target and AI styles]
    Brief --> Replace{Unfinished battle exists?}
    Replace -->|Yes| Confirm[Confirm replacement]
    Confirm -->|Cancel / keep exact model| Brief
    Confirm -->|Replace| Start[Create clean attempt / log explicit abandonment]
    Replace -->|No| Start
    Start --> First{Tutorial completed?}
    First -->|No| Tutorial[Staged practical tutorial / Skip]
    First -->|Yes| Guide[First-large-map camera guide if needed]
    Tutorial --> Guide
    Guide --> Battle[Battlefield]
    Menu -->|Continue Battle| Battle
    Battle --> Input[Deploy, pan, pinch or zoom]
    Input --> Frame[Simulation and render frame]
    Frame --> Battle
    Battle --> Pause[Pause]
    Pause -->|Resume| Battle
    Pause -->|Restart| Confirm
    Battle -->|Toolbar restart| Confirm
    Pause -->|Main menu / keep battle| Menu
    Battle --> End{Win or lose}
    End --> Record[Record result once / campaign or mission records and eligible mastery]
    Record --> Result[Result screen]
    Result -->|Next| Brief
    Result -->|Retry / no discard confirmation| Start
    Result --> Menu
    Battle -->|Background activity| Save[Pause and save; pause audio]
    Menu --> Save
    Save -->|Relaunch process| Load
    Save -->|Activity resumed| Previous[Resume previous screen; battle stays paused]
```

The tutorial's practice state cannot modify a retained battle. Screens outside the battlefield freeze simulation. Camera gestures cancel troop drags and do not change troop ownership, counts, elapsed time or scoring. Settings difficulty is next-attempt only. Selection is independent of an active attempt; confirmation is required only for actual replacement. Cancel restores the previous paused screen/model, not a newly generated map.

## Simulation Flow

```mermaid
flowchart TD
    Tick[Update with positive dt; clamp to 0.1 seconds] --> Grow[Generate troops up to 100 ordinary / 125 king]
    Grow --> Clash[Swept hostile convoy collision tests]
    Clash --> Move[Advance surviving convoys]
    Move --> Arrive[Resolve arrivals: reinforce, defend or capture]
    Arrive --> Dominance{Player controls more than 90 percent?}
    Dominance -->|No| Reset[Reset sustained-control timer]
    Dominance -->|Yes| Hold[Accumulate up to 10 seconds]
    Hold --> Held{Ten seconds reached?}
    Held -->|No| Outcome
    Held -->|Yes| Guard[Check each rival's garrisons and convoys against exposed player tiles]
    Guard --> Hope{Unable to recapture any tile?}
    Hope -->|Yes| Surrender[Hopeless rivals yield their tiles; their convoys stand down]
    Hope -->|No| Outcome
    Reset --> Outcome[Check surviving armies and configured objective / active counters]
    Surrender --> Outcome
    Outcome -->|Still playing| AI[Run due classic or faction-style AI decisions; reserve and size attacks]
    Outcome -->|Finished| Results[Notify scene; record result once]
    AI --> Render[Render battlefield and HUD]
    Results --> Render
```

Collision tests use relative motion over the tick, not a single rendered position. Hostile units cancel one-for-one only when they physically meet at the same time. Friendly convoys pass through. Packets preserve unit counts within the 900-particle visual budget. Actual arrivals, interceptions and transfers emit bounded aggregated events; the scene drains those for HUD feedback and king logs. Historical counters persist; transient effects are not replayed on restore.

## Persistence and Migration

- SharedPreferences `frontline-v1` retains all historical `best-N`, `stars-N`, `time-N`, unlocks, sector selection, wins, preferences and tutorial completion. These historical records remain Legacy.
- New Base64 keys `progress-v10` and `playtest-v10` store independently checksummed/validated records, awards/themes and opt-in bounded logs. `camera-guide-seen` is independent of tutorial completion. A corrupt new component is reset alone, not the legacy profile or other components.
- Save on menu/settings actions, activity backgrounding and approximately every 15 seconds of drawing. Only the battle model is persisted; camera position resets to a fitted board.
- V10 writes `FL04`: all prior battle data plus original seed/known-history flags, starting-king loss, rules/AI version, factual interception/cap counters, objective configuration/progress, challenge identity/original daily date and exact RNG state.
- `FL01` (V4-compatible), `FL02` (V5 extended armies) and `FL03` (V6/V7 six-team and resignation state) still load. Old battles use classic AI and unknown seed/history; no difficulty-specific record or clean-king badge is invented. Existing over-cap V5 garrisons clamp to 100/125; already-sent packets retain their units.
- V5 campaign completion unlocks Sector 31. Existing scores, stars, best times, preferences and sector selection remain intact. A corrupt battle is discarded without clearing the profile.
- New saves validate size, values, owner IDs, map size, convoy limits, objective/history consistency, timers, dates and resignation state. Exact RNG persistence keeps subsequent AI/setup randomness consistent across V10 save/resume. All restored states open the menu rather than auto-running.
- The daily attempt's original date/configuration remains in its battle save. Current date changes only the next daily selection. The latest 60 daily records and 500 log entries bound local storage.
- Manual CSV export uses `ACTION_CREATE_DOCUMENT`, writes only to the chosen URI and needs no storage/network permission. Cancelling the picker changes no gameplay data.

## Build and Platform Boundaries

The PowerShell build compiles Java, generates the original WAV loop, packages resources, produces DEX, aligns and signs the APK, then verifies the signature. Test tools and fixtures are not included in the APK. `.toolchain/`, generated `build/`, signing keys and credentials are ignored by Git.

`android-tests/` is a separate, test-only instrumentation package. It exercises real Android multi-pointer events against the production BattleView and captures native Canvas pixels at three viewport sizes. Its APK is never part of the game's distribution.

`app/` and root Gradle files contain Android. `ios/` and `backend/` are reserved, unimplemented folders in this monorepo. An iOS port can reuse assets, campaign content and specifications, but native Java does not directly compile for iOS. Future online services must be optional so offline play remains available.

For gameplay details, see [V10 Rules](game-rules-v10.md), [V10 Acceptance](verification-v10.md) and [Screenshots](screenshots.md).
