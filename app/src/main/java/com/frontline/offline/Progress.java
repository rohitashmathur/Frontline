package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Classic campaign records, untouched FP10 history, and configuration-isolated objective records. */
public final class Progress {
    public static final int DAILY_LIMIT = 60;
    public static final int UNVERSIONED_CAMPAIGN_RULES = 0;
    private static final int MAGIC = 0x46503130, FORMAT = 3, MAX_SAVE_BYTES = 1024 * 1024;
    private static final int MAX_MISSION_RECORDS = 4096;
    private static final String DAILY_PREFIX = "daily-v10-1:";
    private static final float MAX_SECONDS = 86400;

    /** Current rules-11 campaign records only. */
    public final int[][] best = new int[3][60], stars = new int[3][60];
    public final float[][] times = new float[3][60];
    /** Rules-10 campaign history, including resumed rules-10 victories. */
    public final int[][] historicalCampaignBest = new int[3][60], historicalCampaignStars = new int[3][60];
    public final float[][] historicalCampaignTimes = new float[3][60];
    public final int[][] challengeBest = new int[3][9], challengeStars = new int[3][9];
    public final float[][] challengeTimes = new float[3][9];
    public boolean crownKeeper, chapterNormal, objectiveMaster;
    public int objectiveMask, theme = 0;

    private final TreeMap<String, DailyRecord> daily = new TreeMap<>();
    private final TreeMap<MissionKey, MissionEntry> missions = new TreeMap<>();
    // Format 2 combined campaign rules without provenance; retain it without guessing a version.
    private final int[][] unversionedCampaignBest = new int[3][60], unversionedCampaignStars = new int[3][60];
    private final float[][] unversionedCampaignTimes = new float[3][60];

    /** A snapshot; historical speed stars/times are never reinterpreted as revised personal bests. */
    public static final class MissionRecord {
        public final boolean completed, historicalCompleted;
        public final ObjectiveResult.Policy policy;
        public final float bestElapsed, actualSeconds, requiredSeconds, historicalTime;
        public final int bestUnits, bestUnitsSent, budget, historicalBest, historicalStars;
        public final String historicalLabel;

        private MissionRecord(ObjectiveResult.Policy policy, MissionEntry entry, MissionKey key,
                              int oldBest, int oldStars, float oldTime, boolean historicalComparable) {
            this.policy = policy;
            historicalCompleted = oldStars > 0;
            completed = entry != null || policy == ObjectiveResult.Policy.HISTORICAL
                && historicalComparable && historicalCompleted;
            bestElapsed = entry == null ? 0 : entry.elapsed;
            bestUnits = entry == null ? -1 : entry.units;
            bestUnitsSent = bestUnits;
            actualSeconds = entry == null ? 0 : entry.actualSeconds;
            requiredSeconds = key == null ? 0 : key.seconds;
            budget = key == null ? 0 : key.budget;
            historicalBest = oldBest;
            historicalStars = oldStars;
            historicalTime = oldTime;
            historicalLabel = historicalCompleted ? ObjectiveResult.HISTORICAL_LABEL : "";
        }

        public boolean completedEver() { return completed || historicalCompleted; }
        public boolean hasFastestTime() { return policy == ObjectiveResult.Policy.ELAPSED_TIME; }
    }

    private static final class MissionEntry {
        float elapsed, actualSeconds;
        int units = -1;
    }

    private static final class MissionKey implements Comparable<MissionKey> {
        final String date, dailyVersion;
        final int rules, config, difficulty, type, id, sector, budget;
        final float seconds;

        MissionKey(GameModel model) {
            this(model.dailyDate, model.dailyDate.isEmpty() ? "" : model.dailyVersion,
                model.rulesVersion, model.missionConfigVersion,
                model.difficulty, model.objectiveType, model.challengeId, model.levelIndex,
                model.objectiveSeconds, model.deploymentBudget);
        }

        MissionKey(String date, String dailyVersion, int rules, int config, int difficulty,
                   int type, int id, int sector, float seconds, int budget) {
            this.date = date;
            this.dailyVersion = dailyVersion;
            this.rules = rules; this.config = config; this.difficulty = difficulty;
            this.type = type; this.id = id; this.sector = sector;
            this.seconds = seconds == 0 ? 0 : seconds; this.budget = budget;
        }

