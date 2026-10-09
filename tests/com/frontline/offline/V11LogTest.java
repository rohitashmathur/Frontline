package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/** Standalone public-API coverage with independently written format-1 history. */
public final class V11LogTest {
    private static int checks;
    private static final String LEGACY_COLUMNS = "timestamp_ms,event,sector,sector_index,difficulty,difficulty_id,"
        + "mode,rules_version,ai_version,seed,seed_known,history_known,starting_king_lost,elapsed_seconds,"
        + "objective_type,objective_target,objective_seconds,objective_progress,deployment_budget,challenge_id,daily_date,outcome,detail";
    private static final String[] NEW_COLUMNS = {"battle_mode", "run_id", "run_node", "run_perks", "terminal_reason"};

    public static int run() throws Exception {
        checks = 0;
        modesAndSnapshots();
        terminalReasons();
        historicalMigration();
        boundsAndOptIn();
        corruption();
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("V11 playtest-log checks passed: " + run());
    }

    private static void modesAndSnapshots() throws Exception {
        PlaytestLog log = new PlaytestLog();
        GameModel run = runModel();
        log.add(1, "run_start", run, "disabled");
        check(log.size() == 0 && csv(log.exportCsv()).size() == 1, "Logging remains opt-in");
        log.enabled = true;
        log.add(1, "menu", null, "first, \"quoted\"\r\nsecond\nthird\rfinal");
        log.add(2, "campaign", new GameModel(2, 2, Long.MIN_VALUE, 10), "");
        log.add(3, "challenge", Challenge.PRESETS[0].create(1, 42, 0, ""), "");
        log.add(4, "daily", Challenge.PRESETS[6].create(0, 42, 6, "2026-10-09"), "");
        log.add(5, "run_start", run, "run association");
        GameModel logistics = new GameModel(2, 1, 42);
        logistics.battleMode = GameModel.MODE_LOGISTICS;
        log.add(6, "logistics_start", logistics, "experimental");
        String originalId = run.runId;
        int originalPerks = run.runPerks;
        run.runId = "mutated"; run.runNode = 4; run.runPerks = GameModel.PERK_START;
        run.elapsed = 99; run.terminalReason = GameModel.TERMINAL_SURRENDER;
        logistics.battleMode = GameModel.MODE_CAMPAIGN;
        List<List<String>> rows = csv(log.exportCsv());
        check(rows.get(0).subList(0, 23).equals(Arrays.asList(LEGACY_COLUMNS.split(","))), "Existing CSV header prefix and order are unchanged");
        check(rows.get(0).subList(23, 28).equals(Arrays.asList(NEW_COLUMNS)), "Only appended CSV columns");
        String[] modes = {"menu", "campaign", "challenge", "daily", "run", "logistics"};
        for (int row = 1; row <= modes.length; row++) {
            check(rows.get(row).size() == 28 && field(rows, row, "mode").equals(modes[row - 1]), "Distinct modes and consistent column counts");
            if (row != 5) check(field(rows, row, "run_id").isEmpty() && field(rows, row, "run_node").isEmpty()
                && field(rows, row, "run_perks").isEmpty(), "Other modes do not acquire run associations");
        }
        check(field(rows, 5, "battle_mode").equals(Integer.toString(GameModel.MODE_RUN))
            && field(rows, 6, "battle_mode").equals(Integer.toString(GameModel.MODE_LOGISTICS)), "Raw model mode is separately snapshotted");
        check(field(rows, 5, "run_id").equals(originalId) && field(rows, 5, "run_node").equals("2")
            && field(rows, 5, "run_perks").equals(Integer.toString(originalPerks)), "Run identity, zero-based node, and perk mask are event-time facts");
        check(field(rows, 5, "elapsed_seconds").equals("0.0") && field(rows, 5, "terminal_reason").equals("0"), "Later model changes cannot rewrite captured context");
        check(field(rows, 1, "detail").equals("first, \"quoted\"\r\nsecond\nthird\rfinal"), "CSV quoting and newlines preserve existing values");
        for (String column : NEW_COLUMNS) check(field(rows, 1, column).isEmpty(), "Menu has no fabricated battle metadata");
        byte[] saved = log.save();
        check(ByteBuffer.wrap(saved).getInt(4) == 2 && PlaytestLog.restore(saved).exportCsv().equals(log.exportCsv()), "All modes round trip in format 2");
        check(Arrays.equals(saved, PlaytestLog.restore(saved).save()), "Format-2 serialization is canonical");

        GameModel objectiveLogistics = Challenge.PRESETS[0].create(1, 42, 0, "");
        objectiveLogistics.battleMode = GameModel.MODE_LOGISTICS;
        log.add(7, "objective_logistics", objectiveLogistics, "");
        rows = csv(log.exportCsv());
        check(field(rows, 7, "mode").equals("logistics") && field(rows, 7, "objective_type").equals("1"),
            "Explicit Logistics mode takes precedence without discarding objective facts");
        List<Layout> layout = layouts(saved);
        for (int row = 0; row < layout.size(); row++) check(ByteBuffer.wrap(saved).getInt(layout.get(row).mode) == row,
            "Saved mode codes retain 0-3 and extend with Run 4 and Logistics 5");
    }

