package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

public final class V10ProgressTest {
    private static int checks;

    public static int run() throws Exception {
        checks = 0;
        campaignRecords();
        legacyRecords();
        mastery();
        challengeRecords();
        dailyRecords();
        progressPersistence();
        logging();
        logPersistence();
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("V10 progress/playtest checks passed: " + run());
    }

    private static void campaignRecords() {
        campaignRecords(10);
        campaignRecords(11);
    }

    private static void campaignRecords(int rules) {
        Progress progress = new Progress();
        check(progress.best.length == 3 && progress.best[0].length == 60
            && progress.historicalCampaignBest.length == 3 && progress.historicalCampaignBest[0].length == 60
            && progress.challengeBest.length == 3 && progress.challengeBest[0].length == 9, "Record dimensions");
        for (int d = 0; d < 3; d++) {
            GameModel model = won(0, d, 130 - d * 20, d + 2, rules);
            check(progress.recordCampaign(model), "First winning time improves its difficulty record");
            check(progress.campaignBest(0, d, rules) == model.score() && progress.campaignStars(0, d, rules) == model.stars()
                && progress.campaignTime(0, d, rules) == model.elapsed, "Use matching-rules model scoring and stars unchanged");
            check(!progress.recordCampaign(model), "Repeated result does not improve time");
        }
        int easyScore = progress.campaignBest(0, 0, rules), easyStars = progress.campaignStars(0, 0, rules);
        float easyTime = progress.campaignTime(0, 0, rules);
        GameModel higherScore = won(0, 1, 200, 50, rules);
        check(!progress.recordCampaign(higherScore), "Higher score alone is not a time improvement");
        check(progress.campaignBest(0, 1, rules) == higherScore.score() && progress.campaignTime(0, 1, rules) == 110
            && progress.campaignStars(0, 1, rules) == 2, "Independent best score, stars, and time");
        GameModel faster = won(0, 1, 20, 0, rules);
        check(progress.recordCampaign(faster) && progress.campaignTime(0, 1, rules) == 20 && progress.campaignStars(0, 1, rules) == 3
            && progress.campaignBest(0, 1, rules) == higherScore.score(), "Faster time cannot erase a higher score");
        check(progress.campaignBest(0, 0, rules) == easyScore && progress.campaignStars(0, 0, rules) == easyStars
            && progress.campaignTime(0, 0, rules) == easyTime, "Other difficulties remain unchanged");
        int otherRules = rules == 10 ? 11 : 10;
        check(progress.campaignBest(0, 1, otherRules) == 0 && progress.campaignStars(0, 1, otherRules) == 0
            && progress.campaignTime(0, 1, otherRules) == 0, "Other campaign rules remain unchanged");
        check(progress.recordCampaign(won(59, 2, 0, 0, rules)), "Zero-second first record is representable");
        check(!progress.recordCampaign(won(59, 2, 0, 0, rules)), "Zero-second record remains idempotent");
        byte[] before = progress.save();
        for (int outcome : new int[] {GameModel.PLAYING, GameModel.LOST}) {
            GameModel model = won(4, 1, 40, 2, rules); model.outcome = outcome;
            check(!progress.recordCampaign(model), "Only victories count");
        }
        for (float elapsed : new float[] {-1, Float.NaN, Float.POSITIVE_INFINITY, 86401}) {
            GameModel model = won(4, 1, 40, 2, rules); model.elapsed = elapsed;
            check(!progress.recordCampaign(model), "Reject invalid result time");
        }
        GameModel invalidDifficulty = won(4, 1, 40, 2, rules); invalidDifficulty.difficulty = 3;
        check(!progress.recordCampaign(invalidDifficulty) && !progress.recordCampaign(null), "Invalid result is harmless");
        check(Arrays.equals(before, progress.save()), "Ignored results do not mutate records or awards");
    }

    private static void legacyRecords() throws Exception {
        GameModel legacy = legacyWin();
        check(legacy.rulesVersion == 0 && !legacy.seedKnown && !legacy.historyKnown, "Legacy provenance remains unknown");
        Progress progress = new Progress();
        byte[] before = progress.save();
        check(!progress.recordCampaign(legacy), "Legacy win is not assigned a new difficulty record");
        legacy.objectiveType = 1; legacy.challengeId = 0;
        check(!progress.recordChallenge(legacy), "Legacy win cannot manufacture objective mastery");
        for (int rules : new int[] {0, 7, 9, 12}) {
            GameModel model = won(0, 1, 20, 2); model.rulesVersion = rules;
            check(!progress.recordCampaign(model), "Only supported Classic campaign rules count");
        }
        check(Arrays.equals(before, progress.save()) && progress.earnedCount() == 0, "Legacy records and unknown history are not migrated");
    }

