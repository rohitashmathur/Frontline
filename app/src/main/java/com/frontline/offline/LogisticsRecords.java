package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/** Independent logistics history; no preferences, campaign progress, missions, or runs are modified. */
public final class LogisticsRecords {
    public static final int FORMAT_VERSION = 1, MAX_RECORDS = 4096;
    public static final int MAX_SAVE_BYTES = 16 + MAX_RECORDS * 21;
    private static final int MAGIC = 0x464C5231, HEADER_BYTES = 16, ENTRY_BYTES = 21;
    private static final float MAX_SECONDS = 86400;
    private final TreeMap<Key, Record> records = new TreeMap<>();

    /** Immutable facts for one exact map/difficulty/rules/configuration key. */
    public static final class Record {
        public final int mapId, difficulty, rulesVersion, configVersion;
        public final boolean completed;
        public final float bestElapsed;

        private Record(Key key, boolean completed, float bestElapsed) {
            mapId = key.mapId; difficulty = key.difficulty;
            rulesVersion = key.rulesVersion; configVersion = key.configVersion;
            this.completed = completed; this.bestElapsed = bestElapsed;
        }
    }

    /** Missing keys return completed=false, bestElapsed=0; no fallback to another version or difficulty. */
    public Record get(int mapId, int difficulty, int rulesVersion, int configVersion) {
        if (!validKey(mapId, difficulty, rulesVersion, configVersion))
            throw new IllegalArgumentException("Logistics record key");
        Key key = new Key(mapId, difficulty, rulesVersion, configVersion);
        Record existing = records.get(key);
        return existing == null ? new Record(key, false, 0) : existing;
    }

    /** Records under the attempt's actual configuration, never the current factory default. */
    public boolean record(GameModel model) {
        return model != null && record(model, model.logisticsConfigVersion);
    }

    /**
     * Returns true only for a new completion or faster win. A full table refuses new keys without eviction.
     * A supplied configuration must match the positive configuration stored on the attempt.
     */
    public boolean record(GameModel model, int configVersion) {
        if (!eligible(model) || configVersion <= 0 || configVersion != model.logisticsConfigVersion) return false;
        int mapId = Logistics.getIndex(model);
        if (!validKey(mapId, model.difficulty, model.rulesVersion, configVersion)) return false;
        Key key = new Key(mapId, model.difficulty, model.rulesVersion, configVersion);
        Record existing = records.get(key);
        float elapsed = model.elapsed == 0 ? 0 : model.elapsed;
        if (existing != null && elapsed >= existing.bestElapsed) return false;
        if (existing == null && records.size() >= MAX_RECORDS) return false;
        records.put(key, new Record(key, true, elapsed));
        return true;
    }

    public int size() { return records.size(); }

    public byte[] save() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + records.size() * ENTRY_BYTES);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeInt(FORMAT_VERSION); out.writeInt(records.size());
            for (Map.Entry<Key, Record> item : records.entrySet()) {
                Key key = item.getKey();
                out.writeInt(key.mapId); out.writeInt(key.difficulty);
                out.writeInt(key.rulesVersion); out.writeInt(key.configVersion);
                out.writeBoolean(item.getValue().completed); out.writeFloat(item.getValue().bestElapsed);
            }
            out.flush();
            CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
            out.writeInt((int) crc.getValue()); out.flush();
            return bytes.toByteArray();
        } catch (IOException invalid) { throw new IllegalStateException("Cannot save logistics records", invalid); }
    }

    /**
     * All-or-nothing restore. On IOException the parent must retain/isolate the original preference bytes;
     * this method never substitutes empty history or touches another save. Positive rules/config IDs and
     * retired map IDs remain opaque exact namespaces, not evidence that their gameplay is supported.
     */
    public static LogisticsRecords restore(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < HEADER_BYTES || bytes.length > MAX_SAVE_BYTES)
            throw new IOException("Invalid logistics records size");
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        DataInputStream checksum = new DataInputStream(new ByteArrayInputStream(bytes, bytes.length - 4, 4));
        if (checksum.readInt() != (int) crc.getValue()) throw new IOException("Invalid logistics records checksum");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION)
            throw new IOException("Unsupported logistics records format");
        int count = in.readInt();
        if (count < 0 || count > MAX_RECORDS || bytes.length != HEADER_BYTES + count * ENTRY_BYTES)
            throw new IOException("Invalid logistics records count");
        LogisticsRecords result = new LogisticsRecords();
        Key previous = null;
        for (int i = 0; i < count; i++) {
            int mapId = in.readInt(), difficulty = in.readInt(), rules = in.readInt(), config = in.readInt();
            int completed = in.readUnsignedByte();
            float elapsed = in.readFloat();
            if (!validKey(mapId, difficulty, rules, config) || completed != 1 || !validSeconds(elapsed)
                || Float.floatToRawIntBits(elapsed) == 0x80000000)
                throw new IOException("Invalid logistics record");
            Key key = new Key(mapId, difficulty, rules, config);
            // Canonical key order rejects both duplicates and ambiguous serialized ordering.
            if (previous != null && previous.compareTo(key) >= 0)
                throw new IOException("Unordered or duplicate logistics record");
            result.records.put(key, new Record(key, true, elapsed));
            previous = key;
        }
        if (in.available() != 0) throw new IOException("Unexpected logistics records data");
        return result;
    }

    private static boolean eligible(GameModel model) {
        return model != null && model.battleMode == GameModel.MODE_LOGISTICS && model.outcome == GameModel.WON
            && model.objectiveType == Challenge.CAMPAIGN && model.objectiveTarget == -1
            && model.objectiveSeconds == 0 && model.objectiveProgress == 0 && model.deploymentBudget == 0
            && model.challengeId == -1 && model.missionConfigVersion == 0 && model.missionPressure == 0
            && model.dailyDate != null && model.dailyDate.isEmpty() && model.dailyVersion != null && model.dailyVersion.isEmpty()
            && model.runId != null && model.runId.isEmpty() && model.runNode == -1 && model.runPerks == 0
            && (model.terminalReason == GameModel.TERMINAL_NONE || model.terminalReason == GameModel.TERMINAL_VICTORY)
            && validSeconds(model.elapsed);
    }

    private static boolean validKey(int mapId, int difficulty, int rules, int config) {
        return mapId >= 0 && difficulty >= 0 && difficulty < 3 && rules > 0 && config > 0;
    }

    private static boolean validSeconds(float seconds) {
        return Float.isFinite(seconds) && seconds >= 0 && seconds <= MAX_SECONDS;
    }

    private static final class Key implements Comparable<Key> {
        final int mapId, difficulty, rulesVersion, configVersion;

        Key(int mapId, int difficulty, int rulesVersion, int configVersion) {
            this.mapId = mapId; this.difficulty = difficulty;
            this.rulesVersion = rulesVersion; this.configVersion = configVersion;
        }

        @Override public int compareTo(Key other) {
            int result = Integer.compare(mapId, other.mapId);
            if (result == 0) result = Integer.compare(difficulty, other.difficulty);
            if (result == 0) result = Integer.compare(rulesVersion, other.rulesVersion);
            if (result == 0) result = Integer.compare(configVersion, other.configVersion);
            return result;
        }
    }
}
