package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Standalone Java 8 tests; terminal battle fixtures are not human playtest evidence. */
public final class RunStateTest {
    private static int checks;
    private interface Action { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " RunState checks.");
    }

    public static int run() throws Exception {
        checks = 0;
        configurations(); completeRun(); councilPaths(); retries(); councils(); abandonments();
        associations(); frozenMapsAndStartingPerk(); corruption();
        return checks;
    }

    private static void configurations() throws Exception {
        check(RunState.ORDINARY_PERCENT == 8 && RunState.KING_PERCENT == 15
            && RunState.SPEED_PERCENT == 12 && RunState.KING_CAP_BONUS == 15
            && RunState.STARTING_KING_BONUS == 10, "Exact non-stacking version-one values");
        check((RunState.ORDINARY_PRODUCTION | RunState.KING_PRODUCTION | RunState.CONVOY_SPEED
            | RunState.KING_CAP | RunState.STARTING_KING) == 31, "Exactly five independent bits");
        int[] maps = {0, 2, 8, 24, 36}, opponents = {1, 2, 2, 3, 5}, difficulty = {0, 1, 1, 2, 2};
        for (long seed : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            RunState run = RunState.newRun(seed), sameSeed = RunState.newRun(seed);
            check(!run.id.equals(sameSeed.id), "Same seeds have separate run identities");
            check(run.status == RunState.READY && run.node == 0 && run.perks == 0
                && run.retriesRemaining() == 1 && run.revision == 0, "New run initial state");
            check(run.nodes().length == 5 && run.outcomes().length == 0
                && run.councilOffer().length == 0 && !run.terminal(), "Exactly five curated nodes");
            for (int i = 0; i < maps.length; i++) {
                RunState.Node node = run.nodes()[i];
                check(node.index == i && node.mapIndex == maps[i] && node.opponents == opponents[i]
                    && node.difficulty == difficulty[i], "Visible rising node configuration");
                check(node.seed == sameSeed.nodes()[i].seed, "Node seed is deterministic");
                for (int j = 0; j < i; j++) check(node.seed != run.nodes()[j].seed, "Distinct node seeds");
            }
            roundTrip(run);
        }
        RunState run = RunState.newRun(7);
        RunState.Node original = run.currentNode();
        RunState.Node[] exposed = run.nodes(); exposed[0] = null;
        check(run.currentNode() == original, "Node array cannot mutate the state");
        int[] holes = run.nodes()[1].holes(); holes[0] = 999;
        check(run.nodes()[1].holes()[0] != 999, "Frozen topology is defensively copied");
        reject(() -> run.createBattle(), "Cannot create a battle before association");
    }

    private static void completeRun() throws Exception {
        RunState run = roundTrip(RunState.newRun(881));
        double expectedTime = 0;
        for (int i = 0; i < 5; i++) {
            RunState before = run;
            run = roundTrip(run.beginBattle());
            check(before.status == RunState.READY && before.activeBattle == null,
                "Beginning a battle does not mutate the previous snapshot");
            check(run.beginBattle() == run && run.activeBattle.node == i
                && run.activeBattle.nonce == i + 1 && run.activeBattle.attempt == 0,
                "Duplicate start retains association and nonce");
            GameModel model = restoredBattle(run);
            check(run.matchesActiveBattle(model) && model.runNode == i && model.battleMode == 1
                && model.runPerks == run.perks, "Restored battle retains complete association");
            terminalFixture(model, GameModel.WON, 20 + i);
            expectedTime += model.elapsed;
            byte[] unfinished = run.save();
            RunState finished = roundTrip(run.finishBattle(model));
            check(Arrays.equals(unfinished, run.save()), "Result transition is atomic and immutable");
            check(finished.finishBattle(GameModel.restore(model.save())) == finished,
                "Replayed restored result is idempotent");
            check(finished.battlesCleared() == i + 1 && finished.outcomes().length == i + 1,
                "Exactly one result per battle");
            run = finished;
            if (i < 4) {
                check(run.status == RunState.COUNCIL && run.node == i
                    && run.councilNonce == i + 1, "Win exposes council tied to result nonce");
                int[] offer = run.councilOffer();
                check(offer.length == (i == 3 ? 2 : 3), "Final council has two actual remaining perks");
                int offered = 0;
                for (int bit : offer) {
                    check(!run.hasPerk(bit) && (offered & bit) == 0, "Distinct unowned offers");
                    offered |= bit;
                }
                long nonce = run.councilNonce;
                run = roundTrip(run.choosePerk(nonce, offer[0]));
                check(run.choosePerk(nonce, offer[0]) == run, "Duplicate selection cannot stack rewards");
                check(run.status == RunState.READY && run.node == i + 1
                    && Integer.bitCount(run.perks) == i + 1, "Council advances exactly one node");
                int[] exposed = run.chosenPerks(); exposed[0] = 0;
                check(run.chosenPerks()[0] != 0, "Chosen perks are immutable");
            }
        }
        RunState.Summary summary = run.summary();
        check(run.status == RunState.COMPLETED && run.terminal() && run.councilOffer().length == 0
            && run.activeBattle == null && run.chosenPerks().length == 4, "Fifth win ends without council");
        check(summary.completed && !summary.abandoned && summary.battlesCleared == 5
            && summary.battlesPlayed == 5 && summary.retriesUsed == 0
            && summary.elapsed == expectedTime && summary.captures == 10
            && summary.unitsSent == 50 && summary.unitsLost == 15
            && summary.intercepted == 5 && summary.cappedReinforcements == 10,
            "Summary has factual totals, no farmable score or stars");
        RunState done = run;
        reject(() -> done.beginBattle(), "Completed run cannot restart");
        reject(() -> done.abandon(true, done.revision), "Completion cannot become abandonment");
    }

    private static void retries() throws Exception {
        for (int lossNode = 0; lossNode < 5; lossNode++) {
            RunState run = RunState.newRun(701 + lossNode);
            for (int i = 0; i < 5; i++) {
                run = roundTrip(run.beginBattle());
                GameModel model = restoredBattle(run);
                if (i == lossNode) {
                    terminalFixture(model, GameModel.LOST, 13);
                    run = roundTrip(run.finishBattle(model));
                    check(run.status == RunState.RETRY_AVAILABLE && run.retriesUsed == 0,
                        "First loss offers retry without consuming it");
                    check(run.finishBattle(model) == run, "Repeated loss cannot consume a retry");
                    long nonce = run.outcomes()[run.outcomes().length - 1].battle.nonce;
                    RunState retry = roundTrip(run.retry(nonce));
                    check(retry.status == RunState.BATTLE && retry.retriesUsed == 1
                        && retry.retriesRemaining() == 0 && retry.retry(nonce) == retry,
                        "Retry token and new association commit once");
                    GameModel replay = restoredBattle(retry);
                    check(replay.seed == model.seed && replay.levelIndex == model.levelIndex
                        && replay.difficulty == model.difficulty && replay.runPerks == model.runPerks
                        && !replay.runId.equals(model.runId), "Retry preserves seed/map/build with a fresh nonce");
                    check(!retry.matchesActiveBattle(model) && retry.finishBattle(model) == retry,
                        "Original loss cannot defeat the retry");
                    run = retry; model = replay;
                }
                terminalFixture(model, GameModel.WON, 20);
                run = roundTrip(run.finishBattle(model));
                if (i < 4) run = roundTrip(run.choosePerk(run.councilNonce, run.councilOffer()[0]));
            }
            check(run.summary().completed && run.summary().battlesPlayed == 6
                && run.summary().battlesCleared == 5 && run.summary().retriesUsed == 1,
                "Run completes after a same-node retry at any of its five nodes");
        }
        RunState run = RunState.newRun(-331).beginBattle();
        GameModel first = restoredBattle(run); terminalFixture(first, GameModel.LOST, 10);
        run = roundTrip(run.finishBattle(first));
        RunState pending = run;
        reject(() -> pending.retry(88), "Retry needs the actual loss nonce");
        run = roundTrip(run.retry(1));
        GameModel second = restoredBattle(run); terminalFixture(second, GameModel.LOST, 12);
        run = roundTrip(run.finishBattle(second));
        check(run.status == RunState.DEFEATED && run.terminal() && run.outcomes().length == 2
            && run.retriesUsed == 1 && run.battlesCleared() == 0, "Second same-node loss ends the run");
        RunState defeated = run;
        reject(() -> defeated.retry(2), "Second loss cannot receive another retry");
        check(defeated.retry(1) == defeated && defeated.finishBattle(first) == defeated
            && defeated.finishBattle(second) == defeated, "Delayed retry/result callbacks remain no-ops");

        run = RunState.newRun(82).beginBattle();
        first = restoredBattle(run); terminalFixture(first, GameModel.LOST, 10);
        run = run.finishBattle(first).retry(1);
        GameModel wonRetry = restoredBattle(run); terminalFixture(wonRetry, GameModel.WON, 15);
        run = run.finishBattle(wonRetry);
        run = roundTrip(run.choosePerk(run.councilNonce, run.councilOffer()[0]).beginBattle());
        GameModel laterLoss = restoredBattle(run); terminalFixture(laterLoss, GameModel.LOST, 11);
        run = roundTrip(run.finishBattle(laterLoss));
        check(run.status == RunState.DEFEATED && run.node == 1 && run.battlesCleared() == 1,
            "Retry belongs to the run, not one token per node");
    }

    private static void councilPaths() throws Exception {
        int[] seen = {0};
        check(walkCouncils(RunState.newRun(281), seen) == 54,
            "All 3 x 3 x 3 x 2 available council paths complete");
        check(seen[0] == RunState.ALL_PERKS, "Every independent perk occurs in valid persisted builds");
    }

    private static int walkCouncils(RunState ready, int[] seen) throws Exception {
        RunState run = roundTrip(ready.beginBattle());
        seen[0] |= run.perks;
        GameModel model = restoredBattle(run); terminalFixture(model, GameModel.WON, 5);
        run = roundTrip(run.finishBattle(model));
        if (run.status == RunState.COMPLETED) return 1;
        int count = 0;
        for (int perk : run.councilOffer())
            count += walkCouncils(roundTrip(run.choosePerk(run.councilNonce, perk)), seen);
        return count;
    }

    private static void councils() throws Exception {
        RunState run = council(RunState.newRun(918));
        RunState restored = roundTrip(run);
        check(Arrays.equals(run.councilOffer(), restored.councilOffer()), "Persist actual ordered offer");
        RunState sameSeed = council(RunState.newRun(918));
        check(Arrays.equals(run.councilOffer(), sameSeed.councilOffer()), "Offer depends on seed/build, not UUID");
        int[] offer = run.councilOffer();
        int original = offer[0]; offer[0] = 0;
        check(run.councilOffer()[0] == original, "Offer exposure cannot reroll or mutate save");
        int unavailable = 0;
        for (int bit = 1; bit <= 16; bit <<= 1)
            if ((bit & (run.councilOffer()[0] | run.councilOffer()[1] | run.councilOffer()[2])) == 0) unavailable = bit;
        final int absent = unavailable;
        RunState council = run;
        reject(() -> council.choosePerk(council.councilNonce, absent), "Choice must be in actual offer");
        reject(() -> council.choosePerk(council.councilNonce + 1, original), "Council nonce guards stale taps");
        reject(() -> council.choosePerk(council.councilNonce, 3), "Perk bits cannot be combined as one choice");
        reject(() -> council.beginBattle(), "Council cannot be bypassed");
        long nonce = run.councilNonce;
        run = run.choosePerk(nonce, original);
        RunState selected = run;
        reject(() -> selected.choosePerk(nonce, council.councilOffer()[1]), "Conflicting duplicate choice rejected");
        run = council(run);
        check(run.choosePerk(nonce, original) == run && run.status == RunState.COUNCIL,
            "Old council duplicate does not select from a new council");
        RunState newerCouncil = run;
        reject(() -> newerCouncil.choosePerk(newerCouncil.councilNonce, original), "Owned perks never stack");
    }

    private static void abandonments() throws Exception {
        RunState ready = RunState.newRun(51), battle = ready.beginBattle(), council = council(ready);
        GameModel loss = restoredBattle(battle); terminalFixture(loss, GameModel.LOST, 14);
        RunState retry = battle.finishBattle(loss);
        RunState retryBattle = retry.retry(1);
        for (RunState candidate : new RunState[] {ready, battle, council, retry, retryBattle}) {
            check(candidate.abandon(false, -1) == candidate, "Declining abandonment leaves state untouched");
            reject(() -> candidate.abandon(true, candidate.revision + 1), "Stale abandonment rejected");
            RunState abandoned = roundTrip(candidate.abandon(true, candidate.revision));
            check(abandoned.status == RunState.ABANDONED && abandoned.activeBattle == null
                && abandoned.terminal() && abandoned.summary().abandoned
                && abandoned.summary().battlesCleared == candidate.battlesCleared()
                && abandoned.summary().retriesUsed == candidate.retriesUsed,
                "Explicit abandonment preserves achievements and retry accounting");
            check(abandoned.abandon(true, candidate.revision) == abandoned, "Duplicate abandon is idempotent");
            reject(() -> abandoned.beginBattle(), "Abandoned run cannot start");
            if (candidate.activeBattle != null) {
                GameModel abandonedResult = candidate.createBattle();
                terminalFixture(abandonedResult, GameModel.WON, 9);
                reject(() -> abandoned.finishBattle(abandonedResult), "Abandoned active battle cannot reward a council");
            }
        }
    }

    private static void associations() throws Exception {
        RunState run = roundTrip(RunState.newRun(900).beginBattle());
        check(!run.matchesActiveBattle(null), "Missing single-slot battle is explicit mismatch");
        GameModel foreign = RunState.newRun(900).beginBattle().createBattle();
        terminalFixture(foreign, GameModel.WON, 8);
        reject(() -> run.finishBattle(foreign), "Matching node/seed from another run is not proof");
        reject(() -> run.finishBattle(run.createBattle()), "Playing battle cannot be awarded");
        for (int change = 0; change < 14; change++) {
            GameModel invalid = restoredBattle(run);
            terminalFixture(invalid, GameModel.WON, 8);
            switch (change) {
                case 0: invalid.battleMode = 0; break;
                case 1: invalid.runId = run.id; break;
                case 2: invalid.runNode++; break;
                case 3: invalid.runPerks = 16; break;
                case 4: invalid.seed++; break;
                case 5: invalid.difficulty = 2; break;
                case 6: invalid.rulesVersion--; break;
                case 7: invalid.aiVersion = 0; break;
                case 8: invalid.objectiveType = 1; break;
                case 9: invalid.dailyDate = "2026-10-09"; break;
                case 10: invalid.missionPressure = 2; break;
                case 11: invalid.dailyVersion = "different-rules"; break;
                case 12: invalid.objectiveTarget = 0; break;
                default: invalid.deploymentBudget = 125; break;
            }
            check(!run.matchesActiveBattle(invalid), "Every battle identity field is guarded");
            reject(() -> run.finishBattle(invalid), "Mismatched result cannot advance run");
        }
        GameModel won = restoredBattle(run); terminalFixture(won, GameModel.WON, 8);
        RunState finished = roundTrip(run.finishBattle(won));
        won.captures++;
        reject(() -> finished.finishBattle(won), "Same nonce with different statistics is not a duplicate");
        GameModel bad = run.createBattle(); terminalFixture(bad, GameModel.WON, 8);
        bad.elapsed = Float.NaN;
        reject(() -> run.finishBattle(bad), "Invalid statistics rejected atomically");
        check(run.status == RunState.BATTLE && run.outcomes().length == 0,
            "Failed transition leaves original active state intact");
    }

    private static void frozenMapsAndStartingPerk() throws Exception {
        RunState run = RunState.newRun(301).beginBattle();
        GameModel original = restoredBattle(run);
        int map = run.currentNode().mapIndex;
        GameModel.Level current = GameModel.LEVELS[map];
        try {
            GameModel.LEVELS[map] = new GameModel.Level("Updated campaign", 4, 4, 2, new int[] {5}, 90);
            RunState restored = roundTrip(run);
            GameModel frozen = restoredBattle(restored);
            check(Arrays.equals(original.save(), frozen.save()), "Updated campaign cannot change saved run topology/spawns");
            GameModel resumed = GameModel.restore(original.save());
            check(restored.matchesActiveBattle(resumed) && resumed.level().columns == 3,
                "Active model restores original frozen map independently of campaign definitions");
            check(new GameModel(map, 0, 1).level().columns == 4, "Frozen run does not overwrite campaign map");
        } finally { GameModel.LEVELS[map] = current; }

        RunState selected = null;
        for (int seed = 0; seed < 50 && selected == null; seed++) {
            RunState offer = council(RunState.newRun(seed));
            for (int bit : offer.councilOffer()) if (bit == RunState.STARTING_KING)
                selected = offer.choosePerk(offer.councilNonce, bit).beginBattle();
        }
        check(selected != null, "Starting king perk is reachable through real offer API");
        GameModel bonus = restoredBattle(selected);
        GameModel ordinary = new GameModel(bonus.levelIndex, bonus.difficulty, bonus.seed);
        int king = bonus.originalKing(GameModel.PLAYER);
        check(bonus.runPerks == 16 && bonus.territories.get(king).troops
            == ordinary.territories.get(king).troops + 10, "configureRun applies exact starting king bonus");
        double initial = bonus.territories.get(king).troops;
        reject(() -> bonus.configureRun(bonus.runPerks, bonus.runId, bonus.runNode),
            "Model rejects configuring a run twice");
        check(bonus.territories.get(king).troops == initial, "Duplicate configuration cannot apply start bonus twice");
        terminalFixture(bonus, GameModel.LOST, 9);
        RunState retry = selected.finishBattle(bonus).retry(selected.activeBattle.nonce);
        GameModel replay = restoredBattle(roundTrip(retry));
        check(replay.territories.get(replay.originalKing(0)).troops == initial,
            "Same-build retry starts with one bonus, not zero or two");
        check(ordinary.runPerks == 0 && ordinary.battleMode == 0 && ordinary.runId.isEmpty(),
            "Run perks do not leak into Classic campaign");
    }

    private static void corruption() throws Exception {
        Progress campaign = new Progress();
        byte[] campaignBefore = campaign.save();
        RunState run = council(RunState.newRun(881));
        byte[] valid = run.save();
        for (int i = 0; i < valid.length; i++) {
            byte[] corrupt = valid.clone(); corrupt[i] ^= 1;
            rejectsSave(corrupt, "Every single-byte corruption fails CRC");
        }
        for (int length : new int[] {0, 1, 8, 15, 20, valid.length - 4, valid.length - 1})
            rejectsSave(Arrays.copyOf(valid, length), "Truncated saves rejected");
        rejectsSave(null, "Null run save rejected");
        rejectsSave(new byte[16385], "Oversized run save rejected");
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1); checksum(trailing);
        rejectsSave(trailing, "Extra payload rejected even with a valid CRC");

        int[] offsets = headerOffsets(valid);
        int[][] forged = {{0, 0}, {1, 0}, {2, 2}, {3, 2}, {4, 0}, {5, 9},
            {6, 7}, {7, 5}, {8, 32}, {9, 2}, {10, 4}};
        for (int[] field : forged) {
            byte[] bad = valid.clone(); ByteBuffer.wrap(bad).putInt(offsets[field[0]], field[1]);
            checksum(bad); rejectsSave(bad, "Semantically invalid checksummed save rejected");
        }
        byte[] negativeRevision = valid.clone();
        ByteBuffer.wrap(negativeRevision).putLong(offsets[11], -1L);
        checksum(negativeRevision); rejectsSave(negativeRevision, "Negative transition revision rejected");
        byte[] duplicateOffer = valid.clone();
        int offerOffset = offerOffset(valid);
        ByteBuffer.wrap(duplicateOffer).putInt(offerOffset + 4,
            ByteBuffer.wrap(duplicateOffer).getInt(offerOffset));
        checksum(duplicateOffer); rejectsSave(duplicateOffer, "Checksummed duplicate council offer rejected");
        byte[] inconsistentLifecycle = valid.clone();
        ByteBuffer.wrap(inconsistentLifecycle).putInt(offsets[6], RunState.READY);
        checksum(inconsistentLifecycle); rejectsSave(inconsistentLifecycle, "Council cannot restore as ready");
        byte[] inconsistentBuild = valid.clone();
        ByteBuffer.wrap(inconsistentBuild).putInt(offsets[8], RunState.STARTING_KING);
        checksum(inconsistentBuild); rejectsSave(inconsistentBuild, "Perk mask must agree with choice history");

        RunState chosen = run.choosePerk(run.councilNonce, run.councilOffer()[0]);
        byte[] ownedOffer = council(chosen).save();
        ByteBuffer.wrap(ownedOffer).putInt(offerOffset(ownedOffer), chosen.perks);
        checksum(ownedOffer); rejectsSave(ownedOffer, "Checksummed owned council option rejected");
        byte[] invalidCause = valid.clone();
        int resultStart = firstResultOffset(valid);
        ByteBuffer.wrap(invalidCause).putInt(resultStart + 32, GameModel.TERMINAL_SURRENDER);
        checksum(invalidCause); rejectsSave(invalidCause, "Won result cannot carry defeat reason");
        byte[] invalidNonce = valid.clone();
        ByteBuffer.wrap(invalidNonce).putLong(resultStart + 12, 88);
        checksum(invalidNonce); rejectsSave(invalidNonce, "Checksummed outcome nonce proof is validated");
        check(Arrays.equals(campaignBefore, campaign.save()), "Corrupt run never erases campaign progress");
        roundTrip(run);
    }

    private static int[] headerOffsets(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        int[] result = new int[12];
        in.readInt(); result[0] = position(in, bytes); in.readInt(); in.readUTF();
        in.readLong(); result[11] = position(in, bytes); in.readLong();
        for (int i = 1; i <= 4; i++) { result[i] = position(in, bytes); in.readInt(); }
        result[5] = position(in, bytes);
        for (int i = 0; i < 5; i++) in.readInt();
        for (int i = 6; i <= 9; i++) { result[i] = position(in, bytes); in.readInt(); }
        in.readLong(); result[10] = position(in, bytes);
        return result;
    }

    private static int offerOffset(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        int countOffset = headerOffsets(bytes)[10];
        in.skipBytes(countOffset);
        int nodes = in.readInt();
        for (int i = 0; i < nodes; i++) {
            in.readInt(); in.readLong(); in.readInt(); in.readInt(); in.readUTF();
            for (int field = 0; field < 7; field++) in.readInt();
            for (int array = 0; array < 3; array++) {
                int count = in.readInt();
                for (int value = 0; value < count; value++) in.readInt();
            }
        }
        in.readInt();
        return position(in, bytes);
    }

    private static int firstResultOffset(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 4));
        int offset = offerOffset(bytes);
        in.skipBytes(offset - 4);
        for (int array = 0; array < 2; array++) {
            int length = in.readInt();
            for (int i = 0; i < length; i++) in.readInt();
        }
        if (in.readBoolean()) in.skipBytes(28);
        in.readInt();
        return position(in, bytes);
    }

    private static int position(DataInputStream in, byte[] bytes) throws IOException {
        return bytes.length - 4 - in.available();
    }

    private static void checksum(byte[] bytes) {
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) crc.getValue());
    }

    private static RunState council(RunState ready) throws Exception {
        RunState run = ready.beginBattle();
        GameModel model = restoredBattle(run); terminalFixture(model, GameModel.WON, 10);
        return roundTrip(run.finishBattle(model));
    }

    private static GameModel restoredBattle(RunState run) throws Exception {
        GameModel model = run.createBattle(), restored = GameModel.restore(model.save());
        check(run.matchesActiveBattle(restored), "Battle save roundtrip preserves nonce/config/build");
        check(Arrays.equals(model.save(), restored.save()), "Battle save roundtrip is byte exact");
        return restored;
    }

    private static void terminalFixture(GameModel model, int outcome, float elapsed) {
        model.troops.clear();
        for (GameModel.Territory tile : model.territories) {
            tile.owner = outcome == GameModel.WON ? 0 : 1; tile.troops = 5;
        }
        model.outcome = outcome; model.elapsed = elapsed;
        model.terminalReason = outcome == GameModel.WON
            ? GameModel.TERMINAL_VICTORY : GameModel.TERMINAL_ELIMINATED;
        model.captures = 2; model.unitsSent = 10; model.unitsLost = 3;
        model.intercepted = 1; model.cappedReinforcements = 2;
        model.startingKingLost = outcome == GameModel.LOST;
    }

    private static RunState roundTrip(RunState run) throws IOException {
        byte[] bytes = run.save(); RunState restored = RunState.restore(bytes);
        check(Arrays.equals(bytes, restored.save()), "Run save restores byte exactly");
        return restored;
    }

    private static void rejectsSave(byte[] bytes, String message) throws Exception {
        try { RunState.restore(bytes); } catch (IOException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void reject(Action action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
