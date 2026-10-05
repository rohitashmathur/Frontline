package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** V10 records only; historical records remain in the scene's legacy profile. */
public final class Progress {
    public static final int DAILY_LIMIT = 60;
    private static final int MAGIC = 0x46503130, FORMAT = 1, MAX_SAVE_BYTES = 16384;
    private static final String DAILY_PREFIX = "daily-v10-1:";
    private static final float MAX_SECONDS = 86400;

    public final int[][] best = new int[3][60], stars = new int[3][60];
    public final float[][] times = new float[3][60];
    public final int[][] challengeBest = new int[3][9], challengeStars = new int[3][9];
    public final float[][] challengeTimes = new float[3][9];
    public boolean crownKeeper, chapterNormal, objectiveMaster;
    public int objectiveMask, theme = 0;

    private final TreeMap<String, DailyRecord> daily = new TreeMap<>();

    private static final class DailyRecord {
        int best, stars;
        float time;
    }

    public boolean themeUnlocked(int candidate) {
        return candidate == 0 || candidate == 1 && crownKeeper || candidate == 2 && chapterNormal;
    }

    public int chapterProgress() {
        int cleared = 0;
        for (int i = 0; i < 6; i++) if (stars[1][i] > 0) cleared++;
        return cleared;
    }

    public int objectiveProgress() { return Integer.bitCount(objectiveMask & 7); }

    public int earnedCount() {
        return (crownKeeper ? 1 : 0) + (chapterNormal ? 1 : 0) + (objectiveMaster ? 1 : 0);
    }

    /** Returns true for a first winning time or a strictly faster winning time. */
    public boolean recordCampaign(GameModel model) {
        if (!eligible(model) || model.objectiveType != 0 || hasDate(model)
            || model.levelIndex < 0 || model.levelIndex >= 60) return false;
        boolean improved = record(best, stars, times, model.difficulty, model.levelIndex, model);
        awardCrown(model);
        if (chapterProgress() == 6) chapterNormal = true;
        return improved;
    }

    /** Daily attempts never write campaign or ordinary mission records. */
    public boolean recordChallenge(GameModel model) {
        if (!eligible(model) || model.objectiveType < 1 || model.objectiveType > 3) return false;
        boolean improved;
        if (hasDate(model)) {
            if (!validDate(model.dailyDate)) return false;
            String key = DAILY_PREFIX + model.dailyDate;
            DailyRecord entry = daily.get(key);
            improved = entry == null || model.elapsed < entry.time;
            if (entry == null) {
                entry = new DailyRecord();
                daily.put(key, entry);
            }
            entry.best = Math.max(entry.best, model.score());
            entry.stars = Math.max(entry.stars, model.stars());
            if (improved) entry.time = model.elapsed;
            while (daily.size() > DAILY_LIMIT) daily.pollFirstEntry();
            improved &= daily.containsKey(key);
        } else {
            if (model.challengeId < 0 || model.challengeId >= 9) return false;
            improved = record(challengeBest, challengeStars, challengeTimes,
                model.difficulty, model.challengeId, model);
        }
        awardCrown(model);
        objectiveMask |= 1 << (model.objectiveType - 1);
        if (objectiveMask == 7) objectiveMaster = true;
        return improved;
    }

    public int dailyBest(String date) {
        DailyRecord entry = daily.get(DAILY_PREFIX + date);
        return entry == null ? 0 : entry.best;
    }

    public float dailyTime(String date) {
        DailyRecord entry = daily.get(DAILY_PREFIX + date);
        return entry == null ? 0 : entry.time;
    }

    public int dailyStars(String date) {
        DailyRecord entry = daily.get(DAILY_PREFIX + date);
        return entry == null ? 0 : entry.stars;
    }

    private static boolean eligible(GameModel model) {
        return model != null && model.rulesVersion == 10 && model.outcome == GameModel.WON
            && model.difficulty >= 0 && model.difficulty < 3
            && Float.isFinite(model.elapsed) && model.elapsed >= 0 && model.elapsed <= MAX_SECONDS
            && model.score() > 0 && model.stars() >= 1 && model.stars() <= 3;
    }

    private static boolean hasDate(GameModel model) {
        return model.dailyDate != null && !model.dailyDate.isEmpty();
    }

    private void awardCrown(GameModel model) {
        if (model.historyKnown && !model.startingKingLost) crownKeeper = true;
    }

    private static boolean record(int[][] best, int[][] stars, float[][] times,
                                  int difficulty, int index, GameModel model) {
        boolean improved = stars[difficulty][index] == 0 || model.elapsed < times[difficulty][index];
        best[difficulty][index] = Math.max(best[difficulty][index], model.score());
        stars[difficulty][index] = Math.max(stars[difficulty][index], model.stars());
        if (improved) times[difficulty][index] = model.elapsed;
        return improved;
    }

    public byte[] save() {
        try {
            validate();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeInt(FORMAT);
            writeRecords(out, best, stars, times);
            writeRecords(out, challengeBest, challengeStars, challengeTimes);
            out.writeBoolean(crownKeeper); out.writeBoolean(chapterNormal); out.writeBoolean(objectiveMaster);
            out.writeInt(objectiveMask); out.writeInt(theme); out.writeInt(daily.size());
            for (Map.Entry<String, DailyRecord> item : daily.entrySet()) {
                DailyRecord entry = item.getValue();
                out.writeUTF(item.getKey()); out.writeInt(entry.best);
                out.writeInt(entry.stars); out.writeFloat(entry.time);
            }
            out.flush();
            CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
            out.writeInt((int) crc.getValue()); out.flush();
            return bytes.toByteArray();
        } catch (IOException invalid) {
            throw new IllegalStateException("Invalid progress", invalid);
        }
    }

