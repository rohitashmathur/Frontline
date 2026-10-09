# V11 Phase 4: Experimental Logistics

Three purpose-built maps implement routed rules without migrating the campaign or Run Mode. Hex adjacency excludes holes; stable ID-ordered BFS permits only friendly interiors and a final adjacent attack. Previews show legal targets, the actual path and first-packet ETA. Refused releases spend nothing. Transit ownership is checked on arrival: hostile transit resolves ordinary combat and ends that packet there. There is no distance attrition.

## Portable Evidence

`scripts/test.ps1` passed all existing and V11 tests and rendered all campaign, Run and Logistics fixtures. Focused checks: LogisticsRoutesTest 194,984; LogisticsRecordsTest 10,461; V11LogisticsModelTest 369,503; V11LogisticsSceneTest 1,591. Tests cover actual AI routes/arrival estimates, holes, refused moves, friendly transit without cap loss, interruption, startup stagger across legs, swept interceptions, chronological arrivals/stable ties, all 900 packet slots, malformed paths, independent record keys and exact mid-route continuation.

Classic/Run FL05 bytes and legacy FL01-FL05 direct rules remain unchanged. Routed saves alone use FL06 with frozen topology, stable map/config/routing identity, full paths/current legs and RNG. Record eligibility uses the battle's actual configuration and rejects mismatched caller versions. Human outcomes are not inferred from staged scene victories.

## Android Evidence

`scripts/test-android-v11-logistics.ps1 -RunDevice` passed on Android 15 emulator-5580: 480x800/240 dpi, 720x1600/320 dpi and 1200x1920/320 dpi. Suite counts were 5,321 / 5,316 / 5,321; a separate-process mid-route recovery added 463 checks: 16,421 total, plus cleanup. The harness exercised all three languages, all maps, actual native touch, route-line/ETA observations, refusal, result facts, matching records, virtual accessibility labels and pixel-identical Canvas measurement. It generated 104 PNGs and 104 ink-bound reports. Preferences and display configuration were restored.

The tall-phone checkpoint retained real in-flight routed packets. Relaunch preserved exact battle bytes, language, next-attempt difficulty, campaign progress and independent records. Corrupt records were quarantined without erasing the battle; a structurally invalid route was rejected without erasing progress/records. An initial harness failure queried controls before rendering the new screen; the corrected harness and full matrix passed. No production workaround was introduced.

Native acceptance fixtures, not organically won battles: [Hindi selector](screenshots/v11/logistics-selector-hi.png), [English path preview](screenshots/v11/logistics-route-en.png), [Indonesian result](screenshots/v11/logistics-result-id.png).

## Matched Comparison

`LogisticsComparison --revision working-tree-v11-phase4 --out tools/balance-reports/v11-logistics --verify-rerun` ran 324 attempts / 162 matched pairs twice. Raw CSV, grouped summary and report were byte-identical. Every pair shares its frozen map, starting armies, seed and controller assignment. Normal difficulty, seeds 0/7/1000, step 0.05 seconds, cap 180 seconds, resignation disabled. Seats and controller styles rotate independently.

| Map | Classic completed / attempts | Routed completed / attempts | Classic / routed timeouts | Mean completed seconds, Classic / routed |
| --- | --- | --- | --- | --- |
| Twin Causeways | 36/36 | 36/36 | 0/36 / 0/36 | 50.158 / 72.770 |
| Broken Junction | 38/54 | 33/54 | 16/54 / 21/54 | 94.440 / 105.153 |
| Crown Circuit | 45/72 | 26/72 | 27/72 / 46/72 | 102.072 / 126.463 |

See [raw attempts and summary](../tools/balance-reports/v11-logistics/summary.md). Completed-only means compare different subsets; the report also supplies both-completed matched-pair deltas. Timeouts have no inferred winner. Longer games and Crown Circuit's 46/72 routed timeouts are unresolved tuning concerns, not evidence that routing improves enjoyment.

## Limits

Human comprehension, route interruption clarity, Classic-versus-routing enjoyment, physical-device performance and fluent-speaker review remain pending. Use the [playtest checklist](playtest-v11.md). Logistics is implemented as a separate experiment, not validated for general rollout.