        @Override public int compareTo(MissionKey other) {
            int result = date.compareTo(other.date);
            if (result == 0) result = dailyVersion.compareTo(other.dailyVersion);
            if (result == 0) result = Integer.compare(rules, other.rules);
            if (result == 0) result = Integer.compare(config, other.config);
            if (result == 0) result = Integer.compare(difficulty, other.difficulty);
            if (result == 0) result = Integer.compare(type, other.type);
            if (result == 0) result = Integer.compare(id, other.id);
            if (result == 0) result = Integer.compare(sector, other.sector);
            if (result == 0) result = Float.compare(seconds, other.seconds);
            if (result == 0) result = Integer.compare(budget, other.budget);
            return result;
        }
    }

    private static final class DailyRecord {
        int best, stars;
        float time;
    }

    public boolean themeUnlocked(int candidate) {
        return candidate == 0 || candidate == 1 && crownKeeper || candidate == 2 && chapterNormal;
    }

    public int chapterProgress() {
        int cleared = 0;
        for (int i = 0; i < 6; i++) if (campaignCleared(i, 1)) cleared++;
        return cleared;
    }

    /** Rules 0 exposes retained, unversioned format-2 history, never a comparable battle record. */
    public int campaignBest(int sector, int difficulty, int rules) {
        if (!validCampaignIndex(sector, difficulty)) return 0;
        if (rules == 11) return best[difficulty][sector];
        if (rules == 10) return historicalCampaignBest[difficulty][sector];
        return rules == UNVERSIONED_CAMPAIGN_RULES ? unversionedCampaignBest[difficulty][sector] : 0;
    }

    public int campaignStars(int sector, int difficulty, int rules) {
        if (!validCampaignIndex(sector, difficulty)) return 0;
        if (rules == 11) return stars[difficulty][sector];
        if (rules == 10) return historicalCampaignStars[difficulty][sector];
        return rules == UNVERSIONED_CAMPAIGN_RULES ? unversionedCampaignStars[difficulty][sector] : 0;
    }

    public float campaignTime(int sector, int difficulty, int rules) {
        if (!validCampaignIndex(sector, difficulty)) return 0;
        if (rules == 11) return times[difficulty][sector];
        if (rules == 10) return historicalCampaignTimes[difficulty][sector];
        return rules == UNVERSIONED_CAMPAIGN_RULES ? unversionedCampaignTimes[difficulty][sector] : 0;
    }

    /** Completion is earned once per sector/difficulty, across all retained campaign versions. */
    public boolean campaignCleared(int sector, int difficulty) {
        return campaignStars(sector, difficulty, 10) > 0 || campaignStars(sector, difficulty, 11) > 0
            || campaignStars(sector, difficulty, UNVERSIONED_CAMPAIGN_RULES) > 0;
    }

    public String campaignHistoricalLabel(int sector, int difficulty, int rules) {
        return (rules == 10 || rules == UNVERSIONED_CAMPAIGN_RULES) && campaignStars(sector, difficulty, rules) > 0
            ? ObjectiveResult.HISTORICAL_LABEL : "";
    }

    private static boolean validCampaignIndex(int sector, int difficulty) {
        return sector >= 0 && sector < 60 && difficulty >= 0 && difficulty < 3;
    }

    public int objectiveProgress() { return Integer.bitCount(objectiveMask & 7); }

    public int earnedCount() {
        return (crownKeeper ? 1 : 0) + (chapterNormal ? 1 : 0) + (objectiveMaster ? 1 : 0);
    }

    /** Returns true for a first winning time or a strictly faster winning time. */
    public boolean recordCampaign(GameModel model) {
        if (!eligibleClassic(model) || model.objectiveType != 0 || hasDate(model)
            || model.levelIndex < 0 || model.levelIndex >= 60) return false;
        boolean improved = model.rulesVersion == 10
            ? record(historicalCampaignBest, historicalCampaignStars, historicalCampaignTimes,
                model.difficulty, model.levelIndex, model)
            : record(best, stars, times, model.difficulty, model.levelIndex, model);
        awardCrown(model);
        if (chapterProgress() == 6) chapterNormal = true;
        return improved;
    }