    private static void mastery() throws Exception {
        Progress progress = new Progress();
        check(progress.themeUnlocked(0) && !progress.themeUnlocked(1) && !progress.themeUnlocked(2)
            && !progress.themeUnlocked(-1) && !progress.themeUnlocked(3), "Only default theme starts unlocked");
        GameModel unknown = won(0, 1, 20, 0); unknown.historyKnown = false;
        progress.recordCampaign(unknown);
        check(!progress.crownKeeper, "Unknown history cannot earn Crown Keeper");
        GameModel lostKing = kingLoss();
        check(lostKing.startingKingLost, "Model records an actual starting-king loss event");
        for (GameModel.Territory tile : lostKing.territories) tile.owner = GameModel.PLAYER;
        lostKing.outcome = GameModel.WON;
        progress.recordCampaign(lostKing);
        check(!progress.crownKeeper, "Recapturing the starting king cannot erase its loss history");
        GameModel genuine = actualVictory();
        check(genuine.outcome == GameModel.WON && genuine.historyKnown && !genuine.startingKingLost,
            "A simulated victory has genuine intact king history");
        progress.recordCampaign(genuine);
        check(progress.crownKeeper && progress.themeUnlocked(1) && progress.earnedCount() == 1, "Genuine win earns Crown Keeper");
        for (int i = 0; i < 6; i++) progress.recordCampaign(won(i, 0, 20, 0));
        check(!progress.chapterNormal && progress.chapterProgress() == 1, "Easy clears cannot complete the Normal chapter");
        for (int i = 1; i < 6; i++) {
            progress.recordCampaign(won(i, 1, 20, 0));
            check(progress.chapterProgress() == i + 1, "Normal chapter progress counts distinct sectors");
        }
        check(progress.chapterNormal && progress.themeUnlocked(2) && progress.earnedCount() == 2, "First six Normal wins earn Blueprint");
        byte[] before = progress.save();
        for (int i = 0; i < 6; i++) progress.recordCampaign(won(i, 1, 200, 0));
        progress.recordCampaign(genuine);
        check(Arrays.equals(before, progress.save()), "Repeated awards and slower replays are idempotent");
        progress.theme = 2;
        Progress restored = Progress.restore(progress.save());
        check(restored.theme == 2 && restored.crownKeeper && restored.chapterNormal && restored.earnedCount() == 2,
            "Earned cosmetics survive restart");
    }

    private static void challengeRecords() throws Exception {
        Progress progress = new Progress();
        for (int type = 1; type <= 3; type++) {
            GameModel model = challenge(type, type - 1, 1, 30);
            check(!progress.recordCampaign(model), "Challenge cannot enter campaign records");
            check(progress.recordChallenge(model), "First challenge record improves time");
            check(progress.challengeBest[1][type - 1] == model.score()
                && progress.challengeStars[1][type - 1] == model.stars()
                && progress.challengeTimes[1][type - 1] == 30, "Mission records are indexed by difficulty and ID");
            check(progress.objectiveProgress() == type, "Each distinct objective adds one mastery bit");
            check(!progress.recordChallenge(model), "Repeated objective does not add another award");
        }
        check(progress.objectiveMask == 7 && progress.objectiveMaster && progress.earnedCount() == 2,
            "All three objective wins earn Objective Master once");
        GameModel easy = challenge(1, 0, 0, 10);
        progress.recordChallenge(easy);
        check(progress.challengeTimes[0][0] == 10 && progress.challengeTimes[1][0] == 30
            && progress.challengeBest[2][0] == 0, "Challenge difficulties are independent");
        GameModel faster = challenge(1, 0, 1, 15);
        check(progress.recordChallenge(faster) && progress.challengeTimes[1][0] == 15, "Challenge time improves");
        check(progress.chapterProgress() == 0 && !progress.chapterNormal && emptyCampaign(progress), "Missions cannot clear a campaign chapter");
        byte[] before = progress.save();
        GameModel invalid = challenge(1, 9, 1, 10);
        check(!progress.recordChallenge(invalid), "Reject out-of-range mission ID");
        invalid = won(0, 1, 10, 0);
        check(!progress.recordChallenge(invalid), "Campaign cannot enter mission records");
        invalid = challenge(2, 2, 1, 10); invalid.outcome = GameModel.LOST;
        check(!progress.recordChallenge(invalid) && !progress.recordChallenge(null), "Failed missions grant no records");
        check(Arrays.equals(before, progress.save()), "Invalid challenge records are harmless");
        Progress restored = Progress.restore(progress.save());
        check(restored.objectiveMaster && restored.objectiveProgress() == 3
            && restored.challengeTimes[0][0] == 10 && restored.challengeTimes[1][0] == 15, "Mission records and mastery persist");
    }

