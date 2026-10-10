package com.frontline.offline;

import java.util.Arrays;
import java.util.UUID;

/** Staged results check event boundaries, not human difficulty or native dialogs. */
public final class V12PlaytestTest {
    private static int checks;
    private static final GameScene.Graphics GRAPHICS = new GameModelTest.NullGraphics();
    private static final class Events implements GameScene.Events {
        String feedbackAttempt;
        public void changed() {}
        public void cue(int kind) {}
        public long now() { return 1791504000000L; }
        public void battleFeedbackRequested(String attemptId) { feedbackAttempt = attemptId; }
    }
    public static void main(String[] args) throws Exception {
        navigation(); firstDispatch(); feedback(); runMilestones(); runRetryAndReplacement(); routing(); compatibility();
        System.out.println("PASS: "+checks+" V12.1 playtest event checks.");
    }
    private static GameScene scene(boolean enabled,Events events) {
        GameScene.Profile p = new GameScene.Profile();
        p.log.enabled = enabled; p.tutorialSeen = p.cameraGuideSeen = true;
        GameScene s = new GameScene(p,null,events); s.back(); return s;
    }
    private static void press(GameScene s,String id) {
        float[] p = UiTestControls.find(s,id,780);
        check(p != null,"Rendered control exists: "+id);
        s.down(p[0],p[1]); s.up(p[0],p[1]);
    }
    private static int count(GameScene s,String event) {
        int count = 0;
        for (String row : s.profile.log.exportCsv().split("\r?\n")) if (row.contains(","+event+",")) count++;
        return count;
    }
    private static void navigation() throws Exception {
        for (boolean enabled : new boolean[] {false,true}) {
            GameScene s = scene(enabled,new Events());
            s.render(GRAPHICS,780); s.render(GRAPHICS,780);
            check(count(s,"home_tap") == 0,"Rendering cannot create taps");
            press(s,"settings");
            check(count(s,"home_tap") == (enabled ? 1 : 0),"Home entry is opt-in and logged once");
            s.activateButton("settings");
            check(count(s,"home_tap") == (enabled ? 1 : 0),"Stale home button does not add a tap");
            press(s,"language_hi"); press(s,"language_hi");
            check(count(s,"language_change") == (enabled ? 1 : 0),"Language no-op is not a change");
            check(s.redeemUnlockCode("12345") && s.profile.unlocked == 59,"V7 unlock remains available");
            if (enabled) {
                String csv = s.profile.log.exportCsv();
                check(csv.contains("target=settings") && csv.contains("from=en;to=hi"),"Stable target and language codes");
                check(csv.contains("build="+AppVersion.NAME),"Events identify their recording build");
            }
            check(PlaytestLog.restore(s.profile.log.save()).exportCsv().equals(s.profile.log.exportCsv()),"New details round-trip");
        }
    }
    private static void drag(GameScene s,int source,int target) {
        s.render(GRAPHICS,780);
        float[] from = s.position(source), to = s.position(target);
        s.down(from[0],from[1]); s.move(to[0],to[1]); s.up(to[0],to[1]);
    }
    private static int target(GameModel m,int source,boolean routeRequired) {
        for (GameModel.Territory t : m.territories) if (t.id != source
            && (!routeRequired || m.route(source,t.id,GameModel.PLAYER) != null)) return t.id;
        throw new AssertionError("No target in fixture");
    }
    private static GameScene restored(GameScene s,Events events) throws Exception {
        GameScene.Profile p = new GameScene.Profile();
        p.log = PlaytestLog.restore(s.profile.log.save());
        p.attemptId = s.profile.attemptId; p.firstLaunchObserved = s.profile.firstLaunchObserved;
        p.feedbackPrompted = s.profile.feedbackPrompted; p.battleFeedback = s.profile.battleFeedback;
        if (s.profile.run != null) p.run = RunState.restore(s.profile.run.save());
        GameScene copy = new GameScene(p,GameModel.restore(s.model.save()),events);
        copy.overlay = GameScene.NONE; return copy;
    }
    private static void firstDispatch() throws Exception {
        GameScene s = scene(true,new Events()); s.start(0);
        String id = s.profile.attemptId;
        check(UUID.fromString(id).toString().equals(id),"New attempt has a UUID");
        s.model.elapsed = 3.25f;
        int source = s.model.originalKing(0), target = target(s.model,source,false);
        drag(s,source,source);
        check(count(s,"first_launch") == 0,"Same-tile release is not a dispatch");
        s.fraction = .25; drag(s,source,target); drag(s,source,target);
        check(count(s,"first_launch") == 1,"Only first actual dispatch is logged");
        check(s.profile.log.exportCsv().contains("active_seconds=3.25"),"Timing uses active battle time");
        s = restored(s,new Events()); drag(s,source,target);
        check(id.equals(s.profile.attemptId) && count(s,"first_launch") == 1,"Restore preserves dispatch identity and deduplication");
        s.start(0);
        check(!id.equals(s.profile.attemptId) && !s.profile.firstLaunchObserved,"New attempt resets tracking");
        s.profile.log.enabled = false; source = s.model.originalKing(0); target = target(s.model,source,false);
        drag(s,source,target); s.profile.log.enabled = true; drag(s,source,target);
        check(count(s,"first_launch") == 1,"Opting in later cannot fabricate first dispatch");
        GameScene.Profile legacy = new GameScene.Profile(); legacy.log.enabled = true;
        GameScene old = new GameScene(legacy,GameModel.restore(s.model.save()),new Events()); old.overlay = GameScene.NONE;
        drag(old,source,target);
        check(count(old,"first_launch") == 0 && legacy.firstLaunchObserved,"Older dispatched saves do not invent timing");
    }
    private static void terminal(GameScene s,int outcome) {
        s.model.troops.clear();
        for (GameModel.Territory t : s.model.territories) { t.owner = outcome == GameModel.WON ? 0 : 1; t.troops = 5; }
        s.model.outcome = outcome; s.model.elapsed = 12;
        s.model.terminalReason = outcome == GameModel.WON ? GameModel.TERMINAL_VICTORY : GameModel.TERMINAL_ELIMINATED;
        s.model.startingKingLost = outcome == GameModel.LOST;
        s.update(0);
    }
    private static void feedback() throws Exception {
        for (int choice = 0; choice < 3; choice++) {
            Events events = new Events(); GameScene s = scene(true,events); s.start(0);
            terminal(s,GameModel.LOST); String id = s.profile.attemptId;
            check(id.equals(events.feedbackAttempt) && s.canPromptBattleFeedback(id),"Finished attempt requests feedback");
            byte[] battle = s.model.save();
            check(s.markBattleFeedbackPrompted(id),"Visible prompt is marked once");
            check(!s.markBattleFeedbackPrompted(id) && !s.submitBattleFeedback(id,3),"Duplicate prompt and invalid rating rejected");
            check(s.submitBattleFeedback(id,choice) && !s.submitBattleFeedback(id,choice),"One rating per attempt");
            check(count(s,"battle_feedback") == 1 && Arrays.equals(battle,s.model.save()),"Rating does not change combat state");
            GameScene copy = restored(s,new Events()); copy.overlay = GameScene.RESULT;
            check(!copy.canPromptBattleFeedback(id) && !copy.submitBattleFeedback(id,choice),"Feedback guard survives restore");
            s.start(0); check(!s.submitBattleFeedback(id,choice),"Late callback cannot rate a replacement attempt");
        }
        Events events = new Events(); GameScene s = scene(false,events); s.start(0); terminal(s,GameModel.LOST);
        check(events.feedbackAttempt == null && !s.canPromptBattleFeedback(s.profile.attemptId),"Logging off hides feedback");
    }
    private static void runMilestones() throws Exception {
        GameScene s = scene(true,new Events()); press(s,"run"); press(s,"new_run");
        String runId = s.profile.run.id;
        check(count(s,"run_start") == 1,"Run starts once");
        for (int node = 0; node < RunState.BATTLE_COUNT; node++) {
            press(s,"begin_run_battle");
            s.activateButton("begin_run_battle");
            check(count(s,"node_start") == node+1,"Node start is transition-based");
            terminal(s,GameModel.WON); s.update(0);
            check(count(s,"node_end") == node+1,"Node result is committed once");
            s = restored(s,new Events()); s.back(); press(s,"menu"); press(s,"run");
            check(count(s,"node_end") == node+1,"Restored results are not re-logged");
            if (node < RunState.BATTLE_COUNT-1) {
                int perk = s.profile.run.councilOffer()[0]; press(s,"perk_"+perk);
                s.activateButton("perk_"+perk);
                check(count(s,"perk_pick") == node+1,"Perk pick is transition-based");
            }
        }
        check(count(s,"council_offer") == 4 && count(s,"run_end") == 1,"Offers and terminal run are recorded once");
        check(s.profile.log.exportCsv().contains("run_id="+runId),"Run events retain stable run identity");
        press(s,"new_run"); press(s,"run_abandon"); press(s,"cancel_replace");
        check(count(s,"run_abandon") == 0,"Cancelled abandonment is not terminal");
        press(s,"run_abandon"); press(s,"confirm_replace"); s.activateButton("confirm_replace");
        check(count(s,"run_abandon") == 1 && count(s,"run_end") == 2,"Confirmed abandonment adds one terminal event");
    }
    private static void runRetryAndReplacement() throws Exception {
        GameScene s = scene(true,new Events()); press(s,"run"); press(s,"new_run"); press(s,"begin_run_battle");
        String attemptId = s.profile.attemptId;
        terminal(s,GameModel.LOST);
        check(count(s,"node_end") == 1 && count(s,"run_end") == 0,"Retryable loss is not the end of a run");
        press(s,"run_retry"); s.activateButton("run_retry");
        check(count(s,"node_start") == 2 && !attemptId.equals(s.profile.attemptId),"Retry is a distinct attempt, logged once");
        terminal(s,GameModel.LOST);
        check(count(s,"node_end") == 2 && count(s,"run_end") == 1,"Exhausted retry ends the run once");
        press(s,"new_run"); press(s,"home"); press(s,"play"); press(s,"begin_attempt");
        check(s.overlay == GameScene.CONFIRM,"Campaign replacement still protects an unfinished run");
        press(s,"confirm_replace"); s.activateButton("confirm_replace");
        check(count(s,"run_abandon") == 1 && count(s,"run_end") == 2,"Confirmed mode replacement logs abandonment once");
    }
    private static void routing() {
        GameScene s = scene(true,new Events()); press(s,"logistics"); press(s,"logistics_map_0");
        int source = s.model.originalKing(0), unreachable = -1;
        for (GameModel.Territory t : s.model.territories)
            if (t.id != source && s.model.route(source,t.id,0) == null) { unreachable = t.id; break; }
        check(unreachable >= 0,"Fixture has an unreachable tile");
        byte[] battle = s.model.save(); drag(s,source,unreachable);
        check(count(s,"routing_refusal") == 1 && count(s,"first_launch") == 0,"Refusal is not a troop dispatch");
        check(s.profile.log.exportCsv().contains("reason=no_friendly_path"),"Stable refusal reason");
        check(Arrays.equals(battle,s.model.save()),"Refused route preserves combat state");
        drag(s,source,target(s.model,source,true));
        check(count(s,"first_launch") == 1,"Legal routed dispatch has normal first-launch tracking");
    }
    private static void compatibility() throws Exception {
        PlaytestLog log = new PlaytestLog(); log.enabled = true;
        log.add(1,"historical",null,"old detail");
        String before = log.exportCsv();
        check(!before.contains("build="),"Historical entries are not assigned a new build");
        check(PlaytestLog.restore(log.save()).exportCsv().equals(before),"Existing API and save format remain compatible");
        for (int i = 0; i <= PlaytestLog.ENTRY_LIMIT; i++) log.add(i,"home_tap",null,"target=settings",UUID.randomUUID().toString());
        check(log.size() == 500 && !log.exportCsv().contains(",historical,"),"Existing 500-entry bound remains in force");
    }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
}
