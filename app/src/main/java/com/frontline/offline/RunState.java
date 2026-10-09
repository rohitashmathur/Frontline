package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Immutable, independent Run Mode save. Nodes are zero-based; perk values are bits.
 * Serialize callbacks and check the source revision before committing. Publish a
 * returned transition only after saving it and the single battle slot in one
 * storage transaction. A missing/mismatched battle must be continued from a
 * verified save or explicitly abandoned, never reconstructed after play began.
 */
public final class RunState {
    public static final int BATTLE_COUNT = 5, CONTENT_VERSION = 1, PERK_VERSION = 1;
    public static final int ORDINARY_PRODUCTION = 1, KING_PRODUCTION = 2,
        CONVOY_SPEED = 4, KING_CAP = 8, STARTING_KING = 16, ALL_PERKS = 31;
    public static final int ORDINARY_PERCENT = 8, KING_PERCENT = 15,
        SPEED_PERCENT = 12, KING_CAP_BONUS = 15, STARTING_KING_BONUS = 10;
    public static final int READY = 0, BATTLE = 1, COUNCIL = 2, RETRY_AVAILABLE = 3,
        COMPLETED = 4, DEFEATED = 5, ABANDONED = 6;
    private static final int MAGIC = 0x46523131, FORMAT = 1, MAX_BYTES = 16384;
    private static final int[] MAPS = {0, 2, 8, 24, 36};
    private static final int[] DIFFICULTIES = {0, 1, 1, 2, 2};

    public final String id;
    public final long seed, revision;
    public final int rulesVersion, contentVersion, perkVersion, aiVersion;
    public final int status, node, perks, retriesUsed;
    public final long councilNonce;
    public final BattleAssociation activeBattle;
    private final Node[] nodes;
    private final int[] offer, choices;
    private final NodeOutcome[] outcomes;

    /** A frozen configuration, including the map's topology and spawn definition. */
    public static final class Node {
        public final int index, mapIndex, difficulty, opponents;
        public final long seed;
        public final String name;
        public final int columns, rows, parSeconds, playerTroops, rivalTroops, neutralMinimum;
        private final int[] holes, startingCells, factions;

        private Node(int index, long seed, int mapIndex, int difficulty, GameModel.Level level) {
            this(index, seed, mapIndex, difficulty, level.name, level.columns, level.rows,
                level.opponents, level.parSeconds, level.playerTroops, level.rivalTroops,
                level.neutralMinimum, level.holes, level.startingCells, level.factions);
        }

        private Node(int index, long seed, int mapIndex, int difficulty, String name,
                     int columns, int rows, int opponents, int parSeconds, int playerTroops,
                     int rivalTroops, int neutralMinimum, int[] holes, int[] startingCells,
                     int[] factions) {
            this.index = index; this.seed = seed; this.mapIndex = mapIndex;
            this.difficulty = difficulty; this.name = name; this.columns = columns;
            this.rows = rows; this.opponents = opponents; this.parSeconds = parSeconds;
            this.playerTroops = playerTroops; this.rivalTroops = rivalTroops;
            this.neutralMinimum = neutralMinimum;
            this.holes = copy(holes); this.startingCells = copy(startingCells);
            this.factions = copy(factions);
        }

        public int[] holes() { return copy(holes); }
        public int[] startingCells() { return copy(startingCells); }
        public int[] factions() { return copy(factions); }

        public GameModel.Level frozenLevel() {
            return new GameModel.Level(name, columns, rows, copy(holes), parSeconds,
                copy(startingCells), copy(factions), playerTroops, rivalTroops, neutralMinimum);
        }

        private boolean matches(GameModel.Level level) {
            return name.equals(level.name) && columns == level.columns && rows == level.rows
                && opponents == level.opponents && parSeconds == level.parSeconds
                && playerTroops == level.playerTroops && rivalTroops == level.rivalTroops
                && neutralMinimum == level.neutralMinimum && Arrays.equals(holes, level.holes)
                && Arrays.equals(startingCells, level.startingCells)
                && Arrays.equals(factions, level.factions);
        }
    }

