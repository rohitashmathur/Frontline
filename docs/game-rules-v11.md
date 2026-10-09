# V11 Rules and Compatibility

V11 builds on V10. The Classic campaign retains direct targeting across gaps, 100 ordinary/125 king caps, one-for-one airborne interception, held-enemy-king growth bonuses, difficulty-specific campaign scores/stars and guarded opponent resignation. Pressure and Guardian AI retain their campaign behaviours. New armies start equally across all factions; continuing an old save does not reset troops.

## Objective Results

| Objective | Winning condition | Comparable record |
| --- | --- | --- |
| Campaign | Eliminate all rival territories and surviving armies | Existing campaign score, stars and best time |
| Hold King | Continuous control of the marked enemy king for the specified interval | Least total winning elapsed time |
| Home Guard | Never lose the starting king during the specified interval | Completion by mission/difficulty/configuration; no fastest-time target |
| Troop Budget | Eliminate rivals without exceeding the deployment allowance | Fewest deployed troops; elapsed time breaks equal-troop ties |

Mission completion is independent of positive campaign scores or stars. Daily uses the same evaluator. Records compare matching rules, mission configuration, difficulty, objective parameters and Daily identity. Historical mission stars/times remain labelled historical and retain their earned completion/mastery.

Deployment previews and launch share integer rounding. Friendly reinforcements count, as do attacks. Invalid, self-targeted and zero-troop deployments consume nothing. Releasing an otherwise legal over-budget deployment still causes defeat; the UI warns but does not silently clamp it.

The battle stores numeric terminal-reason codes and the relevant objective counters. The selected interface language formats these facts when displaying a restored result. Legacy defeats with no stored cause use a neutral historical message instead of guessing.

Terminal precedence is deterministic: an already finished command cannot be overridden; budget excess precedes protected-king loss, which precedes player elimination, which precedes a timed or elimination victory. Explicit player surrender is its own cause. Simultaneous arrivals still use the model's deterministic packet order.

Home Guard and Province Guard have explicit configuration-2 defence pressure on Normal/Hard: easier neutral expansion, more frequent enemy decisions and mission-local focus/coordination against the protected king. This does not grant enemy production, troop-cap or starting-army bonuses, and does not change campaign targeting or AI cadence. Veiled Guard remains in the regression matrix. See the matched seed report for actual passive and active results, including unresolved human balance questions.

## Tutorial and Settings

Each tutorial run can emit one terminal event: `tutorial_complete` after all required steps, or `tutorial_skip` for explicit Skip. Pause/background is neither. `tutorialSeen` suppresses repeat prompting and does not imply completion. V11 adds `semantics=v11` to terminal detail; historical events are never rewritten.

Exactly three offline languages are supported: English (`en`), Bahasa Indonesia (`id`) and Hindi (`hi`). Native labels remain recognizable in the selector. Changing language is presentation-only. Existing installations without a preference remain English; supported device language is used only on a new profile.

Settings and Daily occupy distinct top-right home targets. Android scales the gear target to at least 48 dp, respecting the existing safe-area adapter. Battle settings/pause access and the exact `12345` unlock shortcut remain available. Unlocking does not fabricate sector clears.

## Save Compatibility

The existing local profile and legacy battle readers are retained. The battle extension stores configuration, reason, Daily version and mode metadata. An active pre-V11 mission retains its original timer, AI/rules, seed, difficulty and Daily date. The revised Daily version is separate from `daily-v10-1` historical records and still resets at 00:00 UTC.

New record data extends the CRC-protected progress format while reading FP10 history. Corruption is isolated to the affected saved component; it must not erase campaign unlocks, settings or unrelated records. No backend or cloud migration is involved.

## Classic Run Mode

A run has exactly five frozen Classic maps (sectors 1, 3, 9, 25 and 37), with visible Easy/Normal/Normal/Hard/Hard difficulties and up to five rivals. Four councils award one unowned perk each. The first three offer three distinct choices; the last offers the remaining two. Perks are non-stacking and player-only: ordinary production +8%, king production +15%, convoy speed +12%, king cap +15, or starting king troops +10. They never apply to campaign or missions.

One run-wide retry repeats the same node seed and selected perks with a new battle association. A second loss ends the run. Toolbar restart explicitly records defeat before offering the remaining retry; it cannot grant free rerolls. Declining a retry ends without adding a fictitious second loss. Run summaries contain factual battles, elapsed time, captures, losses, perks and retries, with no farmable campaign score or mastery rewards.

Run state is an independent checksummed snapshot with frozen maps, rule/perk versions, actual ordered offers, outcomes and battle nonces. It and the single battle slot are saved in one synchronous preferences transaction. A restored terminal battle is recorded once. Switching modes protects every unfinished run state, including councils and retry offers. Missing/mismatched battles require explicit abandonment, never reconstruction after play began. Corrupt run data cannot wipe campaign progress.

The proposed 10-15 minute run length and perk balance remain hypotheses for human testing.

## Validation Limits

Automated seed simulations measure determinism and defined success/failure gates, not human difficulty, enjoyment or linguistic quality. Fluent-speaker review, physical-device behaviour and human sessions remain separate evidence requirements. See the V11 verification reports for actual executed checks.