    private static void dailyRecords() throws Exception {
        Progress progress = new Progress();
        LocalDate first = LocalDate.of(2026, 7, 1);
        for (int i = 60; i >= 0; i--) {
            GameModel model = challenge(i % 3 + 1, i % 9, 1, 30);
            model.dailyDate = first.plusDays(i).toString();
            check(!progress.recordCampaign(model), "Daily is not campaign progression");
            check(progress.recordChallenge(model) == (i != 0), "Daily best is retained only among latest sixty dates");
        }
        check(progress.dailyBest(first.toString()) == 0 && progress.dailyTime(first.toString()) == 0
            && progress.dailyStars(first.toString()) == 0, "Oldest daily is pruned");
        for (int i = 1; i <= 60; i++) check(progress.dailyBest(first.plusDays(i).toString()) > 0, "Latest sixty daily dates remain");
        check(emptyCampaign(progress) && !progress.chapterNormal && progress.chapterProgress() == 0,
            "Daily results never modify campaign scores or chapter unlocks");
        for (int[] row : progress.challengeBest) for (int score : row) check(score == 0, "Daily cannot overwrite ordinary mission scores");
        check(progress.objectiveMaster && progress.objectiveProgress() == 3, "Daily objective wins contribute genuine mastery");
        GameModel newest = challenge(1, 0, 1, 20);
        newest.dailyDate = first.plusDays(60).toString();
        check(progress.recordChallenge(newest) && progress.dailyTime(newest.dailyDate) == 20
            && progress.dailyStars(newest.dailyDate) == newest.stars(), "Daily personal best improves");
        check(!progress.recordChallenge(newest), "Repeated daily award is idempotent");
        GameModel older = challenge(2, 1, 1, 10); older.dailyDate = "2020-01-01";
        check(!progress.recordChallenge(older) && progress.dailyBest(older.dailyDate) == 0
            && progress.dailyTime(newest.dailyDate) == 20, "Old replay cannot evict newer dates");
        byte[] before = progress.save();
        for (String date : new String[] {"2026-02-29", "2026-04-31", "2026-13-01", "0000-01-01", "2026-7-01", "garbage"}) {
            GameModel invalid = challenge(1, 0, 1, 10); invalid.dailyDate = date;
            check(!progress.recordChallenge(invalid), "Reject invalid daily date");
        }
        check(Arrays.equals(before, progress.save()), "Invalid dates do not award or alter records");
        Progress restored = Progress.restore(progress.save());
        check(restored.dailyBest(first.toString()) == 0 && restored.dailyTime(newest.dailyDate) == 20
            && restored.objectiveMaster && Arrays.equals(before, restored.save()), "Daily save round trip is canonical and bounded");
        Progress leap = new Progress(); newest.dailyDate = "2028-02-29";
        check(leap.recordChallenge(newest), "Gregorian leap dates are valid");
    }