    /** model.runId stores this association, not just the stable RunState.id. */
    public static final class BattleAssociation {
        public final String runId, association;
        public final int node, attempt, perks;
        public final long nonce, seed;

        private BattleAssociation(String runId, int node, int attempt, int perks,
                                  long nonce, long seed) {
            this.runId = runId; this.node = node; this.attempt = attempt;
            this.perks = perks; this.nonce = nonce; this.seed = seed;
            association = runId + "/" + nonce;
        }
    }

    public static final class NodeOutcome {
        public final BattleAssociation battle;
        public final int outcome, terminalReason, captures, unitsSent, unitsLost, intercepted, cappedReinforcements;
        public final float elapsed;
        public final boolean startingKingLost;

        private NodeOutcome(BattleAssociation battle, GameModel model) {
            this(battle, model.outcome, model.terminalReason, model.elapsed, model.captures, model.unitsSent,
                model.unitsLost, model.intercepted, model.cappedReinforcements,
                model.startingKingLost);
        }

        private NodeOutcome(BattleAssociation battle, int outcome, int terminalReason, float elapsed, int captures,
                            int unitsSent, int unitsLost, int intercepted,
                            int cappedReinforcements, boolean startingKingLost) {
            this.battle = battle; this.outcome = outcome; this.terminalReason = terminalReason; this.elapsed = elapsed;
            this.captures = captures; this.unitsSent = unitsSent; this.unitsLost = unitsLost;
            this.intercepted = intercepted; this.cappedReinforcements = cappedReinforcements;
            this.startingKingLost = startingKingLost;
        }

        private boolean matches(GameModel model) {
            return outcome == model.outcome && terminalReason == model.terminalReason
                && Float.floatToIntBits(elapsed) == Float.floatToIntBits(model.elapsed)
                && captures == model.captures && unitsSent == model.unitsSent
                && unitsLost == model.unitsLost && intercepted == model.intercepted
                && cappedReinforcements == model.cappedReinforcements
                && startingKingLost == model.startingKingLost;
        }
    }

    /** Factual statistics only; no run score, stars, campaign rewards or records. */
    public static final class Summary {
        public final int status, battlesCleared, battlesPlayed, retriesUsed, perks, terminalReason;
        public final double elapsed;
        public final long captures, unitsSent, unitsLost, intercepted, cappedReinforcements;
        public final boolean completed, abandoned;

        private Summary(RunState run) {
            status = run.status; battlesCleared = run.battlesCleared();
            battlesPlayed = run.outcomes.length; retriesUsed = run.retriesUsed; perks = run.perks;
            completed = status == COMPLETED; abandoned = status == ABANDONED;
            terminalReason = run.outcomes.length == 0 ? GameModel.TERMINAL_NONE
                : run.outcomes[run.outcomes.length - 1].terminalReason;
            double seconds = 0;
            long captured = 0, sent = 0, lost = 0, interceptedUnits = 0, capped = 0;
            for (NodeOutcome result : run.outcomes) {
                seconds += result.elapsed; captured += result.captures; sent += result.unitsSent;
                lost += result.unitsLost; interceptedUnits += result.intercepted;
                capped += result.cappedReinforcements;
            }
            elapsed = seconds; captures = captured; unitsSent = sent; unitsLost = lost;
            intercepted = interceptedUnits; cappedReinforcements = capped;
        }
    }

    private RunState(String id, long seed, long revision, int rulesVersion, int contentVersion,
                     int perkVersion, int aiVersion, int status, int node, int perks,
                     int retriesUsed, long councilNonce, BattleAssociation activeBattle,
                     Node[] nodes, int[] offer, int[] choices, NodeOutcome[] outcomes) {
        this.id = id; this.seed = seed; this.revision = revision;
        this.rulesVersion = rulesVersion; this.contentVersion = contentVersion;
        this.perkVersion = perkVersion; this.aiVersion = aiVersion; this.status = status;
        this.node = node; this.perks = perks; this.retriesUsed = retriesUsed;
        this.councilNonce = councilNonce; this.activeBattle = activeBattle;
        this.nodes = nodes.clone(); this.offer = offer.clone(); this.choices = choices.clone();
        this.outcomes = outcomes.clone();
    }

