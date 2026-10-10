package com.frontline.offline;

import java.util.Arrays;

public final class V11SceneTest {
    private static int checks;
    private static final GameScene.Events EVENTS = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
        public long now() { return 1791504000000L; }
    };
    private static final GameScene.Graphics GRAPHICS = new GameModelTest.NullGraphics();
    public static void main(String[] args) throws Exception {
        tutorialLogging(); navigationAndLanguage(); objectives();
        System.out.println("PASS: "+checks+" V11 scene checks.");
    }
    private static GameScene scene(boolean logging) {
        GameScene.Profile profile = new GameScene.Profile();
        profile.tutorialSeen = true; profile.log.enabled = logging;
        GameScene scene = new GameScene(profile,null,EVENTS); scene.back(); return scene;
    }
    private static void press(GameScene scene,String id) {
        scene.render(GRAPHICS,700);
        float[] p = UiTestControls.find(scene,id,700);
        check(p != null,"Missing control "+id+" on "+scene.overlay);
        scene.down(p[0],p[1]); scene.up(p[0],p[1]);
    }
    private static int count(GameScene scene,String event) {
        int result = 0;
        for (String row : scene.profile.log.exportCsv().split("\r?\n"))
            if (row.contains(","+event+",")) result++;
        return result;
    }
    private static void tutorialLogging() throws Exception {
        for (boolean logging : new boolean[] {true,false}) {
            GameScene s = scene(logging); press(s,"tutorial"); press(s,"tutorial_skip");
            check(count(s,"tutorial_skip") == (logging ? 1 : 0),"Immediate skip logged once");
            check(count(s,"tutorial_complete") == 0,"Skip never completes");
            s.activateButton("tutorial_skip"); check(count(s,"tutorial_skip") == (logging ? 1 : 0),"Duplicate terminal input ignored");
            press(s,"tutorial"); press(s,"tutorial_next");
            s.render(GRAPHICS,700); s.down(110,225); s.up(310,225); press(s,"tutorial_next");
            press(s,"demo_quarter"); press(s,"demo_half"); press(s,"demo_all");
            s.render(GRAPHICS,700); s.down(110,225); s.up(310,225); press(s,"tutorial_next");
            press(s,"demo_capture"); press(s,"demo_lose"); press(s,"tutorial_next"); press(s,"tutorial_next");
            check(count(s,"tutorial_complete") == (logging ? 1 : 0),"Full tutorial alone completes");
            check(count(s,"tutorial_skip") == (logging ? 1 : 0),"Replay completion does not add skip");
            press(s,"tutorial"); press(s,"tutorial_next"); s.pause(); s.update(1);
            check(count(s,"tutorial_complete") == (logging ? 1 : 0),"Pause never completes tutorial");
            press(s,"tutorial_skip");
            check(count(s,"tutorial_skip") == (logging ? 2 : 0),"Mid-tutorial skip only adds skip");
            check(s.profile.tutorialSeen,"Seen remains compatible with skip");
            PlaytestLog restored = PlaytestLog.restore(s.profile.log.save());
            check(restored.exportCsv().equals(s.profile.log.exportCsv()),"Terminal history round trips");
        }
    }
    private static void navigationAndLanguage() throws Exception {
        for (float height : new float[] {620,780,1120}) {
            GameScene s = scene(false); s.start(8); s.pause(); press(s,"menu");
            s.render(GRAPHICS,height); GameScene.AccessibleButton gear = null, daily = null;
            int gears = 0, dailies = 0;
            for (GameScene.AccessibleButton b : s.accessibleButtons()) {
                if (b.id.equals("settings")) { gear = b; gears++; }
                if (b.id.equals("daily")) { daily = b; dailies++; }
            }
            check(gears == 1 && dailies == 1,"Home has one gear and one Daily entry");
            check(gear.width >= 64 && gear.height >= 64,"Generous gear touch area");
            check(daily.y >= gear.y+gear.height+8 && daily.height >= 64,"Daily separately beneath gear");
            byte[] battle = s.model.save(), progress = s.profile.progress.save();
            press(s,"settings");
            for (String lang : new String[] {"id","hi","en","hi","id","en"}) {
                press(s,"language_"+lang);
                check(s.profile.language.equals(lang),"Language applies immediately");
                check(Arrays.equals(battle,s.model.save()),"Language cannot change frozen battle");
                check(Arrays.equals(progress,s.profile.progress.save()),"Language cannot change progress");
                s.render(GRAPHICS,height);
                check(s.accessibleButtons().stream().anyMatch(b -> b.id.equals("unlock_code") && !b.label.isEmpty()),"Localized controls remain accessible");
            }
            check(s.redeemUnlockCode("12345") && s.profile.unlocked == 59,"V7 shortcut retained");
            check(!s.profile.cleared(59),"Unlock does not mark completion");
            press(s,"back"); check(s.overlay == GameScene.MENU,"Settings Back returns home");
            press(s,"daily"); check(s.overlay == GameScene.DAILY,"Relocated Daily opens existing flow");
            check(Arrays.equals(battle,s.model.save()),"Relocated actions preserve attempt");
        }
    }
    private static void objectives() throws Exception {
        for (int type : new int[] {Challenge.HOLD_KING,Challenge.KEEP_KING,Challenge.BUDGET}) {
            GameScene s = scene(false);
            GameModel m = new Challenge("Fixture",0,type,type == Challenge.BUDGET ? 0 : 2,type == Challenge.BUDGET ? 120 : 0).create(1,4,-1,"");
            s.model = m; s.hasBattle = true; s.overlay = GameScene.NONE;
            m.elapsed = 3; m.objectiveProgress = type == Challenge.BUDGET ? 0 : 2; m.outcome = GameModel.WON;
            s.update(.01f); s.render(GRAPHICS,700);
            check(s.overlay == GameScene.RESULT,"Objective result opens");
            ObjectiveResult result = ObjectiveResult.evaluate(m);
            check(result.completed && result.policy != ObjectiveResult.Policy.CAMPAIGN,"Objective completion independent of stars");
        }
    }
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
}
