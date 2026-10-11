# V12 Step 2 / 0.12.2

Current source version: `0.12.2` (version code `14`).

Status: **verified local testing build**, 2026-10-11. This step implements the first four approved work items in order. The initial push was deliberately code-only; the user subsequently authorized testing and APK creation. The current portable suite, native V12 suite, preview rendering, APK packaging and signature verification passed. No new GitHub release or APK asset is published.

## Approved Work

1. Compile fix: declare the checked exception in `V12PlaytestTest.routing()`, which calls `GameModel.save()`. The corrected suite now compiles and passes.
2. Preview and log safeguards: cache the inactive Home preview by selected sector/difficulty; show log usage, warn from 450/500 and explicitly describe oldest-event replacement at the 500-entry limit. Capacity, opt-in state, manual export/clear and binary format remain unchanged.
3. Home/Campaign polish: full-width Daily card with Available/Completed and UTC countdown; separate Run battle/retry details and factual last-run clears; earned ten-chapter progress ribbon and best total; more compact Home spacing; proportionate preview crowns; compact vertical Campaign trail as chosen by the user. Previous-rule scores use readable labels. Scrolling and localized accessible context remain in place.
4. Documentation: current version/verification status in README and architecture; separate current release/verification report; explicitly historical screenshots and reports. The exact current-metadata guard passes, and current native screenshots are stored separately from historical captures.

Home's minute-boundary countdown redraw is lifecycle-managed; no continuous Home animation is introduced. Campaign drawing, node lookup and touch rows share the vertical trail geometry. Native checks cover non-overlapping text/targets and callback cleanup across four display configurations and three languages. The current test-only changes update the stale previous-rules assertion, check 100 preview redraws and expand native coverage; no production gameplay code was changed during verification.

## Preserved 0.12.1 Work

- Opt-in local event context identifies recording build and attempt without changing historical entries or CSV columns.
- Home navigation, language changes, first actual troop dispatch timing, Run milestones and Logistics routing refusals retain their explicit event boundaries.
- Optional native Too easy / About right / Too hard / Not now feedback remains guarded by attempt identity and persistent duplicate-prompt/submission state. Logging off does not offer feedback.

The original 0.12.1 push was not separately built. Its retained code is now exercised in the 0.12.2 portable playtest suite and native rating/persistence checks. That evidence applies to the current build, not a historical 0.12.1 APK.

## Compatibility and Deferrals

V7's exact `12345` unlock remains available and does not manufacture clears. All V11 combat, AI, production, caps, objectives, Daily identity, Run/Logistics behavior, records and save formats are preserved. UI version increments do not change gameplay-rule versions. Package identity and the original prototype signer are verified. Dedicated older-APK upgrade and manual-export scenarios were not rerun; portable migration checks passed.

Daily streaks remain deferred pending agreement on rule-version changes and earned historical completion. The Linux runner is also deferred. Further V12 implementation requires separate approval. Development stops after this verification commit and push.

Latest verified local V12 build: 0.12.2, `build/FrontlineV12_2.apk` (version code 14). The original 0.12.0 `build/FrontlineV12.apk` is unchanged; its evidence remains in [Step 1 verification](verification-v12.md). Current and historical captures are separately labelled in the [gallery](screenshots.md).

Actual counts, APK identity and remaining checks: [verification report](verification-v12-step2.md).