    private static void terminalReasons() throws Exception {
        PlaytestLog log = new PlaytestLog(); log.enabled = true;
        GameModel run = runModel(); run.surrender();
        GameModel restored = GameModel.restore(run.save());
        log.add(1, "run_result", restored, "");
        GameModel budget = Challenge.PRESETS[6].create(1, 42, 6, "");
        int source = budget.originalKing(GameModel.PLAYER);
        budget.territories.get(source).troops = 125;
        budget.launch(source, budget.originalKing(1), 1);
        log.add(2, "budget_result", GameModel.restore(budget.save()), "125 / 120");
        List<List<String>> rows = csv(log.exportCsv());
        check(field(rows, 1, "mode").equals("run") && field(rows, 1, "outcome").equals(Integer.toString(GameModel.LOST))
            && field(rows, 1, "terminal_reason").equals(Integer.toString(GameModel.TERMINAL_SURRENDER)), "Resumed run surrender has its actual terminal code");
        check(field(rows, 2, "mode").equals("challenge")
            && field(rows, 2, "terminal_reason").equals(Integer.toString(GameModel.TERMINAL_BUDGET)), "Actual resumed budget failure is distinct from surrender");
        for (int reason = GameModel.TERMINAL_NONE; reason <= GameModel.TERMINAL_VICTORY; reason++) {
            GameModel model = new GameModel(0, 1, 42); model.terminalReason = reason;
            log.add(10 + reason, "reason_snapshot", model, "");
        }
        rows = csv(PlaytestLog.restore(log.save()).exportCsv());
        for (int reason = GameModel.TERMINAL_NONE; reason <= GameModel.TERMINAL_VICTORY; reason++)
            check(field(rows, 3 + reason, "terminal_reason").equals(Integer.toString(reason)), "Logger stores provided codes without guessing gameplay outcomes");
    }

    private static void historicalMigration() throws Exception {
        LegacyEntry[] history = {
            new LegacyEntry(1, "tutorial_skip", 0, 0, "menu, \"quoted\"\r\nnext"),
            new LegacyEntry(2, "run_result", 1, 11, "run_id=old; do not infer a run"),
            new LegacyEntry(3, "budget_result", 2, 10, "125 / 120; cause was not stored"),
            new LegacyEntry(4, "daily_result", 3, 10, "historical Daily"),
            new LegacyEntry(5, "logistics_result", 1, 11, "routing experiment; do not infer Logistics"),
            new LegacyEntry(6, "legacy_win", 1, 0, "unknown provenance")
        };
        byte[] old = legacyFixture(history);
        check(ByteBuffer.wrap(old).getInt(4) == 1, "Independent historical fixture is format 1");
        PlaytestLog log = PlaytestLog.restore(old);
        List<List<String>> rows = csv(log.exportCsv());
        check(log.enabled && log.size() == history.length, "Historical opt-in and every event survive migration");
        for (int i = 0; i < history.length; i++) {
            check(rows.get(i + 1).subList(0, 23).equals(history[i].expectedCsv()), "Every historical CSV value remains unchanged");
            for (String column : NEW_COLUMNS) check(field(rows, i + 1, column).isEmpty(), "Absent format-1 metadata stays unknown, including terminal cause");
        }
        byte[] upgraded = log.save();
        List<Layout> before = layouts(old), after = layouts(upgraded);
        for (int i = 0; i < history.length; i++) check(Arrays.equals(
            Arrays.copyOfRange(old, before.get(i).start, before.get(i).end),
            Arrays.copyOfRange(upgraded, after.get(i).start, after.get(i).end)), "Historical serialized entry bytes are preserved exactly");
        check(field(rows, 2, "mode").equals("campaign") && field(rows, 5, "mode").equals("campaign"),
            "Historical misclassified modes are not retroactively reinterpreted from event names or detail");
        log.add(7, "new_run", runModel(), "new context");
        GameModel logistics = new GameModel(0, 1, 42); logistics.battleMode = GameModel.MODE_LOGISTICS;
        log.add(8, "new_logistics", logistics, "new context");
        byte[] mixed = log.save();
        String expected = log.exportCsv();
        for (int i = 0; i < 3; i++) {
            log = PlaytestLog.restore(mixed);
            check(Arrays.equals(mixed, log.save()) && log.exportCsv().equals(expected), "Mixed old/new history survives repeated canonical restores");
        }
        reject(patchInt(old, before.get(1).mode, 4));
        reject(patchInt(old, before.get(1).mode, 5));
        reject(patchInt(old, 4, 2));
    }