    public static RunState newRun(long seed) {
        Node[] nodes = new Node[BATTLE_COUNT];
        for (int i = 0; i < nodes.length; i++) {
            int map = MAPS[i];
            nodes[i] = new Node(i, nodeSeed(seed, i), map, DIFFICULTIES[i], GameModel.LEVELS[map]);
        }
        return new RunState(UUID.randomUUID().toString(), seed, 0, GameModel.RULES_VERSION,
            CONTENT_VERSION, PERK_VERSION, 1, READY, 0, 0, 0, 0, null, nodes,
            new int[0], new int[0], new NodeOutcome[0]);
    }

    public Node currentNode() { return nodes[node]; }
    public Node[] nodes() { return nodes.clone(); }
    public int[] councilOffer() { return offer.clone(); }
    public int[] chosenPerks() { return choices.clone(); }
    public NodeOutcome[] outcomes() { return outcomes.clone(); }
    public boolean terminal() { return status == COMPLETED || status == DEFEATED || status == ABANDONED; }
    public boolean hasPerk(int perk) { return singlePerk(perk) && (perks & perk) != 0; }
    public int retriesRemaining() { return 1 - retriesUsed; }
    public Summary summary() { return new Summary(this); }

    public int battlesCleared() {
        int count = 0;
        for (NodeOutcome result : outcomes) if (result.outcome == GameModel.WON) count++;
        return count;
    }

    /** Repeated start taps return the same nonce, never a second battle. */
    public RunState beginBattle() {
        if (status == BATTLE) return this;
        require(status == READY, "Run is not ready for a battle");
        BattleAssociation battle = new BattleAssociation(id, node, attemptAt(node), perks,
            outcomes.length + 1L, currentNode().seed);
        return next(BATTLE, node, perks, retriesUsed, 0, battle, new int[0], choices, outcomes);
    }

    /** Only for the initial start transaction, not for recreating a missing active save. */
    public GameModel createBattle() {
        require(status == BATTLE && activeBattle != null, "No active run battle");
        requireCompatibleContent();
        Node selected = currentNode();
        GameModel model = new GameModel(selected.mapIndex, selected.difficulty, selected.seed,
            selected.frozenLevel());
        model.aiVersion = aiVersion;
        model.configureRun(perks, activeBattle.association, node);
        return model;
    }

    public boolean matchesActiveBattle(GameModel model) {
        return status == BATTLE && activeBattle != null && matchesAssociation(activeBattle, model);
    }

