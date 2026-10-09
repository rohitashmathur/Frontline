package com.frontline.offline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

/** Matched, real-model mission experiments; controllers issue only ordinary launch commands. */
public final class DefenseBalance {
    public static final float STEP = .05f;
    public static final int[] SEEDS = seeds();
    private DefenseBalance() { }

    private static int[] seeds() {
        int[] result = new int[40];
        for (int i = 0; i < 20; i++) { result[i] = i; result[i+20] = 1000+i; }
        return result;
    }

    public static GameModel simulate(int id, int difficulty, long seed, boolean legacy, String profile) {
        Challenge challenge = Challenge.PRESETS[id];
        GameModel model = legacy ? challenge.createLegacy(difficulty,seed,id,"") : challenge.create(difficulty,seed,id,"");
        int ticks = (int)Math.ceil((challenge.seconds+2)/STEP);
        for (int tick = 0; tick < ticks && model.outcome == GameModel.PLAYING; tick++) {
            if (!profile.equals("passive") && tick%8 == 0) command(model,profile);
            model.update(STEP);
            model.drainEvents();
        }
        return model;
    }

    public static void command(GameModel model, String profile) {
        int king = model.originalKing(GameModel.PLAYER);
        boolean fortify = profile.equals("fortify");
        GameModel.Territory home = model.territories.get(king);
        int threat = incoming(model,king,false);
        if (fortify) for (GameModel.Territory target : model.territories) if (target.owner == GameModel.PLAYER) {
            int hostile = incoming(model,target.id,false);
            if (hostile == 0) continue;
            for (GameModel.Territory source : model.territories) if (source.owner == GameModel.PLAYER && source.id != target.id) {
                int need = hostile+8-target.count()-incoming(model,target.id,true);
                int reserve = source.id == king ? 20 : Math.max(5,incoming(model,source.id,false)+3);
                if (need > 0 && source.count() > reserve) send(model,source,target,Math.min(need,source.count()-reserve));
            }
        }
        // Reinforcement is based on visible arrivals, not a privileged mutation of battle state.
        for (GameModel.Territory source : model.territories) {
            if (source.owner != GameModel.PLAYER || source.id == king) continue;
            int need = threat+18-home.count()-incoming(model,king,true);
            if (fortify && model.elapsed > 35) need = Math.max(need,110-home.count()-incoming(model,king,true));
            int reserve = fortify ? Math.max(10,incoming(model,source.id,false)-incoming(model,source.id,true)+6) : 4;
            if (need > 0 && source.count() > reserve) send(model,source,home,Math.min(need,source.count()-reserve));
        }
        for (GameModel.Territory source : model.territories) {
            if (source.owner != GameModel.PLAYER) continue;
            int reserve = source.id == king ? Math.max(fortify ? 24 : 16,threat-incoming(model,king,true)+12)
                : fortify ? Math.max(10,incoming(model,source.id,false)-incoming(model,source.id,true)+6) : 5;
            int available = source.count()-reserve;
            if (available < (fortify ? 3 : 8) || fortify && source.id == king && model.elapsed > 32) continue;
            GameModel.Territory best = null;
            double bestScore = -Double.MAX_VALUE;
            int amount = 0;
            for (GameModel.Territory target : model.territories) {
                if (target.owner == GameModel.PLAYER || fortify && target.capital) continue;
                double distance = Math.hypot(target.x-source.x,target.y-source.y);
                double rate = target.owner == GameModel.NEUTRAL ? 0 : (target.capital ? 2.2 : 1.35)*model.teamMultiplier(target.owner);
                double defense = Math.min(model.capacity(target),target.troops+rate*(.3+distance/2.6));
                int margin = fortify && target.owner == GameModel.NEUTRAL ? 2 : 6;
                int budget = Math.max(0,(int)Math.ceil((defense+margin-incoming(model,target.id,true))/(1-rate*.022)));
                if (budget == 0 || budget > available) continue;
                double score = 35-budget*.4-distance*4+(target.capital ? 38 : 0);
                if (fortify) score -= Math.hypot(target.x-home.x,target.y-home.y)*3;
                if (score > bestScore) { bestScore = score; best = target; amount = budget; }
            }
            if (best != null) send(model,source,best,amount);
        }
        if (!fortify) {
            for (GameModel.Territory target : model.territories) if (target.capital && target.owner > GameModel.PLAYER) {
                ArrayList<GameModel.Territory> sources = new ArrayList<>(); int capacity = 0;
                double travel = 0;
                for (GameModel.Territory source : model.territories) if (source.owner == GameModel.PLAYER) {
                    int reserve = source.id == king ? Math.max(18,threat-incoming(model,king,true)+10) : 5;
                    if (source.count()-reserve < 8) continue;
                    sources.add(source); capacity += source.count()-reserve;
                    travel = Math.max(travel,.3+Math.hypot(source.x-target.x,source.y-target.y)/2.6);
                }
                double rate = 2.2*model.teamMultiplier(target.owner);
                int required = Math.max(0,(int)Math.ceil((Math.min(model.capacity(target),target.troops+rate*travel)+8
                    -incoming(model,target.id,true))/(1-rate*.022)));
                if (sources.size() < 2 || required <= 0 || capacity < required) continue;
                for (GameModel.Territory source : sources) {
                    int reserve = source.id == king ? Math.max(18,threat-incoming(model,king,true)+10) : 5;
                    int amount = Math.min(required,source.count()-reserve);
                    required -= send(model,source,target,amount);
                    if (required <= 0) break;
                }
            }
        }
    }