    private static void boundsAndOptIn() throws Exception {
        PlaytestLog log = new PlaytestLog(); log.enabled = true;
        GameModel valid = runModel();
        for (int i = 0; i <= PlaytestLog.ENTRY_LIMIT; i++) log.add(i, "event_" + i, valid, "");
        List<List<String>> rows = csv(log.exportCsv());
        check(log.size() == 500 && rows.size() == 501 && field(rows, 1, "event").equals("event_1")
            && field(rows, 500, "event").equals("event_500"), "Latest 500 events remain bounded");
        for (int i = 1; i <= 500; i++) check(field(rows, i, "mode").equals("run")
            && field(rows, i, "run_id").equals(valid.runId), "Retention does not lose or reassign run metadata");
        byte[] before = log.save();
        for (int i = 0; i < 12; i++) {
            GameModel invalid = runModel();
            switch (i) {
                case 0: invalid.battleMode = -1; break;
                case 1: invalid.battleMode = 3; break;
                case 2: invalid.runId = null; break;
                case 3: invalid.runId = ""; break;
                case 4: invalid.runId = repeat('x', 129); break;
                case 5: invalid.runNode = -1; break;
                case 6: invalid.runNode = 5; break;
                case 7: invalid.runPerks = -1; break;
                case 8: invalid.runPerks = 32; break;
                case 9: invalid.terminalReason = -1; break;
                case 10: invalid.terminalReason = 7; break;
                default: invalid.battleMode = GameModel.MODE_CAMPAIGN;
            }
            log.add(1000, "invalid", invalid, "");
            check(Arrays.equals(before, log.save()), "Invalid metadata is rejected before evicting valid history");
        }
        log.add(-1, "invalid", valid, ""); log.add(1, "", valid, ""); log.add(1, null, valid, "");
        check(Arrays.equals(before, log.save()), "Invalid logging inputs remain harmless");
        valid.runId = repeat('\u0905', 128); valid.runNode = 4; valid.runPerks = GameModel.ALL_RUN_PERKS;
        log.add(1001, repeat('e', 10000), valid, repeat('d', 10000));
        rows = csv(log.exportCsv());
        check(field(rows, 500, "event").length() == 128 && field(rows, 500, "detail").length() == 2048, "Existing text bounds remain unchanged");
        check(field(rows, 500, "run_id").equals(valid.runId) && field(rows, 500, "run_perks").equals("31"), "Maximum Unicode identity and all valid perks survive without truncation");
        check(PlaytestLog.restore(log.save()).exportCsv().equals(log.exportCsv()), "Bounded retained history and Unicode metadata round trip");
        String csvBefore = log.exportCsv(); log.enabled = false; log.add(1002, "disabled", valid, "");
        check(log.exportCsv().equals(csvBefore) && !PlaytestLog.restore(log.save()).enabled, "Opt-out stops new logging without deleting previous history");
        log.clear(); check(log.size() == 0 && !log.enabled, "Clear preserves opt-out");
        log.enabled = true; log.clear(); check(log.enabled && log.size() == 0, "Clear preserves opt-in");
    }

