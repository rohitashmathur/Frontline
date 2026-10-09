# Matched Defence Balance

Actual GameModel; step 0.05s; seeds 0-19 and 1000-1019. No state overrides. Before: rules 10, configuration 1. After: rules 11, configuration 2.

All factions begin with one king and the same entire base army in revised missions. Home/Province neutral garrisons are floor(original * 0.40/0.15), minimum 1. Both use protected-king priority +55, coordinated protected-king attacks when affordable, and do not intentionally attack other rivals. Normal/Hard enemy intervals are 0.9/0.6s plus the unchanged 0-0.6s seeded jitter. Easy uses its existing interval. No combat/production/cap bonuses or seed exceptions. Home/Province survival durations remain 45/60s. Veiled has no pressure changes; only the new equal-start rule applies.

| Mission | Difficulty | Seed Set | Before Wins | After Wins | Attempts |
|---|---|---|---:|---:|---:|
| Home Guard | Easy | 0-19 | 20 | 20 | 20 |
| Home Guard | Easy | 1000-1019 | 20 | 20 | 20 |
| Home Guard | Normal | 0-19 | 16 | 0 | 20 |
| Home Guard | Normal | 1000-1019 | 15 | 0 | 20 |
| Home Guard | Hard | 0-19 | 20 | 0 | 20 |
| Home Guard | Hard | 1000-1019 | 20 | 0 | 20 |
| Province Guard | Easy | 0-19 | 20 | 20 | 20 |
| Province Guard | Easy | 1000-1019 | 20 | 20 | 20 |
| Province Guard | Normal | 0-19 | 17 | 0 | 20 |
| Province Guard | Normal | 1000-1019 | 16 | 0 | 20 |
| Province Guard | Hard | 0-19 | 20 | 0 | 20 |
| Province Guard | Hard | 1000-1019 | 20 | 0 | 20 |
| Veiled Guard | Easy | 0-19 | 20 | 20 | 20 |
| Veiled Guard | Easy | 1000-1019 | 20 | 20 | 20 |
| Veiled Guard | Normal | 0-19 | 2 | 1 | 20 |
| Veiled Guard | Normal | 1000-1019 | 3 | 5 | 20 |
| Veiled Guard | Hard | 0-19 | 1 | 4 | 20 |
| Veiled Guard | Hard | 1000-1019 | 3 | 5 | 20 |

## Active Strategies

Fortify expands near home, defends ordinary holdings against visible arrivals, and reinforces the king; it never attacks enemy kings. Counterattack reinforces the king only against visible threats, expands, then attacks enemy kings with combined launches. Commands every 0.4s, no hidden state changes. All 40 seeds are evaluated, without cherry-picking. Per-seed results and deployed troop counts are in attempts.csv.

| Mission | Strategy | Seed Set | Wins | Losses | Timeouts | Attempts |
|---|---|---|---:|---:|---:|---:|
| Home Guard | fortify | 0-19 | 15 | 5 | 0 | 20 |
| Home Guard | fortify | 1000-1019 | 14 | 6 | 0 | 20 |
| Home Guard | counterattack | 0-19 | 8 | 12 | 0 | 20 |
| Home Guard | counterattack | 1000-1019 | 5 | 15 | 0 | 20 |
| Province Guard | fortify | 0-19 | 10 | 10 | 0 | 20 |
| Province Guard | fortify | 1000-1019 | 4 | 16 | 0 | 20 |
| Province Guard | counterattack | 0-19 | 2 | 18 | 0 | 20 |
| Province Guard | counterattack | 1000-1019 | 1 | 19 | 0 | 20 |

Gate violations: 0. Counts are completed wins / 20, not human difficulty evidence. Active results are automated feasibility only. Province counterattack is difficult for this simple controller; its low win rate is an unresolved balance/playtest question, not validation of enjoyable difficulty. Home seed 0 and Province seed 13 demonstrate wins by both distinct approaches, without state mutation. Human playtesting pending.