    /** Includes completed results, so replaying a committed result is a true no-op. */
    public RunState finishBattle(GameModel model) {
        require(model != null, "Missing battle result");
        for (NodeOutcome result : outcomes) {
            if (result.battle.association.equals(model.runId)) {
                require(matchesAssociation(result.battle, model) && result.matches(model),
                    "Conflicting replayed battle result");
                return this;
            }
        }
        require(matchesActiveBattle(model), "Battle does not belong to the active run node");
        require(model.outcome == GameModel.WON || model.outcome == GameModel.LOST,
            "Run battle is not finished");
        NodeOutcome result = new NodeOutcome(activeBattle, model);
        try { validateOutcome(result); } catch (IOException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
        NodeOutcome[] updated = Arrays.copyOf(outcomes, outcomes.length + 1);
        updated[outcomes.length] = result;
        if (model.outcome == GameModel.LOST)
            return next(retriesUsed == 0 ? RETRY_AVAILABLE : DEFEATED, node, perks,
                retriesUsed, 0, null, new int[0], choices, updated);
        if (node == BATTLE_COUNT - 1)
            return next(COMPLETED, node, perks, retriesUsed, 0, null, new int[0], choices, updated);
        return next(COUNCIL, node, perks, retriesUsed, activeBattle.nonce, null,
            makeOffer(seed, node, perks), choices, updated);
    }

    /** Use the councilNonce captured with the visible offer, not a fresh UI lookup. */
    public RunState choosePerk(long expectedCouncilNonce, int perk) {
        require(singlePerk(perk), "Unknown perk");
        int council = councilIndex(expectedCouncilNonce);
        if (council >= 0 && council < choices.length) {
            require(choices[council] == perk, "Council already chose a different perk");
            return this;
        }
        require(status == COUNCIL && expectedCouncilNonce == councilNonce && council >= 0
            && !hasPerk(perk) && contains(offer, perk), "Invalid or stale council choice");
        int[] selected = Arrays.copyOf(choices, choices.length + 1);
        selected[choices.length] = perk;
        return next(READY, node + 1, perks | perk, retriesUsed, 0, null,
            new int[0], selected, outcomes);
    }

    /** Atomically consumes the sole token and associates the same node/seed/build. */
    public RunState retry(long lostBattleNonce) {
        NodeOutcome loss = findOutcome(lostBattleNonce);
        require(loss != null && loss.outcome == GameModel.LOST, "Invalid retry proof");
        if (retriesUsed == 1 && loss.battle.attempt == 0) return this;
        require(status == RETRY_AVAILABLE && retriesUsed == 0 && loss == outcomes[outcomes.length - 1],
            "Run cannot retry this battle");
        BattleAssociation battle = new BattleAssociation(id, node, 1, perks,
            outcomes.length + 1L, currentNode().seed);
        return next(BATTLE, node, perks, 1, 0, battle, new int[0], choices, outcomes);
    }

    /** Explicit user confirmation is required; declining preserves the exact snapshot. */
    public RunState abandon(boolean confirmed, long expectedRevision) {
        if (!confirmed || status == ABANDONED) return this;
        require(expectedRevision == revision && !terminal(), "Stale abandonment confirmation");
        return next(ABANDONED, node, perks, retriesUsed, 0, null, new int[0], choices, outcomes);
    }

    private RunState next(int status, int node, int perks, int retriesUsed, long councilNonce,
                          BattleAssociation battle, int[] offer, int[] choices, NodeOutcome[] outcomes) {
        return new RunState(id, seed, revision + 1, rulesVersion, contentVersion, perkVersion,
            aiVersion, status, node, perks, retriesUsed, councilNonce, battle,
            nodes, offer, choices, outcomes);
    }

    private boolean matchesAssociation(BattleAssociation battle, GameModel model) {
        Node selected = nodes[battle.node];
        return model != null && model.battleMode == 1 && battle.association.equals(model.runId)
            && model.runNode == battle.node && model.runPerks == battle.perks
            && model.levelIndex == selected.mapIndex && model.difficulty == selected.difficulty
            && model.seedKnown && model.historyKnown && model.seed == battle.seed
            && model.rulesVersion == rulesVersion && model.aiVersion == aiVersion
            && model.objectiveType == 0 && model.challengeId == -1
            && model.objectiveTarget == -1 && model.objectiveSeconds == 0
            && model.objectiveProgress == 0 && model.deploymentBudget == 0
            && model.missionPressure == 0 && model.missionConfigVersion == 0
            && "".equals(model.dailyDate) && "".equals(model.dailyVersion)
            && selected.matches(model.level());
    }

    private void requireCompatibleContent() {
        require(rulesVersion == GameModel.RULES_VERSION && contentVersion == CONTENT_VERSION
            && perkVersion == PERK_VERSION, "Saved run requires its original rules/content/perks");
    }

    private int attemptAt(int selectedNode) {
        for (NodeOutcome result : outcomes)
            if (result.battle.node == selectedNode && result.outcome == GameModel.LOST) return 1;
        return 0;
    }

    private int councilIndex(long nonce) {
        for (NodeOutcome result : outcomes)
            if (result.battle.nonce == nonce && result.outcome == GameModel.WON
                && result.battle.node < BATTLE_COUNT - 1) return result.battle.node;
        return -1;
    }

    private NodeOutcome findOutcome(long nonce) {
        for (NodeOutcome result : outcomes) if (result.battle.nonce == nonce) return result;
        return null;
    }

    private static int[] makeOffer(long seed, int node, int perks) {
        int[] eligible = new int[5 - Integer.bitCount(perks)];
        int count = 0;
        for (int bit = 1; bit <= STARTING_KING; bit <<= 1)
            if ((perks & bit) == 0) eligible[count++] = bit;
        Random random = new Random(mix(seed ^ 0x434F554E43494CL ^ node * 0x9E3779B97F4A7C15L ^ perks));
        for (int i = eligible.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1), swap = eligible[i];
            eligible[i] = eligible[j]; eligible[j] = swap;
        }
        return Arrays.copyOf(eligible, Math.min(3, eligible.length));
    }

