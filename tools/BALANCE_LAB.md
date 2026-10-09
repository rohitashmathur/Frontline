# Frontline Model Verification and Balance Lab

These tools run the actual Java model, not a combat approximation. No human difficulty,
enjoyment, native-device performance, or external balance claim is validated here.

## Phase 1 Model Handoff

- New battles use rules 11; campaign simulation, scoring, caps, combat and AI are unchanged
  except every faction's starting king uses the map's playerTroops value. Whole starting
  armies, not merely king counts, are equal. Old restores retain their original armies.
- Terminal codes: NONE=0, BUDGET=1, PROTECTED_KING=2, ELIMINATED=3, SURRENDER=4,
  LEGACY=5, VICTORY=6. Use GameModel.TERMINAL_* constants, not duplicated numbers.
- Precedence: an existing terminal result is immutable; budget failure is immediate;
  on an update, budget/protected-king failure precedes elimination, which precedes victory.
- deploymentAmount(int count, double fraction) implements integer floor and rejects
  invalid fractions/counts. previewAmount(int source, int target, double fraction)
  also applies launch legality and convoy capacity. An over-budget deployment is still
  legal and launches before losing; previews do not mutate anything. surrender() is
  idempotent and records its distinct cause.
- FL05 stores the frozen Level descriptor before territory data, then appends terminal,
  mission and run metadata after the existing random-state payload. FL01-04 remain readable.
  FL01-03 retain unknown-history metadata and original reseeding. FL04 keeps its exact RNG,
  AI, objective, timer, budget, initial army and daily date. Unknown old defeats use LEGACY;
  recoverable old budget/king failures are explicit.
- missionConfigVersion is 0 for campaign/run, 1 for legacy missions, 2 for revised missions.
  dailyVersion is empty outside Daily; legacy Daily uses daily-v10-1, new Daily daily-v11-2.
  Daily record keys should include dailyVersion, rulesVersion, missionConfigVersion and difficulty.
- Challenge.createLegacy(...) and createVersioned(..., configVersion) support old retries.
  dailySeed(date, version) and dailyId(date, version) preserve old generation. Custom timed
  objectives still use their supplied duration. All nine preset timers/budgets remain unchanged.
- Revised Home/Province: neutral garrisons become floor(original*0.40/0.15), minimum 1;
  rivals focus on player holdings/the protected king instead of each other, prioritize
  affordable coordinated king attacks (+55 target priority), and use 0.9/0.6s Normal/Hard
  decision intervals plus the existing seeded 0-0.6s jitter. Easy retains its old interval.
  No bonus armies, production, combat, cap or seed-specific changes. Veiled has no such tuning.
- New mission/run score() and stars() return zero; campaign results and old mission
  results retain their original formulas. ObjectiveResult/Progress owns mission evaluation.

### Reserved Run and Map API

battleMode: MODE_CAMPAIGN=0, MODE_RUN=1, MODE_LOGISTICS=2 (routing is not implemented).
runPerks: PERK_PRODUCTION=1 (+8% ordinary), PERK_KING_PRODUCTION=2 (+15% king),
PERK_SPEED=4 (+12% convoy speed), PERK_KING_CAP=8 (+15 king cap), PERK_START=16 (+10
starting king troops). Effects apply only to the run player, not rivals or Classic campaign.

Call configureRun(int perks, String association, int node) on a fresh battle; the id-first
overload also exists. Directly assigning runPerks does not apply the one-time starting bonus.
Run nodes are 0-4; runId stores the caller's battle association, not a UI title.
Use model.capacity(territory) for the effective run cap; static troopCap remains the Classic cap.
new GameModel(index, difficulty, seed, frozenLevel) and snapshotLevel() support frozen node
topology/factions/starting conditions. FL05 accepts 140-troop player kings only with the run cap perk.

## Compile and Run

From the repository root in PowerShell (Java 17):

```powershell
$jdk = (Get-ChildItem .toolchain -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
$source = 'app/src/main/java/com/frontline/offline'
& "$jdk/bin/javac.exe" -encoding UTF-8 -d build/v11-model "$source/GameModel.java" "$source/Challenge.java" tools/com/frontline/offline/DefenseBalance.java tools/com/frontline/offline/BalanceLab.java tests/com/frontline/offline/V11ModelTest.java tests/com/frontline/offline/V11BalanceTest.java tests/com/frontline/offline/V10ModelTest.java tests/com/frontline/offline/V10ChallengeTest.java
& "$jdk/bin/java.exe" -cp build/v11-model com.frontline.offline.V11ModelTest
& "$jdk/bin/java.exe" -cp build/v11-model com.frontline.offline.V11BalanceTest
& "$jdk/bin/java.exe" -cp build/v11-model com.frontline.offline.V10ModelTest
& "$jdk/bin/java.exe" -cp build/v11-model com.frontline.offline.V10ChallengeTest
& "$jdk/bin/java.exe" -cp build/v11-model com.frontline.offline.DefenseBalance tools/balance-reports/v11-defense working-tree-v11
& "$jdk/bin/java.exe" -cp build/v11-model com.frontline.offline.BalanceLab --suite ai --out tools/balance-reports/v11-lab --revision working-tree-v11 --seeds 0,7,1000 --maps 0,12,36 --difficulties 1 --rules 10,11 --step 0.05 --cap 120
```

