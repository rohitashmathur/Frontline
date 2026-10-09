package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Matched actual-LabAi measurement; no campaign maps, preferences, or records are changed. */
public final class LogisticsComparison {
    private static final long[] SEEDS = {0, 7, 1000};
    private static final int DIFFICULTY = 1, AI_VERSION = 1;
    private static final float STEP = .05f, CAP = 180;
    private static final String[] PROFILES = {"mixed", "pressure_vs_guardians"};
    private static final String RAW_HEADER = "revision,pair_id,mode,map_id,map,columns,rows,opponents,rules_version,config_version,routing_version,ai_version,difficulty,seed,step,duration_cap,controller_profile,seat_rotation,style_rotation,focus_seat,focus_style,styles_by_seat,starting_cells,resignation_policy,result,completed,winner_seat,winner_controller,winner_basis,terminal_reason,elapsed,ticks,player_outcome,model_terminal_reason,units_sent,units_lost,captures,intercepted,capped_reinforcements,initial_sha256,final_state_sha256\n";
    private static final String SUMMARY_HEADER = "revision,mode,map_id,map,rules_version,config_version,routing_version,ai_version,difficulty,controller_profile,seat_rotation,style_rotation,resignation_policy,attempts,completed,focus_wins,focus_losses,draws,timeouts,mean_all_seconds,mean_completed_seconds,mean_timeout_seconds\n";

    private LogisticsComparison() {}

    /** In-memory results; compare() performs no filesystem writes. */
    public static final class Report {
        public final String attemptsCsv, summaryCsv, summaryMarkdown, seedsText;
        public final int attemptCount;
        public final boolean rerunVerified;

        private Report(Data data, boolean verified) {
            attemptsCsv = data.attempts.toString(); summaryCsv = data.summary.toString();
            summaryMarkdown = data.markdown.toString() + "\nDeterministic rerun: "
                + (verified ? "byte-identical raw CSV, grouped summary, and report verified." : "not checked; use --verify-rerun.") + "\n";
            seedsText = "0\n7\n1000\n";
            attemptCount = data.attemptCount; rerunVerified = verified;
        }
    }

    private static final class Options {
        Path output = Paths.get("build/logistics-comparison");
        String revision;
        boolean verify;
    }

    public static void main(String[] args) throws Exception {
        Options options = parse(args);
        if (options == null) return;
        Report report = compare(options.revision, options.verify);
        // Readiness and the complete matrix (including any rerun) succeed before creating output files.
        Files.createDirectories(options.output);
        Files.write(options.output.resolve("attempts.csv"), report.attemptsCsv.getBytes(StandardCharsets.UTF_8));
        Files.write(options.output.resolve("summary.csv"), report.summaryCsv.getBytes(StandardCharsets.UTF_8));
        Files.write(options.output.resolve("summary.md"), report.summaryMarkdown.getBytes(StandardCharsets.UTF_8));
        Files.write(options.output.resolve("seeds.txt"), report.seedsText.getBytes(StandardCharsets.UTF_8));
        System.out.print(report.summaryMarkdown);
    }

    /** Fixed matrix: every Logistics preset, Normal, seeds 0/7/1000, dt .05, cap 180, no resignation. */
    public static Report compare(String revision, boolean verifyRerun) throws Exception {
        validateRevision(revision);
        requireReady();
        Data first = matrix(revision);
        if (verifyRerun) {
            Data second = matrix(revision);
            if (first.attemptCount != second.attemptCount || !first.attempts.toString().equals(second.attempts.toString())
                || !first.summary.toString().equals(second.summary.toString())
                || !first.markdown.toString().equals(second.markdown.toString()))
                throw new IllegalStateException("Logistics comparison rerun was not byte-identical");
        }
        return new Report(first, verifyRerun);
    }