    private static long nodeSeed(long seed, int node) { return mix(seed + (node + 1L) * 0x9E3779B97F4A7C15L); }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
    private static int[] copy(int[] value) { return value == null ? null : value.clone(); }
    private static boolean contains(int[] values, int value) {
        for (int candidate : values) if (candidate == value) return true;
        return false;
    }
    private static boolean singlePerk(int perk) { return perk > 0 && perk <= STARTING_KING && (perk & (perk - 1)) == 0; }
    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }

    public byte[] save() {
        try {
            validate();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeUTF(id);
            out.writeLong(seed); out.writeLong(revision);
            out.writeInt(rulesVersion); out.writeInt(contentVersion); out.writeInt(perkVersion);
            out.writeInt(aiVersion);
            out.writeInt(ORDINARY_PERCENT); out.writeInt(KING_PERCENT); out.writeInt(SPEED_PERCENT);
            out.writeInt(KING_CAP_BONUS); out.writeInt(STARTING_KING_BONUS);
            out.writeInt(status); out.writeInt(node); out.writeInt(perks); out.writeInt(retriesUsed);
            out.writeLong(councilNonce);
            out.writeInt(nodes.length);
            for (Node selected : nodes) writeNode(out, selected);
            writeInts(out, offer); writeInts(out, choices);
            out.writeBoolean(activeBattle != null);
            if (activeBattle != null) writeBattle(out, activeBattle);
            out.writeInt(outcomes.length);
            for (NodeOutcome result : outcomes) {
                writeBattle(out, result.battle); out.writeInt(result.outcome);
                out.writeInt(result.terminalReason);
                out.writeFloat(result.elapsed); out.writeInt(result.captures);
                out.writeInt(result.unitsSent); out.writeInt(result.unitsLost);
                out.writeInt(result.intercepted); out.writeInt(result.cappedReinforcements);
                out.writeBoolean(result.startingKingLost);
            }
            out.flush();
            CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
            out.writeInt((int) crc.getValue()); out.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_BYTES) throw new IOException("Run save too large");
            return result;
        } catch (IOException invalid) { throw new IllegalStateException("Invalid run state", invalid); }
    }

    /** Does not touch Progress, preferences, or any other save on failure. */
    public static RunState restore(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 16 || bytes.length > MAX_BYTES)
            throw new IOException("Invalid run save size");
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        DataInputStream checksum = new DataInputStream(new ByteArrayInputStream(bytes, bytes.length - 4, 4));
        if (checksum.readInt() != (int) crc.getValue()) throw new IOException("Invalid run checksum");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        if (in.readInt() != MAGIC || in.readInt() != FORMAT) throw new IOException("Unknown run save format");
        String id = in.readUTF();
        long seed = in.readLong(), revision = in.readLong();
        int rules = in.readInt(), content = in.readInt(), perkVersion = in.readInt(), ai = in.readInt();
        if (in.readInt() != ORDINARY_PERCENT || in.readInt() != KING_PERCENT
            || in.readInt() != SPEED_PERCENT || in.readInt() != KING_CAP_BONUS
            || in.readInt() != STARTING_KING_BONUS) throw new IOException("Unknown frozen perk configuration");
        int status = in.readInt(), node = in.readInt(), perks = in.readInt(), retries = in.readInt();
        long councilNonce = in.readLong();
        if (in.readInt() != BATTLE_COUNT) throw new IOException("Run must contain exactly five nodes");
        Node[] nodes = new Node[BATTLE_COUNT];
        for (int i = 0; i < nodes.length; i++) nodes[i] = readNode(in);
        int[] offer = readInts(in, 3, false), choices = readInts(in, 4, false);
        BattleAssociation battle = readFlag(in) ? readBattle(in, id) : null;
        int count = in.readInt();
        if (count < 0 || count > BATTLE_COUNT + 1) throw new IOException("Invalid run outcome count");
        NodeOutcome[] outcomes = new NodeOutcome[count];
        for (int i = 0; i < count; i++) {
            BattleAssociation associated = readBattle(in, id);
            outcomes[i] = new NodeOutcome(associated, in.readInt(), in.readInt(), in.readFloat(), in.readInt(),
                in.readInt(), in.readInt(), in.readInt(), in.readInt(), readFlag(in));
        }
        if (in.available() != 0) throw new IOException("Unexpected run save data");
        RunState result = new RunState(id, seed, revision, rules, content, perkVersion, ai,
            status, node, perks, retries, councilNonce, battle, nodes, offer, choices, outcomes);
        result.validate();
        return result;
    }

    private void validate() throws IOException {
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid run identity", invalid); }
        if (revision < 0 || revision > 21 || rulesVersion <= 0 || contentVersion != CONTENT_VERSION
            || perkVersion != PERK_VERSION || aiVersion != 1 || status < READY || status > ABANDONED
            || node < 0 || node >= BATTLE_COUNT || perks < 0 || (perks & ~ALL_PERKS) != 0
            || retriesUsed < 0 || retriesUsed > 1 || nodes.length != BATTLE_COUNT)
            throw new IOException("Invalid run configuration");
        for (int i = 0; i < nodes.length; i++) validateNode(nodes[i], i);
        int owned = 0;
        for (int selected : choices) {
            if (!singlePerk(selected) || (owned & selected) != 0) throw new IOException("Invalid run perk choices");
            owned |= selected;
        }
        if (choices.length > 4 || owned != perks) throw new IOException("Invalid run perk build");
        int cleared = 0, losses = 0, build = 0, expectedNode = 0, expectedAttempt = 0;
        long expectedRevision = 0;
        if (outcomes.length > BATTLE_COUNT + 1) throw new IOException("Invalid run history size");
        for (int i = 0; i < outcomes.length; i++) {
            NodeOutcome result = outcomes[i]; validateOutcome(result);
            validateBattle(result.battle, expectedNode, expectedAttempt, build, i + 1L);
            if (result.outcome == GameModel.LOST) {
                losses++; expectedAttempt = 1;
                if (losses > 2 || losses == 2 && i != outcomes.length - 1)
                    throw new IOException("History continues after defeat");
            } else {
                cleared++; expectedNode++; expectedAttempt = 0;
                if (result.battle.node < choices.length) build |= choices[result.battle.node];
                else if (i != outcomes.length - 1 || result.battle.node == 4 && choices.length != 4)
                    throw new IOException("History skips council choice");
            }
            expectedRevision += 2;
        }
        if (choices.length > Math.min(cleared, 4) || retriesUsed > losses
            || cleared > choices.length + 1
            || losses > 0 && retriesUsed == 0 && outcomes[outcomes.length - 1].outcome != GameModel.LOST
            || losses == 2 && retriesUsed != 1) throw new IOException("Invalid run retry history");
        expectedRevision += choices.length;
        boolean finalWin = cleared == BATTLE_COUNT;
        boolean pendingCouncil = cleared > choices.length && cleared < BATTLE_COUNT;
        boolean pendingLoss = outcomes.length > 0 && outcomes[outcomes.length - 1].outcome == GameModel.LOST;
        int wantedNode = Math.min(expectedNode, BATTLE_COUNT - 1);
        if (pendingCouncil) wantedNode--;
        if (node != wantedNode) throw new IOException("Invalid run node position");
        if (status == COUNCIL) {
            if (!pendingCouncil || pendingLoss || activeBattle != null
                || councilNonce != outcomes[outcomes.length - 1].battle.nonce
                || offer.length != Math.min(3, 5 - choices.length)) throw new IOException("Invalid council state");
            int offered = 0;
            for (int option : offer) {
                if (!singlePerk(option) || (perks & option) != 0 || (offered & option) != 0)
                    throw new IOException("Invalid council offer");
                offered |= option;
            }
        } else if (offer.length != 0 || councilNonce != 0) throw new IOException("Council data outside council");
        if (status == BATTLE) {
            if (activeBattle == null || finalWin || pendingCouncil || losses == 2
                || pendingLoss && retriesUsed == 0) throw new IOException("Invalid active run battle");
            validateBattle(activeBattle, node, expectedAttempt, perks, outcomes.length + 1L);
            expectedRevision++;
        } else if (activeBattle != null) throw new IOException("Orphaned run battle");
        if (status == READY && (pendingCouncil || pendingLoss || finalWin)
            || status == RETRY_AVAILABLE && (!pendingLoss || retriesUsed != 0 || losses != 1)
            || status == DEFEATED && (!pendingLoss || losses != 2)
            || status == COMPLETED && !finalWin || status != COMPLETED && finalWin
            || status == ABANDONED && losses == 2)
            throw new IOException("Invalid run lifecycle state");
        // Abandonment may follow READY, BATTLE, COUNCIL, or RETRY_AVAILABLE.
        if (status == ABANDONED) {
            long minimum = expectedRevision + 1, maximum = expectedRevision + 2;
            if (pendingCouncil || pendingLoss && retriesUsed == 0) maximum = minimum;
            if (pendingLoss && retriesUsed == 1) minimum = maximum;
            if (revision < minimum || revision > maximum)
                throw new IOException("Invalid abandonment revision");
        } else if (revision != expectedRevision) throw new IOException("Invalid transition revision");
    }

    private void validateNode(Node selected, int index) throws IOException {
        if (selected.index != index || selected.mapIndex != MAPS[index]
            || selected.difficulty != DIFFICULTIES[index] || selected.seed != nodeSeed(seed, index)
            || selected.name == null || selected.name.isEmpty() || selected.name.length() > 80
            || selected.columns < 2 || selected.columns > 12 || selected.rows < 2 || selected.rows > 12
            || selected.opponents != new int[] {1, 2, 2, 3, 5}[index]
            || selected.parSeconds < 1 || selected.parSeconds > 86400
            || selected.playerTroops < 2 || selected.playerTroops > 125
            || selected.rivalTroops < 2 || selected.rivalTroops > 125
            || selected.neutralMinimum < 0 || selected.neutralMinimum > 92
            || selected.holes == null || selected.factions == null
            || selected.factions.length != selected.opponents)
            throw new IOException("Invalid frozen node configuration");
        int cells = selected.columns * selected.rows;
        validateCells(selected.holes, cells, null);
        if (selected.holes.length >= cells - selected.opponents)
            throw new IOException("Invalid map capacity");
        if (selected.startingCells != null) {
            if (selected.startingCells.length != selected.opponents + 1)
                throw new IOException("Invalid map spawns");
            validateCells(selected.startingCells, cells, selected.holes);
        } else if (selected.opponents > 3 || selected.playerTroops != 32
            || selected.rivalTroops != 32 || selected.neutralMinimum != 7) {
            throw new IOException("Invalid frozen Classic spawns");
        }
        for (int faction : selected.factions)
            if (faction < 1 || faction > 5) throw new IOException("Invalid node faction");
    }

    private static void validateCells(int[] positions, int cells, int[] holes) throws IOException {
        boolean[] used = new boolean[cells];
        for (int position : positions) {
            if (position < 0 || position >= cells || used[position]
                || holes != null && contains(holes, position)) throw new IOException("Invalid map cell");
            used[position] = true;
        }
    }

    private void validateBattle(BattleAssociation battle, int node, int attempt, int perks, long nonce)
            throws IOException {
        if (!id.equals(battle.runId) || battle.node != node || battle.attempt != attempt
            || battle.perks != perks || battle.nonce != nonce || node < 0 || node >= BATTLE_COUNT
            || battle.seed != nodes[node].seed) throw new IOException("Invalid battle association");
    }

    private static void validateOutcome(NodeOutcome result) throws IOException {
        if (result.outcome != GameModel.WON && result.outcome != GameModel.LOST
            || result.outcome == GameModel.WON && result.terminalReason != GameModel.TERMINAL_VICTORY
            || result.outcome == GameModel.LOST && result.terminalReason != GameModel.TERMINAL_ELIMINATED
                && result.terminalReason != GameModel.TERMINAL_SURRENDER
            || !Float.isFinite(result.elapsed) || result.elapsed < 0 || result.elapsed > 86400
            || result.captures < 0 || result.unitsSent < 0 || result.unitsLost < 0
            || result.intercepted < 0 || result.intercepted > result.unitsLost
            || result.cappedReinforcements < 0) throw new IOException("Invalid run result statistics");
    }

    private static void writeNode(DataOutputStream out, Node node) throws IOException {
        out.writeInt(node.index); out.writeLong(node.seed); out.writeInt(node.mapIndex);
        out.writeInt(node.difficulty); out.writeUTF(node.name);
        out.writeInt(node.columns); out.writeInt(node.rows); out.writeInt(node.opponents);
        out.writeInt(node.parSeconds); out.writeInt(node.playerTroops); out.writeInt(node.rivalTroops);
        out.writeInt(node.neutralMinimum);
        writeInts(out, node.holes); writeInts(out, node.startingCells); writeInts(out, node.factions);
    }

    private static Node readNode(DataInputStream in) throws IOException {
        int index = in.readInt(); long seed = in.readLong();
        int map = in.readInt(), difficulty = in.readInt(); String name = in.readUTF();
        int columns = in.readInt(), rows = in.readInt(), opponents = in.readInt(), par = in.readInt();
        int player = in.readInt(), rival = in.readInt(), neutral = in.readInt();
        return new Node(index, seed, map, difficulty, name, columns, rows, opponents, par,
            player, rival, neutral, readInts(in, 256, false), readInts(in, 6, true), readInts(in, 5, false));
    }

    private static void writeBattle(DataOutputStream out, BattleAssociation battle) throws IOException {
        out.writeInt(battle.node); out.writeInt(battle.attempt); out.writeInt(battle.perks);
        out.writeLong(battle.nonce); out.writeLong(battle.seed);
    }

    private static BattleAssociation readBattle(DataInputStream in, String id) throws IOException {
        return new BattleAssociation(id, in.readInt(), in.readInt(), in.readInt(), in.readLong(), in.readLong());
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values == null ? -1 : values.length);
        if (values != null) for (int value : values) out.writeInt(value);
    }

    private static int[] readInts(DataInputStream in, int maximum, boolean nullable) throws IOException {
        int count = in.readInt();
        if (count == -1 && nullable) return null;
        if (count < 0 || count > maximum) throw new IOException("Invalid run array size");
        int[] result = new int[count];
        for (int i = 0; i < result.length; i++) result[i] = in.readInt();
        return result;
    }

    private static boolean readFlag(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid run boolean");
        return value == 1;
    }
}