The model mains are standalone so the parent can wire them into the portable suite.
The repository's full portable suite and rendering run remain scripts/test.ps1.

## CLI and Outputs

BalanceLab supports --suite all|defense|ai, --out, --revision, --seeds, --maps,
--difficulties, --rules, --step and --cap. Defaults are the matrix in the command above;
the default suite is all. Map indices are zero-based. Duration caps must be positive,
at most 3600 seconds; update steps must be positive and at most 0.1 seconds. The defence
suite deliberately uses its fixed 0.05s acceptance step and both 20-seed sets regardless
of the AI tournament's options.

- DefenseBalance: attempts.csv and summary.md. Seeds 0-19 and 1000-1019 are matched
  before/after across Home, Province and Veiled on all difficulties. Both legal active
  profiles run all 40 seeds on Normal. Each row includes troops sent/captures/losses.
- BalanceLab: attempts.csv, summary.csv, summary.md and seeds.txt. Revision, rules/AI
  version, map, difficulty, seed, step, profile, independent seat/style rotations,
  explicit seat assignment, resignation policy, duration cap and terminal reason are recorded.
- Lab result is measured relative to the rotating focus controller. A timeout never
  becomes a win or loss. Completed elimination, completed resignation, draws and timeouts
  are separate. Summary denominators include every attempt, not only completions.
- Jobs run in fixed map/difficulty/rules/profile/seat/style/policy/seed order. Same inputs
  yield byte-identical CSV/summary output; no timestamps or wall-time estimates are included.
- --revision should identify the parent commit or explicitly identify uncommitted code.
  Baseline regression fingerprints and compatibility tests are separate from these experiments.

### Narrow AI Interface

model.labAi(int[] stylesBySeat, boolean allowResignation) returns a LabAi with
step(float), winner(), finished(), resignationUsed(). Styles use CLASSIC/PRESSURE/GUARDIAN.
Winner -1 means unfinished, -2 draw, otherwise the actual surviving seat. Lab stepping
uses real production, arrivals, interception, AI and legal launch. It never changes the
player-facing model.outcome or terminalReason. A temporary style override is scoped to
one step. Normal update() remains the player game; lab sessions are not resumable battle saves.

The guarded >90% ownership/10s resignation rule is generalized to any dominant lab seat
only inside LabAi. The two policies are disabled and guarded_anyseat90. Actual player-seat
timer asymmetry and Pressure's preference for the player king are retained and measured
through seat rotation, not silently normalized away.

## Available Evidence and Limitations

Focused checks: V10ModelTest 199,581; V10ChallengeTest 2,197; V11ModelTest 94,219;
V11BalanceTest 5,574. The V10 campaign fingerprints are deliberately rules-10 fixtures.
V11 campaign checks match initial army counts before comparing every simulation state
across all 60 maps and all three difficulties. FL04 mission continuation is tested for
all nine presets and all difficulties without changing old timers or rules.

The committed-source defence matrix has zero passive-gate violations. Home seed 0 and
Province seed 13 each permit both distinct active approaches. The simple Province
counterattack controller wins only 2/20 development and 1/20 holdout attempts: this is
an unresolved difficulty/playtest question, not a claim of reasonable human difficulty.
Veiled's reported changes are caused by equal starting armies, not mission pressure.

The 720-job AI sample produced 256 natural completions and 464 timeouts at 120 seconds.
There were no resignation-enabled completions in this sample; the policy itself has focused
unit coverage. This does not establish a campaign win-rate target or justify global AI tuning.
Native performance and human sessions remain pending.

Human checklist (pending): start a mission unaided; explain its objective; interpret an
actual budget/king defeat; try reinforcement and counterattack; locate Settings/Daily;
switch languages; continue a protected old attempt; record confusion, attempted actions,
completion, and desire to replay with consent. No automated result substitutes for sessions.

## Parent Staging Boundaries

Phase 1: GameModel/Challenge metadata and objective changes, V10ModelTest/V10ChallengeTest/
SharedRulesTest updates, V11ModelTest, DefenseBalance and v11-defense reports.
Phase 2: BalanceLab, V11BalanceTest, the LabAi/advance wrapper hunks in GameModel,
v11-lab reports, and this lab guide. Run perk/frozen-map APIs are reserved model integration
for the independent Run phase. No commits, staging, GameScene/Progress/MainActivity edits,
or output/tmp changes are performed by this owner. Logistics routing remains deferred.