    private static void progressPersistence() throws Exception {
        byte[] empty = new Progress().save();
        check(Arrays.equals(empty, Progress.restore(empty).save()), "Empty progress round trip");
        rejectProgress(null); rejectProgress(new byte[1024 * 1024 + 1]);
        for (int length : new int[] {0, 1, 8, empty.length / 2, empty.length - 1}) rejectProgress(Arrays.copyOf(empty, length));
        byte[] corrupt = empty.clone(); corrupt[20] ^= 1; rejectProgress(corrupt);
        rejectProgress(patchInt(empty, 0, 0));
        rejectProgress(patchInt(empty, 4, 4));
        rejectProgress(patchInt(empty, 8, -1));
        rejectProgress(patchInt(empty, 12, 4));
        rejectProgress(patchInt(empty, 16, Float.floatToIntBits(Float.NaN)));
        rejectProgress(patchInt(empty, 16, Float.floatToIntBits(1)));
        int flags = 8 + 3 * (60 + 9) * 12;
        rejectProgress(patchByte(empty, flags, 2));
        rejectProgress(patchByte(empty, flags + 1, 1));
        rejectProgress(patchByte(empty, flags + 2, 1));
        rejectProgress(patchInt(empty, flags + 3, 8));
        rejectProgress(patchInt(empty, flags + 7, 1));
        rejectProgress(patchInt(empty, flags + 11, 61));
        rejectProgress(withExtraBody(empty));
        Progress dirty = new Progress(); dirty.times[0][0] = Float.NaN;
        try { dirty.save(); throw new AssertionError("Invalid public state was saved"); }
        catch (IllegalStateException expected) { checks++; }
    }

    private static void logging() throws Exception {
        PlaytestLog log = new PlaytestLog();
        GameModel model = new GameModel(2, 2, Long.MIN_VALUE, GameModel.LEGACY_RULES_VERSION);
        model.elapsed = 12.5f; model.aiVersion = 1;
        log.add(1000, "attempt_start", model, "disabled");
        check(!log.enabled && log.size() == 0 && csv(log.exportCsv()).size() == 1, "Logging is opt-in, including export");
        log.enabled = true;
        String event = "tutorial,\"step\"", detail = "first, \"quoted\"\r\nsecond\nthird\rfinal";
        log.add(1000, event, null, detail);
        log.add(2000, "attempt_start", model, "snapshot");
        model.elapsed = 99; model.difficulty = 0; model.seed = 123; model.rulesVersion = 0;
        List<List<String>> rows = csv(log.exportCsv());
        check(rows.size() == 3 && rows.get(1).size() == rows.get(0).size()
            && field(rows, 1, "event").equals(event) && field(rows, 1, "detail").equals(detail), "CSV quotes, commas, CR and LF round trip");
        check(field(rows, 1, "mode").equals("menu") && field(rows, 1, "seed").equals("unknown"), "Menu/tutorial has no fabricated battle metadata");
        check(field(rows, 2, "timestamp_ms").equals("2000") && field(rows, 2, "sector").equals("3")
            && field(rows, 2, "sector_index").equals("2") && field(rows, 2, "difficulty").equals("Hard")
            && field(rows, 2, "rules_version").equals("10") && field(rows, 2, "ai_version").equals("1")
            && field(rows, 2, "seed").equals(Long.toString(Long.MIN_VALUE))
            && field(rows, 2, "elapsed_seconds").equals("12.5"), "Metadata is captured at event time, not export time");
        log.add(3000, "legacy_result", legacyWin(), "old save");
        rows = csv(log.exportCsv());
        check(field(rows, 3, "seed").equals("unknown") && field(rows, 3, "seed_known").equals("false")
            && field(rows, 3, "history_known").equals("false") && field(rows, 3, "rules_version").equals("0"), "Legacy seeds and history export as unknown");
        GameModel daily = challenge(2, 4, 1, 15); daily.dailyDate = "2026-10-06";
        daily.objectiveSeconds = 60; daily.objectiveProgress = 15;
        log.add(4000, "result", daily, "daily");
        rows = csv(log.exportCsv());
        check(field(rows, 4, "mode").equals("daily") && field(rows, 4, "daily_date").equals(daily.dailyDate)
            && field(rows, 4, "challenge_id").equals("4") && field(rows, 4, "objective_type").equals("2")
            && field(rows, 4, "objective_seconds").equals("60.0")
            && field(rows, 4, "objective_progress").equals("15.0"), "Daily objective metadata is exported");
        daily.dailyDate = ""; log.add(5000, "result", daily, "mission");
        check(field(csv(log.exportCsv()), 5, "mode").equals("challenge"), "Ordinary mission has its own mode");
        PlaytestLog pause = new PlaytestLog(); pause.enabled = true;
        model = new GameModel(0, 1, 0); model.elapsed = 7.25f;
        pause.add(1, "attempt_start", model, "");
        check(pause.size() == 1, "No implicit lifecycle event");
        pause.add(3600001, "pause", model, "background");
        PlaytestLog resumed = PlaytestLog.restore(pause.save());
        rows = csv(resumed.exportCsv());
        check(resumed.size() == 2 && field(rows, 1, "elapsed_seconds").equals("7.25")
            && field(rows, 2, "elapsed_seconds").equals("7.25") && !resumed.exportCsv().contains("abandon"),
            "Pause/background wall time is neither active simulation time nor abandonment");
        check(field(rows, 1, "seed").equals("0"), "A known zero seed is not unknown");
        resumed.enabled = false; resumed.add(3600002, "abandon", model, "disabled");
        check(resumed.size() == 2, "Disabled logger does not append or clear previous opt-in entries");
        check(!PlaytestLog.restore(resumed.save()).enabled, "Opt-in choice persists");
        resumed.clear(); check(resumed.size() == 0 && !resumed.enabled, "Clear preserves opt-out");
        log.clear(); check(log.size() == 0 && log.enabled, "Clear preserves opt-in");
        for (int i = 0; i < 501; i++) log.add(i, "event_" + i, null, "");
        rows = csv(log.exportCsv());
        check(log.size() == 500 && rows.size() == 501 && field(rows, 1, "event").equals("event_1")
            && field(rows, 500, "event").equals("event_500"), "Logger retains latest five hundred entries");
        log.add(-1, "invalid", null, ""); log.add(1, null, null, null);
        check(log.size() == 500, "Invalid logging inputs do not evict valid history");
        StringBuilder large = new StringBuilder(); for (int i = 0; i < 10000; i++) large.append('x');
        log.add(600, large.toString(), null, large.toString());
        rows = csv(log.exportCsv());
        check(field(rows, 500, "event").length() == 128 && field(rows, 500, "detail").length() == 2048,
            "Event and detail text are bounded too");
        check(PlaytestLog.restore(log.save()).exportCsv().equals(log.exportCsv()), "Bounded history round trip preserves all metadata");
    }

