# Balance Lab

Revision: working-tree-v11 (uncommitted working tree).

Maps: [0, 12, 36]; difficulties: [1]; rules: [10, 11]; seeds: [0, 7, 1000]; step: 0.0500s; duration cap: 120.000s.

Focus controller rotates through every starting seat; controller styles rotate independently through Classic/Pressure/Guardian. Seat assignments are explicit per row. Opponent policies are measured with resignation disabled and with the existing guarded >90%/10s rule generalized to any lab seat. Lab winner tracking never changes player-facing WON/LOST. No production, cap, combat or campaign-AI bonuses are introduced.

| Result | Count | Denominator |
|---|---:|---:|
| Completed focus wins | 120 | 720 |
| Completed focus losses | 136 | 720 |
| Timeouts (no inferred winner) | 464 | 720 |
| Draws | 0 | 720 |
| Natural elimination completions | 256 | 720 |
| Completions after resignation | 0 | 720 |

Raw results: attempts.csv. Full matched groups and denominators: summary.csv. Explicit seeds: seeds.txt. Deterministic job order and no wall-clock timestamps make reruns byte-reproducible. Percentages must use each group's attempts as their denominator, not only completed games.

This small matrix is measurement, not shipping balance targets. Timeouts remain unresolved games. Player-seat timing and Pressure's player-king targeting are intentionally inherited from the actual model; seat rotation exposes their effect. Human difficulty/enjoyment and native-device performance are not validated.
