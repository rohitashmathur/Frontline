# V6 Verification Results

Verified distribution: Frontline **0.6.0**, version code **6**, package `com.frontline.offline`. This is an offline, debug-signed Android prototype, not a store release.

## Automated Rules and Rendering

`scripts/test.ps1` passed **2,597 checks**. Coverage includes all 60 maps on Easy, Normal and Hard; king bonuses and loss/recapture; fixed 100/125 caps; capture, reinforcement and in-air cancellation; six-team saves; FL01/FL02 migration and corrupt-save recovery; defensive AI and coordinated king recovery; guarded resignation; progression; tutorial interactions; camera controls and restart.

The portable Java2D adapter rendered every sector, all ten chapters/endings and all five tutorial steps, including compact/tall layouts and zoomed/panned tablet battlefields. It rejects text extending beyond the viewport. Representative native captures were also visually inspected for readable counts and non-overlapping controls.

## Native Android

`scripts/device-smoke-test.ps1` passed on the dedicated Android 15 emulator in airplane mode. It exercised:

- Splash/menu, all five tutorial steps, previous-step navigation, deployment percentages, expanded swipe targets and the booster demo.
- Music preference and background/foreground behavior, settings, pause, restart and saved battles.
- Ten chapters, five opponents, map zoom/pan/fit, persistent 1.2x/3x/3.6x boost states and fixed troop caps.
- In-air 20-versus-30 cancellation, guarded surrender, campaign completion and sector locks/unlocks.
- Installation of the actual V5 APK, then an in-place V6 upgrade with the same signing certificate. Scores, stars, preferences and the unfinished battle survived; overfilled garrisons were clamped, a legacy in-flight army retained its units, and V5 completion unlocked Sector 31.
- No AndroidRuntime crash output during the smoke run.

The separate test-only instrumentation package dispatched real multi-pointer `MotionEvent` sequences into the production Android view. At each size below it confirmed pinch zoom, two-finger pan, cancellation of an existing troop drag, no accidental troop dispatch, and nonblank battlefield pixels in an actual native Canvas capture:

| Android viewport | Result |
| --- | --- |
| 480x800, density 240 | Passed |
| 720x1280, density 320 | Passed |
| 1200x1920, density 320 | Passed |

The instrumentation APK was removed after testing. Tests and fixture generators are not included in the game APK. See [Native Screenshots](screenshots.md) for representative captures; test saves make special states reproducible.

## APK

| Property | Value |
| --- | --- |
| Distribution filename | `FrontlineV6.apk` |
| Size | 934,517 bytes |
| Android requirement | Android 7.0 / API 24 or newer |
| Target SDK | API 35 |
| Internet permission | None |
| Signing | Original prototype debug certificate, matching V5 |

SHA-256 of both `build/Frontline-debug.apk` and its identical `build/FrontlineV6.apk` distribution copy:

```text
4EA6F09F35C0F637F1D90F13B720935662A67426A104B7E5A6A1A07D8C1E0F13
```

Install over V5 without uninstalling to keep data. Signing material and APKs are excluded from source control; the downloadable APK is a private GitHub prerelease asset.

## Remaining Playtesting

Automated checks do not establish that later sectors are enjoyable or well balanced. Human testing is still needed for five-opponent difficulty, the four-king power jump and surrender pacing. The headless emulator checks audio state rather than perceived loudness, and cannot cover every manufacturer's touch, audio-focus or safe-area behavior. Full TalkBack navigation and production signing/store requirements remain outside this prototype.
