package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class V10SceneTest {
    private static int checks;
    private static final List<String> failures = new ArrayList<>();

    public static int run() throws Exception {
        checks = 0; failures.clear();
        runCase("A1 menu, selection and new attempt", V10SceneTest::menuAndReplacement);
        runCase("A1 restart cancellation and confirmation", V10SceneTest::restarts);
        runCase("A1 completed retry", V10SceneTest::completedRetries);
        runCase("A2 practical tutorial gates", V10SceneTest::tutorialPractice);
        runCase("A2 loss must follow an actual held king", V10SceneTest::tutorialRealKingLoss);
        runCase("A2 skip and replay preserve battle", V10SceneTest::tutorialReplay);
        runCase("A5 fixed difficulty, targets and records", V10SceneTest::difficultyAndRecords);
        runCase("A5 Legacy result isolation", V10SceneTest::legacyRecords);
        runCase("A5 sector picker new records", V10SceneTest::sectorPickerRecords);
        runCase("A6 opt-in decisions and paused time", V10SceneTest::playtestDecisions);
        runCase("B1 curated mission IDs and isolated unlocks", V10SceneTest::challengeFlows);
        runCase("B3 deterministic daily and midnight continuation", V10SceneTest::dailyFlows);
        runCase("B4 cosmetic themes and faction readability", V10SceneTest::cosmeticThemes);
        runCase("First large battlefield guidance", V10SceneTest::cameraGuidance);
        if (!failures.isEmpty()) throw new AssertionError("V10 scene failures after " + checks + " checks:\n"
            + String.join("\n", failures));
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " focused V10 scene checks.");
    }

    private static void menuAndReplacement() throws Exception {
        Fixture f = active(2, 1);
        f.profile.unlocked = 8;
        f.profile.best[0] = 1500; f.profile.stars[0] = 2; f.profile.times[0] = 80;
        byte[] battle = f.scene.model.save(), campaign = campaignState(f.profile);
        float[] resume = position(f, "resume"), play = position(f, "play");
        check(resume[1] < play[1] && "Continue Battle".equals(label(f, "resume")),
            "An unfinished menu battle has Continue Battle above New Attempt");
        check(render(f).activeAt(resume[0], resume[1]), "Continue Battle is the primary highlighted action");
        click(f, "sectors"); click(f, "level_5");
        check(f.scene.overlay == GameScene.MENU && f.profile.selectedSector == 5, "Sector selection returns to menu");
        sameBattle(f, battle, "Selecting a sector alone must not replace the active battle");
        click(f, "resume");
        check(f.scene.overlay == GameScene.NONE && f.scene.model.levelIndex == 2, "Continue resumes the original sector");
        sameBattle(f, battle, "Continue does not rebuild the selected sector or advance paused time");
        click(f, "pause"); click(f, "menu"); click(f, "play");
        check(f.scene.overlay == GameScene.BRIEFING && render(f).has(GameModel.LEVELS[5].name),
            "New Attempt briefs the selected sector before replacement");
        sameBattle(f, battle, "Opening the briefing preserves the unfinished battle");
        click(f, "begin_attempt");
        check(f.scene.overlay == GameScene.CONFIRM, "Starting a new attempt requires replacement confirmation");
        advance(f, 20); sameBattle(f, battle, "Confirmation pauses the exact battle state");
        click(f, "cancel_replace");
        check(f.scene.overlay == GameScene.BRIEFING, "Cancel returns to the paused briefing");
        sameBattle(f, battle, "Cancel preserves troops, convoys, counters and RNG bytes");
        click(f, "brief_back"); click(f, "play"); click(f, "begin_attempt"); f.scene.back();
        sameBattle(f, battle, "System Back also cancels replacement without discarding the battle");
        click(f, "begin_attempt"); click(f, "confirm_replace");
        check(f.scene.overlay == GameScene.NONE && f.scene.hasBattle && f.scene.model.levelIndex == 5,
            "Confirmation installs the selected fresh attempt");
        freshCounters(f.scene.model);
        check(f.profile.selectedSector == 5 && Arrays.equals(campaign, campaignState(f.profile)),
            "Replacement keeps the chosen sector and retains all campaign records and unlocks");
    }

    private static void restarts() throws Exception {
        Fixture f = active(2, 1); click(f, "resume");
        GameModel original = f.scene.model;
        byte[] battle = original.save(), progress = f.profile.progress.save();
        click(f, "restart");
        check(f.scene.overlay == GameScene.CONFIRM, "An active toolbar restart requires confirmation");
        advance(f, 10); click(f, "cancel_replace");
        check(f.scene.overlay == GameScene.PAUSE, "Cancelling a toolbar restart leaves the same battle paused");
        sameBattle(f, battle, "Cancelled toolbar restart is byte-exact");
        click(f, "resume"); click(f, "pause"); click(f, "restart"); click(f, "cancel_replace");
        check(f.scene.overlay == GameScene.PAUSE, "Cancelling a paused restart returns to Pause");
        sameBattle(f, battle, "Cancelled paused restart is byte-exact");
        click(f, "restart"); click(f, "confirm_replace");
        check(f.scene.overlay == GameScene.NONE && f.scene.model != original && f.scene.model.levelIndex == 2,
            "Confirmed restart installs a fresh battle in the same sector");
        freshCounters(f.scene.model);
        check(Arrays.equals(progress, f.profile.progress.save()), "Restart does not erase records or mastery");
    }

    private static void completedRetries() throws Exception {
        for (int outcome : new int[] {GameModel.WON, GameModel.LOST}) {
            Fixture f = active(0, 1); f.profile.log.enabled = true; click(f, "resume");
            finishFixture(f, outcome, 25);
            check(f.scene.overlay == GameScene.RESULT, "A completed attempt opens its result");
            int wins = f.profile.wins;
            click(f, "restart");
            check(f.scene.overlay == GameScene.NONE && !has(f, "confirm_replace"),
                "Retrying either a win or loss needs no discard confirmation");
            freshCounters(f.scene.model);
            check(eventCount(f, "abandon") == 0 && eventCount(f, "restart_request") == 0,
                "Completed retries are not abandoned battles");
            advance(f, 1);
            check(f.profile.wins == wins, "Retry does not record the previous result twice");
        }
    }

    private static void tutorialPractice() throws Exception {
        for (float height : new float[] {620, 780, 1040}) {
            GameScene.Profile profile = profile(); profile.tutorialSeen = false;
            Fixture f = new Fixture(profile, null, height);
            click(f, "play"); click(f, "begin_attempt");
            check(f.scene.overlay == GameScene.TUTORIAL && f.scene.hasBattle, "First attempt opens its practical tutorial");
            byte[] battle = f.scene.model.save();
            click(f, "tutorial_next");
            check(render(f).has("Capture a Tile") && !has(f, "tutorial_next"), "Capture practice gates Next");
            float y = practiceY(f);
            f.scene.down(110, y); f.scene.up(110, y);
            check(!has(f, "tutorial_next"), "Tapping the source is not a capture swipe");
            f.scene.down(110, y); f.scene.move(210, y); f.scene.up(210, y);
            check(!has(f, "tutorial_next"), "A swipe missing the practice target cannot complete capture");
            swipePractice(f); check(has(f, "tutorial_next"), "A capture swipe enables Next");
            click(f, "tutorial_next");
            check(render(f).has("Reinforce and Defend") && !has(f, "tutorial_next"), "Reinforcement practice also gates Next");
            click(f, "demo_quarter"); check(render(f).has("32 - 8 = 24"), "25% practice shows eight sent and 24 retained");
            click(f, "demo_half"); check(render(f).has("32 - 16 = 16"), "50% practice shows sixteen sent and retained");
            swipePractice(f);
            check(!has(f, "tutorial_next"), "Reinforcement alone cannot bypass the untried 100% amount");
            click(f, "demo_all"); check(render(f).has("32 - 32 = 0"), "100% practice shows no defenders retained");
            check(has(f, "tutorial_next"), "All amounts and reinforcement together enable Next");
            click(f, "tutorial_next");
            check(!has(f, "tutorial_next"), "King practice initially requires capture and loss");
            click(f, "demo_capture");
            check(render(f).has("BOOST x1.20") && !has(f, "tutorial_next"), "Capture alone does not finish king practice");
            click(f, "demo_lose");
            check(render(f).has("BOOST x1.00") && has(f, "tutorial_next"), "Actual king loss removes the practiced boost and enables Next");
            advance(f, 20); sameBattle(f, battle, "Every tutorial step freezes the real battle");
            click(f, "tutorial_next"); click(f, "tutorial_next");
            check(profile.tutorialSeen && f.scene.overlay == GameScene.NONE, "Completing first-run tutorial starts the installed attempt");
            sameBattle(f, battle, "Tutorial completion does not replace or advance the installed battle");
        }
    }

    private static void tutorialRealKingLoss() throws Exception {
        Fixture f = active(2, 1); click(f, "tutorial"); reachKingPractice(f);
        click(f, "demo_lose"); click(f, "demo_capture");
        check(!has(f, "tutorial_next"),
            "Lose King at zero held kings must not satisfy loss practice; capture must still be followed by a real loss");
        click(f, "demo_lose"); check(has(f, "tutorial_next"), "Losing the captured king completes the practice");
    }

    private static void tutorialReplay() throws Exception {
        Fixture f = active(2, 1); f.profile.log.enabled = true;
        byte[] battle = f.scene.model.save();
        click(f, "tutorial"); click(f, "tutorial_next"); click(f, "tutorial_skip");
        check(f.scene.overlay == GameScene.MENU, "Skipping a replay returns to menu");
        sameBattle(f, battle, "Skipping a replay preserves the resumable battle");
        click(f, "tutorial"); click(f, "tutorial_next");
        check(!has(f, "tutorial_next"), "Replaying How to Play resets the capture practice gate");
        swipePractice(f); click(f, "tutorial_next");
        click(f, "demo_quarter"); click(f, "demo_half"); click(f, "demo_all"); swipePractice(f);
        click(f, "tutorial_next"); click(f, "demo_capture"); click(f, "demo_lose");
        click(f, "tutorial_next"); click(f, "tutorial_next");
        check(f.scene.overlay == GameScene.MENU, "Finishing a replay returns to menu without starting another battle");
        sameBattle(f, battle, "Full tutorial replay preserves the active battle byte-for-byte");
        check(eventCount(f, "tutorial_start") == 2 && eventCount(f, "tutorial_skip") == 1
            && eventCount(f, "tutorial_practice") == 2 && eventCount(f, "abandon") == 0,
            "Tutorial replay records explicit practice/skip decisions, not abandonment");
        click(f, "resume"); sameBattle(f, battle, "Continue after tutorial replay resumes the original battle");
        click(f, "quarter"); check(f.scene.fraction == .25, "Battle quarter control selects 25% deployment");
        click(f, "half"); check(f.scene.fraction == .5, "Battle half control selects 50% deployment");
        click(f, "all"); check(f.scene.fraction == 1, "Battle all control selects 100% deployment");
        sameBattle(f, battle, "Changing deployment amounts alone never dispatches troops");
        GameScene.Profile first = profile(); first.tutorialSeen = false;
        Fixture fresh = new Fixture(first, null, 780);
        click(fresh, "play"); click(fresh, "begin_attempt");
        byte[] installed = fresh.scene.model.save(); click(fresh, "tutorial_skip");
        check(first.tutorialSeen && fresh.scene.overlay == GameScene.NONE, "First-run tutorial can be skipped into the selected battle");
        sameBattle(fresh, installed, "First-run Skip preserves the already installed battle");
    }

    private static void difficultyAndRecords() throws Exception {
        Fixture f = active(2, 1);
        f.profile.best[2] = 1800; f.profile.stars[2] = 2; f.profile.times[2] = 85;
        GameModel previous = won(2, 1, 90, 2); f.profile.progress.recordCampaign(previous);
        byte[] legacy = legacyState(f.profile), battle = f.scene.model.save();
        click(f, "resume"); click(f, "settings");
        check(render(f).has("NEXT ATTEMPT"), "Settings discloses when difficulty changes take effect");
        click(f, "difficulty_2"); click(f, "back");
        check(f.profile.difficulty == 2 && f.scene.model.difficulty == 1, "Settings difficulty changes only the next attempt");
        sameBattle(f, battle, "Changing difficulty leaves the active battle byte-exact");
        check(render(f).has("Normal"), "In-battle difficulty continues to identify the fixed attempt difficulty");
        click(f, "pause"); click(f, "menu"); click(f, "play");
        Draws briefing = render(f);
        check(briefing.has("Hard / Sector 3") && briefing.has("3 STARS <= " + GameScene.time(f.scene.model.level().parSeconds)),
            "Briefing shows next difficulty and discoverable star targets");
        check(briefing.has("difficulty unknown"), "Historical records are labeled Legacy with unknown difficulty");
        click(f, "brief_back"); click(f, "resume"); finishFixture(f, GameModel.WON, 60.25f);
        GameModel result = f.scene.model;
        check(f.profile.progress.best[1][2] == Math.max(previous.score(), result.score())
            && f.profile.progress.stars[1][2] == 3 && f.profile.progress.times[1][2] == 60.25f,
            "New records use the attempt's Normal difficulty, model score, stars and actual time");
        check(f.profile.progress.best[2][2] == 0 && Arrays.equals(legacy, legacyState(f.profile)),
            "A Normal result cannot overwrite Hard or Legacy records");
        Draws results = render(f);
        check(results.has("NORMAL / PB 01:00 / TIME IMPROVED") && results.has("TIME / 3-STAR TARGET")
            && results.has("01:00 / " + GameScene.time(result.level().parSeconds)),
            "Result shows improvement and actual time against the same star target");
        int wins = f.profile.wins; advance(f, 10);
        check(f.profile.wins == wins, "Result recording is idempotent while the result is open");
        click(f, "restart");
        check(f.scene.model.difficulty == 2 && f.scene.overlay == GameScene.NONE, "A completed retry applies the next-attempt difficulty");
        finishFixture(f, GameModel.WON, 80);
        check(f.profile.progress.times[2][2] == 80 && f.profile.progress.times[1][2] == 60.25f,
            "A Hard retry writes only its own difficulty record");
        click(f, "restart"); finishFixture(f, GameModel.WON, 100, 20);
        Draws slower = render(f);
        check(f.profile.progress.times[2][2] == 80 && slower.has("HARD / PB 01:20 / NO FASTER TIME")
            && slower.has("NEW BEST SCORE"), "A higher score without a faster time does not claim a time improvement");
        check(Arrays.equals(legacy, legacyState(f.profile)), "Later retries continue to preserve historical records");
    }

    private static void legacyRecords() throws Exception {
        GameScene.Profile profile = profile(); profile.best[0] = 1400; profile.stars[0] = 1; profile.times[0] = 100;
        GameModel model = new GameModel(0, 2, 91);
        model.rulesVersion = 0; model.seedKnown = false; model.seed = 0; model.historyKnown = false; model.aiVersion = 0;
        Fixture f = new Fixture(profile, model, 780); byte[] progress = profile.progress.save();
        click(f, "resume"); finishFixture(f, GameModel.WON, 20);
        check(profile.best[0] == model.score() && profile.stars[0] == model.stars() && profile.times[0] == 20,
            "A migrated Legacy battle updates only its historical record");
        check(Arrays.equals(progress, profile.progress.save()), "Legacy results are not assigned an invented difficulty or new mastery award");
        Draws result = render(f);
        check(result.has("LEGACY / DIFFICULTY UNKNOWN"), "Legacy result is explicitly identified as unknown difficulty");
        check(!result.has("territories captured this attempt."), "Unknown Legacy event history does not fabricate a result insight");
        check(profile.unlocked >= 1 && profile.cleared(0), "Legacy clears continue to preserve campaign unlocks");
    }

    private static void sectorPickerRecords() throws Exception {
        GameScene.Profile profile = profile();
        GameModel record = won(0, 1, 30, 4); profile.progress.recordCampaign(record);
        Fixture f = new Fixture(profile, null, 780); click(f, "sectors");
        Draws picker = render(f);
        check(picker.rowHas(Integer.toString(record.score()), 208, 267),
            "Sector 1's cleared row must show its new Normal best, not Legacy Best 0");
        check(picker.goldStars(208, 267) == record.stars(),
            "Sector 1's new difficulty stars must appear even when Legacy stars are zero");
    }

    private static void playtestDecisions() throws Exception {
        Fixture f = active(2, 1);
        click(f, "resume"); click(f, "pause"); click(f, "menu");
        check(!f.profile.log.enabled && f.profile.log.size() == 0, "Logging is off by default, including lifecycle decisions");
        click(f, "settings"); click(f, "playtest_log"); click(f, "back");
        check(f.profile.log.enabled, "The settings control explicitly opts into local logging");
        click(f, "resume"); advance(f, 2); click(f, "pause");
        byte[] paused = f.scene.model.save(); float elapsed = f.scene.model.elapsed;
        int size = f.profile.log.size(); f.clock.wall += 3_600_000; advance(f, 30);
        sameBattle(f, paused, "An hour of wall time plus paused updates is not active battle time");
        check(f.profile.log.size() == size && eventCount(f, "pause") == 1 && eventCount(f, "abandon") == 0,
            "Pause is one explicit event, never an inferred abandonment");
        click(f, "menu"); click(f, "tutorial"); click(f, "tutorial_skip");
        click(f, "play"); click(f, "begin_attempt"); click(f, "cancel_replace");
        sameBattle(f, paused, "Cancelled new-attempt decision retains the logged battle");
        check(eventCount(f, "new_attempt_request") == 1 && eventCount(f, "new_attempt_cancel") == 1
            && eventCount(f, "abandon") == 0, "Request/cancel are logged without abandonment");
        click(f, "begin_attempt"); click(f, "confirm_replace");
        check(eventCount(f, "new_attempt_confirm") == 1 && eventCount(f, "abandon") == 1
            && eventCount(f, "attempt_start") == 1, "Confirmed replacement logs one abandonment and one new attempt");
        List<List<String>> csv = csvRows(f.profile.log.exportCsv());
        List<String> abandoned = lastEvent(csv, "abandon");
        check(field(csv, abandoned, "elapsed_seconds").equals(Float.toString(elapsed))
            && field(csv, abandoned, "sector_index").equals("2")
            && field(csv, abandoned, "difficulty_id").equals("1")
            && field(csv, abandoned, "rules_version").equals("10"), "Decision snapshots retain factual duration and attempt context");
        click(f, "restart"); click(f, "cancel_replace"); click(f, "resume");
        check(eventCount(f, "restart_request") == 1 && eventCount(f, "restart_cancel") == 1
            && eventCount(f, "abandon") == 1, "Cancelled restart is an explicit decision, not another abandonment");
        finishFixture(f, GameModel.LOST, 15);
        check(eventCount(f, "attempt_result") == 1, "Attempt results are logged once");
        click(f, "restart");
        check(eventCount(f, "retry") == 2 && eventCount(f, "abandon") == 1, "Completed retry is logged without abandoning a completed result");
        click(f, "settings"); click(f, "export_log");
        check(f.clock.exports == 1, "Manual export reaches the platform callback");
        click(f, "playtest_log"); int disabled = f.profile.log.size(); click(f, "back");
        click(f, "pause"); click(f, "resume");
        check(!f.profile.log.enabled && f.profile.log.size() == disabled, "Opting out stops further decision logging");
    }

    private static void challengeFlows() throws Exception {
        GameScene.Profile profile = profile(); byte[] campaign = campaignState(profile);
        for (int id = 0; id < Challenge.PRESETS.length; id++) {
            Fixture f = new Fixture(profile, null, 780); Challenge preset = Challenge.PRESETS[id];
            click(f, "challenges"); click(f, "mission_tab_" + id / 3); click(f, "difficulty_" + id % 3);
            click(f, "challenge_" + id);
            check(f.scene.overlay == GameScene.BRIEFING && render(f).has(preset.objective()), "Every mission button briefs its exact objective");
            click(f, "begin_attempt"); GameModel model = f.scene.model;
            check(f.scene.overlay == GameScene.NONE && model.challengeId == id && model.levelIndex == preset.sector
                && model.objectiveType == preset.type && model.objectiveSeconds == preset.seconds
                && model.deploymentBudget == preset.budget && model.difficulty == id % 3 && model.dailyDate.isEmpty(),
                "Mission UI installs the chosen curated ID, objective and difficulty");
            check(model.objectiveTarget == model.originalKing(preset.type == Challenge.HOLD_KING ? 1 : 0)
                && model.aiVersion == 1, "Scene missions use the agreed king target and AI version");
            check(render(f).has(preset.type == Challenge.HOLD_KING ? "HOLD MARKED KING" :
                preset.type == Challenge.KEEP_KING ? "KEEP STARTING KING" : "DEPLOYMENT BUDGET"),
                "The objective remains visible during play");
            finishFixture(f, GameModel.WON, Math.max(20, preset.seconds));
            check(profile.progress.challengeBest[id % 3][id] == model.score()
                && profile.progress.challengeTimes[id % 3][id] == model.elapsed, "Mission results have separate per-difficulty records");
            check(!has(f, "next") && "Missions".equals(label(f, "challenges")), "Mission results do not route to campaign Next Sector");
            check(profile.selectedSector == 0 && Arrays.equals(campaign, campaignState(profile)),
                "Mission starts and wins cannot select, clear or unlock campaign sectors");
            click(f, "challenges"); check(f.scene.overlay == GameScene.CHALLENGES, "Mission result returns to mission selection");
        }
        check(profile.progress.objectiveMaster && profile.progress.objectiveProgress() == 3,
            "Scene mission results contribute all three objective types to mastery");
    }

    private static void dailyFlows() throws Exception {
        for (String[] dates : new String[][] {{"2000-02-28", "2000-02-29"},
            {"2024-12-31", "2025-01-01"}, {"2026-10-06", "2026-10-07"}}) {
            GameScene.Profile profile = profile(); profile.difficulty = 2;
            Fixture f = new Fixture(profile, null, 780); f.clock.wall = utcMillis(dates[0] + " 23:59:59.900");
            byte[] campaign = campaignState(profile);
            startDaily(f); GameModel active = f.scene.model;
            int id = Challenge.dailyId(dates[0]); Challenge preset = Challenge.PRESETS[id];
            check(active.difficulty == 1 && profile.difficulty == 2 && active.challengeId == id
                && active.dailyDate.equals(dates[0]) && active.seed == Challenge.dailySeed(dates[0]),
                "Daily UI fixes Normal difficulty and uses its UTC version/date seed without changing preferences");
            byte[] initial = active.save();
            Fixture repeat = new Fixture(profile(), null, 780); repeat.clock.wall = f.clock.wall;
            startDaily(repeat);
            check(Arrays.equals(initial, repeat.scene.model.save()), "The same UTC date repeats the entire scene-created daily setup");
            advance(f, 2); click(f, "pause"); click(f, "menu"); click(f, "daily");
            byte[] saved = active.save(); f.clock.wall += 100;
            Draws today = render(f);
            check(today.has(dates[1]) && today.has("Resets 00:00 UTC") && today.has("Saved daily: " + dates[0]),
                "Midnight refreshes today's card while identifying the original saved date");
            advance(f, 20); sameBattle(f, saved, "Daily browsing after midnight must not advance or replace the retained battle");
            click(f, "resume"); sameBattle(f, saved, "Continue Saved Daily resumes the old date's exact battle");
            click(f, "pause"); click(f, "menu"); click(f, "daily"); click(f, "today_mission"); click(f, "begin_attempt");
            check(f.scene.overlay == GameScene.CONFIRM, "Today's mission cannot silently discard yesterday's active daily");
            click(f, "cancel_replace"); sameBattle(f, saved, "Cancelling today's mission preserves yesterday's exact configuration");
            Fixture restored = new Fixture(profile, GameModel.restore(saved), 780); restored.clock.wall = f.clock.wall;
            check("Continue Battle".equals(label(restored, "resume")), "Restarted app makes the saved daily primary");
            click(restored, "daily"); click(restored, "resume");
            sameBattle(restored, saved, "Saved continuation survives app restart and midnight together");
            check(restored.scene.model.dailyDate.equals(dates[0]) && restored.scene.model.challengeId == id
                && restored.scene.model.objectiveSeconds == preset.seconds && restored.scene.model.deploymentBudget == preset.budget,
                "Restored daily retains its original ID, date, timers and budget");
            click(restored, "restart"); click(restored, "confirm_replace");
            check(Arrays.equals(initial, restored.scene.model.save()), "Daily restart after midnight repeats its original Normal setup and seed");
            finishFixture(restored, GameModel.WON, Math.max(20, preset.seconds));
            check(profile.progress.dailyBest(dates[0]) == restored.scene.model.score()
                && profile.progress.dailyBest(dates[1]) == 0 && profile.progress.challengeBest[1][id] == 0,
                "A retained daily result belongs only to its original date, not today or ordinary missions");
            check(profile.selectedSector == 0 && Arrays.equals(campaign, campaignState(profile)),
                "Daily results never select, unlock or clear campaign sectors");
            click(restored, "daily"); click(restored, "today_mission"); click(restored, "begin_attempt");
            check(restored.scene.overlay == GameScene.NONE && restored.scene.model.dailyDate.equals(dates[1])
                && restored.scene.model.challengeId == Challenge.dailyId(dates[1]),
                "Completed daily needs no discard confirmation before starting the new UTC date");
        }
    }

    private static void cosmeticThemes() throws Exception {
        Fixture locked = active(36, 1); click(locked, "mastery");
        check(has(locked, "theme_0") && !has(locked, "theme_1") && !has(locked, "theme_2"),
            "Unearned cosmetic themes cannot be selected");
        Fixture f = active(36, 1);
        for (int level = 0; level < 6; level++) f.profile.progress.recordCampaign(won(level, 1, 30, 2));
        byte[] battle = f.scene.model.save(), records = recordState(f.profile);
        for (float height : new float[] {620, 780, 1040}) {
            f.height = height; List<String> baselineLabels = null, baselineMarks = null; String baselineArt = null;
            for (int theme = 0; theme < 3; theme++) {
                click(f, "mastery"); click(f, "theme_" + theme); click(f, "home"); click(f, "resume");
                Draws board = render(f);
                check(f.profile.progress.theme == theme, "Earned theme control selects its cosmetic theme");
                sameBattle(f, battle, "Theme changes cannot affect model troops, multipliers, AI or RNG");
                check(Arrays.equals(records, recordState(f.profile)), "Theme changes preserve records, objective mastery and earned awards");
                List<String> labels = board.boardLabels(height);
                check(labels.size() == f.scene.model.territories.size(), "All tile counts remain visible at fit across compact/tall layouts");
                check(board.readableCounts(height), "Tile count text remains opaque, bright and within board bounds");
                if (theme == 0) { baselineLabels = labels; baselineMarks = board.factionMarks; baselineArt = board.art.toString(); }
                else {
                    check(labels.equals(baselineLabels) && board.factionMarks.equals(baselineMarks),
                        "Themes preserve troop labels, faction colors and non-color faction marks");
                    check(!board.art.toString().equals(baselineArt), "Unlocked themes change only decorative board rendering");
                }
                click(f, "pause"); click(f, "menu");
            }
        }
        Progress restored = Progress.restore(f.profile.progress.save());
        check(restored.theme == 2 && restored.themeUnlocked(2), "Earned selected theme persists with mastery records");
    }

    private static void cameraGuidance() throws Exception {
        GameScene.Profile nativeProfile = profile(); nativeProfile.cameraGuideSeen = false;
        Fixture nativeFixture = new Fixture(nativeProfile, null, 780); nativeFixture.scene.start(36);
        check(nativeFixture.scene.overlay == GameScene.NONE && !nativeProfile.cameraGuideSeen,
            "Public start remains a direct native fixture entry without camera guidance");
        GameScene.Profile profile = profile(); profile.unlocked = 59; profile.selectedSector = 29; profile.cameraGuideSeen = false;
        Fixture small = new Fixture(profile, null, 780); click(small, "play"); click(small, "begin_attempt");
        check(small.scene.overlay == GameScene.NONE && !profile.cameraGuideSeen, "Earlier maps do not consume first-large-map guidance");
        click(small, "pause"); click(small, "menu"); click(small, "sectors"); click(small, "chapter_next"); click(small, "level_30");
        click(small, "play"); click(small, "begin_attempt"); click(small, "confirm_replace");
        check(small.scene.overlay == GameScene.CAMERA_HELP && !profile.cameraGuideSeen,
            "Production button flow guides the first newly launched large map");
        byte[] battle = small.scene.model.save(); advance(small, 20);
        sameBattle(small, battle, "Camera guidance pauses the new large battle");
        click(small, "camera_ready");
        check(profile.cameraGuideSeen && small.scene.overlay == GameScene.NONE, "Acknowledging guidance persists the one-time flag");
        click(small, "restart"); click(small, "confirm_replace");
        check(small.scene.overlay == GameScene.NONE, "Subsequent large-map attempts do not repeat acknowledged guidance");
        Fixture restored = new Fixture(profile, GameModel.restore(small.scene.model.save()), 780); click(restored, "resume");
        check(restored.scene.overlay == GameScene.NONE && profile.cameraGuideSeen, "Saved large-map continuation does not repeat guidance");
        GameScene.Profile first = profile(); first.unlocked = 59; first.selectedSector = 30;
        first.tutorialSeen = false; first.cameraGuideSeen = false;
        Fixture tutorial = new Fixture(first, null, 780); click(tutorial, "play"); click(tutorial, "begin_attempt");
        check(tutorial.scene.overlay == GameScene.TUTORIAL, "First-run tutorial precedes first-large-map guidance");
        click(tutorial, "tutorial_skip");
        check(tutorial.scene.overlay == GameScene.CAMERA_HELP, "Finishing or skipping first-run tutorial still presents large-map guidance");
    }

    private static void startDaily(Fixture f) {
        click(f, "daily");
        check(render(f).has("NORMAL / fixed difficulty") && !has(f, "difficulty_2"), "Daily selection identifies its fixed difficulty");
        click(f, "today_mission");
        check(f.scene.overlay == GameScene.BRIEFING && render(f).has("Normal / " + Challenge.date(f.clock.wall)),
            "Today's briefing shows its UTC date and Normal difficulty");
        click(f, "begin_attempt");
    }

    private static void reachKingPractice(Fixture f) {
        click(f, "tutorial_next"); swipePractice(f); click(f, "tutorial_next");
        click(f, "demo_quarter"); click(f, "demo_half"); click(f, "demo_all"); swipePractice(f); click(f, "tutorial_next");
        check(render(f).has("King Boost"), "King practice is reached through both required swipes");
    }

    private static float practiceY(Fixture f) {
        for (Text text : render(f).texts) if (text.x == 110 && text.value.matches("[0-9]+")) return text.y - 7;
        throw new AssertionError("Missing tutorial source tile");
    }

    private static void swipePractice(Fixture f) {
        float y = practiceY(f); f.scene.down(110, y); f.scene.move(310, y); f.scene.up(310, y);
    }

    // Simulation results are staged fixtures; all user-facing decisions use rendered controls.
    private static void finishFixture(Fixture f, int outcome, float seconds) {
        finishFixture(f, outcome, seconds, 4);
    }

    private static void finishFixture(Fixture f, int outcome, float seconds, int captures) {
        GameModel model = f.scene.model;
        model.elapsed = seconds; model.outcome = outcome; model.captures = captures;
        if (outcome == GameModel.WON && (model.objectiveType == Challenge.HOLD_KING || model.objectiveType == Challenge.KEEP_KING))
            model.objectiveProgress = model.objectiveSeconds;
        f.scene.update(.01f);
    }

    private static GameModel won(int sector, int difficulty, float elapsed, int captures) {
        GameModel model = new GameModel(sector, difficulty, 710 + sector);
        model.outcome = GameModel.WON; model.elapsed = elapsed; model.captures = captures;
        return model;
    }

    private static GameScene.Profile profile() {
        GameScene.Profile profile = new GameScene.Profile(); profile.tutorialSeen = true; profile.cameraGuideSeen = true;
        return profile;
    }

    private static Fixture active(int sector, int difficulty) throws Exception {
        GameScene.Profile profile = profile(); profile.difficulty = difficulty;
        profile.unlocked = sector; profile.selectedSector = sector;
        GameModel model = new GameModel(sector, difficulty, 710 + sector); model.aiVersion = 1;
        model.launch(model.originalKing(0), 1, .25); model.update(.1f);
        return new Fixture(profile, model, 780);
    }

    private static final class Fixture {
        final GameScene.Profile profile;
        final Clock clock = new Clock();
        final GameScene scene;
        float height;
        Fixture(GameScene.Profile profile, GameModel model, float height) {
            this.profile = profile; this.height = height; scene = new GameScene(profile, model, clock);
            for (int tick = 0; tick < 10; tick++) scene.update(.1f);
            check(scene.overlay == GameScene.MENU, "Splash reaches menu without running the retained battle");
        }
    }

    private static final class Clock implements GameScene.Events {
        long wall = 1_700_000_000_000L;
        int changes, exports;
        public void changed() { changes++; }
        public void cue(int kind) {}
        public long now() { return wall; }
        public void exportPlaytestRequested() { exports++; }
    }

    private static void click(Fixture f, String id) {
        float[] p = position(f, id); f.scene.down(p[0], p[1]);
        check(f.scene.pressedLabel() != null, "Control is hit-testable: " + id);
        f.scene.up(p[0], p[1]);
    }

    private static float[] position(Fixture f, String id) {
        render(f); float[] p = f.scene.buttonPosition(id);
        check(p != null, "Missing rendered control: " + id + " / overlay=" + f.scene.overlay);
        check(p[0] > 0 && p[0] < 420 && p[1] > 0 && p[1] < f.height, "Control fits viewport: " + id);
        return p;
    }

    private static String label(Fixture f, String id) {
        float[] p = position(f, id); f.scene.down(p[0], p[1]); String label = f.scene.pressedLabel(); f.scene.cancel(); return label;
    }

    private static boolean has(Fixture f, String id) { render(f); return f.scene.buttonPosition(id) != null; }
    private static Draws render(Fixture f) { Draws g = new Draws(); f.scene.render(g, f.height); return g; }
    private static void advance(Fixture f, int ticks) { for (int i = 0; i < ticks; i++) f.scene.update(.1f); }
    private static void sameBattle(Fixture f, byte[] expected, String message) throws Exception {
        check(Arrays.equals(expected, f.scene.model.save()), message);
    }
    private static void freshCounters(GameModel model) {
        check(model.elapsed == 0 && model.captures == 0 && model.unitsSent == 0 && model.unitsLost == 0
            && model.objectiveProgress == 0 && model.troops.isEmpty(), "Fresh attempt resets only battle counters and convoys");
    }

    private static byte[] legacyState(GameScene.Profile profile) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        for (int i = 0; i < profile.best.length; i++) { out.writeInt(profile.best[i]); out.writeInt(profile.stars[i]); out.writeFloat(profile.times[i]); }
        out.flush(); return bytes.toByteArray();
    }
    private static byte[] campaignState(GameScene.Profile profile) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(profile.unlocked); out.write(legacyState(profile));
        for (int d = 0; d < 3; d++) for (int i = 0; i < profile.best.length; i++) {
            out.writeInt(profile.progress.best[d][i]); out.writeInt(profile.progress.stars[d][i]); out.writeFloat(profile.progress.times[d][i]);
        }
        out.flush(); return bytes.toByteArray();
    }

    private static byte[] recordState(GameScene.Profile profile) throws Exception {
        Progress records = Progress.restore(profile.progress.save()); records.theme = 0; return records.save();
    }

    private static long utcMillis(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); format.setLenient(false); return format.parse(value).getTime();
    }

    private static int eventCount(Fixture f, String event) {
        int count = 0; List<List<String>> rows = csvRows(f.profile.log.exportCsv());
        for (int i = 1; i < rows.size(); i++) if (field(rows, rows.get(i), "event").equals(event)) count++;
        return count;
    }
    private static List<String> lastEvent(List<List<String>> rows, String event) {
        for (int i = rows.size() - 1; i > 0; i--) if (field(rows, rows.get(i), "event").equals(event)) return rows.get(i);
        throw new AssertionError("Missing logged event: " + event);
    }
    private static String field(List<List<String>> rows, List<String> row, String name) {
        int column = rows.get(0).indexOf(name);
        if (column < 0) throw new AssertionError("Missing CSV column: " + name);
        return row.get(column);
    }
    private static List<List<String>> csvRows(String csv) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && (c == ',' || c == '\n')) {
                row.add(field.toString()); field.setLength(0);
                if (c == '\n') { rows.add(row); row = new ArrayList<>(); }
            } else if (quoted || c != '\r') field.append(c);
        }
        if (quoted) throw new AssertionError("Unterminated exported CSV field");
        if (field.length() > 0 || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    private static final class Text {
        final String value; final float x, y, size; final int color;
        Text(String value, float x, float y, float size, int color) { this.value = value; this.x = x; this.y = y; this.size = size; this.color = color; }
    }
    private static final class Draws implements GameScene.Graphics {
        final List<Text> texts = new ArrayList<>();
        final List<float[]> active = new ArrayList<>(), stars = new ArrayList<>();
        final List<String> factionMarks = new ArrayList<>();
        final StringBuilder art = new StringBuilder();
        public void rect(float x, float y, float w, float h, float radius, int color) {
            if (color == GameScene.COLORS[0]) active.add(new float[] {x, y, w, h});
            art.append('R').append(x).append(',').append(y).append(',').append(w).append(',').append(h).append(',').append(color);
        }
        public void circle(float x, float y, float radius, int color) {
            String mark = "C" + x + "," + y + "," + radius + "," + color; art.append(mark);
            if (factionColor(color)) factionMarks.add(mark);
        }
        public void line(float x1, float y1, float x2, float y2, float width, int color) {
            String mark = "L" + x1 + "," + y1 + "," + x2 + "," + y2 + "," + width + "," + color; art.append(mark);
            if (factionColor(color)) factionMarks.add(mark);
        }
        public void polygon(float[] points, int fill, int stroke, float width) {
            String mark = "P" + Arrays.toString(points) + "," + fill + "," + stroke + "," + width; art.append(mark);
            if (factionColor(fill) || factionColor(stroke)) factionMarks.add(mark);
            if (points.length == 20 && fill == GameScene.COLORS[2]) stars.add(points.clone());
        }
        public void text(String text, float x, float baseline, float size, int color, boolean bold, int align) {
            texts.add(new Text(text, x, baseline, size, color));
        }
        boolean has(String fragment) { for (Text text : texts) if (text.value.contains(fragment)) return true; return false; }
        boolean rowHas(String fragment, float top, float bottom) {
            for (Text text : texts) if (text.y >= top && text.y < bottom && text.value.contains(fragment)) return true; return false;
        }
        boolean activeAt(float x, float y) {
            for (float[] r : active) if (x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) return true; return false;
        }
        int goldStars(float top, float bottom) {
            int count = 0; for (float[] points : stars) if (points[1] >= top && points[1] < bottom) count++; return count;
        }
        List<String> boardLabels(float height) {
            List<String> labels = new ArrayList<>();
            for (Text text : texts) if (text.y >= 244 && text.y < height - 149 && text.value.matches("[0-9]+"))
                labels.add(text.value + ":" + text.x + ":" + text.y + ":" + text.size + ":" + text.color);
            return labels;
        }
        boolean readableCounts(float height) {
            for (Text text : texts) if (text.y >= 244 && text.y < height - 149 && text.value.matches("[0-9]+"))
                if (text.color != GameScene.WHITE || text.size < 8 || text.x < 20 || text.x > 400 || text.y - text.size < 244) return false;
            return true;
        }
        private static boolean factionColor(int color) { for (int faction : GameScene.COLORS) if (color == faction) return true; return false; }
    }

    private interface TestCase { void run() throws Exception; }
    private static void runCase(String name, TestCase test) {
        try { test.run(); } catch (Exception | AssertionError failure) { failures.add(name + ": " + failure.getMessage()); }
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
