package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Standalone record fixtures, not gameplay validation or mode activation. */
public final class LogisticsRecordsTest {
    private static int checks;
    private interface Action { void run(); }
    private interface Change { void apply(GameModel model); }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " logistics records checks.");
    }

    public static int run() throws Exception {
        checks = 0;
        records(); configurationValidation(); isolation(); eligibility(); deterministicSave(); persistence();
        corruption(); bounds(); noSideEffects();
        return checks;
    }

    private static void records() throws Exception {
        LogisticsRecords records = new LogisticsRecords();
        check(records.size() == 0 && records.save().length == 16, "Sparse empty history");
        LogisticsRecords.Record missing = records.get(0, 1, 11, 1);
        check(!missing.completed && missing.bestElapsed == 0 && missing.mapId == 0 && missing.difficulty == 1
            && missing.rulesVersion == 11 && missing.configVersion == 1, "Absent exact key exposes explicit non-completion");
        GameModel first = win(0, 1, 11, 120, 1);
        byte[] modelBefore = first.save();
        check(records.record(first, 1) && records.size() == 1, "First logistics victory records without campaign scoring");
        check(first.score() == 0 && first.stars() == 0 && Arrays.equals(modelBefore, first.save()), "Recording never changes the terminal model");
        LogisticsRecords.Record snapshot = records.get(0, 1, 11, 1);
        check(snapshot.completed && snapshot.bestElapsed == 120 && !missing.completed, "Queries are immutable snapshots");
        byte[] before = records.save();
        check(!records.record(first, 1) && Arrays.equals(before, records.save()), "Repeated result is idempotent");
        check(!records.record(win(0, 1, 11, 150, 2), 1) && Arrays.equals(before, records.save()), "Slower winning seed cannot replace best time");
        check(records.record(win(0, 1, 11, 95.5f, 3), 1) && records.get(0, 1, 11, 1).bestElapsed == 95.5f,
            "Faster winning seed improves the same exact namespace");
        check(snapshot.bestElapsed == 120 && records.size() == 1, "Earlier snapshot and key count unchanged by improvement");
        GameModel zero = win(1, 2, 11, -0f, 4);
        check(records.record(zero, 1), "Zero elapsed completion is distinct from absence");
        LogisticsRecords.Record zeroRecord = records.get(1, 2, 11, 1);
        check(zeroRecord.completed && Float.floatToRawIntBits(zeroRecord.bestElapsed) == 0, "Signed zero normalized for stable persistence");
        check(!records.record(win(1, 2, 11, 0, 5), 1), "Equal zero elapsed replay is idempotent");
        check(records.record(win(2, 0, 11, 86400, 6), 1), "Maximum elapsed bound accepted");
        roundtrip(records);
    }

    private static void configurationValidation() throws Exception {
        LogisticsRecords records = new LogisticsRecords();
        GameModel original = win(0, 1, 11, 40, 1, 1);
        check(records.record(original), "Convenience overload records the actual model configuration");
        byte[] before = records.save();
        check(!records.record(null), "Convenience overload refuses null");
        for (int supplied : new int[] {-1, 0, 2, Integer.MAX_VALUE}) {
            GameModel faster = win(0, 1, 11, 1, 2, 1);
            byte[] modelBefore = faster.save();
            check(!records.record(faster, supplied) && Arrays.equals(before, records.save()),
                "Caller cannot reinterpret a win as another configuration");
            check(Arrays.equals(modelBefore, faster.save()), "Rejected caller configuration does not rewrite attempt metadata");
        }
        check(!records.get(0, 1, 11, 2).completed && !records.get(0, 1, 11, Integer.MAX_VALUE).completed,
            "Wrong caller configuration creates no historical or future completion");
        GameModel newer = win(0, 1, 11, 12, 3, 2);
        check(!records.record(newer, 1) && Arrays.equals(before, records.save()),
            "Newer attempt cannot overwrite an older configuration's best time");
        check(records.record(newer) && records.get(0, 1, 11, 2).bestElapsed == 12
            && records.get(0, 1, 11, 1).bestElapsed == 40, "Convenience overload isolates a future model configuration");
        newer.elapsed = 8;
        check(records.record(newer, 2) && !records.record(newer), "Both overloads share exact-key improvement and idempotence");
        before = records.save();
        for (int actual : new int[] {0, -1, Integer.MIN_VALUE}) {
            GameModel invalid = win(0, 1, 11, 1, 4, actual);
            check(!records.record(invalid) && !records.record(invalid, actual) && !records.record(invalid, 1)
                && Arrays.equals(before, records.save()), "Non-positive actual configuration cannot be supplied or guessed");
        }
        GameModel opaque = win(2, 2, 700, 18.5f, 5, 900);
        opaque.logisticsId = 12345;
        check(records.record(opaque), "Stable ID and actual future configuration remain opaque namespaces");
        records = roundtrip(records);
        check(records.get(12345, 2, 700, 900).completed && records.get(12345, 2, 700, 900).bestElapsed == 18.5f
            && !records.get(2, 2, 700, 900).completed, "Stored stable ID is not reinterpreted from frozen display metadata");
        check(records.get(0, 1, 11, 1).bestElapsed == 40 && records.get(0, 1, 11, 2).bestElapsed == 8,
            "New write gate does not change persisted configuration isolation");
    }

    private static void isolation() throws Exception {
        LogisticsRecords records = new LogisticsRecords();
        int[] rules = {10, 11, 12, Integer.MAX_VALUE}, configs = {1, 2, 3, Integer.MAX_VALUE};
        for (int id = 0; id < 3; id++) for (int difficulty = 0; difficulty < 3; difficulty++)
            for (int r = 0; r < rules.length; r++) for (int c = 0; c < configs.length; c++) {
                float elapsed = 1 + id * 1000 + difficulty * 100 + r * 10 + c;
                GameModel model = win(id, difficulty, rules[r], elapsed, 71, configs[c]);
                check(model.levelIndex == 0 && Logistics.getIndex(model) == id, "Map identity is not the campaign placeholder index");
                check(records.record(model, configs[c]), "Independent exact map/difficulty/rules/config key");
            }
        check(records.size() == 144, "All version combinations retained independently");
        records = roundtrip(records);
        for (int id = 0; id < 3; id++) for (int difficulty = 0; difficulty < 3; difficulty++)
            for (int r = 0; r < rules.length; r++) for (int c = 0; c < configs.length; c++) {
                LogisticsRecords.Record result = records.get(id, difficulty, rules[r], configs[c]);
                check(result.completed && result.bestElapsed == 1 + id * 1000 + difficulty * 100 + r * 10 + c,
                    "Restore never compares or blends namespaces");
            }
        check(!records.get(0, 1, 13, 1).completed && !records.get(0, 1, 11, 4).completed
            && !records.get(3, 1, 11, 1).completed, "Unknown key never falls back to current or historical record");
        check(records.record(win(1, 1, 11, .5f, 9, 2), 2), "Improve exactly one tuple");
        check(records.get(1, 1, 11, 2).bestElapsed == .5f && records.get(1, 1, 11, 1).bestElapsed == 1111
            && records.get(0, 1, 11, 2).bestElapsed == 112 && records.get(1, 0, 11, 2).bestElapsed == 1012
            && records.get(1, 1, 10, 2).bestElapsed == 1102, "Improvement cannot leak to adjacent key dimensions");
        byte[] retired = fixture(1, new int[][] {{12345, 2, 700, 900}}, new float[] {18.5f}, new int[] {1});
        LogisticsRecords history = LogisticsRecords.restore(retired);
        check(history.get(12345, 2, 700, 900).completed && Arrays.equals(retired, history.save()),
            "Retired map and opaque positive versions preserved, not silently migrated");
        check(!history.get(0, 2, 11, 1).completed, "Unknown historical content is not claimed as current completion");
    }

    private static void eligibility() throws Exception {
        LogisticsRecords records = new LogisticsRecords();
        records.record(win(0, 1, 11, 40, 1), 1);
        byte[] before = records.save();
        check(!records.record(null, 1), "Null result refused");
        for (Change invalid : new Change[] {
            model -> model.outcome = GameModel.PLAYING,
            model -> model.outcome = GameModel.LOST,
            model -> model.outcome = 7,
            model -> model.battleMode = GameModel.MODE_CAMPAIGN,
            model -> model.battleMode = GameModel.MODE_RUN,
            model -> model.battleMode = 3,
            model -> model.difficulty = -1,
            model -> model.difficulty = 3,
            model -> model.rulesVersion = 0,
            model -> model.rulesVersion = -1,
            model -> model.logisticsConfigVersion = 0,
            model -> model.logisticsConfigVersion = -1,
            model -> model.elapsed = -1,
            model -> model.elapsed = Float.NaN,
            model -> model.elapsed = Float.POSITIVE_INFINITY,
            model -> model.elapsed = Float.NEGATIVE_INFINITY,
            model -> model.elapsed = 86401,
            model -> model.objectiveType = Challenge.HOLD_KING,
            model -> model.objectiveType = Challenge.KEEP_KING,
            model -> model.objectiveType = Challenge.BUDGET,
            model -> model.objectiveTarget = 0,
            model -> model.objectiveSeconds = 1,
            model -> model.objectiveProgress = 1,
            model -> model.deploymentBudget = 100,
            model -> model.challengeId = 0,
            model -> model.missionConfigVersion = 1,
            model -> model.missionPressure = 1,
            model -> model.dailyDate = "2026-10-09",
            model -> model.dailyDate = null,
            model -> model.dailyVersion = "daily-v11-2",
            model -> model.dailyVersion = null,
            model -> model.runId = "other-run",
            model -> model.runId = null,
            model -> model.runNode = 0,
            model -> model.runPerks = GameModel.PERK_SPEED,
            model -> model.terminalReason = GameModel.TERMINAL_SURRENDER,
            model -> model.terminalReason = GameModel.TERMINAL_BUDGET,
            model -> model.terminalReason = GameModel.TERMINAL_ELIMINATED,
            model -> model.terminalReason = 100
        }) {
            GameModel model = win(0, 1, 11, 1, 12); invalid.apply(model);
            check(!records.record(model, 1) && !records.record(model) && Arrays.equals(before, records.save()),
                "Neither overload accepts invalid or cross-mode results");
        }
        for (int config : new int[] {0, -1, Integer.MIN_VALUE})
            check(!records.record(win(0, 1, 11, 1, 12), config) && Arrays.equals(before, records.save()), "No guessed or invalid configuration version");
        GameModel campaign = new GameModel(0, 1, 8); campaign.outcome = GameModel.WON;
        check(!records.record(campaign, 1), "Campaign win cannot enter logistics records");
        campaign.battleMode = 2;
        check(!records.record(campaign, 1), "Mode tag alone does not identify a logistics map");
        GameModel mission = Challenge.PRESETS[0].create(1, 8, 0, "2026-10-09"); mission.outcome = GameModel.WON;
        check(!records.record(mission, 1), "Daily mission win cannot enter logistics records");
        GameModel run = new GameModel(0, 1, 8); run.configureRun(0, "run-fixture", 0); run.outcome = GameModel.WON;
        check(!records.record(run, 1), "Run win cannot enter logistics records");
        GameModel fixture = new GameModel(0, 1, 8, new GameModel.Level("Logistics: Unknown", 3, 2, 1, new int[0], 100));
        fixture.battleMode = 2; fixture.logisticsConfigVersion = 1; fixture.outcome = GameModel.WON;
        check(!records.record(fixture, 1), "Unknown frozen map name is not guessed as ID zero");
        GameModel oldTerminalFixture = win(2, 2, 11, 9, 8); oldTerminalFixture.terminalReason = GameModel.TERMINAL_NONE;
        check(records.record(oldTerminalFixture, 1), "WON gate accepts fixtures lacking an explicit reason without inventing one");
        LogisticsRecords query = records;
        for (int[] key : new int[][] {{-1, 1, 11, 1}, {0, -1, 11, 1}, {0, 3, 11, 1},
            {0, 1, 0, 1}, {0, 1, -1, 1}, {0, 1, 11, 0}, {0, 1, 11, -1}})
            rejectArgument(() -> query.get(key[0], key[1], key[2], key[3]));
    }

    private static void deterministicSave() throws Exception {
        LogisticsRecords forward = new LogisticsRecords(), backward = new LogisticsRecords();
        for (int i = 0; i < 90; i++)
            check(forward.record(win(i % 3, i / 3 % 3, 10 + i / 9 % 2, i + 1, i, 1 + i / 18)), "Forward insertion");
        for (int i = 89; i >= 0; i--)
            check(backward.record(win(i % 3, i / 3 % 3, 10 + i / 9 % 2, i + 1, i, 1 + i / 18)), "Reverse insertion");
        check(Arrays.equals(forward.save(), backward.save()), "Serialized bytes independent of insertion order");
        byte[] saved = forward.save();
        CRC32 crc = new CRC32(); crc.update(saved, 0, saved.length - 4);
        check(ByteBuffer.wrap(saved).getInt(saved.length - 4) == (int) crc.getValue(), "CRC covers every header and record byte");
        roundtrip(forward);
        saved[0] ^= 1;
        check(!Arrays.equals(saved, forward.save()), "Returned save bytes do not alias record state");
    }

    private static void persistence() throws Exception {
        byte[] empty = fixture(1, new int[0][4], new float[0], new int[0]);
        check(Arrays.equals(empty, new LogisticsRecords().save()), "Independent empty-format fixture matches serializer");
        byte[] bytes = fixture(1, new int[][] {{0, 0, 10, 1}, {1, 1, 11, 2}, {2, 2, 12, 3}},
            new float[] {0, 12.25f, 86400}, new int[] {1, 1, 1});
        byte[] original = bytes.clone();
        LogisticsRecords restored = LogisticsRecords.restore(bytes);
        check(Arrays.equals(original, bytes) && Arrays.equals(original, restored.save()), "Restore reads input without mutating it");
        check(restored.size() == 3 && restored.get(0, 0, 10, 1).completed && restored.get(0, 0, 10, 1).bestElapsed == 0
            && restored.get(1, 1, 11, 2).bestElapsed == 12.25f && restored.get(2, 2, 12, 3).bestElapsed == 86400,
            "Independent fixture recovers every exact completion and best time");
        bytes[12] ^= 1;
        check(Arrays.equals(original, restored.save()), "Restored history does not retain the caller's byte array");
        check(restored.record(win(1, 1, 11, 10, 100, 2), 2), "Restored record remains updateable");
        check(restored.get(1, 1, 11, 2).bestElapsed == 10 && restored.get(2, 2, 12, 3).bestElapsed == 86400,
            "Updating restored record does not change other versions");
        roundtrip(restored);
    }

    private static void corruption() throws Exception {
        rejectSave(null);
        rejectSave(new byte[LogisticsRecords.MAX_SAVE_BYTES + 1]);
        byte[] valid = fixture(1, new int[][] {{0, 0, 11, 1}, {1, 1, 11, 2}, {2, 2, 12, 3}},
            new float[] {10, 20, 30}, new int[] {1, 1, 1});
        for (int size = 0; size < valid.length; size++) rejectSave(Arrays.copyOf(valid, size));
        for (int index = 0; index < valid.length; index++) for (int bit = 0; bit < 8; bit++) {
            byte[] changed = valid.clone(); changed[index] ^= 1 << bit;
            rejectSave(changed);
        }
        rejectSave(fixture(0, new int[0][4], new float[0], new int[0]));
        rejectSave(fixture(2, new int[0][4], new float[0], new int[0]));
        rejectSave(fixture(Integer.MAX_VALUE, new int[0][4], new float[0], new int[0]));
        byte[] bad = valid.clone(); ByteBuffer.wrap(bad).putInt(0, 0x46503130); rejectSave(seal(bad));
        for (int count : new int[] {-1, LogisticsRecords.MAX_RECORDS + 1, Integer.MAX_VALUE, 0, 2, 4}) {
            bad = valid.clone(); ByteBuffer.wrap(bad).putInt(8, count); rejectSave(seal(bad));
        }
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1); rejectSave(seal(trailing));
        byte[] shortBody = Arrays.copyOf(valid, valid.length - 1); rejectSave(seal(shortBody));
        for (int[] key : new int[][] {{-1, 1, 11, 1}, {0, -1, 11, 1}, {0, 3, 11, 1},
            {0, 1, 0, 1}, {0, 1, -1, 1}, {0, 1, 11, 0}, {0, 1, 11, -1}})
            rejectSave(fixture(1, new int[][] {key}, new float[] {10}, new int[] {1}));
        for (int flag : new int[] {0, 2, 255})
            rejectSave(fixture(1, new int[][] {{0, 1, 11, 1}}, new float[] {10}, new int[] {flag}));
        for (float time : new float[] {-1, -0f, 86401, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            rejectSave(fixture(1, new int[][] {{0, 1, 11, 1}}, new float[] {time}, new int[] {1}));
        rejectSave(fixture(1, new int[][] {{0, 1, 11, 1}, {0, 1, 11, 1}}, new float[] {10, 20}, new int[] {1, 1}));
        rejectSave(fixture(1, new int[][] {{1, 1, 11, 1}, {0, 1, 11, 1}}, new float[] {10, 20}, new int[] {1, 1}));
        byte[] extremes = fixture(1, new int[][] {{Integer.MAX_VALUE, 2, Integer.MAX_VALUE, Integer.MAX_VALUE}},
            new float[] {86400}, new int[] {1});
        check(Arrays.equals(extremes, LogisticsRecords.restore(extremes).save()), "Opaque identity bounds cannot overflow comparison or allocation");
    }

    private static void bounds() throws Exception {
        LogisticsRecords records = new LogisticsRecords();
        GameModel model = win(0, 0, 11, 100, 42);
        for (int config = 1; config <= LogisticsRecords.MAX_RECORDS; config++) {
            model.logisticsConfigVersion = config;
            check(records.record(model, config), "Capacity stores every exact configuration without eviction");
        }
        check(records.size() == LogisticsRecords.MAX_RECORDS && records.save().length == LogisticsRecords.MAX_SAVE_BYTES,
            "Serialized table is tightly byte-bounded");
        byte[] full = records.save();
        model.logisticsConfigVersion = LogisticsRecords.MAX_RECORDS + 1;
        check(!records.record(model, LogisticsRecords.MAX_RECORDS + 1) && Arrays.equals(full, records.save()),
            "Full table refuses new key without deleting history");
        check(records.get(0, 0, 11, 1).completed && records.get(0, 0, 11, LogisticsRecords.MAX_RECORDS).completed,
            "Oldest and newest namespaces retained at capacity");
        records = roundtrip(records);
        model.elapsed = 50; model.logisticsConfigVersion = 1;
        check(records.record(model, 1) && records.get(0, 0, 11, 1).bestElapsed == 50 && records.size() == LogisticsRecords.MAX_RECORDS,
            "Existing best time may improve at capacity");
        for (int config = 2; config <= LogisticsRecords.MAX_RECORDS; config++)
            check(records.get(0, 0, 11, config).bestElapsed == 100, "Full-table improvement remains exact-key isolated");
        roundtrip(records);
    }

    private static void noSideEffects() throws Exception {
        LogisticsRecords first = new LogisticsRecords(), second = new LogisticsRecords();
        GameModel model = win(2, 2, 11, 55, 19);
        byte[] modelBefore = model.save();
        check(first.record(model) && Arrays.equals(modelBefore, model.save()), "Accepted result does not mutate model or its save");
        check(second.size() == 0 && !second.get(2, 2, 11, model.logisticsConfigVersion).completed,
            "Record containers share no mutable static history");
        byte[] recordsBefore = first.save(), secondBefore = second.save();
        byte[] unsupported = fixture(2, new int[0][4], new float[0], new int[0]);
        rejectSave(unsupported);
        check(Arrays.equals(recordsBefore, first.save()) && Arrays.equals(secondBefore, second.save()),
            "Failed restore cannot clear any existing record container");
        GameModel playing = Logistics.create(0, 1, 99), control = Logistics.create(0, 1, 99);
        for (int i = 0; i < 50; i++) {
            check(!first.record(playing, 1), "Unfinished attempt cannot record completion");
            first.get(0, 1, 11, 1); first.save();
        }
        playing.update(.05f); control.update(.05f);
        check(Arrays.equals(playing.save(), control.save()) && Arrays.equals(recordsBefore, first.save()),
            "Rejected results and UI queries consume no troops, counters, clocks, or RNG state");
    }

    private static GameModel win(int mapId, int difficulty, int rules, float elapsed, long seed) {
        return win(mapId, difficulty, rules, elapsed, seed, GameModel.LOGISTICS_CONFIG_VERSION);
    }

    private static GameModel win(int mapId, int difficulty, int rules, float elapsed, long seed, int config) {
        GameModel model = Logistics.create(mapId, difficulty, seed);
        model.rulesVersion = rules; model.elapsed = elapsed; model.logisticsConfigVersion = config;
        model.outcome = GameModel.WON; model.terminalReason = GameModel.TERMINAL_VICTORY;
        return model;
    }

    private static LogisticsRecords roundtrip(LogisticsRecords records) throws Exception {
        byte[] saved = records.save();
        LogisticsRecords restored = LogisticsRecords.restore(saved);
        check(Arrays.equals(saved, restored.save()) && records.size() == restored.size(), "Exact CRC roundtrip");
        return restored;
    }

    private static byte[] fixture(int format, int[][] keys, float[] times, int[] flags) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x464C5231); out.writeInt(format); out.writeInt(keys.length);
        for (int i = 0; i < keys.length; i++) {
            for (int field : keys[i]) out.writeInt(field);
            out.writeByte(flags[i]); out.writeFloat(times[i]);
        }
        out.writeInt(0); out.flush();
        return seal(bytes.toByteArray());
    }

    private static byte[] seal(byte[] bytes) {
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) crc.getValue());
        return bytes;
    }

    private static void rejectSave(byte[] bytes) throws Exception {
        byte[] original = bytes == null ? null : bytes.clone();
        try { LogisticsRecords.restore(bytes); throw new AssertionError("Invalid or unsupported logistics history accepted"); }
        catch (IOException expected) { checks++; }
        check(Arrays.equals(original, bytes), "Restore failure preserves original bytes for parent preference isolation");
    }

    private static void rejectArgument(Action action) {
        try { action.run(); throw new AssertionError("Invalid record query accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