    /** True when the objective's record improves, including a budget elapsed-time tie-break. */
    public boolean recordChallenge(GameModel model) {
        if (ObjectiveResult.revised(model)) return recordMission(model);
        if (!eligibleClassic(model) || model.rulesVersion != 10 || model.missionConfigVersion < 0
            || model.missionConfigVersion > 1 || model.dailyDate == null || model.dailyVersion == null
            || hasDate(model) && !model.dailyVersion.isEmpty() && !model.dailyVersion.equals("daily-v10-1")
            || model.objectiveType < 1 || model.objectiveType > 3) return false;
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

    private boolean recordMission(GameModel model) {
        if (!eligibleMission(model)) return false;
        MissionKey key = new MissionKey(model);
        MissionEntry entry = missions.get(key);
        ObjectiveResult.Policy policy = ObjectiveResult.policy(model);
        boolean improved = entry == null || policy == ObjectiveResult.Policy.ELAPSED_TIME && model.elapsed < entry.elapsed
            || policy == ObjectiveResult.Policy.DEPLOYED_TROOPS && (model.unitsSent < entry.units
                || model.unitsSent == entry.units && model.elapsed < entry.elapsed);
        if (entry == null) {
            if (missions.size() >= MAX_MISSION_RECORDS) return false;
            entry = new MissionEntry();
            missions.put(key, entry);
        }
        if (improved) {
            entry.elapsed = policy == ObjectiveResult.Policy.COMPLETION_ONLY ? 0 : model.elapsed;
            entry.units = policy == ObjectiveResult.Policy.DEPLOYED_TROOPS ? model.unitsSent : -1;
            entry.actualSeconds = model.objectiveType == Challenge.KEEP_KING ? model.elapsed : model.objectiveProgress;
        }
        pruneMissionDates();
        awardCrown(model);
        objectiveMask |= 1 << (model.objectiveType - 1);
        if (objectiveMask == 7) objectiveMaster = true;
        return improved && missions.containsKey(key);
    }

    /** Exact current configuration plus separately labelled, earned FP10 history. */
    public MissionRecord missionRecord(GameModel model) {
        if (model == null) return new MissionRecord(ObjectiveResult.Policy.NONE, null, null, 0, 0, 0, false);
        MissionKey key = validMissionIdentity(model) ? new MissionKey(model) : null;
        return missionSnapshot(ObjectiveResult.policy(model), key, key == null ? null : missions.get(key),
            model.challengeId, model.difficulty, model.dailyDate);
    }

    /** Prefer the model overload when a configuration has multiple timer/budget variants. */
    public MissionRecord missionRecord(int id, int diff, int config, int type, String date,
                                      String dailyVersion, int rules) {
        ObjectiveResult.Policy policy = ObjectiveResult.policy(type, rules, config);
        MissionKey match = null;
        boolean ambiguous = false;
        if (id >= 0 && id < 9 && diff >= 0 && diff < 3 && date != null
            && (date.isEmpty() || validDate(date) && validVersion(dailyVersion))) {
            for (MissionKey key : missions.keySet()) {
                if (key.id == id && key.difficulty == diff && key.config == config && key.type == type
                    && key.rules == rules && key.date.equals(date)
                    && (date.isEmpty() || key.dailyVersion.equals(dailyVersion))) {
                    if (match != null) { ambiguous = true; break; }
                    match = key;
                }
            }
        }
        if (ambiguous) match = null;
        return missionSnapshot(policy, match, match == null ? null : missions.get(match), id, diff, date);
    }

    private MissionRecord missionSnapshot(ObjectiveResult.Policy policy, MissionKey key, MissionEntry entry,
                                          int id, int difficulty, String date) {
        int oldBest = 0, oldStars = 0;
        float oldTime = 0;
        if (date != null && !date.isEmpty()) {
            DailyRecord old = daily.get(DAILY_PREFIX + date);
            if (old != null) { oldBest = old.best; oldStars = old.stars; oldTime = old.time; }
        } else if (difficulty >= 0 && difficulty < 3 && id >= 0 && id < 9) {
            oldBest = challengeBest[difficulty][id];
            oldStars = challengeStars[difficulty][id];
            oldTime = challengeTimes[difficulty][id];
        }
        return new MissionRecord(policy, entry, key, oldBest, oldStars, oldTime,
            date != null && date.isEmpty() && difficulty >= 0 && difficulty < 3 && id >= 0 && id < 9);
    }

    public boolean missionCompleted(GameModel model) { return missionRecord(model).completed; }

    /** Earned completion across versions; use missionRecord(model) for comparable personal bests. */
    public boolean challengeCompleted(int id, int difficulty) {
        if (id < 0 || id >= 9 || difficulty < 0 || difficulty >= 3) return false;
        if (challengeStars[difficulty][id] > 0) return true;
        for (MissionKey key : missions.keySet())
            if (key.date.isEmpty() && key.id == id && key.difficulty == difficulty) return true;
        return false;
    }

    /** Version 1 did not store difficulty; it must not manufacture a difficulty-specific clear. */
    public boolean dailyCompleted(String date, int difficulty, String version) {
        if (!validDate(date) || difficulty < 0 || difficulty >= 3 || !validVersion(version)) return false;
        for (MissionKey key : missions.keySet())
            if (key.date.equals(date) && key.difficulty == difficulty && key.dailyVersion.equals(version)) return true;
        return false;
    }

    public boolean dailyCompleted(String date, int difficulty, String version, int rules, int config) {
        if (!validDate(date) || difficulty < 0 || difficulty >= 3 || !validVersion(version)) return false;
        for (MissionKey key : missions.keySet())
            if (key.date.equals(date) && key.difficulty == difficulty && key.dailyVersion.equals(version)
                && key.rules == rules && key.config == config) return true;
        return false;
    }

    public boolean historicalDailyCompleted(String date) { return dailyStars(date) > 0; }

    private void pruneMissionDates() {
        TreeSet<String> dates = new TreeSet<>();
        for (MissionKey key : missions.keySet()) if (!key.date.isEmpty()) dates.add(key.date);
        while (dates.size() > DAILY_LIMIT) {
            String oldest = dates.pollFirst();
            missions.keySet().removeIf(key -> key.date.equals(oldest));
        }
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

    private static boolean eligibleClassic(GameModel model) {
        return eligibleWin(model)
            && model.score() > 0 && model.stars() >= 1 && model.stars() <= 3;
    }

    private static boolean eligibleWin(GameModel model) {
        return model != null && (model.rulesVersion == 10 || model.rulesVersion == 11) && model.outcome == GameModel.WON
            && model.difficulty >= 0 && model.difficulty < 3
            && validSeconds(model.elapsed);
    }

    private static boolean eligibleMission(GameModel model) {
        if (!eligibleWin(model) || !validMissionIdentity(model) || model.unitsSent < 0
            || model.unitsLost < 0 || model.captures < 0 || !validSeconds(model.objectiveProgress)
            || model.objectiveProgress > model.objectiveSeconds || model.objectiveProgress > model.elapsed + .0001f)
            return false;
        if (model.objectiveType == Challenge.BUDGET) return model.unitsSent <= model.deploymentBudget;
        return model.objectiveProgress >= model.objectiveSeconds && model.elapsed >= model.objectiveSeconds
            && (model.objectiveType != Challenge.KEEP_KING || !model.startingKingLost);
    }

    private static boolean validMissionIdentity(GameModel model) {
        if (!ObjectiveResult.revised(model) || model.difficulty < 0 || model.difficulty >= 3
            || model.challengeId < 0 || model.challengeId >= 9 || model.levelIndex < 0 || model.levelIndex >= 60
            || model.dailyDate == null || !model.dailyDate.isEmpty()
                && (!validDate(model.dailyDate) || !validVersion(model.dailyVersion))) return false;
        return validObjective(model.objectiveType, model.objectiveSeconds, model.deploymentBudget);
    }

    private static boolean validObjective(int type, float seconds, int budget) {
        return type >= Challenge.HOLD_KING && type <= Challenge.BUDGET && validSeconds(seconds)
            && (type == Challenge.BUDGET ? seconds == 0 && budget > 0 : seconds > 0 && budget == 0);
    }

    private static boolean validSeconds(float seconds) {
        return Float.isFinite(seconds) && seconds >= 0 && seconds <= MAX_SECONDS;
    }

    private static boolean validVersion(String version) {
        return version != null && version.matches("[A-Za-z0-9_-]{1,64}");
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
            out.writeInt(missions.size());
            for (Map.Entry<MissionKey, MissionEntry> item : missions.entrySet()) {
                MissionKey key = item.getKey(); MissionEntry entry = item.getValue();
                out.writeUTF(key.date); out.writeUTF(key.dailyVersion);
                out.writeInt(key.rules); out.writeInt(key.config); out.writeInt(key.difficulty);
                out.writeInt(key.type); out.writeInt(key.id); out.writeInt(key.sector);
                out.writeFloat(key.seconds); out.writeInt(key.budget);
                out.writeFloat(entry.elapsed); out.writeInt(entry.units); out.writeFloat(entry.actualSeconds);
            }
            writeRecords(out, historicalCampaignBest, historicalCampaignStars, historicalCampaignTimes);
            writeRecords(out, unversionedCampaignBest, unversionedCampaignStars, unversionedCampaignTimes);
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
        if (in.readInt() != MAGIC) throw new IOException("Unknown progress version");
        int format = in.readInt();
        if (format < 1 || format > FORMAT) throw new IOException("Unknown progress version");
        if (format == 1 && bytes.length > 16384) throw new IOException("Invalid historical progress size");
        Progress result = new Progress();
        if (format == 1)
            readRecords(in, result.historicalCampaignBest, result.historicalCampaignStars, result.historicalCampaignTimes);
        else if (format == 2)
            readRecords(in, result.unversionedCampaignBest, result.unversionedCampaignStars, result.unversionedCampaignTimes);
        else readRecords(in, result.best, result.stars, result.times);
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
        if (format >= 2) {
            int missionCount = in.readInt();
            if (missionCount < 0 || missionCount > MAX_MISSION_RECORDS)
                throw new IOException("Invalid objective record count");
            for (int i = 0; i < missionCount; i++) {
                MissionKey key = new MissionKey(in.readUTF(), in.readUTF(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readFloat(), in.readInt());
                MissionEntry entry = new MissionEntry();
                entry.elapsed = in.readFloat(); entry.units = in.readInt(); entry.actualSeconds = in.readFloat();
                validateMission(key, entry);
                if (result.missions.put(key, entry) != null) throw new IOException("Duplicate objective record");
            }
        }
        if (format >= 3) {
            readRecords(in, result.historicalCampaignBest, result.historicalCampaignStars, result.historicalCampaignTimes);
            readRecords(in, result.unversionedCampaignBest, result.unversionedCampaignStars, result.unversionedCampaignTimes);
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
        validateRecords(historicalCampaignBest, historicalCampaignStars, historicalCampaignTimes, 60);
        validateRecords(unversionedCampaignBest, unversionedCampaignStars, unversionedCampaignTimes, 60);
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
        if (missions.size() > MAX_MISSION_RECORDS) throw new IOException("Invalid objective record count");
        TreeSet<String> dates = new TreeSet<>();
        for (Map.Entry<MissionKey, MissionEntry> item : missions.entrySet()) {
            validateMission(item.getKey(), item.getValue());
            if (!item.getKey().date.isEmpty()) dates.add(item.getKey().date);
        }
        if (dates.size() > DAILY_LIMIT) throw new IOException("Invalid objective daily date count");
    }

    private static void validateMission(MissionKey key, MissionEntry entry) throws IOException {
        if ((key.rules != 10 && key.rules != 11) || key.config <= 0 || key.rules == 10 && key.config < 2
            || key.difficulty < 0 || key.difficulty >= 3 || key.id < 0 || key.id >= 9
            || key.sector < 0 || key.sector >= 60 || !validObjective(key.type, key.seconds, key.budget)
            || (key.date.isEmpty() ? !key.dailyVersion.isEmpty() : !validDate(key.date) || !validVersion(key.dailyVersion))
            || !validSeconds(entry.elapsed) || !validSeconds(entry.actualSeconds))
            throw new IOException("Invalid objective record");
        if (key.type == Challenge.BUDGET) {
            if (entry.units < 0 || entry.units > key.budget || entry.actualSeconds != 0)
                throw new IOException("Invalid deployment record");
        } else if (entry.units != -1 || entry.actualSeconds < key.seconds
            || (key.type == Challenge.KEEP_KING ? entry.elapsed != 0
                : entry.actualSeconds != key.seconds || entry.elapsed < key.seconds)) {
            throw new IOException("Invalid timed objective record");
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