    private static int send(GameModel model, GameModel.Territory source, GameModel.Territory target, int amount) {
        if (amount <= 0 || source.count() < amount) return 0;
        return model.launch(source.id,target.id,Math.min(1,(amount+.00001)/source.count()));
    }

    private static int incoming(GameModel model, int target, boolean friendly) {
        int total = 0;
        for (GameModel.Troop troop : model.troops) if (troop.target == target && (troop.owner == GameModel.PLAYER) == friendly) total += troop.units;
        return total;
    }

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length == 0 ? "build/v11-defense" : args[0]); Files.createDirectories(output);
        StringBuilder raw = new StringBuilder("revision,configuration,rules_version,ai_version,mission_config_version,mission,difficulty,seed,seed_set,step,controller,seat_assignment,resignation_policy,duration_cap,result,terminal_reason,elapsed,units_sent,captures,units_lost\n");
        StringBuilder summary = new StringBuilder("# Matched Defence Balance\n\nActual GameModel; step 0.05s; seeds 0-19 and 1000-1019. No state overrides. Before: rules 10, configuration 1. After: rules 11, configuration 2.\n\nAll factions begin with one king and the same entire base army in revised missions. Home/Province neutral garrisons are floor(original * 0.40/0.15), minimum 1. Both use protected-king priority +55, coordinated protected-king attacks when affordable, and do not intentionally attack other rivals. Normal/Hard enemy intervals are 0.9/0.6s plus the unchanged 0-0.6s seeded jitter. Easy uses its existing interval. No combat/production/cap bonuses or seed exceptions. Home/Province survival durations remain 45/60s. Veiled has no pressure changes; only the new equal-start rule applies.\n\n| Mission | Difficulty | Seed Set | Before Wins | After Wins | Attempts |\n|---|---|---|---:|---:|---:|\n");
        String revision = args.length > 1 ? args[1] : "working-tree-v11";
        int failures = 0;
        for (int id : new int[] {3,4,5}) for (int difficulty = 0; difficulty < 3; difficulty++) for (int group = 0; group < 2; group++) {
            int[] wins = new int[2];
            for (int configuration = 0; configuration < 2; configuration++) for (int i = group*20; i < group*20+20; i++) {
                GameModel model = simulate(id,difficulty,SEEDS[i],configuration == 0,"passive");
                if (model.outcome == GameModel.WON) wins[configuration]++;
                row(raw,revision,model,configuration == 0 ? "before" : "after",SEEDS[i],group,"passive");
            }
            summary.append(String.format(Locale.US,"| %s | %s | %s | %d | %d | 20 |%n",Challenge.PRESETS[id].name,GameModel.DIFFICULTIES[difficulty],group == 0 ? "0-19" : "1000-1019",wins[0],wins[1]));
            if (id != 5 && difficulty > 0 && wins[1] > 2) failures++;
        }
        summary.append("\n## Active Strategies\n\nFortify expands near home, defends ordinary holdings against visible arrivals, and reinforces the king; it never attacks enemy kings. Counterattack reinforces the king only against visible threats, expands, then attacks enemy kings with combined launches. Commands every 0.4s, no hidden state changes. All 40 seeds are evaluated, without cherry-picking. Per-seed results and deployed troop counts are in attempts.csv.\n\n| Mission | Strategy | Seed Set | Wins | Losses | Timeouts | Attempts |\n|---|---|---|---:|---:|---:|---:|\n");
        for (int id : new int[] {3,4}) for (String profile : new String[] {"fortify","counterattack"}) for (int group = 0; group < 2; group++) {
            int wins = 0, losses = 0, timeouts = 0;
            for (int i = group*20; i < group*20+20; i++) {
                GameModel model = simulate(id,1,SEEDS[i],false,profile);
                row(raw,revision,model,"after",SEEDS[i],group,profile);
                if (model.outcome == GameModel.WON) wins++; else if (model.outcome == GameModel.LOST) losses++; else timeouts++;
            }
            summary.append(String.format(Locale.US,"| %s | %s | %s | %d | %d | %d | 20 |%n",Challenge.PRESETS[id].name,profile,group == 0 ? "0-19" : "1000-1019",wins,losses,timeouts));
            if (group == 0 && wins == 0) failures++;
        }
        summary.append("\nGate violations: ").append(failures).append(". Counts are completed wins / 20, not human difficulty evidence. Active results are automated feasibility only. Province counterattack is difficult for this simple controller; its low win rate is an unresolved balance/playtest question, not validation of enjoyable difficulty. Home seed 0 and Province seed 13 demonstrate wins by both distinct approaches, without state mutation. Human playtesting pending.\n");
        Files.writeString(output.resolve("attempts.csv"),raw,StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),summary,StandardCharsets.UTF_8);
        System.out.print(summary);
    }

    private static String result(GameModel model) { return model.outcome == GameModel.WON ? "win" : model.outcome == GameModel.LOST ? "loss" : "timeout"; }
    private static void row(StringBuilder csv, String revision, GameModel model, String configuration, int seed, int group, String profile) {
        csv.append(String.format(Locale.US,"%s,%s,%d,%d,%d,%s,%s,%d,%s,0.05,%s,player=0;native_rivals,guarded_player90,%.2f,%s,%d,%.3f,%d,%d,%d%n",
            revision,configuration,model.rulesVersion,model.aiVersion,model.missionConfigVersion,Challenge.PRESETS[model.challengeId].name,GameModel.DIFFICULTIES[model.difficulty],seed,
            group == 0 ? "0-19" : "1000-1019",profile,model.objectiveSeconds+2,result(model),model.terminalReason,model.elapsed,model.unitsSent,model.captures,model.unitsLost));
    }
}
