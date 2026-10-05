# Frontline V6 Game Rules

Version 0.6.0 / version code 6. The game is fully offline; rules apply equally to the player and rivals unless a rule explicitly concerns AI or opponent resignation.

## Campaign and Sides

There are 60 sectors across ten chapters, with one to five AI opponents. Each side starts at a distinct king territory. Later battlefields reach 9 columns by 8 rows with gaps, up to 64 playable territories, and alternate starting positions. Original Sectors 1-30 keep their indexes, geometry and starting armies.

| Faction | Ruler | Colour |
| --- | --- | --- |
| Frontier Alliance (player) | Frontier Council | Green |
| Ember Pact | Marshal Kael Voss | Coral |
| Auric League | Chancellor Mira Sol | Gold |
| Vesper Order | Warden Ilyra Veil | Violet |
| Iron Dominion | General Rhea Soren | Blue |
| Frost Union | Admiral Nara Kestrel | Pink |

| Sectors | Chapter |
| --- | --- |
| 1-6 | Border Sparks |
| 7-12 | Ember Reach |
| 13-18 | Auric Divide |
| 19-24 | Vesper Veil |
| 25-30 | Last Accord |
| 31-36 | Iron Frontier |
| 37-42 | Frost March |
| 43-48 | Shattered Coalition |
| 49-54 | King's Gambit |
| 55-60 | United Horizon |

Colours belong to factions, not their opponent-slot numbers. Neutral territory is grey. Each chapter has six sectors. Winning unlocks the next sector; cleared, unlocked and locked states appear in sector selection. Clearing Sector 60 completes the campaign.

## Troops, Generation and Caps

- Ordinary owned territory generates 1.35 troops per simulation second, before boosts, up to **100** troops.
- King territory generates 2.2 troops per simulation second, before boosts, up to **125** troops. This cap and the king symbol remain with the tile after capture.
- Neutral territories generate nothing. Fractional production is stored internally; tile labels show whole troops, rounded down.
- Caps apply to production, friendly reinforcement and troops left after capture. Excess reinforcements beyond a tile's cap are not stored or refunded.
- The V5 team-saturation/over-99 overflow rule is removed. Having every tile full never enables unbounded growth.

V4/V5 saves are accepted. Old garrisons already above their new cap are reduced to 100/125 on migration, including any overfilled neutral tile. Existing in-flight armies retain their units; arrivals use the new finite limits. The first 30 maps and original ownership of their kings stay unchanged.

## King Booster

Only **currently held enemy kings** count. Your own original king is excluded, even after losing and recapturing it. Every held enemy king boosts the entire team's production, including ordinary tiles and the starting king, while the fixed caps remain unchanged.

| Enemy kings held | Exact team multiplier |
| --- | --- |
| 0 | 1x |
| 1 | 1.2x |
| 2 | 1.44x |
| 3 | 1.728x |
| 4 | 3x |
| 5 | 3.6x |
| More than 5, if future maps add kings | `3 * 1.2^(kings - 4)` |

Below four kings, the formula is `1.2^kings`. At four, the multiplier becomes 3x; every additional king multiplies that by 1.2. Losing the fourth king drops the bonus to 1.728x. Losing any king removes its corresponding held bonus. Recapturing does not stack historical bonuses.

The battlefield continuously shows `BOOST x...` and the held enemy-king count. Capturing or losing a king updates the value and briefly pulses the crown/background without shifting the map. The numeric HUD rounds the multiplier to two decimals for readability; the simulation uses the exact value. Restoring a save shows the current bonus without replaying a capture animation.

How to Play includes an interactive King Boost step with Capture King/Lose King controls and all milestones. The same rules apply to rivals; their original king also does not count as a captured enemy king.

## Sending and Combat

- Drag from your green tile to an enemy/neutral tile to attack, or to a friendly tile to reinforce. Expanded touch gutters remain available around visible tiles.
- The deployment selector sends 25%, 50% or 100% of the source's whole troops, rounded down. Sends of zero units do nothing; the source must have at least two troops.
- Each sent unit costs one source troop. Travel time is `0.3 + distance / 2.6` simulation seconds, with a staggered departure interval of 0.022 seconds between visual packets.
- Friendly arrivals reinforce up to the tile's cap. Hostile arrivals cancel defenders one-for-one; remaining attackers take ownership after the defenders are exhausted.
- Hostile armies that physically meet in the air cancel one-for-one: 20 against 30 leaves 10 survivors from the larger army. Crossing the same point at different times causes no interception; friendly convoys never collide.
- Armies still travel if their source is captured. Surviving armies in flight can recover a side after it loses its final territory.
- Up to 900 visual packets can be active. When few slots remain, multiple units share a packet rather than disappearing. Legacy large-army packets are supported during migration.

