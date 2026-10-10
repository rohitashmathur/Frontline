package com.frontline.offline;

import java.util.Arrays;
import java.util.HashSet;

public final class V12ScreensTest {
    private static int checks;
    private static final GameScene.Events EVENTS = new GameScene.Events() { public void changed() {} public void cue(int kind) {} };
    public static void main(String[] args) throws Exception {
        for (String language : Localization.LANGUAGES) for (float height : new float[] {620,780,1120}) for (float touch : new float[] {64,82}) {
            GameScene.Profile profile = new GameScene.Profile(); profile.language = language; profile.tutorialSeen = true;
            profile.unlocked = 59; profile.best[0] = 1700;
            GameScene scene = new GameScene(profile,new GameModel(1,1,72),EVENTS); scene.back(); scene.minimumTouchSize = touch;
            byte[] battle = scene.model.save(), progress = profile.progress.save();
            auditPages(scene,height);
            press(scene,"settings",height);
            check(scene.overlay == GameScene.SETTINGS,"Full settings opens");
            scene.render(new GameModelTest.NullGraphics(),height);
            check(scene.buttonPosition("language_en") == null && scene.buttonPosition("export_log") == null,"Language and playtest tools start collapsed");
            press(scene,"music",height); check(!profile.music,"Music toggle works");
            press(scene,"sound",height); check(!profile.sound,"Sound toggle works");
            press(scene,"haptics",height); check(!profile.haptics,"Haptic toggle works");
            press(scene,"difficulty_2",height); check(profile.difficulty == 2 && scene.model.difficulty == 1,"Difficulty applies only to future attempts");
            press(scene,"language_id",height); check(profile.language.equals("id"),"Language picker applies selection");
            press(scene,"tools",height); press(scene,"playtest_log",height); check(profile.log.enabled,"Collapsed tools remain functional");
            check(scene.redeemUnlockCode("12345"),"V7 unlock stays functional");
            auditPages(scene,height); press(scene,"back",height); check(scene.overlay == GameScene.MENU,"Settings returns home");
            press(scene,"sectors",height); auditPages(scene,height);
            press(scene,"settings",height); press(scene,"back",height); check(scene.overlay == GameScene.SECTORS,"Settings returns to campaign");
            press(scene,"back",height); check(scene.overlay == GameScene.MENU,"Campaign Back still returns home after visiting settings");
            press(scene,"sectors",height); press(scene,"level_1",height);
            check(scene.overlay == GameScene.SECTORS && profile.selectedSector == 1,"Sector selection stays on path");
            press(scene,"resume",height); check(scene.overlay == GameScene.NONE,"Campaign continues the selected retained battle");
            check(Arrays.equals(battle,scene.model.save()) && Arrays.equals(progress,profile.progress.save()),"Presentation does not change battle or records");
            scene.pause(); press(scene,"settings",height); press(scene,"back",height); check(scene.overlay == GameScene.PAUSE,"Settings returns to pause");
            press(scene,"menu",height); press(scene,"sectors",height); press(scene,"level_0",height); press(scene,"play",height);
            check(scene.overlay == GameScene.BRIEFING,"New selected sector gets briefing"); press(scene,"brief_back",height);
            check(scene.overlay == GameScene.SECTORS,"Briefing Back retains campaign context");
            press(scene,"play",height); press(scene,"begin_attempt",height); check(scene.overlay == GameScene.CONFIRM,"Replacement still requires confirmation");
            press(scene,"cancel_replace",height); check(Arrays.equals(battle,scene.model.save()),"Cancel preserves retained round");
            scene.overlay = GameScene.MENU; scene.scrollScreen(-10000); scene.render(new GameModelTest.NullGraphics(),height);
            float[] primary = scene.buttonPosition("resume");
            if (primary != null && scene.canScrollScreen()) {
                scene.down(primary[0],primary[1]); scene.move(primary[0],primary[1]-80); scene.render(new GameModelTest.NullGraphics(),height); scene.up(primary[0],primary[1]-80);
                check(scene.overlay == GameScene.MENU,"Scrolling from a button cancels its click");
            }
            check(Arrays.equals(battle,scene.model.save()),"Scroll never changes simulation");
        }
        for (int chapter = 0; chapter < 10; chapter++) {
            GameScene.Profile p = new GameScene.Profile(); p.unlocked = 59; p.selectedSector = chapter*6+5;
            GameScene s = new GameScene(p,null,EVENTS); s.back(); press(s,"sectors",620);
            check(UiTestControls.find(s,"level_"+p.selectedSector,620) != null,"All sixty sectors reachable by chapter path");
            auditPages(s,620);
        }
        for (int mode : new int[] {GameModel.MODE_CAMPAIGN,GameModel.MODE_RUN,GameModel.MODE_LOGISTICS}) {
            GameScene.Profile p = new GameScene.Profile(); GameModel m;
            if (mode == GameModel.MODE_RUN) { p.run = RunState.newRun(9).beginBattle(); m = p.run.createBattle(); }
            else m = mode == GameModel.MODE_LOGISTICS ? Logistics.create(2,1,9) : Challenge.PRESETS[3].create(1,9,3,"");
            GameScene s = new GameScene(p,m,EVENTS); s.back(); byte[] before = m.save();
            press(s,"resume",780); check(s.overlay == GameScene.NONE && Arrays.equals(before,m.save()),"Command Deck resumes each actual battle mode");
        }
        check(AppVersion.NAME.equals("0.12.1") && AppVersion.CODE == 13,"V12.1 release number");
        System.out.println("PASS: "+checks+" V12 screen checks.");
    }
    private static void auditPages(GameScene s,float height) {
        s.scrollScreen(-10000);
        for (int page = 0; page < 60; page++) {
            GameModelTest.TextGraphics g = new GameModelTest.TextGraphics(); s.render(g,height);
            check(g.text.contains(AppVersion.NAME),"Version-only footer remains visible");
            HashSet<String> ids = new HashSet<>();
            java.util.List<GameScene.AccessibleButton> buttons = s.accessibleButtons();
            for (GameScene.AccessibleButton b : buttons) {
                check(ids.add(b.id),"No duplicate accessible control IDs");
                check(b.x >= 0 && b.x+b.width <= 420.1f && b.y >= 0 && b.y+b.height <= height+.1f,"Visible controls fit viewport");
                check(b.width+1 >= s.minimumTouchSize && b.height+1 >= s.minimumTouchSize,"Controls retain effective touch size");
                check(!b.label.isEmpty(),"Every control has localized spoken label");
            }
            for (int i = 0; i < buttons.size(); i++) for (int j = i+1; j < buttons.size(); j++) {
                GameScene.AccessibleButton a = buttons.get(i),b = buttons.get(j);
                check(Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x) <= .1f || Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y) <= .1f,"Control targets do not overlap");
            }
            if (!s.scrollScreen(35)) break;
        }
    }
    private static void press(GameScene s,String id,float height) { float[] p = UiTestControls.find(s,id,height); check(p != null,"Visible control: "+id); s.down(p[0],p[1]); s.up(p[0],p[1]); }
    private static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
}
