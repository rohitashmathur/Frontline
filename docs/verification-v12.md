# V12 Step 1 Verification / 0.12.0

Verified on 2026-10-10 against the final local APK. Scope is the dark Home, Campaign and Settings implementation only; gameplay, saves and records retain their V11 versions.

## Portable Regression

`scripts/test.ps1` passed **934,857 assertions**, including **34,062 V12 screen checks** and **9,309 localization checks**. All three bundled languages have 491 complete keys.

Coverage includes all sixty campaign maps and three difficulties; core combat/caps/interception, old saves and progress migration; V7 exact code behavior; tutorials and camera input; protected replacement and saved battles; objectives/Daily/mastery; Classic Run and experimental Logistics. The new tests cover scrolling, unique/non-overlapping controls, minimum touch sizes, version footers, all chapters, selection without replacement, settings/briefing Back context, next-attempt difficulty and exact retained battle/progress bytes. Portable previews also rendered successfully.

## Native Android

`scripts/test-android-v12.ps1` passed **175,523 checks** on the dedicated Android 15 `emulator-5580`:

| Display Configuration | Density | Checks |
| --- | --- | ---: |
| Small phone, 480x800 | 240 dpi | 43,518 |
| Tall phone, 720x1600 | 320 dpi | 45,903 |
| Tablet, 1200x1920 | 320 dpi | 43,531 |
| Wide display, 1200x800 | 320 dpi | 42,571 |

The app retains its portrait orientation; the wide configuration tests Android's constrained app window, not a new landscape layout. Each configuration exercises English, Indonesian and Hindi. Native checks cover Canvas text ink and overlapping visible text, actual touch navigation/scroll cancellation, localized accessibility nodes and scroll actions, at-least-48dp visible targets, settings toggles/picker, all sixty nodes, V7 unlocks and byte-identical retained battle/record data. The harness retains its historical `NativeV11Test` name; the exercised mode is `v12screens`.

Original emulator preferences and display overrides were restored, the separate instrumentation package was removed, and the emulator was stopped after verification. Fixtures never target a physical phone. [Committed screenshots](screenshots.md) are final native captures with a deterministic staged battle/legacy record, not organically won results. Full captures and text-bound reports remain in ignored `build/device/v12-native/`.

## APK Identity

| Field | Value |
| --- | --- |
| Local APK | `build/FrontlineV12.apk` |
| Size | 1,041,013 bytes |
| Package | `com.frontline.offline` |
| Version | `0.12.0` / code `12` |
| Minimum / target SDK | 24 / 35 |
| Network permissions | None |
| Signature | Verified v2 and v3; unchanged prototype debug key |

APK SHA-256:

```text
8680b572c686ba63fc233bd4bdbe563c96b965dec2c5d0ecf7869b1952dd1fb2
```

Signer certificate SHA-256:

```text
33ea82b84a5fa09e52fbaa37769ec18539cc1adf5b80fa22246147b1bfe7996d
```

Install over the existing original-key prototype to retain data; do not uninstall first. Generated APKs, toolchains and signing keys remain excluded from Git. This step pushes source, documentation and screenshots; it does not publish a new GitHub release asset.

## Remaining Human Checks

Physical-phone touch/scroll feel, TalkBack usability, fluent-speaker review, sound quality and manufacturer-specific behavior still require human testing. Automated evidence does not establish enjoyment or later-map balance. No further V12 feature step has been started.
