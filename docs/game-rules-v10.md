# Frontline V10 Rules

V10 implements A1-A6 and B1-B4 of the supplied product priorities. A7 and the monetization/cloud/multiplayer roadmap are outside this release. The V7 settings code `12345` remains available and unlocks all 60 campaign sectors without marking them cleared.

## Unchanged Combat Baseline

- Any territory can target any other territory. Map gaps do not block travel.
- Choose 25%, 50% or 100% of the integer garrison. Dispatch rounds down; an amount below one sends nothing. The preview shows sent and remaining units, not a promised capture.
- Neutral territories do not generate troops. Owned ordinary territories generate 1.35 troops/second; kings generate 2.2, before the same held-king multiplier for every team. Difficulty and personality do not add hidden production bonuses.
- Garrison caps remain ordinary 100 and king 125. Saturation never enables overflow. Friendly arrivals above a cap discard excess; saturated production is not reported as a lost reinforcement.
- Enemy armies meeting in flight cancel one-for-one. Friendly armies pass through. Combat consumes one attacker per integer defender; survivors capture and garrison the tile.
- A held enemy king multiplies the whole team's production: one 1.2x; two 1.44x; three 1.728x; four 3x; five 3.6x. Each additional king multiplies by 1.2. The team's original king is excluded, even after losing and recovering it. Bonuses disappear when a held enemy king is lost.
- Campaign victory requires eliminating rival territories and surviving armies. After strictly more than 90% player coverage for ten continuous active seconds, an opponent resigns only if its garrisons and in-flight attacks cannot recapture a player territory. Its territories transfer to the player. Pauses do not advance the timer.
- Score stays `1000 + captures * 50 + max(0, 1200 - floor(elapsedSeconds) * 6) + difficulty * 250`.
- A win earns three stars at or below the map's displayed par time, two at or below 1.6 times par, otherwise one. Defeat earns zero stars. Repeated-capture scoring is deliberately not tuned in V10.

See [V6 rules](game-rules-v6.md) for the original mechanics and [V10 verification](verification-v10.md) for evidence and pending checks.

## Attempts and Records

Continue Battle is primary when a resumable battle exists. New Attempt first displays the map, fixed attempt difficulty, star targets, personal best and opponent styles. Start/Restart replacing an unfinished attempt requires confirmation; Cancel leaves the saved model unchanged. Selecting a sector is only a selection.

Difficulty selected in Settings affects the next campaign/challenge attempt. It cannot change a running attempt. New scores, best times and stars are stored independently for Easy, Normal and Hard. Existing records remain Legacy with difficulty unknown, and still preserve cleared sectors/unlocks. An old saved battle can finish as a Legacy result; no new difficulty or clean-king badge is inferred from missing history.

The guided tutorial uses explicitly staged practice state. Capture/reinforcement swipes, all deployment amounts and king gain/loss are practical gates; Skip always remains available. Existing tutorial completion is retained. Advanced multiplier, surrender, score and star details are optional Rules.

Capture, interception, original-king loss, held-enemy-king loss/gain and reinforcement cap loss come from actual simulation events, not predictions. Rapid events aggregate into one short priority-based feedback line above the board. Original-king loss does not reduce a held-enemy bonus. Faction colours remain fixed; symbols identify factions independently of colour.

## Challenge Objectives

Challenges are separate from campaign progression. Their wins update challenge records and eligible mastery, not campaign records or unlocks. Each objective is shown before and during play.

| Type | Success | Failure / Reset |
| --- | --- | --- |
| Hold Marked King | Hold the marked rival king continuously for the configured active duration | Losing it resets the hold timer. Losing the player army fails the attempt. Eliminating rivals early does not bypass the hold requirement. |
| Retain Starting King | Reach the configured active timer without ever losing the original king | First actual loss fails immediately; recapture does not erase that history. Losing the player army also fails. |
| Deployment Budget | Achieve ordinary elimination/guarded surrender victory while total player deployments are at most the configured budget | Any deployment exceeding the budget fails immediately. Reinforcements count toward deployments. Losing the player army fails. |

Nine presets span the three types. Timers (20-90 seconds) and budgets (120-300 units) are initial design values, not measured balance conclusions. Objective configuration and counters are saved; menu/pause/background time does not count.

## AI Styles

New attempts use two faction styles. Ember and Vesper use Pressure; Auric, Iron and Frost use Guardian. Difficulty still controls the existing cadence and rules independently of style.

- Pressure prioritizes exposed enemy kings and supports coordinated crown attacks.
- Guardian keeps a larger defensive reserve, prioritizes reinforcement of threatened kings and recovery of its own starting king, including coordinated recovery.

Styles alter decisions, not production bonuses or troop caps. Migrated unfinished V5/V6/V7 battles retain classic AI so an update does not silently change their strategy mid-attempt.

## Offline Daily

The daily version is `daily-v10-1`. The UTC date and version feed a deterministic FNV-1a seed and curated preset selection. Difficulty is always Normal. The screen shows date, version, countdown to 00:00 UTC and local best. A saved daily keeps its original date, seed, objective and difficulty even after midnight. Restart of that attempt repeats its original setup rather than today's setup. The latest 60 dated records are retained; there is no account or leaderboard. Changing the device clock changes which date is considered current.

## Mastery and Cosmetics

| Badge | Measured Goal | Reward |
| --- | --- | --- |
| Crown Keeper | Win an eligible new attempt without ever losing the starting king | Signal theme |
| Normal Chapter | Win all six Border Sparks campaign sectors on Normal | Blueprint theme |
| Objective Specialist | Win each of the three objective types | Badge |

Awards persist once; progress is visible. Missing historical event data never earns a clean-king badge retroactively. Classic is always available. Signal/Blueprint modify board marks only; faction colours, troop values, generation, AI and outcomes are unchanged.

## Local Measurement

Local Playtest Log defaults off. It keeps at most 500 snapshots including tutorial transitions, starts/results, explicit restart/replacement decisions, next/retry, king changes and active elapsed seconds. Fields include sector, attempt difficulty, mode, rules/AI version, seed or unknown, objective configuration and original daily date. Pausing/backgrounding is not abandonment; abandonment is logged only on confirmed replacement.

Export CSV is a manual Android document-picker action. No data leaves the device automatically. Turning logging off stops new entries without deleting existing ones; Clear Log removes history without changing the opt-in choice. Gameplay never requires logging or export.