    private static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (option.equals("--help")) {
                System.out.println("LogisticsComparison --revision LABEL [--out PATH] [--verify-rerun]");
                System.out.println("All Logistics maps; Normal; seeds 0,7,1000; step 0.05; cap 180; resignation disabled.");
                return null;
            }
            if (option.equals("--verify-rerun")) { options.verify = true; continue; }
            if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value: " + option);
            String value = args[++i];
            if (option.equals("--revision")) options.revision = value;
            else if (option.equals("--out")) {
                if (value.trim().isEmpty()) throw new IllegalArgumentException("Output path");
                options.output = Paths.get(value);
            } else throw new IllegalArgumentException("Unknown option: " + option);
        }
        validateRevision(options.revision);
        return options;
    }

    private static void validateRevision(String revision) {
        if (revision == null || revision.trim().isEmpty() || revision.indexOf('\n') >= 0 || revision.indexOf('\r') >= 0)
            throw new IllegalArgumentException("--revision must identify the revision or explicitly label the working tree");
    }

    private static void requireReady() throws Exception {
        for (Logistics preset : Logistics.PRESETS) {
            Pair pair = createPair(preset.id, SEEDS[0]);
            GameModel model = pair.logistics;
            int source = model.originalKing(GameModel.PLAYER), blocked = -1;
            for (GameModel.Territory target : model.territories)
                if (target.id != source && LogisticsRoutes.getRoute(model, source, target.id, GameModel.PLAYER) == null) {
                    blocked = target.id; break;
                }
            if (blocked < 0) throw new IllegalStateException("Readiness fixture has no blocked route");
            byte[] before = model.save();
            if (model.previewAmount(source, blocked, .5) != 0 || model.launch(source, blocked, .5) != 0
                || !Arrays.equals(before, model.save()))
                throw new IllegalStateException("Phase 4 routed launch/refusal is not ready; no comparison was run");
            int[] styles = styles(model.level().opponents + 1, 0, 0, 0);
            try { model.labAi(styles, false); }
            catch (IllegalStateException pending) {
                throw new IllegalStateException("Phase 4 GameModel.labAi must support MODE_LOGISTICS without changing player outcomes", pending);
            }
        }
    }

    private static Pair createPair(int mapId, long seed) throws Exception {
        GameModel logistics = Logistics.create(mapId, DIFFICULTY, seed);
        int config = logistics.logisticsConfigVersion;
        if (logistics.battleMode != GameModel.MODE_LOGISTICS || logistics.logisticsId != mapId
            || Logistics.getIndex(logistics) != mapId || config <= 0 || logistics.routingVersion != GameModel.ROUTING_VERSION)
            throw new IllegalStateException("Phase 4 Logistics.create must configure logisticsId/logisticsConfigVersion/routingVersion");
        GameModel classic = new GameModel(0, DIFFICULTY, seed, logistics.snapshotLevel());
        classic.aiVersion = logistics.aiVersion = AI_VERSION;
        if (classic.battleMode != GameModel.MODE_CAMPAIGN || classic.rulesVersion != logistics.rulesVersion || classic.routingVersion != 0)
            throw new IllegalStateException("Classic counterpart must use identical current rules and a frozen layout");
        byte[] initial = initialState(classic), routedInitial = initialState(logistics);
        if (!Arrays.equals(initial, routedInitial)) throw new IllegalStateException("Matched modes have different initial configurations");
        return new Pair(classic, logistics, config, hex(MessageDigest.getInstance("SHA-256").digest(initial)));
    }

    private static Data matrix(String revision) throws Exception {
        Data data = new Data();
        data.markdown.append("# Logistics Comparison\n\nRevision: ").append(revision)
            .append("\n\nNormal; matched seeds 0, 7, 1000; update step 0.05 seconds; duration cap 180 seconds; resignation disabled.\n\n")
            .append("Each pair uses the same frozen Logistics layout, holes, factions, initial armies, seed, and actual LabAi controller assignment. Classic keeps unrestricted targeting; Logistics uses the model's routed rules. The focus controller rotates through every starting seat; controller styles rotate independently through Classic, Pressure, and Guardian. Player-seat timing and targeting asymmetries are inherited, not normalized.\n\n")
            .append("Completed means LabAi finished, including separately counted draws. Player WON/LOST is never used to infer a lab winner. Timeouts have no winner. Means for completed games and capped timeouts are separate; NA means there are no observations.\n\n")
            .append("| Map ID | Map | Mode | Rules | Config | Routing | Attempts | Completed | Focus Wins | Focus Losses | Draws | Timeouts | Mean Completed Seconds | Mean Timeout Seconds |\n")
            .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        StringBuilder paired = new StringBuilder("\n| Map ID | Matched Pairs | Both Completed | Classic Only Completed | Logistics Only Completed | Both Timeout | Mean Logistics Minus Classic Seconds, Both Completed |\n|---|---|---|---|---|---|---|\n");
        int pairId = 0;
        for (Logistics preset : Logistics.PRESETS) {
            Stats classicTotal = new Stats(), logisticsTotal = new Stats();
            PairStats pairs = new PairStats();
            int config = -1, rules = -1, routing = -1;
            for (int profile = 0; profile < PROFILES.length; profile++)
                for (int seat = 0; seat <= preset.level.opponents; seat++) for (int style = 0; style < 3; style++) {
                    Stats classicGroup = new Stats(), logisticsGroup = new Stats();
                    for (long seed : SEEDS) {
                        Pair pair = createPair(preset.id, seed);
                        if (config < 0) { config = pair.config; rules = pair.classic.rulesVersion; routing = pair.logistics.routingVersion; }
                        if (config != pair.config || rules != pair.classic.rulesVersion || routing != pair.logistics.routingVersion)
                            throw new IllegalStateException("Map configuration changed within the matrix");
                        int[] assignment = styles(pair.classic.level().opponents + 1, profile, seat, style);
                        Attempt classic = simulate(pair.classic, seat, assignment), logistics = simulate(pair.logistics, seat, assignment);
                        appendAttempt(data.attempts, revision, pairId, "classic", preset.id, pair, pair.classic, profile, seat, style, seed, assignment, classic);
                        appendAttempt(data.attempts, revision, pairId, "logistics", preset.id, pair, pair.logistics, profile, seat, style, seed, assignment, logistics);
                        classicGroup.add(classic); logisticsGroup.add(logistics);
                        classicTotal.add(classic); logisticsTotal.add(logistics); pairs.add(classic, logistics);
                        data.attemptCount += 2; pairId++;
                    }
                    appendSummary(data.summary, revision, "classic", preset, rules, config, 0, profile, seat, style, classicGroup);
                    appendSummary(data.summary, revision, "logistics", preset, rules, config, routing, profile, seat, style, logisticsGroup);
                }
            appendTable(data.markdown, preset, "classic", rules, config, 0, classicTotal);
            appendTable(data.markdown, preset, "logistics", rules, config, routing, logisticsTotal);
            paired.append("| ").append(preset.id).append(" | ").append(pairs.count).append(" | ").append(pairs.bothCompleted)
                .append(" | ").append(pairs.classicOnly).append(" | ").append(pairs.logisticsOnly).append(" | ")
                .append(pairs.bothTimeout).append(" | ").append(mean(pairs.delta, pairs.bothCompleted)).append(" |\n");
        }
        data.markdown.append(paired).append("\nTotal attempts: ").append(data.attemptCount)
            .append("; matched pairs: ").append(pairId).append(". Every count uses that row's attempts or matched pairs as its denominator; no timeout becomes an inferred win.\n\n")
            .append("Raw results: attempts.csv. Per-profile/seat/style denominators and separate duration means: summary.csv. Explicit seeds: seeds.txt. Job order is map, controller profile, seat rotation, style rotation, seed, then Classic/Logistics. No timestamps or wall-clock measurements are included. Initial-state hashes prove matched setups; final-state hashes participate in rerun comparison.\n\n")
            .append("This is automated measurement, not human feedback, a shipping balance target, or native-device performance evidence. Routing comprehension and enjoyment remain pending human playtests.\n");
        return data;
    }

    private static Attempt simulate(GameModel model, int focusSeat, int[] styles) throws Exception {
        GameModel.LabAi lab = model.labAi(styles, false);
        int ticks = 0, maximumTicks = (int) Math.ceil(CAP / STEP) + 2;
        while (!lab.finished() && model.elapsed < CAP) {
            if (ticks >= maximumTicks) throw new IllegalStateException("Simulation did not reach its duration cap");
            float before = model.elapsed;
            lab.step(Math.min(STEP, CAP - before)); model.drainEvents(); ticks++;
            if (model.elapsed <= before || model.elapsed > CAP || model.outcome != GameModel.PLAYING
                || model.terminalReason != GameModel.TERMINAL_NONE)
                throw new IllegalStateException("Lab stepping changed player outcome, stalled, or exceeded the cap");
        }
        if (lab.resignationUsed()) throw new IllegalStateException("Resignation must remain disabled");
        for (boolean resigned : model.resigned) if (resigned) throw new IllegalStateException("Unexpected resigned seat");
        int winner = lab.winner();
        if (lab.finished() ? winner < -2 || winner == -1 || winner >= styles.length : winner != -1)
            throw new IllegalStateException("Invalid LabAi result");
        String result = !lab.finished() ? "timeout" : winner == -2 ? "draw" : winner == focusSeat ? "focus_win" : "focus_loss";
        String state = hex(MessageDigest.getInstance("SHA-256").digest(model.save()));
        return new Attempt(result, lab.finished(), winner, model.elapsed, ticks, state);
    }

    private static int[] styles(int seats, int profile, int seatRotation, int styleRotation) {
        int[] result = new int[seats];
        for (int controller = 0; controller < seats; controller++) {
            int base = profile == 0 ? controller % 3 : controller == 0 ? GameModel.PRESSURE : GameModel.GUARDIAN;
            result[(controller + seatRotation) % seats] = (base + styleRotation) % 3;
        }
        return result;
    }

    private static void appendAttempt(StringBuilder csv, String revision, int pairId, String mode, int mapId,
                                      Pair pair, GameModel model, int profile, int seat, int style, long seed,
                                      int[] assignment, Attempt result) {
        String basis = !result.completed ? "none" : result.winner == -2 ? "draw" : "elimination";
        int winnerController = result.winner < 0 ? result.winner : (result.winner - seat + assignment.length) % assignment.length;
        appendRow(csv, revision, pairId, mode, mapId, model.level().name, model.level().columns, model.level().rows,
            model.level().opponents, model.rulesVersion, pair.config, model.routingVersion, model.aiVersion, "Normal", seed, STEP, CAP,
            PROFILES[profile], seat, style, seat, GameModel.styleName(assignment[seat]), ints(assignment), ints(model.level().startingCells),
            "disabled", result.result, result.completed, result.winner, winnerController, basis,
            result.completed ? "lab_" + basis : "duration_cap", result.elapsed, result.ticks, model.outcome, model.terminalReason,
            model.unitsSent, model.unitsLost, model.captures, model.intercepted, model.cappedReinforcements, pair.initialHash, result.finalHash);
    }

    private static void appendSummary(StringBuilder csv, String revision, String mode, Logistics preset, int rules,
                                      int config, int routing, int profile, int seat, int style, Stats stats) {
        appendRow(csv, revision, mode, preset.id, preset.name, rules, config, routing, AI_VERSION, "Normal", PROFILES[profile],
            seat, style, "disabled", stats.attempts, stats.completed(), stats.wins, stats.losses, stats.draws, stats.timeouts,
            mean(stats.completedSeconds + stats.timeoutSeconds, stats.attempts), mean(stats.completedSeconds, stats.completed()),
            mean(stats.timeoutSeconds, stats.timeouts));
    }

    private static void appendTable(StringBuilder markdown, Logistics preset, String mode, int rules, int config, int routing, Stats stats) {
        markdown.append("| ").append(preset.id).append(" | ").append(preset.name).append(" | ").append(mode)
            .append(" | ").append(rules).append(" | ").append(config).append(" | ").append(routing).append(" | ").append(stats.attempts)
            .append(" | ").append(stats.completed()).append(" | ").append(stats.wins).append(" | ").append(stats.losses)
            .append(" | ").append(stats.draws).append(" | ").append(stats.timeouts).append(" | ")
            .append(mean(stats.completedSeconds, stats.completed())).append(" | ").append(mean(stats.timeoutSeconds, stats.timeouts)).append(" |\n");
    }

    private static void appendRow(StringBuilder csv, Object... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) csv.append(',');
            csv.append('"').append(String.valueOf(fields[i]).replace("\"", "\"\"")).append('"');
        }
        csv.append('\n');
    }

    private static String mean(double seconds, int count) {
        return count == 0 ? "NA" : String.format(Locale.ROOT, "%.6f", seconds / count);
    }

    private static String ints(int[] values) {
        if (values == null) return "default";
        StringBuilder text = new StringBuilder();
        for (int value : values) { if (text.length() > 0) text.append(';'); text.append(value); }
        return text.toString();
    }

    private static byte[] initialState(GameModel model) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        GameModel.Level level = model.level();
        out.writeUTF(level.name); out.writeInt(level.columns); out.writeInt(level.rows);
        out.writeInt(level.opponents); out.writeInt(level.parSeconds);
        writeInts(out, level.holes); writeInts(out, level.startingCells); writeInts(out, level.factions);
        out.writeInt(level.playerTroops); out.writeInt(level.rivalTroops); out.writeInt(level.neutralMinimum);
        out.writeInt(model.rulesVersion); out.writeInt(model.aiVersion); out.writeInt(model.difficulty); out.writeLong(model.seed);
        out.writeInt(model.territories.size());
        for (GameModel.Territory tile : model.territories) {
            out.writeInt(tile.id); out.writeFloat(tile.x); out.writeFloat(tile.y); out.writeInt(tile.owner);
            out.writeDouble(tile.troops); out.writeBoolean(tile.capital);
        }
        for (int owner = 0; owner <= level.opponents; owner++) out.writeInt(model.originalKing(owner));
        out.flush(); return bytes.toByteArray();
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values == null ? -1 : values.length);
        if (values != null) for (int value : values) out.writeInt(value);
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray(), result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) { result[i * 2] = digits[(bytes[i] & 255) >>> 4]; result[i * 2 + 1] = digits[bytes[i] & 15]; }
        return new String(result);
    }

    private static final class Pair {
        final GameModel classic, logistics;
        final int config;
        final String initialHash;
        Pair(GameModel classic, GameModel logistics, int config, String initialHash) {
            this.classic = classic; this.logistics = logistics; this.config = config; this.initialHash = initialHash;
        }
    }

    private static final class Attempt {
        final String result, finalHash;
        final boolean completed;
        final int winner, ticks;
        final float elapsed;
        Attempt(String result, boolean completed, int winner, float elapsed, int ticks, String finalHash) {
            this.result = result; this.completed = completed; this.winner = winner;
            this.elapsed = elapsed; this.ticks = ticks; this.finalHash = finalHash;
        }
    }

    private static final class Stats {
        int attempts, wins, losses, draws, timeouts;
        double completedSeconds, timeoutSeconds;
        int completed() { return wins + losses + draws; }
        void add(Attempt result) {
            attempts++;
            if (!result.completed) { timeouts++; timeoutSeconds += result.elapsed; }
            else {
                completedSeconds += result.elapsed;
                if (result.result.equals("focus_win")) wins++; else if (result.result.equals("focus_loss")) losses++; else draws++;
            }
        }
    }

    private static final class PairStats {
        int count, bothCompleted, classicOnly, logisticsOnly, bothTimeout;
        double delta;
        void add(Attempt classic, Attempt logistics) {
            count++;
            if (classic.completed && logistics.completed) { bothCompleted++; delta += logistics.elapsed - (double) classic.elapsed; }
            else if (classic.completed) classicOnly++;
            else if (logistics.completed) logisticsOnly++;
            else bothTimeout++;
        }
    }

    private static final class Data {
        final StringBuilder attempts = new StringBuilder(RAW_HEADER), summary = new StringBuilder(SUMMARY_HEADER), markdown = new StringBuilder();
        int attemptCount;
    }
}