The map has no terrain obstruction or adjacency requirement: troops may travel directly between any two tiles. Gaps alter map shape, not troop flight paths.

## AI and King Recovery

All opponents use tactical heuristics, not machine learning. Their faction names/colours convey story; factions do not have unique abilities yet.

The AI estimates capture cost, growth during travel/arrival, and friendly/hostile reinforcements already in flight. Defender growth before first arrival respects its new cap. It keeps percentage and threat-based defensive reserves, reinforces threatened tiles when support can arrive in time, avoids duplicating a sufficient incoming force, and combines several armies when one source cannot safely capture a target.

**Normal and Hard give their lost original king a stronger target preference**: recovery score bonus 20 and 34 respectively. This applies to both individual and coordinated attacks. They still require enough troops and retain a garrison; an impossible king attack is not launched just because it has a crown. Easy retains its relaxed target choice. Other ordinary or enemy tiles can still be preferred when king recovery is too expensive or unsafe.

Nominal decision intervals are Easy 2.5s, Normal 1.4s, Hard 0.8s, plus up to 0.6s of variation. Base production rules do not change with difficulty. Difficulty also changes safety margins, reserves, target variation and the score bonus.

## Resignation and Victory

Opponent resignation uses the agreed guarded rule:

1. Player ownership must be **strictly greater than 90% of all playable territories**, including neutral tiles in the denominator.
2. Maintain that coverage for **10 continuous simulation seconds**. Dropping to 90% or less resets the timer. Pause/menu/background time does not count.
3. Evaluate each surviving rival independently. Count every troop it still has in garrisons and in flight, including queued packets. Do not resign a rival if that total could exceed the exposed defenders of any player tile.
4. Exposure conservatively subtracts already-incoming hostile troops from player garrisons. This keeps a rival fighting when known attacks may open a recapture opportunity. Future player growth is not used to force a resignation.
5. A hopeless rival yields all remaining territories to the player, and its remaining convoys stand down. Yielded territories retain their garrison within the cap and count toward captures. Yielded kings update the player's team boost. Other rivals continue fighting if their troop check still permits recovery.

This is intentionally conservative: exceeding 90% is not an automatic win, and an opponent with a strong army or a vulnerable player tile can continue. The player is never automatically made to resign.

Victory occurs when no rival owns territory or has a surviving convoy. Neutral tiles need not all be captured. Defeat occurs when the player has neither territory nor surviving convoys. A final king capture/surrender can play the brief boost pulse before results, while score/progression are recorded immediately and only once.

## Coverage, Camera and Controls

The coverage bar is 18 logical pixels thick. Its segments use ownership divided by all playable map tiles. Each active team has a colour-matched, whole-number percentage label, with no decimals; complete coverage correctly shows 100%. Rounded labels can sum to slightly more or less than 100%. Resigned teams show OUT.

The initial view fits the entire board. Use +/- to zoom, the fit icon to restore the whole map, and a two-finger pinch/drag to scale/pan. At a zoomed scale, dragging from neutral/enemy/empty space pans; a one-finger drag starting on your own tile remains troop deployment. Camera movement is constrained to map bounds, clipped to the battlefield, and cannot dispatch troops. Restart returns to the fitted view.

Pause, toolbar restart, settings and Main Menu remain available. Restart immediately creates a clean attempt of the same sector without deleting scores, unlocks or preferences. Main Menu retains an unfinished battle for Resume. Play starts a new attempt. Music defaults on and can be toggled independently of sound effects and vibration.

## Scores and Saves

Winning score: `1000 + 50 * captures + max(0, 1200 - floor(elapsed) * 6) + difficulty * 250`.

Three stars require finishing within the sector's par time; two within 1.6 times par; otherwise one. Store the best score, best star rating and fastest winning time independently per sector. Difficulty is adjustable mid-round, so these are local personal scores, not competitive rankings.

All state stays in Android SharedPreferences. No login or backend is required. Uninstalling or clearing app data removes saves. Installing an APK over the previous version with the same package and signing key retains data. This remains a debug prototype, not a Play Store release.