    private static void corruption() throws Exception {
        byte[] empty = new PlaytestLog().save();
        check(Arrays.equals(empty, PlaytestLog.restore(empty).save()), "Empty format-2 round trip");
        reject(null); reject(new byte[4 * 1024 * 1024 + 1]);
        for (int length : new int[] {0, 1, 8, empty.length - 1}) reject(Arrays.copyOf(empty, length));
        reject(patchInt(empty, 0, 0)); reject(patchInt(empty, 4, 3));
        reject(patchByte(empty, 8, 2)); reject(patchInt(empty, 9, -1)); reject(patchInt(empty, 9, 501));
        byte[] trailing = Arrays.copyOf(empty, empty.length + 1); checksum(trailing); reject(trailing);
        PlaytestLog log = new PlaytestLog(); log.enabled = true; log.add(1, "x", runModel(), "");
        byte[] run = log.save(); Layout layout = layouts(run).get(0);
        byte[] damaged = run.clone(); damaged[20] ^= 1; reject(damaged);
        reject(patchInt(run, layout.mode, 6)); reject(patchInt(run, layout.mode, 1));
        reject(patchInt(run, layout.elapsed, Float.floatToIntBits(Float.NaN)));
        reject(patchByte(run, layout.metadata, 2)); reject(patchByte(run, layout.metadata, 0));
        reject(patchInt(run, layout.battleMode, -1)); reject(patchInt(run, layout.battleMode, 3));
        reject(patchInt(run, layout.battleMode, GameModel.MODE_CAMPAIGN));
        reject(patchInt(run, layout.node, -1)); reject(patchInt(run, layout.node, 5));
        reject(patchInt(run, layout.perks, -1)); reject(patchInt(run, layout.perks, 32));
        reject(patchInt(run, layout.reason, -1)); reject(patchInt(run, layout.reason, 7));
        reject(replaceUtf(run, layout.runId, "")); reject(replaceUtf(run, layout.runId, repeat('x', 129)));
        reject(patchInt(run, 4, 1));
        for (int length : new int[] {layout.end, layout.metadata + 1, layout.node, run.length - 5}) {
            byte[] truncated = Arrays.copyOf(run, length + 4); checksum(truncated); reject(truncated);
        }
        log.clear(); log.add(1, "campaign", new GameModel(0, 1, 42), "");
        byte[] campaign = log.save(); layout = layouts(campaign).get(0);
        reject(patchInt(campaign, layout.node, 0)); reject(patchInt(campaign, layout.perks, 1));
        reject(replaceUtf(campaign, layout.runId, "not-a-run"));
        reject(patchInt(campaign, layout.mode, 5));
        log.clear(); log.add(1, "menu", null, "");
        byte[] menu = log.save(); layout = layouts(menu).get(0);
        reject(patchByte(menu, layout.metadata, 1)); reject(patchInt(menu, layout.reason, 0));
    }

    private static GameModel runModel() {
        GameModel model = new GameModel(2, 1, 42);
        model.configureRun("run, \"A\"\r\nsession", 2, GameModel.PERK_PRODUCTION | GameModel.PERK_SPEED);
        return model;
    }

    private static final class LegacyEntry {
        final long wall, seed;
        final String event, detail, date;
        final int mode, rules, sector, difficulty, ai, type, target, budget, id, outcome;
        final float elapsed, seconds, progress;
        final boolean known, kingLost;

        LegacyEntry(long wall, String event, int mode, int rules, String detail) {
            this.wall = wall; this.event = event; this.mode = mode; this.rules = rules; this.detail = detail;
            sector = mode == 0 ? -1 : 2; difficulty = mode == 0 ? -1 : 2; ai = mode == 0 ? 0 : 1;
            known = rules > 0; seed = known ? Long.MIN_VALUE + mode : 0; kingLost = mode == 2;
            elapsed = mode == 0 ? 0 : mode == 1 ? -0.0f : 15.125f;
            type = mode == 2 ? 3 : mode == 3 ? 2 : 0; target = type == 0 ? -1 : 0;
            seconds = mode == 3 ? 60 : 0; progress = mode == 3 ? 15.125f : 0;
            budget = mode == 2 ? 120 : 0; id = mode == 2 ? 6 : mode == 3 ? 4 : -1;
            date = mode == 3 ? "2026-10-06" : "";
            outcome = mode == 0 ? -1 : mode == 2 ? GameModel.LOST : mode == 3 ? GameModel.PLAYING : GameModel.WON;
        }

        List<String> expectedCsv() {
            return Arrays.asList(Long.toString(wall), event, sector < 0 ? "" : "3", sector < 0 ? "" : "2",
                difficulty < 0 ? "unknown" : "Hard", difficulty < 0 ? "" : "2",
                new String[] {"menu", "campaign", "challenge", "daily"}[mode], Integer.toString(rules), Integer.toString(ai),
                known ? Long.toString(seed) : "unknown", Boolean.toString(known), Boolean.toString(known),
                Boolean.toString(kingLost), Float.toString(elapsed), Integer.toString(type), Integer.toString(target),
                Float.toString(seconds), Float.toString(progress), Integer.toString(budget), id < 0 ? "" : Integer.toString(id),
                date, outcome < 0 ? "" : Integer.toString(outcome), detail);
        }
    }

