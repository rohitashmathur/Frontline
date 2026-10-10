# V12 Step 2 / 0.12.2

Current source version: `0.12.2` (version code `14`).

Status: **code-only, unverified**. This step implements the first four approved work items in order. No tests, compilation, APK build, emulator run, preview rendering or screenshot capture were performed, at the user's request. No new GitHub release or APK asset is published.

## Approved Work

1. Compile fix: declare the checked exception in `V12PlaytestTest.routing()`, which calls `GameModel.save()`. This is a source fix, not a successful compilation claim.
2. Preview and log safeguards: cache the inactive Home preview by selected sector/difficulty; show log usage, warn from 450/500 and explicitly describe oldest-event replacement at the 500-entry limit. Capacity, opt-in state, manual export/clear and binary format remain unchanged.
3. Home/Campaign polish: full-width Daily card with Available/Completed and UTC countdown; separate Run battle/retry details and factual last-run clears; earned ten-chapter progress ribbon and best total; more compact Home spacing; proportionate preview crowns; compact vertical Campaign trail as chosen by the user. Previous-rule scores use readable labels. Scrolling and localized accessible context remain in place.
4. Documentation: current version/verification status in README and architecture; separate current release/pending-verification report; explicitly historical screenshots and reports. Add an exact current-metadata guard to the portable suite for future execution.

Home's minute-boundary countdown redraw is lifecycle-managed; no continuous Home animation is introduced. Campaign drawing, node lookup and touch rows share the vertical trail geometry. No current fit, touch, translation or lifecycle outcome is claimed without a later verification run.

## Preserved 0.12.1 Work

- Opt-in local event context identifies recording build and attempt without changing historical entries or CSV columns.
- Home navigation, language changes, first actual troop dispatch timing, Run milestones and Logistics routing refusals retain their explicit event boundaries.
- Optional native Too easy / About right / Too hard / Not now feedback remains guarded by attempt identity and persistent duplicate-prompt/submission state. Logging off does not offer feedback.

The 0.12.1 code-only push was not verified; this release does not retroactively certify it.

## Compatibility and Deferrals

V7's exact `12345` unlock remains available and does not manufacture clears. All V11 combat, AI, production, caps, objectives, Daily identity, Run/Logistics behavior, records and save formats are preserved. UI version increments do not change gameplay-rule versions. Package identity and signing configuration are unchanged; no APK was produced to verify an upgrade.

Daily streaks remain deferred pending agreement on rule-version changes and earned historical completion. Later V12 work and all build/testing work require separate approval. Development stops after this commit and push.

Latest verified local V12 build: 0.12.0, `build/FrontlineV12.apk` (version code 12). Do not treat that existing file as a 0.12.2 build. Its original evidence remains in [Step 1 verification](verification-v12.md); its screenshots remain in the [historical gallery](screenshots.md).

Next verification scope: [pending checklist](verification-v12-step2.md).
