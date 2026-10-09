package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Standalone public-API tests; FP10 fixtures are written independently of the current serializer. */
public final class V11ProgressTest {
    private static int checks;
    private static final int LEGACY_FLAGS = 8 + 3 * (60 + 9) * 12;
    private static final int EMPTY_MISSIONS = LEGACY_FLAGS + 15;

    public static int run() throws Exception {
        checks = 0;
        objectiveResults();
        resumedReasons();
        holdRecords();
        survivalRecords();
        deploymentRecords();
        isolatedRecords();
        dailyPolicy();
        dailyRetention();
        campaignCompatibility();
        campaignIsolation();
        historicalMigration();
        format2Migration();
        eligibility();
        persistence();
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("V11 objective/progress checks passed: " + run());
    }

    private static void objectiveResults() {
        GameModel hold = mission(0, 1, 87.5f, 20);
        ObjectiveResult result = ObjectiveResult.evaluate(hold);
        check(result.completed && result.code.equals(ObjectiveResult.HOLD_COMPLETED), "Hold completion code");
        check(result.policy == ObjectiveResult.Policy.ELAPSED_TIME && result.hasFastestTime(), "Hold elapsed policy");
        numbers(result, 87.5, 20, 20);
        Number[] arguments = result.numericArguments(); arguments[0] = -100;
        check(result.numericArguments()[0].floatValue() == 87.5f, "Message arguments are defensive snapshots");
        hold.elapsed = 999;
        check(result.elapsedSeconds == 87.5f && result.numericArguments()[0].floatValue() == 87.5f,
            "Result facts do not follow subsequent model mutation");

        GameModel keep = mission(3, 2, 45.25f, 80);
        result = ObjectiveResult.evaluate(keep);
        check(result.policy == ObjectiveResult.Policy.COMPLETION_ONLY && !result.hasFastestTime(), "No survival speed target");
        check(result.code.equals(ObjectiveResult.KEEP_COMPLETED) && result.actualSeconds == 45.25f
            && result.requiredSeconds == 45, "Actual and required survival remain distinct");
        numbers(result, 45.25, 45);

        GameModel budget = mission(6, 0, 100, 100);
        result = ObjectiveResult.evaluate(budget);
        check(result.policy == ObjectiveResult.Policy.DEPLOYED_TROOPS && !result.hasFastestTime(), "Troops are primary");
        check(result.code.equals(ObjectiveResult.BUDGET_COMPLETED) && result.remainingAllowance == 20, "Budget facts");
        numbers(result, 100, 120, 20);
        budget.outcome = GameModel.LOST; budget.unitsSent = 125; budget.terminalReason = GameModel.TERMINAL_BUDGET;
        result = ObjectiveResult.evaluate(budget);
        check(!result.completed && result.code.equals(ObjectiveResult.BUDGET_EXCEEDED)
            && result.remainingAllowance == 0, "Exact exceeded-budget cause");
        numbers(result, 125, 120);
        int[] reasons = {GameModel.TERMINAL_PROTECTED_KING, GameModel.TERMINAL_ELIMINATED,
            GameModel.TERMINAL_SURRENDER, GameModel.TERMINAL_LEGACY, GameModel.TERMINAL_NONE};
        String[] codes = {ObjectiveResult.PROTECTED_KING_LOST, ObjectiveResult.PLAYER_ELIMINATED,
            ObjectiveResult.SURRENDERED, ObjectiveResult.LEGACY_DEFEAT, ObjectiveResult.LEGACY_DEFEAT};
        for (int i = 0; i < reasons.length; i++) {
            budget.terminalReason = reasons[i];
            result = ObjectiveResult.evaluate(budget);
            check(result.code.equals(codes[i]) && !result.completed, "Persisted reason, never guessed from budget counters");
            numbers(result);
        }
        budget.outcome = GameModel.PLAYING;
        check(ObjectiveResult.evaluate(budget).code.equals(ObjectiveResult.IN_PROGRESS), "Playing is not a terminal result");
        check(ObjectiveResult.policy(null) == ObjectiveResult.Policy.NONE, "Null policy is harmless");
        try { ObjectiveResult.evaluate(null); throw new AssertionError("Missing result accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static void holdRecords() {
        Progress progress = new Progress();
        GameModel first = mission(0, 1, 120, 40);
        check(first.score() == 0 && first.stars() == 0, "Revised mission has no campaign score or stars");
        check(progress.recordChallenge(first), "First hold win records with zero campaign score");
        Progress.MissionRecord record = progress.missionRecord(first);
        check(record.completed && record.bestElapsed == 120 && record.bestUnits == -1 && record.hasFastestTime(), "Hold record facts");
        check(!progress.recordChallenge(first), "Repeated hold win is idempotent");
        GameModel slow = mission(0, 1, 140, 1);
        check(!progress.recordChallenge(slow) && progress.missionRecord(slow).bestElapsed == 120, "Troops do not outrank hold elapsed");
        GameModel fast = mission(0, 1, 90, 100);
        check(progress.recordChallenge(fast) && progress.missionRecord(first).bestElapsed == 90, "Use total completion time, not hold duration");
        check(record.bestElapsed == 120, "Mission record is a snapshot");
        Progress.MissionRecord scalar = progress.missionRecord(0, 1, fast.missionConfigVersion,
            fast.objectiveType, "", "", fast.rulesVersion);
        check(scalar.completed && scalar.bestElapsed == 90, "Scalar UI record query");
        GameModel brief = Challenge.PRESETS[0].create(1, 999, 0, "");
        check(progress.missionRecord(brief).completed && progress.missionRecord(brief).bestElapsed == 90,
            "A fresh briefing model queries a record without being a win");
        check(progress.objectiveProgress() == 1 && progress.crownKeeper, "Objective completion awards genuine mastery");
        emptyHistorical(progress);
    }

    private static void resumedReasons() throws Exception {
        GameModel budget = Challenge.PRESETS[6].create(1, 42, 6, "");
        int source = budget.originalKing(GameModel.PLAYER), target = budget.originalKing(1);
        budget.territories.get(source).troops = 125;
        budget.launch(source, target, 1);
        check(budget.outcome == GameModel.LOST && budget.terminalReason == GameModel.TERMINAL_BUDGET,
            "Actual over-budget launch has the explicit model reason");
        GameModel restored = GameModel.restore(budget.save());
        ObjectiveResult result = ObjectiveResult.evaluate(restored);
        check(result.code.equals(ObjectiveResult.BUDGET_EXCEEDED), "Resumed result keeps the failure code");
        numbers(result, 125, 120);
        check(!new Progress().recordChallenge(restored), "A resumed budget defeat earns no completion");

        GameModel keep = Challenge.PRESETS[3].create(1, 42, 3, "");
        keep.elapsed = keep.objectiveProgress = 12;
        keep.surrender();
        restored = GameModel.restore(keep.save());
        result = ObjectiveResult.evaluate(restored);
        check(result.code.equals(ObjectiveResult.SURRENDERED) && !result.completed,
            "Resumed surrender remains distinct from objective failure");
        check(result.actualSeconds == 12 && result.requiredSeconds == 45, "Failed survival has actual/required facts");
    }

    private static void survivalRecords() {
        Progress progress = new Progress();
        GameModel first = mission(3, 1, 45.25f, 30);
        check(progress.recordChallenge(first), "First survival completion is an improvement");
        Progress.MissionRecord record = progress.missionRecord(first);
        check(record.completed && record.bestElapsed == 0 && record.bestUnits == -1 && !record.hasFastestTime(),
            "Survival records completion, never a fastest time");
        check(record.actualSeconds == 45.25f && record.requiredSeconds == 45, "Survival record includes factual duration");
        byte[] before = progress.save();
        check(!progress.recordChallenge(mission(3, 1, 45.01f, 0)), "Fractionally faster survival is not an improvement");
        check(!progress.recordChallenge(mission(3, 1, 60, 100)), "Longer survival cannot create a speed comparison");
        check(Arrays.equals(before, progress.save()), "Repeated survival leaves record and rewards unchanged");
        check(progress.challengeCompleted(3, 1) && !progress.challengeCompleted(3, 0), "Completion is independent and difficulty-specific");
        emptyHistorical(progress);
    }

    private static void deploymentRecords() {
        Progress progress = new Progress();
        GameModel first = mission(6, 1, 30, 110);
        check(progress.recordChallenge(first), "First deployment record");
        GameModel efficient = mission(6, 1, 180, 100);
        check(progress.recordChallenge(efficient), "Fewer troops wins even when much slower");
        check(progress.missionRecord(efficient).bestUnits == 100 && progress.missionRecord(efficient).bestElapsed == 180,
            "Winning efficiency and its time are one paired record");
        check(!progress.recordChallenge(mission(6, 1, 10, 101)), "Faster but wasteful win does not replace record");
        check(progress.recordChallenge(mission(6, 1, 170, 100)), "Elapsed breaks an equal-troops tie");
        check(!progress.recordChallenge(mission(6, 1, 170, 100)), "Exact deployment tie is idempotent");
        check(progress.recordChallenge(mission(6, 1, 250, 95)), "Primary efficiency outranks the old tie-break");
        Progress.MissionRecord record = progress.missionRecord(first);
        check(record.bestUnits == 95 && record.bestUnitsSent == 95 && record.bestElapsed == 250,
            "Do not preserve an unrelated fastest time from a less efficient win");
        check(progress.recordChallenge(mission(7, 0, 0, 0)), "Zero-troop, zero-second winning record is representable");
        check(!progress.recordChallenge(mission(7, 0, 0, 0)), "Zero-valued record still has independent completion");
        emptyHistorical(progress);
    }

    private static void isolatedRecords() {
        Progress progress = new Progress();
        GameModel base = mission(6, 1, 100, 80);
        progress.recordChallenge(base);
        GameModel changed = mission(6, 2, 100, 100);
        check(!progress.missionRecord(changed).completed && progress.recordChallenge(changed), "Difficulty isolation");
        changed = mission(6, 1, 100, 100); changed.missionConfigVersion++;
        check(!progress.missionRecord(changed).completed && progress.recordChallenge(changed), "Configuration version isolation");
        changed = mission(6, 1, 100, 100); changed.rulesVersion = 10;
        check(!progress.missionRecord(changed).completed && progress.recordChallenge(changed), "Revised rules-10 records accepted but not compared to rules-11");
        changed = mission(7, 1, 100, 150);
        check(!progress.missionRecord(changed).completed && progress.recordChallenge(changed), "Mission identity isolation");
        changed = mission(6, 1, 100, 100); changed.objectiveType = Challenge.HOLD_KING;
        changed.objectiveSeconds = changed.objectiveProgress = 20; changed.deploymentBudget = 0;
        check(!progress.missionRecord(changed).completed && progress.recordChallenge(changed), "Objective type isolation");
        changed = mission(6, 1, 100, 100); changed.deploymentBudget = 150;
        check(!progress.missionRecord(changed).completed && progress.recordChallenge(changed), "Never compare different allowances even with a reused version");
        check(progress.missionRecord(base).bestUnits == 80, "Other configurations cannot erase original record");
        check(!progress.missionRecord(6, 1, 2, Challenge.BUDGET, "", "", 11).completed,
            "An ambiguous scalar query does not merge different allowances");
        check(progress.missionRecord(base).completed, "Exact factory/model query remains available");
    }

    private static void dailyPolicy() {
        Progress progress = new Progress();
        String date = "2026-10-09";
        for (int id : new int[] {0, 3, 6}) {
            GameModel model = daily(id, 1, id == 3 ? 45.25f : 100, 100, date);
            check(progress.recordChallenge(model), "Daily uses objective primary policy");
            check(progress.missionRecord(model).completed && progress.missionCompleted(model), "Daily completion query");
            check(progress.dailyCompleted(date, 1, model.dailyVersion)
                && progress.dailyCompleted(date, 1, model.dailyVersion, model.rulesVersion, model.missionConfigVersion),
                "Daily completion is indexed by date, difficulty, identifier, and versions");
            GameModel ordinary = mission(id, 1, model.elapsed, model.unitsSent);
            check(!progress.missionRecord(ordinary).completed, "Daily does not enter ordinary mission records");
            GameModel other = daily(id, 2, model.elapsed, model.unitsSent, date);
            check(!progress.missionRecord(other).completed, "Daily difficulty isolation");
            other.difficulty = 1; other.dailyVersion = "daily-v11-next";
            check(!progress.missionRecord(other).completed, "Daily identifier isolation");
            other.dailyVersion = model.dailyVersion; other.missionConfigVersion++;
            check(!progress.missionRecord(other).completed, "Daily configuration isolation");
            other.missionConfigVersion = model.missionConfigVersion; other.rulesVersion = 10;
            check(!progress.missionRecord(other).completed, "Daily rules isolation");
            check(!progress.recordCampaign(model), "Daily never enters campaign progression");
        }
        check(progress.recordChallenge(daily(0, 1, 80, 100, date)), "Daily hold uses total elapsed");
        check(!progress.recordChallenge(daily(3, 1, 45.01f, 0, date)), "Daily survival does not compare speed");
        check(progress.recordChallenge(daily(6, 1, 180, 90, date)), "Daily budget prefers fewer troops despite slower win");
        check(progress.recordChallenge(daily(6, 1, 170, 90, date)), "Daily budget breaks ties with elapsed");
        check(!progress.dailyCompleted(date, 0, Challenge.DAILY_VERSION)
            && !progress.dailyCompleted(date, 1, "daily-v10-1")
            && !progress.dailyCompleted(date, 1, Challenge.DAILY_VERSION, 10, 2), "No cross-version or difficulty completion");
        check(progress.objectiveMaster && progress.objectiveProgress() == 3, "Daily completion contributes mastery without stars");
        check(progress.dailyBest(date) == 0 && progress.dailyStars(date) == 0 && progress.dailyTime(date) == 0,
            "Revised Daily never overwrites historical stars or times");
        emptyHistorical(progress);
    }

    private static void dailyRetention() throws Exception {
        Progress progress = new Progress();
        LocalDate first = LocalDate.of(2026, 1, 1);
        for (int day = 60; day >= 0; day--) {
            for (int difficulty = 0; difficulty < 3; difficulty++) {
                GameModel model = daily(6, difficulty, 100, 100, first.plusDays(day).toString());
                check(progress.recordChallenge(model) == (day != 0), "Keep latest sixty dates, not sixty difficulty entries");
            }
        }
        for (int day = 1; day <= 60; day++) for (int difficulty = 0; difficulty < 3; difficulty++)
            check(progress.dailyCompleted(first.plusDays(day).toString(), difficulty, Challenge.DAILY_VERSION),
                "All difficulties survive daily date retention");
        byte[] before = progress.save();
        check(!progress.recordChallenge(daily(6, 1, 10, 1, "2020-01-01")), "Old replay cannot evict a newer date");
        check(Arrays.equals(before, progress.save()), "Pruned replay does not alter retained records or duplicate rewards");
        Progress restored = Progress.restore(before);
        check(Arrays.equals(before, restored.save()), "Bounded multi-difficulty daily records round trip canonically");
    }

    private static void campaignCompatibility() {
        Progress progress = new Progress();
        for (int rules : new int[] {10, 11}) {
            GameModel model = campaign(rules, 1, rules == 10 ? 100 : 90);
            check(progress.recordCampaign(model), "Classic campaign accepts rules 10 and 11");
            check(progress.campaignBest(0, 1, rules) == model.score() && progress.campaignStars(0, 1, rules) == model.stars()
                && progress.campaignTime(0, 1, rules) == model.elapsed, "Matching-rules campaign scoring/stars remain unchanged");
            check(ObjectiveResult.evaluate(model).code.equals(ObjectiveResult.CAMPAIGN_WON), "Campaign result identity");
        }
        check(!progress.recordCampaign(campaign(11, 1, 120)), "A slower classic campaign win does not improve time");
        for (int i = 1; i < 6; i++) {
            GameModel model = new GameModel(i, 1, i); model.outcome = GameModel.WON;
            model.terminalReason = GameModel.TERMINAL_VICTORY; model.elapsed = 20;
            progress.recordCampaign(model);
        }
        check(progress.chapterNormal && progress.chapterProgress() == 6 && progress.themeUnlocked(2),
            "Campaign chapter mastery remains earned from six Normal sectors");
    }

    private static void campaignIsolation() throws Exception {
        Progress progress = new Progress();
        GameModel old = campaign(10, 1, 20), current = campaign(11, 1, 200);
        check(progress.recordCampaign(old) && progress.recordCampaign(current),
            "First rules-11 result is a first record even when much slower than rules-10 history");
        check(progress.campaignTime(0, 1, 10) == 20 && progress.campaignTime(0, 1, 11) == 200,
            "Campaign times stay isolated by rules");
        check(progress.campaignBest(0, 1, 10) == old.score() && progress.campaignBest(0, 1, 11) == current.score()
            && progress.campaignStars(0, 1, 10) == 3 && progress.campaignStars(0, 1, 11) == 1,
            "Old high score and stars cannot become current records");
        check(progress.best[1][0] == current.score() && progress.times[1][0] == 200 && progress.stars[1][0] == 1
            && progress.historicalCampaignBest[1][0] == old.score() && progress.historicalCampaignTimes[1][0] == 20,
            "Public current and historical arrays have explicit rules identities");
        check(progress.campaignHistoricalLabel(0, 1, 10).equals(ObjectiveResult.HISTORICAL_LABEL)
            && progress.campaignHistoricalLabel(0, 1, 11).isEmpty(), "Only historical campaign records get a Historical label");
        check(progress.campaignCleared(0, 1) && !progress.campaignCleared(0, 0) && progress.chapterProgress() == 1,
            "Cross-version completion stays difficulty-specific and counts a sector once");
        check(!progress.recordCampaign(campaign(11, 1, 220)), "Current slower win is compared only to current time");
        check(progress.recordCampaign(campaign(10, 1, 10)), "A continuing rules-10 win improves only historical time");
        check(progress.campaignTime(0, 1, 10) == 10 && progress.campaignTime(0, 1, 11) == 200,
            "Continuing old battle cannot overwrite current campaign time");
        check(progress.recordCampaign(campaign(11, 1, 180)) && progress.campaignTime(0, 1, 10) == 10,
            "Current improvement cannot overwrite historical campaign time");
        for (int sector = 1; sector < 6; sector++) {
            int rules = sector % 2 == 0 ? 10 : 11;
            GameModel model = new GameModel(sector, 1, 42, rules);
            model.outcome = GameModel.WON; model.terminalReason = GameModel.TERMINAL_VICTORY; model.elapsed = 20;
            check(progress.recordCampaign(model) && progress.chapterProgress() == sector + 1,
                "A chapter may be earned through distinct sectors across rules versions");
            model = new GameModel(sector, 1, 42, rules == 10 ? 11 : 10);
            model.outcome = GameModel.WON; model.terminalReason = GameModel.TERMINAL_VICTORY; model.elapsed = 30;
            check(progress.recordCampaign(model) && progress.chapterProgress() == sector + 1,
                "Completing the same sector under both rules does not duplicate chapter progress");
        }
        check(progress.chapterNormal && progress.themeUnlocked(2) && progress.earnedCount() == 2,
            "Combined completion preserves campaign mastery without duplicate rewards");
        byte[] saved = progress.save();
        Progress restored = Progress.restore(saved);
        check(Arrays.equals(saved, restored.save()) && restored.chapterProgress() == 6
            && restored.campaignTime(0, 1, 10) == 10 && restored.campaignTime(0, 1, 11) == 180,
            "Both campaign versions and combined mastery persist independently");
        for (int rules : new int[] {10, 11}) {
            GameModel zero = new GameModel(59, 2, 42, rules);
            zero.outcome = GameModel.WON; zero.terminalReason = GameModel.TERMINAL_VICTORY;
            check(progress.recordCampaign(zero) && !progress.recordCampaign(zero),
                "Zero-second wins have independent completion in each campaign rules version");
        }
        for (int[] index : new int[][] {{-1, 1}, {60, 1}, {0, -1}, {0, 3}})
            check(progress.campaignBest(index[0], index[1], 11) == 0
                && progress.campaignStars(index[0], index[1], 10) == 0
                && progress.campaignTime(index[0], index[1], 11) == 0
                && !progress.campaignCleared(index[0], index[1]), "Invalid campaign queries are harmless");
        check(progress.campaignBest(0, 1, 12) == 0 && progress.campaignStars(0, 1, 12) == 0
            && progress.campaignTime(0, 1, 12) == 0 && progress.campaignHistoricalLabel(0, 1, 12).isEmpty(),
            "Unknown campaign versions are not assigned another version's records");
    }

    private static void historicalMigration() throws Exception {
        byte[] fp10 = legacyFixture();
        check(ByteBuffer.wrap(fp10).getInt(4) == 1, "Independent fixture is actually format version 1");
        reject(patchInt(fp10, 4, 2));
        Progress progress = Progress.restore(fp10);
        for (int sector = 0; sector < 6; sector++) {
            check(progress.campaignBest(sector, 1, 10) == 2500 && progress.campaignStars(sector, 1, 10) == 3
                && progress.campaignTime(sector, 1, 10) == 20 && progress.campaignCleared(sector, 1),
                "Format-1 campaign arrays migrate to historical rules 10 with earned completion");
            check(progress.campaignBest(sector, 1, 11) == 0 && progress.campaignStars(sector, 1, 11) == 0
                && progress.campaignTime(sector, 1, 11) == 0 && progress.best[1][sector] == 0,
                "Format-1 campaign records never become current rules-11 records");
        }
        check(progress.campaignHistoricalLabel(0, 1, 10).equals(ObjectiveResult.HISTORICAL_LABEL)
            && progress.chapterProgress() == 6, "Migrated campaign history is labelled and preserves chapter completion");
        check(progress.challengeBest[1][0] == 2000 && progress.challengeStars[1][0] == 3
            && progress.challengeTimes[1][0] == 35, "FP10 arrays remain literal historical values");
        check(progress.objectiveMaster && progress.crownKeeper && progress.chapterNormal && progress.theme == 2,
            "Earned mastery and selected unlocked theme survive migration");
        GameModel revised = mission(0, 1, 100, 30);
        Progress.MissionRecord record = progress.missionRecord(revised);
        check(!record.completed && record.historicalCompleted && record.completedEver() && record.bestElapsed == 0,
            "Historical completion is preserved, not reinterpreted as a revised hold record");
        check(record.historicalStars == 3 && record.historicalTime == 35
            && record.historicalLabel.equals(ObjectiveResult.HISTORICAL_LABEL), "Historical stars/time labelled separately");
        check(progress.challengeCompleted(0, 1), "Old earned mission completion is still queryable");
        String date = "2026-10-08";
        check(progress.historicalDailyCompleted(date) && progress.dailyStars(date) == 3 && progress.dailyTime(date) == 45,
            "Legacy Daily record remains historical");
        check(!progress.dailyCompleted(date, 0, "daily-v10-1") && !progress.dailyCompleted(date, 1, "daily-v10-1"),
            "Unknown historical Daily difficulty is not fabricated");
        check(!progress.missionRecord(daily(3, 1, 45, 0, date)).completed, "Old Daily cannot complete the revised configuration");
        GameModel oldDaily = Challenge.PRESETS[3].createLegacy(1, 42, 3, date);
        check(!progress.missionRecord(oldDaily).completed && progress.missionRecord(oldDaily).historicalCompleted,
            "Historical Daily completion does not invent its missing difficulty");
        check(progress.recordChallenge(revised) && progress.missionRecord(revised).bestElapsed == 100,
            "Revised record starts independently even if slower than historical par-derived record");
        check(progress.challengeTimes[1][0] == 35 && progress.challengeStars[1][0] == 3, "New record leaves FP10 history untouched");
        byte[] upgraded = progress.save();
        check(ByteBuffer.wrap(upgraded).getInt(4) == 3, "New CRC save uses format version 3");
        int campaignBytes = 3 * 60 * 12, historyStart = upgraded.length - 4 - 2 * campaignBytes;
        check(Arrays.equals(Arrays.copyOfRange(fp10, 8, 8 + campaignBytes),
            Arrays.copyOfRange(upgraded, historyStart, historyStart + campaignBytes)),
            "Every FP10 campaign array value is preserved byte-for-byte in the historical rules-10 block");
        check(Arrays.equals(Arrays.copyOfRange(fp10, 8 + campaignBytes, fp10.length - 4),
            Arrays.copyOfRange(upgraded, 8 + campaignBytes, fp10.length - 4)),
            "FP10 mission arrays, mastery flags, and Daily entries remain byte-for-byte unchanged");
        for (int i = 0; i < 3; i++) {
            progress = Progress.restore(upgraded);
            check(Arrays.equals(upgraded, progress.save()) && progress.earnedCount() == 3
                && progress.missionRecord(revised).completed && progress.challengeTimes[1][0] == 35,
                "Repeated migration/restore does not duplicate rewards or erase old/new records");
        }
        GameModel old = Challenge.PRESETS[0].createLegacy(1, 42, 0, "");
        old.outcome = GameModel.WON; old.elapsed = 30; old.objectiveProgress = old.objectiveSeconds;
        check(progress.recordChallenge(old) && progress.challengeTimes[1][0] == 30,
            "Resumed genuine V10/config-1 win still writes its old-format historical record");
        check(progress.missionRecord(revised).bestElapsed == 100, "Resumed old win cannot alter revised record");
        check(progress.recordCampaign(campaign(11, 1, 200)) && progress.campaignTime(0, 1, 11) == 200
            && progress.campaignTime(0, 1, 10) == 20, "First current win is not compared against migrated historical time");
        check(progress.recordCampaign(campaign(10, 1, 10)) && progress.campaignTime(0, 1, 10) == 10
            && progress.campaignTime(0, 1, 11) == 200 && progress.campaignBest(0, 1, 10) == 2500,
            "Continuing rules-10 battle updates only its migrated historical records without erasing best score");
    }

    private static void format2Migration() throws Exception {
        byte[] fp2 = format2Fixture();
        check(ByteBuffer.wrap(fp2).getInt(4) == 2, "Independent format-2 fixture");
        Progress progress = Progress.restore(fp2);
        for (int sector = 0; sector < 6; sector++) {
            check(progress.campaignBest(sector, 1, Progress.UNVERSIONED_CAMPAIGN_RULES) == 2500
                && progress.campaignStars(sector, 1, Progress.UNVERSIONED_CAMPAIGN_RULES) == 3
                && progress.campaignTime(sector, 1, Progress.UNVERSIONED_CAMPAIGN_RULES) == 20,
                "Format-2 campaign values remain intact as unversioned history");
            check(progress.campaignBest(sector, 1, 10) == 0 && progress.campaignBest(sector, 1, 11) == 0
                && progress.campaignStars(sector, 1, 10) == 0 && progress.campaignStars(sector, 1, 11) == 0
                && progress.campaignTime(sector, 1, 10) == 0 && progress.campaignTime(sector, 1, 11) == 0,
                "Format-2 mixed records cannot invent comparable rules-10 or rules-11 provenance");
            check(progress.campaignCleared(sector, 1), "Unversioned records retain earned sector completion");
        }
        check(progress.chapterProgress() == 6 && progress.chapterNormal && progress.earnedCount() == 3 && progress.theme == 2,
            "Unversioned format-2 completion retains chapter mastery and selected rewards");
        check(progress.campaignHistoricalLabel(0, 1, Progress.UNVERSIONED_CAMPAIGN_RULES).equals(ObjectiveResult.HISTORICAL_LABEL),
            "Unversioned records are explicitly labelled Historical");
        Progress.MissionRecord hold = progress.missionRecord(mission(0, 1, 100, 20));
        Progress.MissionRecord keep = progress.missionRecord(mission(3, 1, 45.25f, 20));
        GameModel budgetQuery = daily(6, 2, 200, 90, "2026-10-09");
        Progress.MissionRecord budget = progress.missionRecord(budgetQuery);
        check(hold.completed && hold.bestElapsed == 100 && keep.completed && keep.bestElapsed == 0
            && keep.actualSeconds == 45.25f, "Format-2 revised hold and survival records survive migration");
        check(budget.completed && budget.bestUnits == 90 && budget.bestElapsed == 200,
            "Format-2 revised Daily paired efficiency/time record survives migration");
        check(progress.challengeTimes[1][0] == 35 && progress.dailyTime("2026-10-08") == 45,
            "Format-2 FP10 objective history remains untouched");
        check(progress.recordCampaign(campaign(10, 1, 200)) && progress.recordCampaign(campaign(11, 1, 300)),
            "Both proven rules versions start independently of ambiguous format-2 historical time");
        byte[] upgraded = progress.save();
        check(ByteBuffer.wrap(upgraded).getInt(4) == 3, "Format 2 upgrades to format 3");
        for (int i = 0; i < 3; i++) {
            progress = Progress.restore(upgraded);
            check(Arrays.equals(upgraded, progress.save()) && progress.earnedCount() == 3 && progress.chapterProgress() == 6,
                "Repeated format-2 migration does not duplicate rewards or completion");
            check(progress.campaignTime(0, 1, 10) == 200 && progress.campaignTime(0, 1, 11) == 300
                && progress.campaignTime(0, 1, Progress.UNVERSIONED_CAMPAIGN_RULES) == 20,
                "All known and unversioned campaign records survive format-3 round trips separately");
            check(progress.missionRecord(budgetQuery).bestUnits == 90 && progress.missionRecord(budgetQuery).bestElapsed == 200,
                "Objective records are not erased by campaign version migration");
        }
    }

    private static void eligibility() {
        Progress progress = new Progress(); byte[] before = progress.save();
        for (int outcome : new int[] {GameModel.PLAYING, GameModel.LOST}) {
            GameModel model = mission(6, 1, 50, 10); model.outcome = outcome;
            check(!progress.recordChallenge(model), "Only completed victories qualify");
        }
        for (float elapsed : new float[] {-1, Float.NaN, Float.POSITIVE_INFINITY, 86401}) {
            GameModel model = mission(6, 1, 50, 10); model.elapsed = elapsed;
            check(!progress.recordChallenge(model), "Reject invalid elapsed");
        }
        for (int rules : new int[] {0, 7, 9, 12}) {
            GameModel model = mission(6, 1, 50, 10); model.rulesVersion = rules;
            check(!progress.recordChallenge(model) && !progress.recordCampaign(model), "Reject unsupported rules");
        }
        GameModel model = mission(6, 1, 50, 121);
        check(!progress.recordChallenge(model), "Over-budget win fixture is not eligible");
        model.unitsSent = -1; check(!progress.recordChallenge(model), "Reject negative troop count");
        model = mission(0, 1, 50, 10); model.objectiveProgress = 19;
        check(!progress.recordChallenge(model), "An incomplete continuous hold cannot earn a record");
        model = mission(0, 1, 19.99995f, 10);
        check(!progress.recordChallenge(model), "A hold result cannot claim more required duration than total elapsed");
        model = mission(3, 1, 45, 10); model.startingKingLost = true;
        check(!progress.recordChallenge(model), "Protected king loss cannot earn survival completion");
        model = mission(6, 1, 50, 10); model.missionConfigVersion = 0;
        check(!progress.recordChallenge(model), "Unknown revised configuration is not eligible");
        model = mission(6, 1, 50, 10); model.difficulty = 3;
        check(!progress.recordChallenge(model), "Reject invalid difficulty");
        model = mission(6, 1, 50, 10); model.challengeId = 9;
        check(!progress.recordChallenge(model), "Reject invalid content ID");
        for (String date : new String[] {"2026-02-29", "2026-04-31", "0000-01-01", "2026-7-01", "bad"}) {
            model = mission(6, 1, 50, 10); model.dailyDate = date; model.dailyVersion = Challenge.DAILY_VERSION;
            check(!progress.recordChallenge(model), "Reject invalid Daily date");
        }
        model = daily(6, 1, 50, 10, "2026-10-09"); model.dailyVersion = "";
        check(!progress.recordChallenge(model), "Daily requires an explicit version identity");
        check(!progress.recordChallenge(null) && !progress.recordCampaign(null)
            && !progress.missionRecord(null).completed, "Null APIs are harmless");
        check(Arrays.equals(before, progress.save()), "Invalid inputs do not mutate progress or mastery");
    }

    private static void persistence() throws Exception {
        Progress progress = new Progress();
        for (int id = 0; id < 9; id++) for (int difficulty = 0; difficulty < 3; difficulty++)
            check(progress.recordChallenge(mission(id, difficulty, 120, id >= 6 ? 90 : 20)), "All mission records stored");
        progress.recordChallenge(daily(6, 2, 123, 50, "2028-02-29"));
        byte[] saved = progress.save();
        Progress restored = Progress.restore(saved);
        check(Arrays.equals(saved, restored.save()), "Mixed objective save is canonical");
        for (int id = 0; id < 9; id++) for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel query = Challenge.PRESETS[id].create(difficulty, 0, id, "");
            Progress.MissionRecord record = restored.missionRecord(query);
            check(record.completed, "Every difficulty/objective completion survives restore");
            check(record.bestElapsed == (id >= 3 && id < 6 ? 0 : 120), "Objective-specific elapsed survives restore");
        }
        check(restored.missionRecord(daily(6, 2, 123, 50, "2028-02-29")).bestUnits == 50,
            "Leap-date Daily record survives restore");
        byte[] empty = new Progress().save();
        reject(null); reject(new byte[1024 * 1024 + 1]);
        for (int length : new int[] {0, 1, 8, empty.length / 2, empty.length - 1}) reject(Arrays.copyOf(empty, length));
        byte[] damaged = saved.clone(); damaged[20] ^= 1; reject(damaged);
        reject(patchInt(empty, 0, 0)); reject(patchInt(empty, 4, 4));
        reject(patchInt(empty, 8, -1)); reject(patchInt(empty, 12, 4));
        reject(patchInt(empty, 16, Float.floatToIntBits(Float.NaN)));
        reject(patchInt(empty, EMPTY_MISSIONS, -1)); reject(patchInt(empty, EMPTY_MISSIONS, 4097));
        reject(patchByte(empty, LEGACY_FLAGS, 2)); reject(patchInt(empty, LEGACY_FLAGS + 3, 8));
        byte[] trailing = Arrays.copyOf(empty, empty.length + 1); checksum(trailing); reject(trailing);

        Progress one = new Progress(); one.recordChallenge(mission(6, 1, 100, 100));
        byte[] record = one.save();
        int key = EMPTY_MISSIONS + 4;
        int rules = key + 4; // Two empty UTF strings.
        reject(patchInt(record, rules, 9)); reject(patchInt(record, rules + 4, 0));
        reject(patchInt(record, rules + 8, 3)); reject(patchInt(record, rules + 12, 0));
        reject(patchInt(record, rules + 16, 9)); reject(patchInt(record, rules + 24, Float.floatToIntBits(Float.NaN)));
        reject(patchInt(record, rules + 28, 0));
        reject(patchInt(record, rules + 32, Float.floatToIntBits(Float.NaN)));
        reject(patchInt(record, rules + 36, 121));
        reject(patchInt(record, rules + 40, Float.floatToIntBits(1)));
        ByteArrayOutputStream altered = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(altered);
        out.write(record, 0, key); out.writeUTF(""); out.writeUTF("unexpected-version");
        out.write(record, rules, record.length - rules); out.flush();
        byte[] wrongVersion = altered.toByteArray(); checksum(wrongVersion); reject(wrongVersion);
        int campaignHistory = record.length - 4 - 2 * 3 * 60 * 12;
        int entryBytes = campaignHistory - key;
        byte[] duplicate = new byte[record.length + entryBytes];
        System.arraycopy(record, 0, duplicate, 0, campaignHistory);
        System.arraycopy(record, key, duplicate, campaignHistory, entryBytes);
        System.arraycopy(record, campaignHistory, duplicate, campaignHistory + entryBytes,
            record.length - 4 - campaignHistory);
        ByteBuffer.wrap(duplicate).putInt(EMPTY_MISSIONS, 2); checksum(duplicate); reject(duplicate);
        reject(patchInt(record, campaignHistory, -1));
        reject(patchInt(record, campaignHistory + 4, 4));
        reject(patchInt(record, campaignHistory + 8, Float.floatToIntBits(Float.NaN)));
        reject(patchInt(record, campaignHistory + 3 * 60 * 12, -1));
        reject(patchInt(record, campaignHistory + 3 * 60 * 12 + 4, 4));
        reject(patchInt(record, campaignHistory + 3 * 60 * 12 + 8, Float.floatToIntBits(Float.NaN)));
    }

    private static GameModel mission(int id, int difficulty, float elapsed, int units) {
        GameModel model = Challenge.PRESETS[id].create(difficulty, 42, id, "");
        model.outcome = GameModel.WON; model.terminalReason = GameModel.TERMINAL_VICTORY;
        model.elapsed = elapsed; model.unitsSent = units; model.captures = 2;
        model.objectiveProgress = model.objectiveSeconds;
        return model;
    }

    private static GameModel daily(int id, int difficulty, float elapsed, int units, String date) {
        GameModel model = mission(id, difficulty, elapsed, units);
        model.dailyDate = date; model.dailyVersion = Challenge.DAILY_VERSION;
        return model;
    }

    private static GameModel campaign(int rules, int difficulty, float elapsed) {
        GameModel model = new GameModel(0, difficulty, 42, rules);
        model.outcome = GameModel.WON;
        model.terminalReason = GameModel.TERMINAL_VICTORY; model.elapsed = elapsed;
        return model;
    }

    private static byte[] legacyFixture() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x46503130); out.writeInt(1);
        for (int difficulty = 0; difficulty < 3; difficulty++) for (int id = 0; id < 60; id++) {
            boolean won = difficulty == 1 && id < 6;
            out.writeInt(won ? 2500 : 0); out.writeInt(won ? 3 : 0); out.writeFloat(won ? 20 : 0);
        }
        for (int difficulty = 0; difficulty < 3; difficulty++) for (int id = 0; id < 9; id++) {
            boolean won = difficulty == 1 && id == 0;
            out.writeInt(won ? 2000 : 0); out.writeInt(won ? 3 : 0); out.writeFloat(won ? 35 : 0);
        }
        out.writeBoolean(true); out.writeBoolean(true); out.writeBoolean(true);
        out.writeInt(7); out.writeInt(2); out.writeInt(1);
        out.writeUTF("daily-v10-1:2026-10-08"); out.writeInt(1900); out.writeInt(3); out.writeFloat(45);
        out.flush(); CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
        out.writeInt((int) crc.getValue()); out.flush(); return bytes.toByteArray();
    }

    private static byte[] format2Fixture() throws IOException {
        byte[] legacy = legacyFixture();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x46503130); out.writeInt(2);
        out.write(legacy, 8, legacy.length - 12);
        out.writeInt(3);
        writeObjectiveFixture(out, 0, 1, Challenge.HOLD_KING, "", "", 20, 0, 100, -1, 20);
        writeObjectiveFixture(out, 3, 1, Challenge.KEEP_KING, "", "", 45, 0, 0, -1, 45.25f);
        writeObjectiveFixture(out, 6, 2, Challenge.BUDGET, "2026-10-09", "daily-v11-2", 0, 120, 200, 90, 0);
        out.flush(); CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
        out.writeInt((int) crc.getValue()); out.flush(); return bytes.toByteArray();
    }

    private static void writeObjectiveFixture(DataOutputStream out, int id, int difficulty, int type,
                                             String date, String dailyVersion, float seconds, int budget,
                                             float elapsed, int units, float actualSeconds) throws IOException {
        out.writeUTF(date); out.writeUTF(dailyVersion);
        out.writeInt(11); out.writeInt(2); out.writeInt(difficulty);
        out.writeInt(type); out.writeInt(id); out.writeInt(0);
        out.writeFloat(seconds); out.writeInt(budget);
        out.writeFloat(elapsed); out.writeInt(units); out.writeFloat(actualSeconds);
    }

    private static void emptyHistorical(Progress progress) {
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            for (int rules : new int[] {0, 10, 11}) for (int id = 0; id < 60; id++)
                check(progress.campaignBest(id, difficulty, rules) == 0 && progress.campaignStars(id, difficulty, rules) == 0
                    && progress.campaignTime(id, difficulty, rules) == 0 && !progress.campaignCleared(id, difficulty),
                    "No mission leaks into any campaign rules version or earned completion");
            for (int id = 0; id < 9; id++) check(progress.challengeBest[difficulty][id] == 0
                && progress.challengeStars[difficulty][id] == 0 && progress.challengeTimes[difficulty][id] == 0,
                "Revised missions leave historical arrays untouched");
        }
    }

    private static void numbers(ObjectiveResult result, double... expected) {
        Number[] arguments = result.numericArguments();
        check(arguments.length == expected.length, "Message argument count");
        for (int i = 0; i < expected.length; i++) check(arguments[i].doubleValue() == expected[i], "Exact numeric argument");
    }

    private static byte[] patchInt(byte[] original, int offset, int value) {
        byte[] bytes = original.clone(); ByteBuffer.wrap(bytes).putInt(offset, value); checksum(bytes); return bytes;
    }

    private static byte[] patchByte(byte[] original, int offset, int value) {
        byte[] bytes = original.clone(); bytes[offset] = (byte) value; checksum(bytes); return bytes;
    }

    private static void checksum(byte[] bytes) {
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) crc.getValue());
    }

    private static void reject(byte[] bytes) throws Exception {
        try { Progress.restore(bytes); throw new AssertionError("Corrupt progress accepted"); }
        catch (IOException expected) { checks++; }
    }

    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
