# V12 Step 2 Verification Status

Current source version: `0.12.2` (version code `14`).

Status: **PENDING / UNVERIFIED**. No test suite, compile check, APK build, emulator, rendering tool or screenshot capture was run for this step, by explicit user instruction. There is no current APK checksum, assertion total, device result or screenshot evidence to report. Source/diff review does not establish runtime correctness.

## Prepared, Not Executed

- Fix the checked-exception declaration in the 0.12.1 routing test.
- Extend portable screen checks for cached preview identity/state, Daily completion/reset at UTC midnight, full-width Daily target, earned ribbon totals, factual Run details, shared vertical-trail positions and log warnings at 450/500 including logging-off state.
- Update existing screen/native expectations for the new version and Daily accessible context.
- Add exact current-version metadata checks for AppVersion, Gradle, the local build script, README, architecture and current release/status documents. Historical reports are intentionally excluded.

## Later Authorized Verification

- [ ] Run the portable suite and current release metadata check; resolve failures before publishing any success claims.
- [ ] Build the current debug APK, verify manifest version/signature and offline permissions, then record its actual filename/checksum.
- [ ] Run native Home/Campaign/Settings checks across all four existing display configurations and all three languages. Include fresh/retained battles, min-touch scaling, scroll limits, last chapter/row, long titles and previous-rule labels.
- [ ] Check Daily completion, final-minute countdown, UTC midnight, foreground/background and navigation away from Home; confirm refresh does not advance combat.
- [ ] Verify repeated Home redraws reuse the inactive preview, while sector/difficulty changes refresh it and active battles remain untouched.
- [ ] Check 0/449/450/500 log usage and warning visibility, TalkBack context, export picker cancellation and explicit clear without automatic opt-in or data loss.
- [ ] Exercise 0.12.1 feedback lifecycle, stale callbacks, duplicate guards, opt-in boundaries, restored attempts and Run/routing event deduplication on Android.
- [ ] Upgrade from the correctly signed previous APK with saved progress/battles and verify V7 code behavior.
- [ ] Capture new, accurately labelled screenshots only after a verified build; preserve historical galleries.
- [ ] Perform physical-phone and human playtesting separately; emulator/source checks cannot establish touch comfort, language quality or enjoyment.

## Historical Evidence

[V12 Step 1 verification](verification-v12.md) applies to 0.12.0 only. Existing `build/FrontlineV12.apk` and V12 gallery images remain that version. The 0.12.1 source push and this 0.12.2 source push have no build/test evidence; older passing totals and APK hashes must not be reused as current results.