    public static Progress restore(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 12 || bytes.length > MAX_SAVE_BYTES)
            throw new IOException("Invalid progress size");
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        DataInputStream checksum = new DataInputStream(new ByteArrayInputStream(bytes, bytes.length - 4, 4));
        if (checksum.readInt() != (int) crc.getValue()) throw new IOException("Invalid progress checksum");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        if (in.readInt() != MAGIC || in.readInt() != FORMAT) throw new IOException("Unknown progress version");
        Progress result = new Progress();
        readRecords(in, result.best, result.stars, result.times);
        readRecords(in, result.challengeBest, result.challengeStars, result.challengeTimes);
        result.crownKeeper = readFlag(in); result.chapterNormal = readFlag(in);
        result.objectiveMaster = readFlag(in);
        result.objectiveMask = in.readInt(); result.theme = in.readInt();
        int count = in.readInt();
        if (count < 0 || count > DAILY_LIMIT) throw new IOException("Invalid daily count");
        for (int i = 0; i < count; i++) {
            String key = in.readUTF();
            if (!validDailyKey(key)) throw new IOException("Invalid daily key");
            DailyRecord entry = new DailyRecord();
            entry.best = in.readInt(); entry.stars = in.readInt(); entry.time = in.readFloat();
            validateRecord(entry.best, entry.stars, entry.time);
            if (entry.stars == 0 || result.daily.put(key, entry) != null)
                throw new IOException("Invalid daily record");
        }
        if (in.available() != 0) throw new IOException("Unexpected progress data");
        result.validate();
        return result;
    }

    private static void writeRecords(DataOutputStream out, int[][] best, int[][] stars,
                                     float[][] times) throws IOException {
        for (int d = 0; d < 3; d++) for (int i = 0; i < best[d].length; i++) {
            validateRecord(best[d][i], stars[d][i], times[d][i]);
            out.writeInt(best[d][i]); out.writeInt(stars[d][i]); out.writeFloat(times[d][i]);
        }
    }

    private static void readRecords(DataInputStream in, int[][] best, int[][] stars,
                                    float[][] times) throws IOException {
        for (int d = 0; d < 3; d++) for (int i = 0; i < best[d].length; i++) {
            best[d][i] = in.readInt(); stars[d][i] = in.readInt(); times[d][i] = in.readFloat();
            validateRecord(best[d][i], stars[d][i], times[d][i]);
        }
    }

    private void validate() throws IOException {
        validateRecords(best, stars, times, 60);
        validateRecords(challengeBest, challengeStars, challengeTimes, 9);
        if (objectiveMask < 0 || objectiveMask > 7 || objectiveMaster != (objectiveMask == 7)
            || chapterNormal != (chapterProgress() == 6) || !themeUnlocked(theme))
            throw new IOException("Invalid mastery state");
        if (daily.size() > DAILY_LIMIT) throw new IOException("Invalid daily count");
        for (Map.Entry<String, DailyRecord> item : daily.entrySet()) {
            DailyRecord entry = item.getValue();
            if (!validDailyKey(item.getKey()) || entry.stars == 0) throw new IOException("Invalid daily record");
            validateRecord(entry.best, entry.stars, entry.time);
        }
    }

    private static void validateRecords(int[][] best, int[][] stars, float[][] times,
                                       int columns) throws IOException {
        for (int d = 0; d < 3; d++) {
            if (best[d] == null || stars[d] == null || times[d] == null || best[d].length != columns
                || stars[d].length != columns || times[d].length != columns)
                throw new IOException("Invalid record dimensions");
            for (int i = 0; i < columns; i++) validateRecord(best[d][i], stars[d][i], times[d][i]);
        }
    }

    private static void validateRecord(int best, int stars, float time) throws IOException {
        if (best < 0 || stars < 0 || stars > 3 || !Float.isFinite(time) || time < 0 || time > MAX_SECONDS
            || stars == 0 && (best != 0 || time != 0) || stars > 0 && best == 0)
            throw new IOException("Invalid winning record");
    }

    private static boolean readFlag(DataInputStream in) throws IOException {
        int flag = in.readUnsignedByte();
        if (flag > 1) throw new IOException("Invalid progress flag");
        return flag == 1;
    }

    private static boolean validDailyKey(String key) {
        return key.startsWith(DAILY_PREFIX) && validDate(key.substring(DAILY_PREFIX.length()));
    }

    private static boolean validDate(String date) {
        if (date == null || date.length() != 10 || date.charAt(4) != '-' || date.charAt(7) != '-') return false;
        int year = 0, month = 0, day = 0;
        for (int i = 0; i < date.length(); i++) {
            if (i == 4 || i == 7) continue;
            int digit = date.charAt(i) - '0';
            if (digit < 0 || digit > 9) return false;
            if (i < 4) year = year * 10 + digit;
            else if (i < 7) month = month * 10 + digit;
            else day = day * 10 + digit;
        }
        if (year == 0 || month < 1 || month > 12 || day < 1) return false;
        int[] days = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) days[1] = 29;
        return day <= days[month - 1];
    }
}
