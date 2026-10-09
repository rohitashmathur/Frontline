# Logistics Comparison

Revision: working-tree-v11-phase4

Normal; matched seeds 0, 7, 1000; update step 0.05 seconds; duration cap 180 seconds; resignation disabled.

Each pair uses the same frozen Logistics layout, holes, factions, initial armies, seed, and actual LabAi controller assignment. Classic keeps unrestricted targeting; Logistics uses the model's routed rules. The focus controller rotates through every starting seat; controller styles rotate independently through Classic, Pressure, and Guardian. Player-seat timing and targeting asymmetries are inherited, not normalized.

Completed means LabAi finished, including separately counted draws. Player WON/LOST is never used to infer a lab winner. Timeouts have no winner. Means for completed games and capped timeouts are separate; NA means there are no observations.

| Map ID | Map | Mode | Rules | Config | Routing | Attempts | Completed | Focus Wins | Focus Losses | Draws | Timeouts | Mean Completed Seconds | Mean Timeout Seconds |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | Twin Causeways | classic | 11 | 1 | 0 | 36 | 36 | 16 | 20 | 0 | 0 | 50.157944 | NA |
| 0 | Twin Causeways | logistics | 11 | 1 | 1 | 36 | 36 | 18 | 18 | 0 | 0 | 72.770045 | NA |
| 1 | Broken Junction | classic | 11 | 1 | 0 | 54 | 38 | 10 | 28 | 0 | 16 | 94.439507 | 180.000000 |
| 1 | Broken Junction | logistics | 11 | 1 | 1 | 54 | 33 | 11 | 22 | 0 | 21 | 105.153331 | 180.000000 |
| 2 | Crown Circuit | classic | 11 | 1 | 0 | 72 | 45 | 7 | 38 | 0 | 27 | 102.071656 | 180.000000 |
| 2 | Crown Circuit | logistics | 11 | 1 | 1 | 72 | 26 | 8 | 18 | 0 | 46 | 126.462730 | 180.000000 |

| Map ID | Matched Pairs | Both Completed | Classic Only Completed | Logistics Only Completed | Both Timeout | Mean Logistics Minus Classic Seconds, Both Completed |
|---|---|---|---|---|---|---|
| 0 | 36 | 36 | 0 | 0 | 0 | 22.612101 |
| 1 | 54 | 22 | 16 | 11 | 5 | 26.585434 |
| 2 | 72 | 19 | 26 | 7 | 20 | 14.048225 |

Total attempts: 324; matched pairs: 162. Every count uses that row's attempts or matched pairs as its denominator; no timeout becomes an inferred win.

Raw results: attempts.csv. Per-profile/seat/style denominators and separate duration means: summary.csv. Explicit seeds: seeds.txt. Job order is map, controller profile, seat rotation, style rotation, seed, then Classic/Logistics. No timestamps or wall-clock measurements are included. Initial-state hashes prove matched setups; final-state hashes participate in rerun comparison.

This is automated measurement, not human feedback, a shipping balance target, or native-device performance evidence. Routing comprehension and enjoyment remain pending human playtests.

Deterministic rerun: byte-identical raw CSV, grouped summary, and report verified.
