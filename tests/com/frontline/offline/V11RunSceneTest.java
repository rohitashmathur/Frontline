package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 3 scene acceptance tests. Outcomes are staged fixtures, not automated
 * victories or human playtesting. Reflective phase-3 access lets Phase 1 compile
 * this file without Profile.run, RunState, or the new scene constants present.
 * Native preference transactions have a separate test owner.
 */
public final class V11RunSceneTest {
    private static final int READY = 0, BATTLE = 1, COUNCIL = 2, RETRY_AVAILABLE = 3,
        COMPLETED = 4, DEFEATED = 5, ABANDONED = 6;
    private static final int[] PERKS = {1, 2, 4, 8, 16};
    private static int checks, runHome, runCouncil, runSummary;
    private static Class<?> runType;
    private static Field profileRun, profileLastRun;
    private static final List<String> failures = new ArrayList<>();
    private interface Case { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " V11 Run scene checks (staged outcomes).");
    }

    public static int run() throws Exception {
        checks = 0; failures.clear(); requireContract();
        runCase("Run entry and protected New Run", V11RunSceneTest::menuAndNewRun);
        runCase("Complete five-battle staged flows and restored scenes", V11RunSceneTest::completeFlows);
        runCase("Retry once, second defeat, and declined retry", V11RunSceneTest::retryAndEnd);
        runCase("Manual restart is a confirmed run loss, not a free restart", V11RunSceneTest::manualRunRestart);
        runCase("Surrender uses the same one-retry loss policy", V11RunSceneTest::runSurrender);
        runCase("Missing or corrupt run association cannot resume or recreate", V11RunSceneTest::associationRecovery);
        runCase("Council restoration and duplicate controls", V11RunSceneTest::councilGuards);
        runCase("Stale rendered council nonce", V11RunSceneTest::staleCouncilNonce);
        runCase("Confirmed abandonment and cancellation", V11RunSceneTest::abandonment);
        runCase("Campaign replacement protects every unfinished state", V11RunSceneTest::campaignReplacement);
        runCase("Language changes are presentation-only", V11RunSceneTest::languageIsolation);
        runCase("Restored terminal battle is processed exactly once", V11RunSceneTest::pendingResults);
        if (!failures.isEmpty()) throw new AssertionError("V11 Run scene failures after " + checks
            + " checks:\n" + String.join("\n", failures));
        return checks;
    }

    private static void requireContract() throws Exception {
        try {
            runType = Class.forName("com.frontline.offline.RunState");
            profileRun = GameScene.Profile.class.getField("run");
            try { profileLastRun = GameScene.Profile.class.getField("lastRun"); }
            catch (NoSuchFieldException optional) { profileLastRun = null; }
            runHome = GameScene.class.getField("RUN_HOME").getInt(null);
            runCouncil = GameScene.class.getField("RUN_COUNCIL").getInt(null);
            runSummary = GameScene.class.getField("RUN_SUMMARY").getInt(null);
        } catch (ClassNotFoundException | NoSuchFieldException pending) {
            throw new IllegalStateException("PENDING: Phase 3 Scene APIs are not installed; "
                + "V11RunSceneTest is compile-only until integration begins.", pending);
        }
        check(profileRun.getType() == runType, "Profile.run has the RunState type");
        check(runHome == 15 && runCouncil == 16 && runSummary == 17, "Expected Run scene constants");
    }

    private static void menuAndNewRun() throws Exception {
        Fixture empty = new Fixture(profile(), null, 780);
        check(activeRun(empty.profile) == null, "Existing installation has no invented run");
        click(empty, "run"); check(empty.scene.overlay == runHome, "Run entry opens empty Run Home");
        byte[] records = records(empty.profile);
        click(empty, "new_run");
        Run created = requiredRun(empty.profile);
        check(created.status() == READY && created.number("node") == 0 && created.number("perks") == 0
            && empty.scene.overlay == runHome, "New Run creates READY and stays at Run Home");
        byte[] saved = created.save();
        empty.scene.activateButton("new_run");
        check(Arrays.equals(saved, requiredRun(empty.profile).save()), "Double New Run tap cannot replace the new identity");
        if (empty.scene.overlay == GameScene.CONFIRM) click(empty, "cancel_replace");
        unchangedRecords(empty, records, "New Run does not grant or erase campaign records");

        Fixture campaign = new Fixture(profile(), new GameModel(0, 1, 810), 780);
        byte[] battle = campaign.scene.model.save(), oldRecords = records(campaign.profile);
        click(campaign, "run"); click(campaign, "new_run");
        check(campaign.scene.overlay == GameScene.CONFIRM && activeRun(campaign.profile) == null,
            "New Run protects the existing campaign battle before creating a run");
        click(campaign, "cancel_replace");
        check(campaign.scene.overlay == runHome && activeRun(campaign.profile) == null,
            "Cancel does not create a replacement run");
        sameBattle(campaign, battle, "Cancel retains the exact campaign battle");
        click(campaign, "new_run"); campaign.scene.back();
        sameBattle(campaign, battle, "System Back also cancels New Run replacement");
        click(campaign, "new_run"); click(campaign, "confirm_replace");
        check(requiredRun(campaign.profile).status() == READY && campaign.scene.overlay == runHome,
            "Confirmation creates READY without silently beginning battle one");
        byte[] confirmed = requiredRun(campaign.profile).save();
        campaign.scene.activateButton("confirm_replace");
        check(Arrays.equals(confirmed, requiredRun(campaign.profile).save()), "Repeated confirmation cannot create another run");
        unchangedRecords(campaign, oldRecords, "Confirmed New Run retains all prior records");

        Fixture replacing = runFixture(READY, 410, 780);
        click(replacing, "run");
        byte[] original = requiredRun(replacing.profile).save();
        click(replacing, "new_run");
        check(replacing.scene.overlay == GameScene.CONFIRM, "New Run also protects an unfinished READY run");
        click(replacing, "cancel_replace");
        check(Arrays.equals(original, requiredRun(replacing.profile).save()), "Cancelling run replacement preserves identity and seed");
        String id = requiredRun(replacing.profile).text("id");
        click(replacing, "new_run"); click(replacing, "confirm_replace");
        check(requiredRun(replacing.profile).status() == READY
            && !id.equals(requiredRun(replacing.profile).text("id")), "Confirmed replacement creates a distinct run");
    }

    private static void completeFlows() throws Exception {
        completeFlow(620, "en", -1);
        completeFlow(780, "id", -1);
        completeFlow(1120, "hi", -1);
        completeFlow(780, "en", 2);
    }

    private static void completeFlow(float height, String language, int retryNode) throws Exception {
        GameScene.Profile profile = profile(); profile.language = language;
        Fixture f = new Fixture(profile, null, height);
        byte[] initialRecords = records(profile);
        click(f, "run"); click(f, "new_run");
        for (int node = 0; node < 5; node++) {
            f = restore(f); click(f, "run");
            check(f.scene.overlay == runHome && requiredRun(f.profile).status() == READY,
                "READY reconstructs to the proper Run Home");
            click(f, "begin_run_battle");
            Run started = requiredRun(f.profile);
            check(f.scene.overlay == GameScene.NONE && started.status() == BATTLE
                && f.scene.model.runNode == node && f.scene.model.battleMode == 1,
                "Beginning each curated node installs its associated run model");
            check(started.matches(f.scene.model), "Installed model matches persisted run association");
            byte[] afterStart = started.save(), freshModel = f.scene.model.save();
            f.scene.activateButton("begin_run_battle");
            check(Arrays.equals(afterStart, requiredRun(f.profile).save()), "Duplicate begin cannot advance or mint another nonce");
            sameBattle(f, freshModel, "Duplicate begin cannot rebuild the battlefield");
            sendSmallConvoy(f);
            byte[] midBattle = f.scene.model.save(), midRun = requiredRun(f.profile).save();
            f = restore(f); click(f, "run"); click(f, "continue_run");
            check(f.scene.overlay == GameScene.NONE && requiredRun(f.profile).matches(f.scene.model),
                "Continue resumes the restored model associated with this run");
            sameBattle(f, midBattle, "Construction and Continue preserve in-flight troops, time and RNG");
            check(Arrays.equals(midRun, requiredRun(f.profile).save()), "Resume does not spend retry or change run state");
            if (node == retryNode) {
                finish(f, GameModel.LOST, 14);
                check(f.scene.overlay == runHome && requiredRun(f.profile).status() == RETRY_AVAILABLE,
                    "First defeat opens retry Run Home directly");
                long seed = f.scene.model.seed;
                int perks = f.scene.model.runPerks;
                String association = f.scene.model.runId;
                f = restore(f); click(f, "run"); click(f, "run_retry");
                check(f.scene.overlay == GameScene.NONE && f.scene.model.seed == seed
                    && f.scene.model.runPerks == perks && f.scene.model.runNode == node
                    && !association.equals(f.scene.model.runId), "Retry keeps node seed/build but replaces the association nonce");
                check(requiredRun(f.profile).number("retriesUsed") == 1, "The run-wide retry token is visibly consumed");
            }
            finish(f, GameModel.WON, 20 + node);
            check(requiredRun(f.profile).cleared() == node + 1, "Each staged victory clears exactly one node");
            byte[] processed = requiredRun(f.profile).save();
            update(f, 8);
            check(Arrays.equals(processed, requiredRun(f.profile).save()), "Repeated updates cannot reward the same win twice");
            unchangedRecords(f, initialRecords, "Run wins do not leak into campaign, missions, Daily, mastery or wins counter");
            if (node < 4) {
                check(f.scene.overlay == runCouncil && requiredRun(f.profile).status() == COUNCIL,
                    "Wins one through four open councils directly, never campaign RESULT");
                int[] offered = requiredRun(f.profile).offer();
                check(offered.length == (node == 3 ? 2 : 3), "Final council shows the two actual remaining perks");
                f = restore(f); click(f, "run");
                check(f.scene.overlay == runCouncil && Arrays.equals(offered, requiredRun(f.profile).offer()),
                    "Scene construction preserves the actual ordered council offer");
                assertOfferControls(f);
                click(f, "perk_" + offered[0]);
                check(f.scene.overlay == runHome && requiredRun(f.profile).status() == READY
                    && requiredRun(f.profile).number("node") == node + 1,
                    "Choosing a shown perk returns to the next READY node");
            } else {
                check(f.scene.overlay == runSummary && requiredRun(f.profile).status() == COMPLETED,
                    "Fifth staged win opens completion summary directly");
            }
        }
        Run completed = requiredRun(f.profile);
        check(completed.cleared() == 5 && completed.outcomes().length == (retryNode < 0 ? 5 : 6)
            && completed.number("retriesUsed") == (retryNode < 0 ? 0 : 1), "Full five-battle staged flow has correct summary accounting");
        f = restore(f); click(f, "run");
        check(f.scene.overlay == runSummary && requiredRun(f.profile).status() == COMPLETED,
            "Completed save returns to summary without replaying a battle");
        check(has(f, "new_run") && (has(f, "menu") || has(f, "home")), "Completed summary exposes New Run and Main Menu");
        clickMenu(f); click(f, "run");
        check(f.scene.overlay == runSummary, "Menu Run entry continues to display completed summary");
        String oldId = requiredRun(f.profile).text("id");
        click(f, "new_run");
        check(f.scene.overlay == runHome && requiredRun(f.profile).status() == READY
            && !oldId.equals(requiredRun(f.profile).text("id")), "New Run from completed summary requires no discard confirmation");
        unchangedRecords(f, initialRecords, "Completed summary replay does not create campaign rewards");
    }

    private static void retryAndEnd() throws Exception {
        Fixture f = runFixture(BATTLE, 610, 780);
        byte[] initialRecords = records(f.profile);
        click(f, "run"); click(f, "continue_run"); finish(f, GameModel.LOST, 11);
        check(f.scene.overlay == runHome && requiredRun(f.profile).status() == RETRY_AVAILABLE
            && requiredRun(f.profile).number("retriesUsed") == 0, "Loss offers but does not automatically spend retry");
        check(has(f, "run_retry") && has(f, "run_end"), "Retry and explicit End are both available after first loss");
        long seed = f.scene.model.seed; String firstAssociation = f.scene.model.runId;
        f = restore(f); click(f, "run"); click(f, "run_retry");
        byte[] consumed = requiredRun(f.profile).save(), replay = f.scene.model.save();
        f.scene.activateButton("run_retry");
        check(Arrays.equals(consumed, requiredRun(f.profile).save()), "Double retry cannot spend or mint a second token");
        sameBattle(f, replay, "Double retry cannot reset its battlefield");
        check(f.scene.model.seed == seed && !firstAssociation.equals(f.scene.model.runId), "Retry is same seed with distinct result proof");
        finish(f, GameModel.LOST, 12);
        check(f.scene.overlay == runSummary && requiredRun(f.profile).status() == DEFEATED
            && requiredRun(f.profile).number("retriesUsed") == 1 && requiredRun(f.profile).outcomes().length == 2,
            "Second defeat ends the run directly in summary");
        check(!has(f, "run_retry") && has(f, "new_run") && (has(f, "menu") || has(f, "home")),
            "Defeated summary has no extra retry and permits New Run or Main Menu");
        unchangedRecords(f, initialRecords, "Defeat and retry do not write campaign records");
        click(f, "new_run");
        check(f.scene.overlay == runHome && requiredRun(f.profile).status() == READY,
            "Defeated summary can create a new READY run without confirmation");

        Fixture declined = runFixture(RETRY_AVAILABLE, 611, 780);
        byte[] before = records(declined.profile);
        click(declined, "run"); click(declined, "run_end");
        Run ended = requiredRun(declined.profile);
        check(declined.scene.overlay == runSummary && (ended.status() == DEFEATED || ended.status() == ABANDONED)
            && ended.number("retriesUsed") == 0 && ended.outcomes().length == 1,
            "Declining retry explicitly ends without spending it or inventing another loss");
        byte[] end = ended.save(); declined.scene.activateButton("run_end"); update(declined, 5);
        check(Arrays.equals(end, requiredRun(declined.profile).save()), "Duplicate End is idempotent");
        unchangedRecords(declined, before, "Declined retry preserves unrelated progress");
    }

    private static void councilGuards() throws Exception {
        Fixture f = runFixture(COUNCIL, 712, 780);
        click(f, "run"); assertOfferControls(f);
        int[] offered = requiredRun(f.profile).offer();
        byte[] before = requiredRun(f.profile).save();
        f.scene.activateButton("perk_31");
        check(Arrays.equals(before, requiredRun(f.profile).save()), "Unshown combined-bit choice cannot award a perk");
        int absent = 0;
        for (int bit : PERKS) if (!contains(offered, bit)) absent = bit;
        f.scene.activateButton("perk_" + absent);
        check(Arrays.equals(before, requiredRun(f.profile).save()), "Unshown eligible perk cannot bypass the actual offer");
        click(f, "perk_" + offered[0]);
        byte[] chosen = requiredRun(f.profile).save();
        f.scene.activateButton("perk_" + offered[0]); f.scene.activateButton("perk_" + offered[1]);
        check(Arrays.equals(chosen, requiredRun(f.profile).save()), "Stale council buttons cannot award two perks");
        click(f, "begin_run_battle"); finish(f, GameModel.WON, 18);
        check(f.scene.overlay == runCouncil, "Next win reaches another guarded council");
        before = requiredRun(f.profile).save();
        f.scene.activateButton("perk_" + offered[0]);
        check(Arrays.equals(before, requiredRun(f.profile).save()), "Old owned choice cannot consume the new council");
        check(!contains(requiredRun(f.profile).offer(), offered[0]), "Owned perk is excluded from the next offer");
        assertOfferControls(f);
    }

    private static void manualRunRestart() throws Exception {
        Fixture f = runFixture(COUNCIL, 1210, 780);
        byte[] initialRecords = records(f.profile);
        click(f, "run"); click(f, "perk_" + requiredRun(f.profile).offer()[0]);
        click(f, "begin_run_battle"); sendSmallConvoy(f);
        Run active = requiredRun(f.profile);
        int previousResults = active.outcomes().length, cleared = active.cleared();
        GameModel original = f.scene.model;
        byte[] run = active.save(), battle = original.save();
        long seed = original.seed;
        int node = original.runNode, perks = original.runPerks, sent = original.unitsSent;
        float elapsed = original.elapsed;
        String association = original.runId;
        check(perks != 0 && sent > 0 && elapsed > 0, "Restart fixture has a chosen perk and an actually played battle");
        click(f, "restart");
        check(f.scene.overlay == GameScene.CONFIRM, "Active run toolbar restart requires confirmation");
        update(f, 20); click(f, "cancel_replace");
        check(f.scene.overlay == GameScene.PAUSE && Arrays.equals(run, requiredRun(f.profile).save()),
            "Restart cancellation pauses the identical run without consuming retry");
        sameBattle(f, battle, "Restart cancellation is byte-exact, including time, convoys and RNG");
        click(f, "resume"); click(f, "restart"); f.scene.back();
        check(f.scene.overlay == GameScene.PAUSE && Arrays.equals(run, requiredRun(f.profile).save()),
            "System Back also cancels manual run restart");
        sameBattle(f, battle, "Back cancellation cannot reset a partially played battle");
        click(f, "resume"); click(f, "restart"); click(f, "confirm_replace");
        Run firstLoss = requiredRun(f.profile);
        check(f.scene.model == original && original.outcome == GameModel.LOST
            && original.terminalReason == GameModel.TERMINAL_SURRENDER
            && original.unitsSent == sent && original.elapsed == elapsed && association.equals(original.runId),
            "Confirmed restart ends the same battle as surrender instead of installing a fresh model");
        check(f.scene.overlay == runHome && firstLoss.status() == RETRY_AVAILABLE
            && firstLoss.number("retriesUsed") == 0 && firstLoss.outcomes().length == previousResults + 1
            && firstLoss.cleared() == cleared, "First manual restart records exactly one loss and offers the unspent retry");
        assertSurrenderResult(firstLoss, previousResults);
        byte[] finished = firstLoss.save();
        f.scene.activateButton("confirm_replace"); update(f, 5);
        check(Arrays.equals(finished, requiredRun(f.profile).save()), "Duplicate restart confirmation cannot add a loss or spend retry");
        unchangedRecords(f, initialRecords, "Manual restart cannot change campaign, mission, Daily or mastery records");

        click(f, "run_retry");
        GameModel retry = f.scene.model;
        check(retry != original && retry.outcome == GameModel.PLAYING && retry.seed == seed
            && retry.runNode == node && retry.runPerks == perks && !association.equals(retry.runId)
            && requiredRun(f.profile).number("retriesUsed") == 1, "Only the explicit paid retry creates a fresh same-seed same-build battle");
        sendSmallConvoy(f); click(f, "pause");
        byte[] retryBattle = retry.save(), retryRun = requiredRun(f.profile).save();
        click(f, "restart"); click(f, "cancel_replace");
        sameBattle(f, retryBattle, "Cancelling paused retry restart preserves its spent troops and time");
        check(Arrays.equals(retryRun, requiredRun(f.profile).save()), "Cancelled second restart cannot refund the retry token");
        click(f, "restart"); click(f, "confirm_replace");
        Run defeated = requiredRun(f.profile);
        check(f.scene.model == retry && retry.outcome == GameModel.LOST && defeated.status() == DEFEATED
            && f.scene.overlay == runSummary && defeated.number("retriesUsed") == 1
            && defeated.outcomes().length == previousResults + 2 && defeated.cleared() == cleared,
            "Second manual restart is another loss and ends the run without a third battle");
        assertSurrenderResult(defeated, previousResults + 1);
        check(!has(f, "run_retry") && !has(f, "restart") && !has(f, "begin_run_battle"),
            "Defeated summary cannot offer a free restart or another retry");
        unchangedRecords(f, initialRecords, "Both manual run losses remain outside campaign progression");
    }

    private static void runSurrender() throws Exception {
        Fixture f = runFixture(BATTLE, 1211, 780);
        byte[] initialRecords = records(f.profile);
        click(f, "run"); click(f, "continue_run"); sendSmallConvoy(f);
        GameModel surrendered = f.scene.model;
        surrendered.surrender(); f.scene.update(0);
        Run first = requiredRun(f.profile);
        check(f.scene.model == surrendered && first.status() == RETRY_AVAILABLE && f.scene.overlay == runHome
            && first.outcomes().length == 1 && first.number("retriesUsed") == 0 && first.cleared() == 0,
            "Actual model surrender is processed as one run loss, not campaign RESULT or abandonment");
        assertSurrenderResult(first, 0);
        byte[] saved = first.save();
        surrendered.surrender(); update(f, 5);
        check(Arrays.equals(saved, requiredRun(f.profile).save()), "Repeated surrender cannot consume retry or duplicate its loss");
        f = restore(f); click(f, "run"); click(f, "run_retry");
        check(f.scene.model.seed == surrendered.seed && f.scene.model.runPerks == surrendered.runPerks,
            "Surrender retry restores the original node seed and build");
        f.scene.model.surrender(); f.scene.update(0);
        Run second = requiredRun(f.profile);
        check(second.status() == DEFEATED && f.scene.overlay == runSummary && second.outcomes().length == 2
            && second.number("retriesUsed") == 1 && !has(f, "run_retry"), "Second actual surrender ends the run with no retry remaining");
        assertSurrenderResult(second, 1);
        unchangedRecords(f, initialRecords, "Actual surrender and its retry never leak run records into campaign");
    }

    private static void associationRecovery() throws Exception {
        for (boolean missing : new boolean[] {true, false}) {
            GameScene.Profile profile = profile();
            Run run = Run.create(1212).begin(); setRun(profile, Run.restore(run.save()));
            GameModel wrong = missing ? null : Run.create(1212).begin().model();
            Fixture f = new Fixture(profile, wrong == null ? null : GameModel.restore(wrong.save()), 780);
            byte[] savedRun = requiredRun(profile).save(), savedModel = f.scene.model.save(), initialRecords = records(profile);
            GameModel retained = f.scene.model;
            click(f, "run");
            check(f.scene.overlay == runHome && requiredRun(profile).status() == BATTLE && !has(f, "continue_run"),
                "Missing or mismatched active association disables Continue Run");
            Draws recovery = render(f);
            check(String.join(" ", recovery.texts).contains(Localization.text(profile.language, "run.recovery")),
                "Association failure displays explicit recovery information");
            check(has(f, "run_abandon") && !has(f, "begin_run_battle") && !has(f, "run_retry"),
                "Recovery permits explicit abandonment but cannot recreate the active battle for free");
            f.scene.activateButton("continue_run"); update(f, 10);
            check(f.scene.overlay == runHome && f.scene.model == retained
                && Arrays.equals(savedRun, requiredRun(profile).save()), "Blocked Continue preserves the run and existing model identity");
            sameBattle(f, savedModel, "Recovery cannot advance or reconstruct a missing run battlefield");
            f.scene.back();
            check(f.scene.overlay == GameScene.MENU && !has(f, "resume"), "Main Menu cannot bypass a missing association through ordinary Resume");
            f.scene.activateButton("resume");
            check(f.scene.overlay == GameScene.MENU && f.scene.model == retained, "Unavailable main-menu Resume cannot activate an unassociated battle");
            unchangedRecords(f, initialRecords, "Association recovery keeps unrelated campaign progress intact");
        }

        Fixture valid = runFixture(BATTLE, 1213, 780);
        byte[] initialRecords = records(valid.profile), corrupt = requiredRun(valid.profile).save();
        GameModel orphan = GameModel.restore(valid.scene.model.save());
        corrupt[corrupt.length - 1] ^= 1;
        try {
            Run.restore(corrupt);
            throw new AssertionError("Corrupt run fixture must fail its checksum");
        } catch (IOException expected) { checks++; }
        profileRun.set(valid.profile, null);
        Fixture recovered = new Fixture(valid.profile, orphan, 780);
        check(activeRun(recovered.profile) == null && !recovered.scene.hasBattle && !has(recovered, "resume"),
            "A restored Run model with corrupt/missing Profile.run is not a resumable main-menu battle");
        byte[] orphanBytes = recovered.scene.model.save();
        recovered.scene.activateButton("resume");
        check(recovered.scene.overlay == GameScene.MENU, "Stale Resume cannot reactivate an orphan after corrupt run recovery");
        click(recovered, "run");
        check(recovered.scene.overlay == runHome && activeRun(recovered.profile) == null
            && has(recovered, "new_run") && !has(recovered, "continue_run"), "Corrupt run recovery requires an explicit New Run instead of inventing an association");
        recovered.scene.activateButton("continue_run"); update(recovered, 10);
        sameBattle(recovered, orphanBytes, "Corrupt run recovery leaves the orphan untouched rather than recreating it");
        unchangedRecords(recovered, initialRecords, "A corrupt run never erases or reinterprets campaign records");
    }

    private static void assertSurrenderResult(Run run, int index) throws Exception {
        Object result = run.outcomes()[index];
        check(result.getClass().getField("outcome").getInt(result) == GameModel.LOST
            && result.getClass().getField("terminalReason").getInt(result) == GameModel.TERMINAL_SURRENDER,
            "Persisted run result retains explicit surrender, not an invented elimination or campaign outcome");
    }

    private static void abandonment() throws Exception {
        for (int status : new int[] {READY, BATTLE, COUNCIL, RETRY_AVAILABLE}) {
            Fixture f = runFixture(status, 800 + status, 780);
            byte[] initialRecords = records(f.profile);
            click(f, "run");
            int returnView = f.scene.overlay;
            byte[] run = requiredRun(f.profile).save(), model = f.scene.model.save();
            click(f, "run_abandon");
            check(f.scene.overlay == GameScene.CONFIRM, "Run abandonment always requests explicit confirmation");
            update(f, 20); click(f, "cancel_replace");
            check(f.scene.overlay == returnView && Arrays.equals(run, requiredRun(f.profile).save()),
                "Cancel preserves exact run and returns to its original view");
            sameBattle(f, model, "Cancel and time in confirmation cannot advance the battle");
            click(f, "run_abandon"); f.scene.back();
            check(f.scene.overlay == returnView && Arrays.equals(run, requiredRun(f.profile).save()),
                "System Back cancels abandonment without spending retry or choosing a perk");
            click(f, "run_abandon"); click(f, "confirm_replace");
            check(f.scene.overlay == runSummary && requiredRun(f.profile).status() == ABANDONED,
                "Confirmed abandonment opens explicit abandoned summary");
            byte[] abandoned = requiredRun(f.profile).save();
            f.scene.activateButton("confirm_replace");
            check(Arrays.equals(abandoned, requiredRun(f.profile).save()), "Repeated abandonment confirmation is harmless");
            unchangedRecords(f, initialRecords, "Abandonment keeps all prior records and mastery");
        }
    }

    private static void staleCouncilNonce() throws Exception {
        Fixture staged = null; Run newer = null; GameModel newerModel = null; int staleBit = 0;
        for (long seed = 1; seed <= 20 && staged == null; seed++) {
            Fixture candidate = runFixture(COUNCIL, seed, 780);
            Run old = requiredRun(candidate.profile);
            int[] offer = old.offer();
            Run next = old.choose(offer[1]).begin();
            GameModel model = next.model(); terminalFixture(model, GameModel.WON, 19);
            next = next.finish(model);
            if (contains(next.offer(), offer[0])) {
                staged = candidate; newer = next; newerModel = model; staleBit = offer[0];
            }
        }
        check(staged != null, "Stale-input fixture has a common eligible perk across two different councils");
        click(staged, "run"); render(staged);
        // A queued input retains the rendered nonce while the current save advances.
        setRun(staged.profile, newer); staged.scene.model = newerModel;
        byte[] expected = newer.save();
        staged.scene.activateButton("perk_" + staleBit);
        check(Arrays.equals(expected, requiredRun(staged.profile).save()),
            "A stale rendered choice cannot use the newer council's nonce implicitly");
        render(staged); click(staged, "perk_" + staleBit);
        check(requiredRun(staged.profile).status() == READY && staged.scene.overlay == runHome,
            "Freshly rendered choice uses the new council nonce and succeeds");
    }

    private static void campaignReplacement() throws Exception {
        for (int status : new int[] {READY, BATTLE, COUNCIL, RETRY_AVAILABLE}) {
            Fixture f = runFixture(status, 910 + status, 780);
            byte[] run = requiredRun(f.profile).save(), battle = f.scene.model.save(), before = records(f.profile);
            String originalId = requiredRun(f.profile).text("id");
            click(f, "play"); click(f, "begin_attempt");
            check(f.scene.overlay == GameScene.CONFIRM, "Campaign start protects every unfinished run, including council");
            click(f, "cancel_replace");
            check(f.scene.overlay == GameScene.BRIEFING && Arrays.equals(run, requiredRun(f.profile).save()),
                "Cancelling campaign replacement preserves run without consuming council or retry");
            sameBattle(f, battle, "Cancelled campaign replacement preserves single battle slot");
            click(f, "begin_attempt"); click(f, "confirm_replace");
            check(f.scene.overlay == GameScene.NONE && f.scene.model.battleMode == 0
                && f.scene.model.runPerks == 0 && f.scene.model.runId.isEmpty() && f.scene.model.runNode == -1,
                "Confirmed replacement installs a clean Classic campaign model");
            Run active = activeRun(f.profile), archived = lastRun(f.profile);
            check(active == null || active.status() == ABANDONED,
                "Campaign replacement never leaves an orphaned unfinished run");
            if (archived != null && originalId.equals(archived.text("id")))
                check(archived.status() == ABANDONED, "Optional archived run records explicit abandonment");
            unchangedRecords(f, before, "Confirmed campaign replacement preserves prior records before a new result");
            byte[] clean = f.scene.model.save(); f.scene.activateButton("confirm_replace");
            sameBattle(f, clean, "Repeated confirmation cannot recreate or alter the campaign battle");
        }
    }

    private static void languageIsolation() throws Exception {
        for (int status : new int[] {READY, BATTLE, COUNCIL, RETRY_AVAILABLE, COMPLETED, DEFEATED}) {
            Fixture f = runFixture(status, 1020 + status, 780);
            click(f, "run");
            if (status == BATTLE) { click(f, "continue_run"); click(f, "pause"); click(f, "menu"); }
            else f.scene.back();
            check(f.scene.overlay == GameScene.MENU, "Run view can return to Main Menu without ending the run");
            byte[] run = requiredRun(f.profile).save(), battle = f.scene.model.save(), before = records(f.profile);
            click(f, "settings");
            for (String language : new String[] {"id", "hi", "en", "hi", "id", "en"}) {
                click(f, "language_" + language);
                check(language.equals(f.profile.language), "Language selection applies immediately");
                check(Arrays.equals(run, requiredRun(f.profile).save()), "Language cannot reroll offers, change build or consume retry");
                sameBattle(f, battle, "Language cannot advance or replace a run battlefield");
                unchangedRecords(f, before, "Language leaves all progress untouched");
            }
            click(f, "back"); f = restore(f);
            check("en".equals(f.profile.language), "Language survives profile/scene reconstruction");
            click(f, "run");
            check(f.scene.overlay == viewFor(status), "Language changes leave state-to-view routing intact");
        }
    }

    private static void pendingResults() throws Exception {
        for (int result : new int[] {GameModel.WON, GameModel.LOST}) {
            Fixture original = runFixture(BATTLE, 1110 + result, 780);
            byte[] before = records(original.profile);
            terminalFixture(original.scene.model, result, 22);
            Fixture restored = restore(original);
            click(restored, "run");
            if (requiredRun(restored.profile).status() == BATTLE) {
                click(restored, "continue_run"); restored.scene.update(.01f);
            }
            int wanted = result == GameModel.WON ? COUNCIL : RETRY_AVAILABLE;
            check(requiredRun(restored.profile).status() == wanted
                && restored.scene.overlay == viewFor(wanted) && requiredRun(restored.profile).outcomes().length == 1,
                "Restored terminal model is processed despite its previous process ending before run transition");
            byte[] processed = requiredRun(restored.profile).save();
            update(restored, 10); restored = restore(restored); click(restored, "run"); update(restored, 10);
            check(Arrays.equals(processed, requiredRun(restored.profile).save()), "Processed restored terminal model cannot reward twice");
            unchangedRecords(restored, before, "Recovered run result does not become a campaign record");
        }
    }

    private static Fixture runFixture(int status, long seed, float height) throws Exception {
        GameScene.Profile profile = profile();
        Run run = Run.create(seed); GameModel battle = null;
        if (status != READY) {
            run = run.begin(); battle = run.model();
            if (status == COUNCIL || status == RETRY_AVAILABLE || status == DEFEATED) {
                terminalFixture(battle, status == COUNCIL ? GameModel.WON : GameModel.LOST, 12);
                run = run.finish(battle);
                if (status == DEFEATED) {
                    run = run.retry(1); battle = run.model(); terminalFixture(battle, GameModel.LOST, 14);
                    run = run.finish(battle);
                }
            } else if (status == COMPLETED) {
                for (int i = 0; i < 5; i++) {
                    terminalFixture(battle, GameModel.WON, 15 + i); run = run.finish(battle);
                    if (i < 4) { run = run.choose(run.offer()[0]).begin(); battle = run.model(); }
                }
            }
        }
        check(run.status() == status, "Staged fixture has the requested lifecycle state");
        setRun(profile, Run.restore(run.save()));
        return new Fixture(profile, battle == null ? null : GameModel.restore(battle.save()), height);
    }

    private static Fixture restore(Fixture f) throws Exception {
        GameScene.Profile source = f.profile, copy = new GameScene.Profile();
        System.arraycopy(source.best, 0, copy.best, 0, source.best.length);
        System.arraycopy(source.stars, 0, copy.stars, 0, source.stars.length);
        System.arraycopy(source.times, 0, copy.times, 0, source.times.length);
        copy.unlocked = source.unlocked; copy.selectedSector = source.selectedSector;
        copy.difficulty = source.difficulty; copy.wins = source.wins;
        copy.sound = source.sound; copy.music = source.music; copy.haptics = source.haptics;
        copy.tutorialSeen = source.tutorialSeen; copy.cameraGuideSeen = source.cameraGuideSeen;
        copy.language = source.language;
        copy.progress = Progress.restore(source.progress.save()); copy.log = PlaytestLog.restore(source.log.save());
        Run run = activeRun(source); if (run != null) setRun(copy, Run.restore(run.save()));
        Run previous = lastRun(source);
        if (profileLastRun != null && previous != null) profileLastRun.set(copy, Run.restore(previous.save()).value);
        GameModel model = f.scene.hasBattle ? GameModel.restore(f.scene.model.save()) : null;
        return new Fixture(copy, model, f.height);
    }

    private static GameScene.Profile profile() throws Exception {
        GameScene.Profile profile = new GameScene.Profile();
        profile.tutorialSeen = true; profile.cameraGuideSeen = true; profile.log.enabled = true;
        profile.unlocked = 4; profile.wins = 7; profile.best[0] = 1500;
        profile.stars[0] = 2; profile.times[0] = 90;
        GameModel win = new GameModel(0, 1, 800);
        terminalFixture(win, GameModel.WON, 30); profile.progress.recordCampaign(win);
        GameModel mission = Challenge.PRESETS[6].create(1, 801, 6, "");
        terminalFixture(mission, GameModel.WON, 32); profile.progress.recordChallenge(mission);
        GameModel daily = Challenge.PRESETS[3].create(1, 802, 3, "2026-10-09");
        terminalFixture(daily, GameModel.WON, 60); profile.progress.recordChallenge(daily);
        return profile;
    }

    private static void terminalFixture(GameModel model, int result, float seconds) {
        model.troops.clear();
        for (GameModel.Territory tile : model.territories) {
            tile.owner = result == GameModel.WON ? GameModel.PLAYER : 1; tile.troops = 5;
        }
        model.outcome = result; model.elapsed = seconds;
        model.terminalReason = result == GameModel.WON ? GameModel.TERMINAL_VICTORY : GameModel.TERMINAL_ELIMINATED;
        model.captures = 2; model.unitsSent = 10; model.unitsLost = 3;
        model.intercepted = 1; model.cappedReinforcements = 2; model.startingKingLost = result == GameModel.LOST;
        if (model.objectiveType == Challenge.HOLD_KING || model.objectiveType == Challenge.KEEP_KING)
            model.objectiveProgress = Math.min(model.objectiveSeconds, seconds);
    }

    private static void finish(Fixture f, int outcome, float seconds) {
        check(f.scene.overlay == GameScene.NONE, "Staged result belongs to an active scene battle");
        terminalFixture(f.scene.model, outcome, seconds); f.scene.update(.01f);
    }

    private static void sendSmallConvoy(Fixture f) {
        int king = f.scene.model.originalKing(GameModel.PLAYER), target = -1;
        for (GameModel.Territory tile : f.scene.model.territories)
            if (tile.owner == GameModel.NEUTRAL) { target = tile.id; break; }
        check(target >= 0 && f.scene.model.launch(king, target, .25) > 0, "Mid-battle fixture has a real convoy");
        f.scene.update(.05f);
    }

    private static void assertOfferControls(Fixture f) throws Exception {
        int[] offered = requiredRun(f.profile).offer(); render(f);
        int controls = 0, mask = 0;
        for (GameScene.AccessibleButton button : f.scene.accessibleButtons()) {
            if (!button.id.startsWith("perk_")) continue;
            int bit = Integer.parseInt(button.id.substring(5)); controls++;
            check(contains(offered, bit) && (mask & bit) == 0 && !button.label.isEmpty(),
                "Rendered perk control is a distinct actual offered choice with a label");
            mask |= bit;
        }
        check(controls == offered.length, "Scene renders exactly the saved offer, including final two");
    }

    private static int viewFor(int status) {
        return status == COUNCIL ? runCouncil
            : status == COMPLETED || status == DEFEATED || status == ABANDONED ? runSummary : runHome;
    }

    private static byte[] records(GameScene.Profile profile) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(profile.unlocked); out.writeInt(profile.wins);
        for (int i = 0; i < profile.best.length; i++) {
            out.writeInt(profile.best[i]); out.writeInt(profile.stars[i]); out.writeFloat(profile.times[i]);
        }
        out.write(profile.progress.save()); out.flush(); return bytes.toByteArray();
    }

    private static void unchangedRecords(Fixture f, byte[] expected, String message) throws Exception {
        check(Arrays.equals(expected, records(f.profile)), message);
    }

    private static void sameBattle(Fixture f, byte[] expected, String message) throws Exception {
        check(Arrays.equals(expected, f.scene.model.save()), message);
    }

    private static void click(Fixture f, String id) {
        render(f); float[] position = UiTestControls.find(f.scene,id,f.height);
        check(position != null, "Missing rendered control " + id + " / overlay=" + f.scene.overlay);
        check(position[0] >= 0 && position[0] <= 420 && position[1] >= 0 && position[1] <= f.height,
            "Control is within viewport: " + id);
        f.scene.down(position[0], position[1]);
        check(f.scene.pressedLabel() != null, "Control is hit-testable: " + id);
        f.scene.up(position[0], position[1]);
    }

    private static void clickMenu(Fixture f) {
        click(f, has(f, "menu") ? "menu" : "home");
        check(f.scene.overlay == GameScene.MENU, "Main Menu control returns home");
    }

    private static boolean has(Fixture f, String id) { render(f); return f.scene.buttonPosition(id) != null; }
    private static Draws render(Fixture f) {
        Draws draws = new Draws(); f.scene.render(draws, f.height); return draws;
    }
    private static void update(Fixture f, int ticks) { for (int i = 0; i < ticks; i++) f.scene.update(.1f); }
    private static boolean contains(int[] values, int value) {
        for (int candidate : values) if (candidate == value) return true;
        return false;
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
            check(scene.overlay == GameScene.MENU, "Splash reaches menu without simulating restored battle");
        }
    }

    private static final class Draws implements GameScene.Graphics {
        final List<String> texts = new ArrayList<>();
        public void rect(float x, float y, float w, float h, float radius, int color) {}
        public void circle(float x, float y, float radius, int color) {}
        public void line(float x1, float y1, float x2, float y2, float width, int color) {}
        public void polygon(float[] points, int fill, int stroke, float width) {}
        public void text(String text, float x, float baseline, float size, int color, boolean bold, int align) { texts.add(text); }
    }

    private static Run activeRun(GameScene.Profile profile) throws Exception {
        Object value = profileRun.get(profile); return value == null ? null : new Run(value);
    }
    private static Run lastRun(GameScene.Profile profile) throws Exception {
        Object value = profileLastRun == null ? null : profileLastRun.get(profile);
        return value == null ? null : new Run(value);
    }
    private static Run requiredRun(GameScene.Profile profile) throws Exception {
        Run run = activeRun(profile); if (run == null) run = lastRun(profile);
        check(run != null, "Run view retains a saved run or optional terminal summary"); return run;
    }
    private static void setRun(GameScene.Profile profile, Run run) throws Exception { profileRun.set(profile, run.value); }

    /** Narrow late-bound adapter for the public RunState contract, never private Scene commands. */
    private static final class Run {
        final Object value;
        Run(Object value) { this.value = value; }
        static Run create(long seed) throws Exception {
            return new Run(invoke(runType, null, "newRun", new Class<?>[] {long.class}, seed));
        }
        static Run restore(byte[] data) throws Exception {
            return new Run(invoke(runType, null, "restore", new Class<?>[] {byte[].class}, data));
        }
        int number(String field) throws Exception { return runType.getField(field).getInt(value); }
        String text(String field) throws Exception { return (String) runType.getField(field).get(value); }
        int status() throws Exception { return number("status"); }
        byte[] save() throws Exception { return (byte[]) call("save"); }
        int[] offer() throws Exception { return (int[]) call("councilOffer"); }
        Object[] outcomes() throws Exception { return (Object[]) call("outcomes"); }
        int cleared() throws Exception { return (Integer) call("battlesCleared"); }
        GameModel model() throws Exception { return (GameModel) call("createBattle"); }
        Run begin() throws Exception { return new Run(call("beginBattle")); }
        Run finish(GameModel model) throws Exception {
            return new Run(invoke(runType, value, "finishBattle", new Class<?>[] {GameModel.class}, model));
        }
        Run retry(long nonce) throws Exception {
            return new Run(invoke(runType, value, "retry", new Class<?>[] {long.class}, nonce));
        }
        Run choose(int bit) throws Exception {
            long nonce = runType.getField("councilNonce").getLong(value);
            return new Run(invoke(runType, value, "choosePerk", new Class<?>[] {long.class, int.class}, nonce, bit));
        }
        boolean matches(GameModel model) throws Exception {
            return (Boolean) invoke(runType, value, "matchesActiveBattle", new Class<?>[] {GameModel.class}, model);
        }
        Object call(String method) throws Exception { return invoke(runType, value, method, new Class<?>[0]); }
    }

    private static Object invoke(Class<?> type, Object target, String name, Class<?>[] parameters, Object... args)
            throws Exception {
        Method method = type.getMethod(name, parameters);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw failed;
        }
    }

    private static void runCase(String name, Case test) {
        try { test.run(); }
        catch (Exception | AssertionError failure) { failures.add(name + ": " + failure); }
    }

    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
