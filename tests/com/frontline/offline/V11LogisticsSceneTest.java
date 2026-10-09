package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Standalone Phase 4 scene tests, late-bound until the prototype is activated.
 * Terminal outcomes and connected-territory setups are explicitly staged fixtures;
 * navigation and deployments use actual rendered controls and touch gestures.
 * These are not human playtests or native preference-transaction tests.
 */
public final class V11LogisticsSceneTest {
    private static int checks, selector, resultView;
    private static Class<?> factoryType, historyType, routesType, runType;
    private static Field historyField, runField, lastRunField;
    private static final List<String> failures = new ArrayList<>();
    private interface Case { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " V11 Logistics scene checks (staged outcome fixtures).");
    }

    public static int run() throws Exception {
        checks = 0; failures.clear(); requireContract();
        runCase("Selector, three maps and next-attempt difficulty", V11LogisticsSceneTest::selectorAndMaps);
        runCase("Frozen routed battle save and Continue", V11LogisticsSceneTest::resume);
        runCase("Separate results and exact record namespaces", V11LogisticsSceneTest::resultsAndRecords);
        runCase("Completed and protected active retry", V11LogisticsSceneTest::retries);
        runCase("Campaign and unfinished Run replacement", V11LogisticsSceneTest::protectedReplacement);
        runCase("Leaving logistics for Classic and Run", V11LogisticsSceneTest::leavingLogistics);
        runCase("Touch route preview, ETA and legal highlighting", V11LogisticsSceneTest::routePreview);
        runCase("Illegal routed releases and localized refusal", V11LogisticsSceneTest::illegalRelease);
        runCase("Three-language battle and record isolation", V11LogisticsSceneTest::languageIsolation);
        if (!failures.isEmpty()) throw new AssertionError("V11 Logistics scene failures after " + checks
            + " checks:\n" + String.join("\n", failures));
        return checks;
    }

    private static void requireContract() throws Exception {
        try {
            selector = GameScene.class.getField("LOGISTICS").getInt(null);
            resultView = GameScene.class.getField("LOGISTICS_RESULT").getInt(null);
            factoryType = Class.forName("com.frontline.offline.Logistics");
            historyType = Class.forName("com.frontline.offline.LogisticsRecords");
            routesType = Class.forName("com.frontline.offline.LogisticsRoutes");
            runType = Class.forName("com.frontline.offline.RunState");
            historyField = GameScene.Profile.class.getField("logisticsRecords");
            runField = GameScene.Profile.class.getField("run");
            try { lastRunField = GameScene.Profile.class.getField("lastRun"); }
            catch (NoSuchFieldException optional) { lastRunField = null; }
        } catch (ClassNotFoundException | NoSuchFieldException pending) {
            throw new IllegalStateException("PENDING: Phase 4 Scene APIs are not activated; "
                + "V11LogisticsSceneTest remains standalone and compile-only.", pending);
        }
        check(selector == 18 && resultView == 19, "Expected separate Logistics scene constants");
        check(historyField.getType() == historyType, "Profile.logisticsRecords has the independent records type");
    }

    private static void selectorAndMaps() throws Exception {
        for (int id = 0; id < 3; id++) for (int difficulty = 0; difficulty < 3; difficulty++) {
            Fixture f = new Fixture(profile(), null, new float[] {620, 780, 1120}[id]);
            byte[] campaign = campaign(f.profile), history = history(f.profile);
            click(f, "logistics");
            check(f.scene.overlay == selector, "Main-menu Logistics opens its own selector");
            Draws selection = render(f);
            check(selection.contains(Localization.text("en", "logistics.experimental")), "Selector identifies the experimental ruleset");
            for (int map = 0; map < 3; map++) check(has(f, "logistics_map_" + map), "All three purpose-built maps are selectable");
            for (int d = 0; d < 3; d++) check(has(f, "difficulty_" + d), "Selector exposes every next-attempt difficulty");
            click(f, "difficulty_" + difficulty);
            check(f.profile.difficulty == difficulty, "Selector changes the next-attempt difficulty");
            click(f, "logistics_map_" + id);
            check(f.scene.overlay == GameScene.NONE && f.scene.hasBattle, "Map control installs an active logistics battle");
            assertLogistics(f.scene.model, id, difficulty);
            check(Arrays.equals(campaign, campaign(f.profile)) && Arrays.equals(history, history(f.profile)),
                "Choosing a map or difficulty does not grant, erase or reinterpret records");
        }
    }

    private static void resume() throws Exception {
        for (int id = 0; id < 3; id++) {
            Fixture f = started(id, 1, 780);
            int source = f.scene.model.originalKing(0), target = adjacentTarget(f.scene.model, source);
            click(f, "quarter"); drag(f, source, target); f.scene.update(.05f);
            check(f.scene.model.unitsSent > 0 && !f.scene.model.troops.isEmpty(), "Resume fixture contains real routed troops in flight");
            byte[] battle = f.scene.model.save(), records = history(f.profile), campaign = campaign(f.profile);
            f = restore(f); click(f, "logistics");
            check(has(f, "continue_logistics"), "Selector offers the saved logistics battle");
            click(f, "difficulty_2");
            check(f.profile.difficulty == 2 && f.scene.model.difficulty == 1, "Changing selector difficulty cannot retune the frozen active battle");
            sameBattle(f, battle, "Difficulty selection is byte-exact for the retained battle");
            click(f, "continue_logistics");
            check(f.scene.overlay == GameScene.NONE && mapId(f.scene.model) == id, "Continue restores the same stable map identity");
            sameBattle(f, battle, "Scene reconstruction and Continue preserve map, routes, time, troops and RNG");
            check(Arrays.equals(records, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)),
                "Continue does not write any record or campaign reward");
        }
    }

    private static void resultsAndRecords() throws Exception {
        for (int id = 0; id < 3; id++) for (int difficulty = 0; difficulty < 3; difficulty++) {
            Fixture f = started(id, difficulty, 780);
            byte[] campaign = campaign(f.profile);
            int rules = f.scene.model.rulesVersion, config = modelNumber(f.scene.model, "logisticsConfigVersion");
            float seconds = 64 + id * 10 + difficulty;
            terminalFixture(f.scene.model, GameModel.WON, seconds); f.scene.update(.01f);
            check(f.scene.overlay == resultView && f.scene.overlay != GameScene.RESULT, "Staged logistics win opens its separate result directly");
            Draws result = render(f);
            check(result.contains(Localization.text(f.profile.language, "logistics.result_won")), "Result identifies a logistics victory");
            check(result.contains(GameScene.time(seconds)) && result.contains("37") && result.contains("91") && result.contains("23"),
                "Result renders elapsed time, captures, deployment and loss facts from the staged fixture");
            check(result.stars == 0 && !result.hasEnglishStars() && f.scene.model.score() == 0 && f.scene.model.stars() == 0,
                "Logistics results have no Classic speed stars or farmable campaign score");
            Object entry = record(f.profile, id, difficulty, rules, config);
            check(entry.getClass().getField("completed").getBoolean(entry)
                && entry.getClass().getField("bestElapsed").getFloat(entry) == seconds,
                "Winning fact is stored only under the exact logistics map/difficulty/rules/config key");
            Object otherDifficulty = record(f.profile, id, (difficulty + 1) % 3, rules, config);
            Object otherConfig = record(f.profile, id, difficulty, rules, config + 1);
            check(!otherDifficulty.getClass().getField("completed").getBoolean(otherDifficulty)
                && !otherConfig.getClass().getField("completed").getBoolean(otherConfig), "Records never fall back to another difficulty or configuration");
            byte[] savedRecords = history(f.profile); update(f, 10);
            check(Arrays.equals(savedRecords, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)),
                "Repeated result updates cannot reward the win twice or leak into campaign/mastery");
            f = restore(f); click(f, "logistics");
            check(Arrays.equals(savedRecords, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)),
                "Restored completed logistics attempt preserves separate record bytes");
        }
        for (int id = 0; id < 3; id++) {
            Fixture f = started(id, 1, 780);
            byte[] campaign = campaign(f.profile), before = history(f.profile);
            terminalFixture(f.scene.model, GameModel.LOST, 51); f.scene.update(.01f);
            check(f.scene.overlay == resultView && render(f).contains(Localization.text("en", "logistics.result_lost")),
                "Staged defeat has a distinct logistics result");
            check(Arrays.equals(before, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)),
                "Logistics defeat never creates a completion, campaign win, star, unlock or mastery reward");
        }
    }

    private static void retries() throws Exception {
        for (int id = 0; id < 3; id++) {
            Fixture f = started(id, 1, 780);
            long seed = f.scene.model.seed;
            click(f, "pause"); clickMenu(f); click(f, "logistics"); click(f, "difficulty_2"); click(f, "continue_logistics");
            terminalFixture(f.scene.model, GameModel.WON, 64); f.scene.update(.01f);
            byte[] records = history(f.profile), campaign = campaign(f.profile);
            click(f, "logistics_retry");
            check(f.scene.overlay == GameScene.NONE && f.scene.model.seed != seed,
                "Completed retry starts a fresh seed without discard confirmation");
            assertLogistics(f.scene.model, id, 2); fresh(f.scene.model);
            check(Arrays.equals(records, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)),
                "Completed retry retains history and never records the prior victory twice");

            click(f, "pause"); clickMenu(f); click(f, "logistics"); click(f, "difficulty_0"); click(f, "continue_logistics");
            click(f, "quarter"); drag(f, f.scene.model.originalKing(0), adjacentTarget(f.scene.model, f.scene.model.originalKing(0)));
            f.scene.update(.05f);
            GameModel original = f.scene.model;
            byte[] before = original.save(); seed = original.seed;
            clickActiveRetry(f);
            check(f.scene.overlay == GameScene.CONFIRM, "Active logistics retry requires protected replacement confirmation");
            update(f, 20); click(f, "cancel_replace");
            check(f.scene.overlay == GameScene.PAUSE && f.scene.model == original, "Cancel leaves the original logistics battle paused");
            sameBattle(f, before, "Cancelled active retry is byte-exact, including convoys, difficulty and RNG");
            click(f, "resume"); clickActiveRetry(f); f.scene.back();
            sameBattle(f, before, "System Back also cancels active logistics retry");
            click(f, "resume"); clickActiveRetry(f); click(f, "confirm_replace");
            check(f.scene.overlay == GameScene.NONE && f.scene.model != original && f.scene.model.seed != seed,
                "Only confirmation replaces an active logistics attempt with a fresh seed");
            assertLogistics(f.scene.model, id, 0); fresh(f.scene.model);
            check(Arrays.equals(records, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)),
                "Confirmed active retry does not award a discarded result or alter independent records");
        }
    }

    private static void protectedReplacement() throws Exception {
        Fixture campaignBattle = new Fixture(profile(), new GameModel(2, 1, 421), 780);
        campaignBattle.scene.model.aiVersion = 1;
        verifyReplacement(campaignBattle, 1, false);
        for (int status : new int[] {0, 1, 2, 3}) {
            GameScene.Profile profile = profile();
            Object run = invoke(runType, null, "newRun", new Class<?>[] {long.class}, 450L + status);
            GameModel battle = null;
            if (status != 0) {
                run = invoke(runType, run, "beginBattle", new Class<?>[0]);
                battle = (GameModel) invoke(runType, run, "createBattle", new Class<?>[0]);
                terminalFixture(battle, status == 3 ? GameModel.LOST : GameModel.WON, 64);
                run = invoke(runType, run, "finishBattle", new Class<?>[] {GameModel.class}, battle);
                if (status == 1) {
                    int bit = ((int[]) invoke(runType, run, "councilOffer", new Class<?>[0]))[0];
                    long nonce = runType.getField("councilNonce").getLong(run);
                    run = invoke(runType, run, "choosePerk", new Class<?>[] {long.class, int.class}, nonce, bit);
                    run = invoke(runType, run, "beginBattle", new Class<?>[0]);
                    battle = (GameModel) invoke(runType, run, "createBattle", new Class<?>[0]);
                    check(battle.runPerks != 0, "Run replacement fixture has a real selected perk");
                }
            }
            check(runType.getField("status").getInt(run) == status, "Staged Run fixture has the requested unfinished state");
            runField.set(profile, run);
            verifyReplacement(new Fixture(profile, battle, 780), status % 3, true);
        }
    }

    private static void verifyReplacement(Fixture f, int id, boolean protectsRun) throws Exception {
        byte[] battle = f.scene.model.save(), campaign = campaign(f.profile), records = history(f.profile), run = runBytes(f.profile);
        click(f, "logistics");
        sameBattle(f, battle, "Opening logistics does not replace the protected mode");
        click(f, "logistics_map_" + id);
        check(f.scene.overlay == GameScene.CONFIRM, "Logistics map selection protects unfinished campaign or Run, including councils");
        update(f, 10); click(f, "cancel_replace");
        check(f.scene.overlay == selector && Arrays.equals(run, runBytes(f.profile)), "Cancel returns to selector with identical Run identity/build/council/retry state");
        sameBattle(f, battle, "Cancelling cross-mode replacement preserves exact battle bytes");
        click(f, "logistics_map_" + id); f.scene.back();
        sameBattle(f, battle, "Back cancels cross-mode logistics replacement");
        check(Arrays.equals(run, runBytes(f.profile)), "Back preserves the exact unfinished Run snapshot");
        click(f, "logistics_map_" + id); click(f, "confirm_replace");
        check(f.scene.overlay == GameScene.NONE, "Confirmation installs the selected logistics battle");
        assertLogistics(f.scene.model, id, f.profile.difficulty);
        if (protectsRun) {
            Object current = runField.get(f.profile), archived = lastRunField == null ? null : lastRunField.get(f.profile);
            check(current == null || runType.getField("status").getInt(current) == 6, "Confirmed logistics replacement cannot orphan an unfinished Run");
            if (archived != null) check(runType.getField("status").getInt(archived) == 6,
                "Optional retained Run summary records explicit abandonment before mode replacement");
        }
        check(Arrays.equals(campaign, campaign(f.profile)) && Arrays.equals(records, history(f.profile)),
            "Confirmed mode replacement preserves all existing progression and experimental history");
    }

    private static void leavingLogistics() throws Exception {
        Fixture f = started(2, 1, 780);
        byte[] battle = f.scene.model.save(), campaign = campaign(f.profile), records = history(f.profile);
        click(f, "pause"); clickMenu(f); click(f, "play"); click(f, "begin_attempt");
        check(f.scene.overlay == GameScene.CONFIRM, "Classic start protects an unfinished logistics battle");
        click(f, "cancel_replace"); sameBattle(f, battle, "Cancelled Classic replacement preserves routed battle exactly");
        click(f, "begin_attempt"); click(f, "confirm_replace");
        check(f.scene.overlay == GameScene.NONE && f.scene.model.battleMode == 0 && f.scene.model.runPerks == 0
            && modelNumber(f.scene.model, "routingVersion") == 0 && modelNumber(f.scene.model, "logisticsId") == -1,
            "Classic replacement has neither routed rules nor logistics identity/perks");
        check(Arrays.equals(campaign, campaign(f.profile)) && Arrays.equals(records, history(f.profile)), "Returning to Classic retains separate records");

        f = started(0, 1, 780); battle = f.scene.model.save();
        campaign = campaign(f.profile); records = history(f.profile);
        click(f, "pause"); clickMenu(f); click(f, "run"); click(f, "new_run");
        check(f.scene.overlay == GameScene.CONFIRM, "New Run also protects the active logistics slot");
        click(f, "cancel_replace"); sameBattle(f, battle, "Cancelling New Run retains the routed model");
        click(f, "new_run"); click(f, "confirm_replace"); click(f, "begin_run_battle");
        check(f.scene.overlay == GameScene.NONE && f.scene.model.battleMode == 1
            && modelNumber(f.scene.model, "routingVersion") == 0 && modelNumber(f.scene.model, "logisticsId") == -1,
            "New Run starts on Classic rules, never the previous logistics routes");
        check(Arrays.equals(campaign, campaign(f.profile)) && Arrays.equals(records, history(f.profile)), "Entering Run does not merge or reset logistics records");
    }

    private static void routePreview() throws Exception {
        for (String language : new String[] {"en", "id", "hi"}) {
            Fixture f = started(0, 1, 780); f.profile.language = language;
            GameModel model = f.scene.model;
            int source = model.originalKing(0), legal = adjacentTarget(model, source), far = model.originalKing(1);
            Draws baseline = render(f); float[] start = f.scene.position(source), near = f.scene.position(legal), distant = f.scene.position(far);
            check(!baseline.hasReachableMarker(near) && !baseline.hasReachableMarker(distant),
                "No reachable-target markers appear before source selection");
            f.scene.down(start[0], start[1]); Draws selected = render(f);
            check(selected.hasReachableMarker(near), "Selecting a source draws the legal target's offset 3px army-color marker");
            check(!selected.hasReachableMarker(distant), "Illegal distant target has no reachable marker");
            check(!selected.hasReachableMarker(start), "Selected source is not marked as its own reachable target");
            f.scene.cancel();
            check(!render(f).hasReachableMarker(near), "Cancelling source selection removes the legal-target marker");

            // Staged ownership gives a multi-hop connected route around the map's holes.
            for (GameModel.Territory tile : model.territories) {
                tile.owner = tile.id == far ? 1 : 0; tile.troops = 20;
            }
            model.territories.get(source).troops = 60;
            int[] path = route(model, source, far);
            check(path != null && path.length >= 3, "Connected fixture has a real multi-hop route");
            float eta = (Float) invoke(GameModel.class, model, "routeEta", new Class<?>[] {int[].class}, path);
            click(f, "quarter"); byte[] before = model.save();
            render(f); start = f.scene.position(source); distant = f.scene.position(far);
            f.scene.down(start[0], start[1]); f.scene.move(distant[0], distant[1]);
            Draws preview = render(f);
            String routePrefix = Localization.text(language, "logistics.route", "ROUTE_TOKEN").replace("ROUTE_TOKEN", "");
            String caption = preview.startingWith(routePrefix);
            boolean edges = true;
            for (int i = 1; i < path.length; i++) edges &= preview.edge(f.scene.position(path[i - 1]), f.scene.position(path[i]));
            check(caption != null || edges, "Touch preview shows the actual routed path, not only a direct arrow");
            if (caption != null) for (int tile : path)
                check(caption.matches(".*(?<![0-9])" + (tile + 1) + "(?![0-9]).*"), "Route caption includes every traversed tile");
            check(preview.hasEta(language, eta), "Localized preview ETA agrees with the actual route estimate");
            sameBattle(f, before, "Selecting and previewing cannot deduct troops, advance simulation or change RNG");
            f.scene.up(distant[0], distant[1]);
            check(model.unitsSent == 15 && model.territories.get(source).count() == 45 && !model.troops.isEmpty(),
                "Actual routed touch release deducts the previewed integer deployment once");
            for (GameModel.Troop troop : model.troops) {
                int[] storedPath = (int[]) troop.getClass().getField("route").get(troop);
                check(Arrays.equals(path, storedPath), "Launched convoy retains the exact previewed route");
            }
        }
    }

    private static void illegalRelease() throws Exception {
        for (int id = 0; id < 3; id++) for (String language : new String[] {"en", "id", "hi"}) {
            Fixture f = started(id, 1, 780); f.profile.language = language;
            int source = f.scene.model.originalKing(0), hostile = f.scene.model.originalKing(1);
            check(route(f.scene.model, source, hostile) == null, "Initial distant enemy is unreachable through neutral interiors");
            refuseDrag(f, source, hostile);
            int friendly = -1;
            for (GameModel.Territory tile : f.scene.model.territories)
                if (!tile.capital && tile.id != source && route(f.scene.model, source, tile.id) == null) { friendly = tile.id; break; }
            check(friendly >= 0, "Fixture has a distant disconnected reinforcement target");
            f.scene.model.territories.get(friendly).owner = 0;
            check(route(f.scene.model, source, friendly) == null, "Ownership alone does not create a connected reinforcement route");
            refuseDrag(f, source, friendly);
        }
    }

    private static void refuseDrag(Fixture f, int source, int target) throws Exception {
        byte[] before = f.scene.model.save(), records = history(f.profile), campaign = campaign(f.profile);
        int troops = f.scene.model.territories.get(source).count();
        drag(f, source, target);
        Draws refusal = render(f);
        check(refusal.contains(Localization.text(f.profile.language, "logistics.refused"))
            || refusal.contains(Localization.text(f.profile.language, "logistics.refusal.no_route")),
            "Illegal routed release displays a localized actionable refusal");
        check(f.scene.model.territories.get(source).count() == troops, "Refused release deducts no troops");
        sameBattle(f, before, "Refused release leaves exact model save, counters, convoys and RNG unchanged");
        check(Arrays.equals(records, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)), "Refusal changes presentation only, not any progress");
    }

    private static void languageIsolation() throws Exception {
        for (boolean completed : new boolean[] {false, true}) {
            Fixture f = started(1, 1, 780);
            if (completed) {
                terminalFixture(f.scene.model, GameModel.WON, 64); f.scene.update(.01f);
                clickMenu(f);
            } else { click(f, "pause"); clickMenu(f); }
            byte[] battle = f.scene.model.save(), records = history(f.profile), campaign = campaign(f.profile);
            click(f, "settings");
            for (String language : new String[] {"id", "hi", "en", "hi", "id", "en"}) {
                click(f, "language_" + language);
                check(language.equals(f.profile.language), "Language applies immediately in logistics context");
                sameBattle(f, battle, "Language cannot advance, retune or replace a frozen routed battle");
                check(Arrays.equals(records, history(f.profile)) && Arrays.equals(campaign, campaign(f.profile)), "All three languages preserve experimental and campaign record bytes");
            }
            click(f, "back"); f = restore(f);
            check("en".equals(f.profile.language), "Explicit language survives reconstructed profile and scene");
            sameBattle(f, battle, "Scene reconstruction keeps the same logistics battle after language changes");
            check(Arrays.equals(records, history(f.profile)), "Scene reconstruction preserves independent logistics records");
        }
    }

    private static Fixture started(int id, int difficulty, float height) throws Exception {
        Fixture f = new Fixture(profile(), null, height);
        click(f, "logistics"); click(f, "difficulty_" + difficulty); click(f, "logistics_map_" + id);
        check(f.scene.overlay == GameScene.NONE, "Logistics fixture is installed through actual selector controls");
        assertLogistics(f.scene.model, id, difficulty); return f;
    }

    private static GameScene.Profile profile() throws Exception {
        GameScene.Profile profile = new GameScene.Profile();
        profile.tutorialSeen = true; profile.cameraGuideSeen = true; profile.log.enabled = true;
        profile.unlocked = 4; profile.wins = 7; profile.best[0] = 1500; profile.stars[0] = 2; profile.times[0] = 90;
        GameModel campaign = new GameModel(0, 1, 610);
        terminalFixture(campaign, GameModel.WON, 64); profile.progress.recordCampaign(campaign);
        GameModel mission = Challenge.PRESETS[6].create(1, 611, 6, "");
        terminalFixture(mission, GameModel.WON, 64); profile.progress.recordChallenge(mission);
        GameModel daily = Challenge.PRESETS[3].create(1, 612, 3, "2026-10-09");
        terminalFixture(daily, GameModel.WON, 64); profile.progress.recordChallenge(daily);
        check(historyField.get(profile) != null, "Profile starts with independent logistics history");
        return profile;
    }

    private static Fixture restore(Fixture original) throws Exception {
        GameScene.Profile p = original.profile, copy = new GameScene.Profile();
        System.arraycopy(p.best, 0, copy.best, 0, p.best.length); System.arraycopy(p.stars, 0, copy.stars, 0, p.stars.length);
        System.arraycopy(p.times, 0, copy.times, 0, p.times.length);
        copy.unlocked = p.unlocked; copy.selectedSector = p.selectedSector; copy.difficulty = p.difficulty; copy.wins = p.wins;
        copy.sound = p.sound; copy.music = p.music; copy.haptics = p.haptics;
        copy.tutorialSeen = p.tutorialSeen; copy.cameraGuideSeen = p.cameraGuideSeen; copy.language = p.language;
        copy.progress = Progress.restore(p.progress.save()); copy.log = PlaytestLog.restore(p.log.save());
        historyField.set(copy, invoke(historyType, null, "restore", new Class<?>[] {byte[].class}, history(p)));
        copyRun(p, copy, runField); if (lastRunField != null) copyRun(p, copy, lastRunField);
        GameModel model = original.scene.hasBattle ? GameModel.restore(original.scene.model.save()) : null;
        return new Fixture(copy, model, original.height);
    }

    private static void copyRun(GameScene.Profile source, GameScene.Profile target, Field field) throws Exception {
        Object value = field.get(source);
        if (value != null) {
            byte[] bytes = (byte[]) invoke(runType, value, "save", new Class<?>[0]);
            field.set(target, invoke(runType, null, "restore", new Class<?>[] {byte[].class}, bytes));
        }
    }

    private static void terminalFixture(GameModel model, int outcome, float seconds) {
        model.troops.clear();
        for (GameModel.Territory tile : model.territories) { tile.owner = outcome == GameModel.WON ? 0 : 1; tile.troops = 5; }
        model.outcome = outcome; model.elapsed = seconds;
        model.terminalReason = outcome == GameModel.WON ? GameModel.TERMINAL_VICTORY : GameModel.TERMINAL_ELIMINATED;
        model.captures = 37; model.unitsSent = 91; model.unitsLost = 23;
        model.intercepted = 13; model.cappedReinforcements = 7; model.startingKingLost = outcome == GameModel.LOST;
        if (model.objectiveType == Challenge.HOLD_KING || model.objectiveType == Challenge.KEEP_KING)
            model.objectiveProgress = Math.min(seconds, model.objectiveSeconds);
    }

    private static void assertLogistics(GameModel model, int id, int difficulty) throws Exception {
        check(model.battleMode == 2 && mapId(model) == id && model.difficulty == difficulty
            && modelNumber(model, "logisticsId") == id && modelNumber(model, "logisticsConfigVersion") > 0
            && modelNumber(model, "routingVersion") > 0, "Factory installs persisted, configured routed identity and visible difficulty");
        check(model.runPerks == 0 && model.runId.isEmpty() && model.runNode == -1
            && model.objectiveType == 0 && model.challengeId == -1 && model.dailyDate.isEmpty(),
            "No Run perk, mission, Daily or campaign association leaks into logistics");
    }
    private static int modelNumber(GameModel model, String field) throws Exception { return GameModel.class.getField(field).getInt(model); }
    private static int mapId(GameModel model) throws Exception {
        return (Integer) invoke(factoryType, null, "getIndex", new Class<?>[] {GameModel.class}, model);
    }
    private static int[] route(GameModel model, int source, int target) throws Exception {
        return (int[]) invoke(GameModel.class, model, "route", new Class<?>[] {int.class, int.class, int.class}, source, target, 0);
    }
    private static int adjacentTarget(GameModel model, int source) throws Exception {
        int[] neighbors = (int[]) invoke(routesType, null, "neighbors", new Class<?>[] {GameModel.class, int.class}, model, source);
        for (int target : neighbors) if (route(model, source, target) != null) return target;
        throw new AssertionError("No adjacent legal deployment target in fixture");
    }
    private static byte[] history(GameScene.Profile profile) throws Exception {
        return (byte[]) invoke(historyType, historyField.get(profile), "save", new Class<?>[0]);
    }
    private static Object record(GameScene.Profile profile, int id, int difficulty, int rules, int config) throws Exception {
        return invoke(historyType, historyField.get(profile), "get", new Class<?>[] {int.class, int.class, int.class, int.class}, id, difficulty, rules, config);
    }
    private static byte[] runBytes(GameScene.Profile profile) throws Exception {
        Object value = runField.get(profile);
        return value == null ? new byte[0] : (byte[]) invoke(runType, value, "save", new Class<?>[0]);
    }
    private static byte[] campaign(GameScene.Profile profile) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(profile.unlocked); out.writeInt(profile.wins);
        for (int i = 0; i < profile.best.length; i++) { out.writeInt(profile.best[i]); out.writeInt(profile.stars[i]); out.writeFloat(profile.times[i]); }
        out.write(profile.progress.save()); out.flush(); return bytes.toByteArray();
    }

    private static void drag(Fixture f, int source, int target) {
        render(f); float[] from = f.scene.position(source), to = f.scene.position(target);
        f.scene.down(from[0], from[1]); f.scene.move(to[0], to[1]); render(f); f.scene.up(to[0], to[1]);
    }
    private static void click(Fixture f, String id) {
        render(f); float[] position = f.scene.buttonPosition(id);
        check(position != null, "Missing rendered control " + id + " / overlay=" + f.scene.overlay);
        check(position[0] >= 0 && position[0] <= 420 && position[1] >= 0 && position[1] <= f.height, "Control fits viewport: " + id);
        f.scene.down(position[0], position[1]); check(f.scene.pressedLabel() != null, "Control is touch-accessible: " + id);
        f.scene.up(position[0], position[1]);
    }
    private static void clickMenu(Fixture f) { click(f, has(f, "menu") ? "menu" : "home"); check(f.scene.overlay == GameScene.MENU, "Main Menu control returns home"); }
    private static void clickActiveRetry(Fixture f) { click(f, has(f, "logistics_retry") ? "logistics_retry" : "restart"); }
    private static boolean has(Fixture f, String id) { render(f); return f.scene.buttonPosition(id) != null; }
    private static Draws render(Fixture f) { Draws draws = new Draws(); f.scene.render(draws, f.height); return draws; }
    private static void update(Fixture f, int ticks) { for (int i = 0; i < ticks; i++) f.scene.update(.1f); }
    private static void sameBattle(Fixture f, byte[] bytes, String message) throws Exception { check(Arrays.equals(bytes, f.scene.model.save()), message); }
    private static void fresh(GameModel model) {
        check(model.elapsed == 0 && model.outcome == GameModel.PLAYING && model.captures == 0
            && model.unitsSent == 0 && model.unitsLost == 0 && model.troops.isEmpty(), "Retry factory resets only attempt counters and armies");
    }

    private static final class Fixture {
        final GameScene.Profile profile;
        final GameScene scene;
        final float height;
        Fixture(GameScene.Profile profile, GameModel model, float height) {
            this.profile = profile; this.height = height;
            scene = new GameScene(profile, model, new GameScene.Events() {
                public void changed() {} public void cue(int kind) {}
                public long now() { return 1791504000000L; }
            });
            for (int i = 0; i < 10; i++) scene.update(.1f);
            check(scene.overlay == GameScene.MENU, "Splash reaches menu without advancing the saved attempt");
        }
    }

    private static final class Draws implements GameScene.Graphics {
        final List<String> texts = new ArrayList<>();
        final List<float[]> lines = new ArrayList<>();
        final List<Circle> circles = new ArrayList<>();
        final List<TileGeometry> hexes = new ArrayList<>();
        int stars;
        public void rect(float x, float y, float w, float h, float radius, int color) {}
        public void circle(float x, float y, float radius, int color) { circles.add(new Circle(x, y, radius, color)); }
        public void line(float x, float y, float x2, float y2, float width, int color) { lines.add(new float[] {x, y, x2, y2, width, color}); }
        public void polygon(float[] points, int fill, int stroke, float width) {
            if (points.length == 20 && fill == GameScene.COLORS[2]) stars++;
            if (points.length == 12) {
                float x = 0, y = 0;
                for (int i = 0; i < points.length; i += 2) { x += points[i]; y += points[i + 1]; }
                x /= 6; y /= 6;
                float radius = 0;
                for (int i = 0; i < points.length; i += 2) {
                    float dx = points[i] - x, dy = points[i + 1] - y;
                    radius = Math.max(radius, (float) Math.sqrt(dx * dx + dy * dy));
                }
                hexes.add(new TileGeometry(x, y, radius));
            }
        }
        public void text(String text, float x, float baseline, float size, int color, boolean bold, int align) { texts.add(text); }
        boolean contains(String text) { return String.join(" ", texts).contains(text); }
        boolean hasEnglishStars() { return String.join(" ", texts).toUpperCase(Locale.ROOT).matches(".*\\bSTARS?\\b.*"); }
        String startingWith(String prefix) {
            for (String text : texts) if (text.startsWith(prefix)) return text;
            return null;
        }
        boolean hasReachableMarker(float[] position) {
            for (TileGeometry hex : hexes) if (near(hex.x, hex.y, position)) {
                float[] inset = {hex.x + hex.radius * .5f, hex.y - hex.radius * .45f};
                for (Circle circle : circles)
                    if (circle.color == GameScene.COLORS[0] && Math.abs(circle.radius - 3) < .01f
                        && near(circle.x, circle.y, inset)) return true;
                return false;
            }
            return false;
        }
        boolean edge(float[] from, float[] to) {
            for (float[] line : lines)
                if (near(line[0], line[1], from) && near(line[2], line[3], to)
                    || near(line[0], line[1], to) && near(line[2], line[3], from)) return true;
            return false;
        }
        boolean hasEta(String language, float eta) {
            String marker = Localization.text(language, "logistics.eta", "ETA_TOKEN").split("ETA_TOKEN", -1)[0];
            NumberFormat numbers = NumberFormat.getNumberInstance(Locale.forLanguageTag(language));
            for (String text : texts) {
                int index = text.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
                if (index < 0) continue;
                String tail = text.substring(index + marker.length()).trim();
                Number parsed = numbers.parse(tail, new ParsePosition(0));
                if (parsed != null && Math.abs(parsed.floatValue() - eta) <= 1.01f) return true;
            }
            return false;
        }
        private static boolean near(float x, float y, float[] point) { return Math.abs(x - point[0]) < .75f && Math.abs(y - point[1]) < .75f; }
        private static final class TileGeometry {
            final float x, y, radius;
            TileGeometry(float x, float y, float radius) { this.x = x; this.y = y; this.radius = radius; }
        }
        private static final class Circle {
            final float x, y, radius;
            final int color;
            Circle(float x, float y, float radius, int color) { this.x = x; this.y = y; this.radius = radius; this.color = color; }
        }
    }

    private static Object invoke(Class<?> type, Object target, String method, Class<?>[] arguments, Object... values) throws Exception {
        try { return type.getMethod(method, arguments).invoke(target, values); }
        catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw failed;
        }
    }
    private static void runCase(String name, Case test) {
        try { test.run(); } catch (Exception | AssertionError failed) { failures.add(name + ": " + failed); }
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
