package com.frontline.offline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Small deterministic headless lab; player WON/LOST is never used to infer lab winners. */
public final class BalanceLab {
    private static final class Options {
        Path output = Path.of("build/v11-balance-lab");
        String revision = "working-tree-v11", suite = "all";
        int[] seeds = {0,7,1000}, maps = {0,12,36}, difficulties = {1}, rules = {10,11};
        float step = .05f, cap = 120;
    }
    private static final class Summary {
        int attempts, wins, losses, timeouts, draws, natural, resignation;
        double duration;
    }
    private BalanceLab() { }

    public static void main(String[] args) throws Exception {
        Options options = parse(args); Files.createDirectories(options.output);
        Files.writeString(options.output.resolve("seeds.txt"),Arrays.toString(options.seeds)+"\n",StandardCharsets.UTF_8);
        if (!options.suite.equals("ai")) DefenseBalance.main(new String[] {options.output.resolve("defense").toString(),options.revision});
        if (!options.suite.equals("defense")) tournament(options);
    }

    private static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--help")) {
                System.out.println("BalanceLab --suite all|defense|ai --out PATH --revision LABEL --seeds 0,7,1000 --maps 0,12,36 --difficulties 1 --rules 10,11 --step 0.05 --cap 120");
                System.exit(0);
            }
            if (i+1 >= args.length) throw new IllegalArgumentException("Missing value: "+args[i]);
            String value = args[++i];
            switch (args[i-1]) {
                case "--out": options.output = Path.of(value); break;
                case "--revision": options.revision = value; break;
                case "--suite": options.suite = value; break;
                case "--seeds": options.seeds = ints(value); break;
                case "--maps": options.maps = ints(value); break;
                case "--difficulties": options.difficulties = ints(value); break;
                case "--rules": options.rules = ints(value); break;
                case "--step": options.step = Float.parseFloat(value); break;
                case "--cap": options.cap = Float.parseFloat(value); break;
                default: throw new IllegalArgumentException("Unknown option: "+args[i-1]);
            }
        }
        if (!options.suite.equals("all") && !options.suite.equals("defense") && !options.suite.equals("ai")) throw new IllegalArgumentException("Invalid suite");
        if (!Float.isFinite(options.step) || options.step <= 0 || options.step > .1f || !Float.isFinite(options.cap) || options.cap <= 0 || options.cap > 3600)
            throw new IllegalArgumentException("Invalid simulation duration");
        for (int map : options.maps) if (map < 0 || map >= GameModel.LEVELS.length) throw new IllegalArgumentException("Map index");
        for (int difficulty : options.difficulties) if (difficulty < 0 || difficulty > 2) throw new IllegalArgumentException("Difficulty");
        for (int rule : options.rules) if (rule != 10 && rule != GameModel.RULES_VERSION) throw new IllegalArgumentException("Rules version");
        return options;
    }

    private static int[] ints(String value) { return Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).distinct().toArray(); }

    private static void tournament(Options options) throws Exception {
        StringBuilder csv = new StringBuilder("revision,rules_version,ai_version,map_index,map,difficulty,seed,step,controller_profile,seat_rotation,style_rotation,seat_assignment,resignation_policy,duration_cap,result,winner,winner_basis,terminal_reason,elapsed,player_outcome,model_terminal_reason\n");
        Map<String,Summary> groups = new LinkedHashMap<>();
        // Job order is stable: map, difficulty, rules, profile, seat, style, policy, seed.
        for (int map : options.maps) for (int difficulty : options.difficulties) for (int rules : options.rules)
            for (int profile = 0; profile < 2; profile++) {
                int seats = GameModel.LEVELS[map].opponents+1;
                for (int rotation = 0; rotation < seats; rotation++) for (int styleRotation = 0; styleRotation < 3; styleRotation++)
                    for (boolean resign : new boolean[] {false,true}) for (int seed : options.seeds) {
                        int[] styles = new int[seats];
                        for (int controller = 0; controller < seats; controller++) {
                            int style = profile == 0 ? controller%3 : controller == 0 ? GameModel.PRESSURE : GameModel.GUARDIAN;
                            styles[(controller+rotation)%seats] = (style+styleRotation)%3;
                        }
                        GameModel model = new GameModel(map,difficulty,seed,rules); model.aiVersion = 1;
                        GameModel.LabAi lab = model.labAi(styles,resign);
                        int ticks = (int)Math.ceil(options.cap/options.step);
                        for (int tick = 0; tick < ticks && !lab.finished(); tick++) {
                            float remaining = options.cap-model.elapsed;
                            if (remaining <= .00001f) break;
                            lab.step(Math.min(options.step,remaining)); model.drainEvents();
                        }
                        int winner = lab.winner();
                        String result = !lab.finished() ? "timeout" : winner == -2 ? "draw" : winner == rotation ? "win" : "loss";
                        String basis = !lab.finished() ? "none" : winner == -2 ? "draw" : lab.resignationUsed() ? "resignation" : "elimination";
                        String reason = !lab.finished() ? "duration_cap" : "lab_"+basis;
                        String profileName = profile == 0 ? "mixed" : "pressure_vs_guardians";
                        String policy = resign ? "guarded_anyseat90" : "disabled";
                        String assignment = Arrays.toString(styles).replace(", ",";");
                        csv.append(String.format(Locale.US,"%s,%d,%d,%d,%s,%s,%d,%.4f,%s,%d,%d,%s,%s,%.3f,%s,%d,%s,%s,%.3f,%d,%d%n",
                            csv(options.revision),rules,model.aiVersion,map,csv(model.level().name),GameModel.DIFFICULTIES[difficulty],seed,options.step,
                            profileName,rotation,styleRotation,assignment,policy,options.cap,result,winner,basis,reason,model.elapsed,model.outcome,model.terminalReason));
                        String key = rules+","+map+","+GameModel.DIFFICULTIES[difficulty]+","+profileName+","+rotation+","+styleRotation+","+policy;
                        Summary summary = groups.computeIfAbsent(key,k -> new Summary()); summary.attempts++; summary.duration += model.elapsed;
                        if (result.equals("timeout")) summary.timeouts++; else if (result.equals("draw")) summary.draws++;
                        else { if (result.equals("win")) summary.wins++; else summary.losses++; if (basis.equals("resignation")) summary.resignation++; else summary.natural++; }
                    }
            }
        StringBuilder grouped = new StringBuilder("rules_version,map_index,difficulty,controller_profile,seat_rotation,style_rotation,resignation_policy,attempts,focus_wins,focus_losses,timeouts,draws,natural_eliminations,resignation_enabled_wins,mean_duration\n");
        int attempts = 0, wins = 0, losses = 0, timeouts = 0, draws = 0, natural = 0, resignation = 0;
        for (Map.Entry<String,Summary> group : groups.entrySet()) {
            Summary s = group.getValue(); grouped.append(String.format(Locale.US,"%s,%d,%d,%d,%d,%d,%d,%d,%.3f%n",group.getKey(),s.attempts,s.wins,s.losses,s.timeouts,s.draws,s.natural,s.resignation,s.duration/s.attempts));
            attempts += s.attempts; wins += s.wins; losses += s.losses; timeouts += s.timeouts; draws += s.draws; natural += s.natural; resignation += s.resignation;
        }
        String report = String.format(Locale.US,"# Balance Lab\n\nRevision: %s (uncommitted working tree).\n\nMaps: %s; difficulties: %s; rules: %s; seeds: %s; step: %.4fs; duration cap: %.3fs.\n\nFocus controller rotates through every starting seat; controller styles rotate independently through Classic/Pressure/Guardian. Seat assignments are explicit per row. Opponent policies are measured with resignation disabled and with the existing guarded >90%%/10s rule generalized to any lab seat. Lab winner tracking never changes player-facing WON/LOST. No production, cap, combat or campaign-AI bonuses are introduced.\n\n| Result | Count | Denominator |\n|---|---:|---:|\n| Completed focus wins | %d | %d |\n| Completed focus losses | %d | %d |\n| Timeouts (no inferred winner) | %d | %d |\n| Draws | %d | %d |\n| Natural elimination completions | %d | %d |\n| Completions after resignation | %d | %d |\n\nRaw results: attempts.csv. Full matched groups and denominators: summary.csv. Explicit seeds: seeds.txt. Deterministic job order and no wall-clock timestamps make reruns byte-reproducible. Percentages must use each group's attempts as their denominator, not only completed games.\n\nThis small matrix is measurement, not shipping balance targets. Timeouts remain unresolved games. Player-seat timing and Pressure's player-king targeting are intentionally inherited from the actual model; seat rotation exposes their effect. Human difficulty/enjoyment and native-device performance are not validated.\n",
            options.revision,Arrays.toString(options.maps),Arrays.toString(options.difficulties),Arrays.toString(options.rules),Arrays.toString(options.seeds),options.step,options.cap,
            wins,attempts,losses,attempts,timeouts,attempts,draws,attempts,natural,attempts,resignation,attempts);
        Files.writeString(options.output.resolve("attempts.csv"),csv,StandardCharsets.UTF_8);
        Files.writeString(options.output.resolve("summary.csv"),grouped,StandardCharsets.UTF_8);
        Files.writeString(options.output.resolve("summary.md"),report,StandardCharsets.UTF_8);
        System.out.print(report);
    }

    private static String csv(String value) { return '"'+value.replace("\"","\"\"")+'"'; }
}