    private static void logPersistence() throws Exception {
        byte[] empty = new PlaytestLog().save();
        check(Arrays.equals(empty, PlaytestLog.restore(empty).save()), "Empty log round trip");
        rejectLog(null); rejectLog(new byte[4 * 1024 * 1024 + 1]);
        for (int length : new int[] {0, 1, 8, empty.length / 2, empty.length - 1}) rejectLog(Arrays.copyOf(empty, length));
        byte[] corrupt = empty.clone(); corrupt[0] ^= 1; rejectLog(corrupt);
        rejectLog(patchInt(empty, 0, 0)); rejectLog(patchInt(empty, 4, 3));
        rejectLog(patchByte(empty, 8, 2));
        rejectLog(patchInt(empty, 9, -1)); rejectLog(patchInt(empty, 9, 501));
        rejectLog(withExtraBody(empty));
        PlaytestLog log = new PlaytestLog(); log.enabled = true;
        log.add(1, "x", new GameModel(0, 1, 1), "");
        byte[] entry = log.save();
        int sector = 13 + 8 + 2 + 1;
        rejectLog(patchInt(entry, sector, 60)); rejectLog(patchInt(entry, sector + 4, 3));
        rejectLog(patchInt(entry, sector + 8, 4));
        int seedKnown = sector + 5 * 4;
        rejectLog(patchByte(entry, seedKnown, 2));
        int elapsed = seedKnown + 1 + 8 + 1 + 1;
        rejectLog(patchInt(entry, elapsed, Float.floatToIntBits(Float.NaN)));
        rejectLog(patchInt(entry, elapsed + 4, 4));
    }

    private static GameModel won(int sector, int difficulty, float elapsed, int captures) {
        return won(sector, difficulty, elapsed, captures, GameModel.RULES_VERSION);
    }

    private static GameModel won(int sector, int difficulty, float elapsed, int captures, int rules) {
        GameModel model = new GameModel(sector, difficulty, 100 + sector, rules);
        model.outcome = GameModel.WON; model.elapsed = elapsed; model.captures = captures;
        for (GameModel.Territory tile : model.territories) tile.owner = GameModel.PLAYER;
        return model;
    }

