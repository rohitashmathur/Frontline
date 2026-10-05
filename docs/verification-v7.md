# V7 Local Build

Frontline **0.7.0**, version code **7**, package `com.frontline.offline`. Built locally only: no commit, GitHub push or release was created for V7.

## Unlock Code

Open Settings from the main menu or battlefield, select **Enter Code**, then enter exactly **12345** and tap Unlock or the keyboard's Done action. All 60 sectors become selectable. The unlock is saved through the existing local progression key; no login, server or new permission is involved.

Scores, stars, best times, wins, settings, the selected sector and a retained battle are unchanged. Previously cleared sectors stay cleared; unplayed sectors become unlocked, not cleared. Empty/incorrect codes show an inline error and do nothing. Cancel does nothing; repeated correct entry is safe. The hardcoded code is a prototype review shortcut, not authentication.

## Verification

- `scripts/test.ps1`: **2,690 checks passed**, including all previous rules and 93 V7 checks. Compact/tall Settings and all 60 sectors rendered successfully.
- `scripts/test-unlock-code.ps1`: passed on Android 15 in airplane mode, using the dedicated `emulator-5580`. The actual V6 APK was installed, seeded with a reproducible saved profile/battle, then upgraded in place to V7. Profile and battle bytes were preserved.
- Actual native dialog tests covered empty/wrong codes, Cancel, keyboard and button submission, repeated redemption and persistence after process restart. All stored values were compared, allowing only the unlock key to change.
- Sector 60 was selected and played after redemption. Both main-menu and battlefield Settings could open code entry.
- Native Settings/dialog captures at 720x1280 and 480x800 were visually inspected. The numeric keyboard, field, inline error and action buttons fit without overlapping. No AndroidRuntime crash output was found during the run.
- V7's signature verified and matches the original V6 prototype certificate. APK metadata confirms version 0.7.0/code 7, minimum API 24, target API 35 and no Internet permission.

Native captures are under the ignored `build/device/` directory, including `settings-v7.png`, `unlock-code-invalid.png`, `unlock-code-small.png` and `unlocked-final-chapter-v7.png`. Test code and fixtures are not included in the game APK.

## APK

Distribution file: `build/FrontlineV7.apk`, identical to `build/Frontline-debug.apk`. Size: **934,517 bytes**.

```text
SHA-256: 4417BC91E49444FB49FF4858929C7833EBC330D7278E292B62EF0ED152508D6C
```

Install over V6 without uninstalling to retain progress. This remains a debug prototype; manufacturer-specific keyboard/audio behavior and older Android versions still need physical-device testing.
