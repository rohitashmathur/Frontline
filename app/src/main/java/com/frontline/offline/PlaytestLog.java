package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.zip.CRC32;

/** Explicit, opt-in event snapshots; no lifecycle or abandonment inference. */
public final class PlaytestLog {
    public static final int ENTRY_LIMIT = 500;
    private static final int MAGIC = 0x464C3130, FORMAT = 1, MAX_SAVE_BYTES = 4 * 1024 * 1024;
    private static final int EVENT_LIMIT = 128, DETAIL_LIMIT = 2048;
    private static final int MENU = 0, CAMPAIGN = 1, CHALLENGE = 2, DAILY = 3;
    private static final String[] MODES = {"menu", "campaign", "challenge", "daily"};
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    public boolean enabled = false;

    private static final class Entry {
        long wallMillis, seed;
        String event, detail, dailyDate = "";
        int sector = -1, difficulty = -1, mode = MENU, rulesVersion, aiVersion;
        int objectiveType, objectiveTarget = -1, deploymentBudget, challengeId = -1, outcome = -1;
        boolean seedKnown, historyKnown, startingKingLost;
        float elapsed, objectiveSeconds, objectiveProgress;
    }

    public void add(long wallMillis, String event, GameModel model, String detail) {
        if (!enabled || wallMillis < 0 || event == null || event.isEmpty()) return;
        Entry entry = new Entry();
        entry.wallMillis = wallMillis; entry.event = bounded(event, EVENT_LIMIT);
        entry.detail = bounded(detail, DETAIL_LIMIT);
        if (model != null) {
            entry.sector = model.levelIndex; entry.difficulty = model.difficulty;
            entry.rulesVersion = model.rulesVersion; entry.aiVersion = model.aiVersion;
            entry.seedKnown = model.rulesVersion > 0 && model.seedKnown;
            entry.seed = entry.seedKnown ? model.seed : 0;
            entry.historyKnown = model.rulesVersion > 0 && model.historyKnown;
            entry.startingKingLost = model.startingKingLost;
            entry.elapsed = model.elapsed;
            entry.objectiveType = model.objectiveType; entry.challengeId = model.challengeId;
            entry.objectiveTarget = model.objectiveTarget; entry.objectiveSeconds = model.objectiveSeconds;
            entry.objectiveProgress = model.objectiveProgress; entry.deploymentBudget = model.deploymentBudget;
            entry.dailyDate = model.dailyDate == null ? "" : model.dailyDate;
            entry.outcome = model.outcome;
            entry.mode = !entry.dailyDate.isEmpty() ? DAILY : entry.objectiveType > 0 ? CHALLENGE : CAMPAIGN;
        }
        try {
            validate(entry);
        } catch (IOException invalid) {
            return;
        }
        if (entries.size() == ENTRY_LIMIT) entries.removeFirst();
        entries.addLast(entry);
    }

    public int size() { return entries.size(); }

    /** Clearing history does not change the player's opt-in choice. */
    public void clear() { entries.clear(); }