    private static byte[] legacyFixture(LegacyEntry[] entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x464C3130); out.writeInt(1); out.writeBoolean(true); out.writeInt(entries.length);
        for (LegacyEntry entry : entries) {
            out.writeLong(entry.wall); out.writeUTF(entry.event);
            out.writeInt(entry.sector); out.writeInt(entry.difficulty); out.writeInt(entry.mode);
            out.writeInt(entry.rules); out.writeInt(entry.ai); out.writeBoolean(entry.known); out.writeLong(entry.seed);
            out.writeBoolean(entry.known); out.writeBoolean(entry.kingLost); out.writeFloat(entry.elapsed);
            out.writeInt(entry.type); out.writeInt(entry.target); out.writeFloat(entry.seconds); out.writeFloat(entry.progress);
            out.writeInt(entry.budget); out.writeInt(entry.id); out.writeUTF(entry.date); out.writeInt(entry.outcome); out.writeUTF(entry.detail);
        }
        out.flush(); CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
        out.writeInt((int) crc.getValue()); out.flush(); return bytes.toByteArray();
    }

    private static final class Layout {
        int start, end, mode, elapsed, metadata, battleMode, runId, node, perks, reason;
    }

    private static List<Layout> layouts(byte[] bytes) throws IOException {
        int body = bytes.length - 4;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, body));
        in.readInt(); int format = in.readInt(); in.readBoolean(); int count = in.readInt();
        List<Layout> layouts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Layout layout = new Layout(); layout.start = body - in.available();
            in.readLong(); in.readUTF(); in.readInt(); in.readInt();
            layout.mode = body - in.available(); in.readInt(); in.readInt(); in.readInt();
            in.readBoolean(); in.readLong(); in.readBoolean(); in.readBoolean();
            layout.elapsed = body - in.available(); in.readFloat(); in.readInt(); in.readInt();
            in.readFloat(); in.readFloat(); in.readInt(); in.readInt(); in.readUTF(); in.readInt(); in.readUTF();
            layout.end = body - in.available();
            if (format >= 2) {
                layout.metadata = body - in.available(); in.readBoolean();
                layout.battleMode = body - in.available(); in.readInt();
                layout.runId = body - in.available(); in.readUTF();
                layout.node = body - in.available(); in.readInt();
                layout.perks = body - in.available(); in.readInt();
                layout.reason = body - in.available(); in.readInt();
            }
            layouts.add(layout);
        }
        return layouts;
    }

    private static List<List<String>> csv(String value) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && c == ',') { row.add(field.toString()); field.setLength(0); }
            else if (!quoted && c == '\r') {
                if (i + 1 >= value.length() || value.charAt(++i) != '\n') throw new AssertionError("Invalid CSV newline");
                row.add(field.toString()); field.setLength(0); rows.add(row); row = new ArrayList<>();
            } else field.append(c);
        }
        if (quoted || field.length() != 0 || !row.isEmpty()) throw new AssertionError("Incomplete CSV");
        return rows;
    }

    private static String field(List<List<String>> rows, int row, String column) {
        int index = rows.get(0).indexOf(column);
        if (index < 0) throw new AssertionError("Missing column: " + column);
        return rows.get(row).get(index);
    }

    private static String repeat(char c, int count) {
        char[] value = new char[count]; Arrays.fill(value, c); return new String(value);
    }

    private static byte[] replaceUtf(byte[] source, int offset, String value) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(source, offset, source.length - offset);
        new DataInputStream(input).readUTF(); int end = source.length - input.available();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.write(source, 0, offset); out.writeUTF(value); out.write(source, end, source.length - end); out.flush();
        byte[] result = bytes.toByteArray(); checksum(result); return result;
    }

    private static byte[] patchInt(byte[] source, int offset, int value) {
        byte[] bytes = source.clone(); ByteBuffer.wrap(bytes).putInt(offset, value); checksum(bytes); return bytes;
    }

    private static byte[] patchByte(byte[] source, int offset, int value) {
        byte[] bytes = source.clone(); bytes[offset] = (byte) value; checksum(bytes); return bytes;
    }

    private static void checksum(byte[] bytes) {
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) crc.getValue());
    }

    private static void reject(byte[] bytes) throws Exception {
        try { PlaytestLog.restore(bytes); throw new AssertionError("Corrupt log accepted"); }
        catch (IOException expected) { checks++; }
    }

    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