    private static GameModel challenge(int type, int id, int difficulty, float elapsed) {
        GameModel model = won(0, difficulty, elapsed, 2, GameModel.LEGACY_RULES_VERSION);
        model.missionConfigVersion = Challenge.LEGACY_CONFIG_VERSION;
        model.dailyVersion = Challenge.LEGACY_DAILY_VERSION;
        model.objectiveType = type; model.challengeId = id;
        return model;
    }

    private static GameModel actualVictory() {
        GameModel model = new GameModel(0, 1, 7);
        int player = model.originalKing(GameModel.PLAYER), enemy = model.originalKing(1);
        for (GameModel.Territory tile : model.territories) if (!tile.capital) tile.troops = 0;
        model.territories.get(enemy).troops = 0;
        model.launch(player, enemy, 1);
        for (int i = 0; i < 600 && model.outcome == GameModel.PLAYING; i++) model.update(.1f);
        return model;
    }

    private static GameModel kingLoss() {
        GameModel model = new GameModel(0, 1, 7);
        int player = model.originalKing(GameModel.PLAYER), enemy = model.originalKing(1);
        model.territories.get(player).troops = 0;
        model.launch(enemy, player, 1);
        for (int i = 0; i < 600 && model.outcome == GameModel.PLAYING; i++) model.update(.1f);
        return model;
    }

    private static GameModel legacyWin() throws IOException {
        GameModel model = won(0, 1, 42, 2);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x464C3033); out.writeInt(0); out.writeInt(1);
        out.writeFloat(42); out.writeInt(GameModel.WON); out.writeInt(2);
        out.writeInt(0); out.writeInt(0); out.writeInt(model.territories.size());
        for (GameModel.Territory tile : model.territories) { out.writeInt(tile.owner); out.writeDouble(tile.troops); }
        out.writeInt(0);
        for (int i = 0; i < GameModel.MAX_TEAMS; i++) out.writeFloat(2);
        out.writeFloat(0);
        for (int i = 0; i < GameModel.MAX_TEAMS; i++) out.writeBoolean(false);
        out.flush();
        return GameModel.restore(bytes.toByteArray());
    }

    private static boolean emptyCampaign(Progress progress) {
        for (int rules : new int[] {0, 10, 11}) for (int d = 0; d < 3; d++) for (int i = 0; i < 60; i++)
            if (progress.campaignBest(i, d, rules) != 0 || progress.campaignStars(i, d, rules) != 0
                || progress.campaignTime(i, d, rules) != 0 || progress.campaignCleared(i, d)) return false;
        return true;
    }

    private static byte[] patchInt(byte[] original, int offset, int value) {
        byte[] bytes = original.clone(); ByteBuffer.wrap(bytes).putInt(offset, value); checksum(bytes); return bytes;
    }

    private static byte[] patchByte(byte[] original, int offset, int value) {
        byte[] bytes = original.clone(); bytes[offset] = (byte) value; checksum(bytes); return bytes;
    }

    private static byte[] withExtraBody(byte[] original) {
        byte[] bytes = Arrays.copyOf(original, original.length + 1);
        bytes[original.length - 4] = 0; checksum(bytes); return bytes;
    }

    private static void checksum(byte[] bytes) {
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) crc.getValue());
    }

    private static void rejectProgress(byte[] bytes) throws Exception {
        try { Progress.restore(bytes); throw new AssertionError("Corrupt progress accepted"); }
        catch (IOException expected) { checks++; }
    }

    private static void rejectLog(byte[] bytes) throws Exception {
        try { PlaytestLog.restore(bytes); throw new AssertionError("Corrupt log accepted"); }
        catch (IOException expected) { checks++; }
    }

    private static String field(List<List<String>> rows, int row, String column) {
        int index = rows.get(0).indexOf(column);
        if (index < 0) throw new AssertionError("Missing CSV column: " + column);
        return rows.get(row).get(index);
    }

    private static List<List<String>> csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>(); StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && c == ',') { row.add(field.toString()); field.setLength(0); }
            else if (!quoted && c == '\r') {
                check(i + 1 < text.length() && text.charAt(++i) == '\n', "CSV rows use CRLF");
                row.add(field.toString()); field.setLength(0); rows.add(row); row = new ArrayList<>();
            } else field.append(c);
        }
        check(!quoted && field.length() == 0 && row.isEmpty(), "CSV ends outside quoted fields");
        return rows;
    }

    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