    public String exportCsv() {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, "timestamp_ms", "event", "sector", "sector_index", "difficulty", "difficulty_id",
            "mode", "rules_version", "ai_version", "seed", "seed_known", "history_known",
            "starting_king_lost", "elapsed_seconds", "objective_type", "objective_target", "objective_seconds",
            "objective_progress", "deployment_budget", "challenge_id", "daily_date", "outcome", "detail");
        for (Entry entry : entries) {
            appendRow(csv, Long.toString(entry.wallMillis), entry.event,
                entry.sector < 0 ? "" : Integer.toString(entry.sector + 1),
                entry.sector < 0 ? "" : Integer.toString(entry.sector),
                entry.difficulty < 0 ? "unknown" : GameModel.DIFFICULTIES[entry.difficulty],
                entry.difficulty < 0 ? "" : Integer.toString(entry.difficulty), MODES[entry.mode],
                Integer.toString(entry.rulesVersion), Integer.toString(entry.aiVersion),
                entry.seedKnown ? Long.toString(entry.seed) : "unknown",
                Boolean.toString(entry.seedKnown), Boolean.toString(entry.historyKnown),
                Boolean.toString(entry.startingKingLost), Float.toString(entry.elapsed),
                Integer.toString(entry.objectiveType), Integer.toString(entry.objectiveTarget),
                Float.toString(entry.objectiveSeconds), Float.toString(entry.objectiveProgress),
                Integer.toString(entry.deploymentBudget),
                entry.challengeId < 0 ? "" : Integer.toString(entry.challengeId), entry.dailyDate,
                entry.outcome < 0 ? "" : Integer.toString(entry.outcome), entry.detail);
        }
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) csv.append(',');
            String field = fields[i];
            boolean quoted = field.indexOf(',') >= 0 || field.indexOf('"') >= 0
                || field.indexOf('\r') >= 0 || field.indexOf('\n') >= 0;
            if (quoted) csv.append('"');
            for (int j = 0; j < field.length(); j++) {
                char c = field.charAt(j);
                if (c == '"') csv.append('"');
                csv.append(c);
            }
            if (quoted) csv.append('"');
        }
        csv.append("\r\n");
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        if (value.length() <= limit) return value;
        if (Character.isHighSurrogate(value.charAt(limit - 1))) limit--;
        return value.substring(0, limit);
    }

    public byte[] save() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeBoolean(enabled); out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeLong(entry.wallMillis); out.writeUTF(entry.event);
                out.writeInt(entry.sector); out.writeInt(entry.difficulty); out.writeInt(entry.mode);
                out.writeInt(entry.rulesVersion); out.writeInt(entry.aiVersion);
                out.writeBoolean(entry.seedKnown); out.writeLong(entry.seed);
                out.writeBoolean(entry.historyKnown); out.writeBoolean(entry.startingKingLost);
                out.writeFloat(entry.elapsed); out.writeInt(entry.objectiveType);
                out.writeInt(entry.objectiveTarget); out.writeFloat(entry.objectiveSeconds);
                out.writeFloat(entry.objectiveProgress); out.writeInt(entry.deploymentBudget);
                out.writeInt(entry.challengeId); out.writeUTF(entry.dailyDate);
                out.writeInt(entry.outcome); out.writeUTF(entry.detail);
            }
            out.flush();
            CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
            out.writeInt((int) crc.getValue()); out.flush();
            return bytes.toByteArray();
        } catch (IOException invalid) {
            throw new IllegalStateException("Could not save playtest log", invalid);
        }
    }

    public static PlaytestLog restore(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 12 || bytes.length > MAX_SAVE_BYTES)
            throw new IOException("Invalid playtest log size");
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        DataInputStream checksum = new DataInputStream(new ByteArrayInputStream(bytes, bytes.length - 4, 4));
        if (checksum.readInt() != (int) crc.getValue()) throw new IOException("Invalid playtest log checksum");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        if (in.readInt() != MAGIC || in.readInt() != FORMAT) throw new IOException("Unknown playtest log version");
        PlaytestLog result = new PlaytestLog();
        result.enabled = readFlag(in);
        int count = in.readInt();
        if (count < 0 || count > ENTRY_LIMIT) throw new IOException("Invalid playtest log count");
        for (int i = 0; i < count; i++) {
            Entry entry = new Entry();
            entry.wallMillis = in.readLong(); entry.event = in.readUTF();
            entry.sector = in.readInt(); entry.difficulty = in.readInt(); entry.mode = in.readInt();
            entry.rulesVersion = in.readInt(); entry.aiVersion = in.readInt();
            entry.seedKnown = readFlag(in); entry.seed = in.readLong();
            entry.historyKnown = readFlag(in); entry.startingKingLost = readFlag(in);
            entry.elapsed = in.readFloat(); entry.objectiveType = in.readInt();
            entry.objectiveTarget = in.readInt(); entry.objectiveSeconds = in.readFloat();
            entry.objectiveProgress = in.readFloat(); entry.deploymentBudget = in.readInt();
            entry.challengeId = in.readInt(); entry.dailyDate = in.readUTF();
            entry.outcome = in.readInt(); entry.detail = in.readUTF();
            validate(entry);
            result.entries.addLast(entry);
        }
        if (in.available() != 0) throw new IOException("Unexpected playtest log data");
        return result;
    }

    private static boolean readFlag(DataInputStream in) throws IOException {
        int flag = in.readUnsignedByte();
        if (flag > 1) throw new IOException("Invalid playtest log flag");
        return flag == 1;
    }

    private static void validate(Entry entry) throws IOException {
        if (entry.wallMillis < 0 || entry.event.isEmpty() || entry.event.length() > EVENT_LIMIT
            || entry.detail.length() > DETAIL_LIMIT || entry.dailyDate.length() > 10
            || entry.mode < MENU || entry.mode > DAILY
            || entry.rulesVersion < 0 || entry.rulesVersion > 1000 || entry.aiVersion < 0 || entry.aiVersion > 1000
            || !Float.isFinite(entry.elapsed) || entry.elapsed < 0 || entry.elapsed > 86400
            || entry.objectiveTarget < -1 || entry.objectiveTarget > 1000 || entry.deploymentBudget < 0
            || !Float.isFinite(entry.objectiveSeconds) || entry.objectiveSeconds < 0 || entry.objectiveSeconds > 86400
            || !Float.isFinite(entry.objectiveProgress) || entry.objectiveProgress < 0 || entry.objectiveProgress > 86400
            || entry.objectiveType < 0 || entry.objectiveType > 3 || entry.challengeId < -1 || entry.challengeId > 8
            || !entry.seedKnown && entry.seed != 0
            || entry.rulesVersion == 0 && (entry.seedKnown || entry.historyKnown))
            throw new IOException("Invalid playtest event");
        if (entry.mode == MENU) {
            if (entry.sector != -1 || entry.difficulty != -1 || entry.rulesVersion != 0 || entry.aiVersion != 0
                || entry.seedKnown || entry.historyKnown || entry.startingKingLost || entry.elapsed != 0
                || entry.objectiveType != 0 || entry.challengeId != -1 || !entry.dailyDate.isEmpty()
                || entry.objectiveTarget != -1 || entry.objectiveSeconds != 0 || entry.objectiveProgress != 0
                || entry.deploymentBudget != 0
                || entry.outcome != -1) throw new IOException("Invalid menu event");
        } else {
            if (entry.sector < 0 || entry.sector >= 60 || entry.difficulty < 0 || entry.difficulty > 2
                || entry.outcome < GameModel.PLAYING || entry.outcome > GameModel.LOST
                || entry.mode == CAMPAIGN && (entry.objectiveType != 0 || !entry.dailyDate.isEmpty())
                || entry.mode == CHALLENGE && (entry.objectiveType == 0 || entry.challengeId < 0 || !entry.dailyDate.isEmpty())
                || entry.mode == DAILY && (entry.objectiveType == 0 || entry.dailyDate.isEmpty()))
                throw new IOException("Invalid playtest context");
        }
    }
}
