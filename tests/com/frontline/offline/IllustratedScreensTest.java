package com.frontline.offline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** UI-only examples must remain reachable without touching the retained simulation. */
public final class IllustratedScreensTest {
    private static int checks;
    private static final long NOW = 1791676800000L;
    private static final GameScene.Events EVENTS = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
        public long now() { return NOW; }
    };
    public static void main(String[] args) throws Exception {
        for (String language : Localization.LANGUAGES) for (float height : new float[] {620,780,1100})
            for (float touch : new float[] {64,96}) exercise(language,height,touch);
        dailyAndObjectives(); routedManual(); coverageGap();
        System.out.println("PASS: "+checks+" illustrated briefing/manual and coverage-spacing checks.");
    }
    private static void exercise(String language,float height,float touch) throws Exception {
        GameScene.Profile p = new GameScene.Profile(); p.language = language; p.tutorialSeen = true;
        p.unlocked = p.selectedSector = 59;
        GameScene s = new GameScene(p,new GameModel(2,1,72),EVENTS); s.back(); s.minimumTouchSize = touch;
        GameModel retained = s.model; byte[] battle = retained.save(), progress = p.progress.save();
        press(s,"play",height);
        for (int tab = 0; tab < 3; tab++) {
            press(s,"attempt_tab_"+tab,height); audit(s,height,touch);
            check(button(s,"begin_attempt") != null && button(s,"brief_back") != null && button(s,"help") != null,"Fixed briefing actions");
        }
        press(s,"help",height);
        for (int tab = 0; tab < 3; tab++) {
            press(s,"manual_tab_"+tab,height); audit(s,height,touch);
            check(button(s,"manual_practice") != null && button(s,"help_back") != null,"Fixed manual actions");
        }
        press(s,"manual_tab_0",height); press(s,"manual_collision",height);
        check(allText(s,height).contains(Localization.text(language,"manual.collision_result")),"Interception leaves ten enemy troops");
        press(s,"manual_collision",height);
        check(allText(s,height).contains(Localization.text(language,"manual.collision_example")),"Interception replay");
        String[] ids = {"manual_quarter","manual_half","manual_all"}; int[] counts = {10,20,40};
        for (int i = 0; i < ids.length; i++) {
            press(s,ids[i],height);
            check(allText(s,height).contains(Localization.text(language,"manual.deployment_count",counts[i],40-counts[i])),"Correct deployment and remaining count");
        }
        press(s,"manual_tab_1",height);
        for (int i = 0; i < 4; i++) press(s,"manual_gain",height);
        check(allText(s,height).contains(Localization.text(language,"manual.held_count",5)),"Five held kings shown");
        check(UiTestControls.find(s,"manual_gain",height) == null,"No extra gain beyond five in demo");
        for (int i = 0; i < 5; i++) press(s,"manual_lose",height);
        check(allText(s,height).contains(Localization.text(language,"manual.held_count",0)),"King losses reach zero");
        check(UiTestControls.find(s,"manual_lose",height) == null,"No negative king count");
        press(s,"manual_practice",height); check(s.overlay == GameScene.TUTORIAL,"Manual starts isolated tutorial");
        press(s,"tutorial_skip",height); check(s.overlay == GameScene.HELP,"Tutorial Skip returns to manual");
        press(s,"help_back",height); check(s.overlay == GameScene.BRIEFING,"Manual returns to briefing");
        press(s,"begin_attempt",height); check(s.overlay == GameScene.CONFIRM,"Start still protects retained battle");
        press(s,"cancel_replace",height); check(s.overlay == GameScene.BRIEFING,"Cancel returns to briefing");
        press(s,"brief_back",height); check(s.overlay == GameScene.MENU,"Briefing returns to origin");
        check(s.model == retained && Arrays.equals(battle,s.model.save()) && Arrays.equals(progress,p.progress.save()),"Tabs, demos and tutorial preserve model/progression");
    }
    private static void audit(GameScene s,float height,float touch) {
        s.scrollScreen(-10000);
        for (int page = 0; page < 100; page++) {
            s.render(new GameModelTest.NullGraphics(),height);
            List<GameScene.AccessibleButton> buttons = s.accessibleButtons();
            for (GameScene.AccessibleButton b : buttons) {
                check(b.x >= 0 && b.y >= 0 && b.x+b.width <= 420.1f && b.y+b.height <= height,"Control inside viewport");
                check(b.width >= touch && b.height >= touch,"Minimum touch target");
                check(b.label != null && !b.label.isEmpty(),"Localized accessible label");
            }
            for (int i = 0; i < buttons.size(); i++) for (int j = i+1; j < buttons.size(); j++) {
                GameScene.AccessibleButton a = buttons.get(i),b = buttons.get(j);
                check(Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x) <= .1f
                    || Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y) <= .1f,"No overlapping targets");
            }
            if (!s.scrollScreen(40)) break;
        }
    }
    private static String allText(GameScene s,float height) {
        List<String> lines = new ArrayList<>();
        s.render(new GameModelTest.NullGraphics() {
            @Override public void text(String value,float x,float y,float size,int color,boolean bold,int align) { lines.add(value); }
        },height);
        return String.join(" ",lines);
    }
    private static void dailyAndObjectives() throws Exception {
        GameScene.Profile p = new GameScene.Profile(); p.tutorialSeen = true;
        GameScene s = new GameScene(p,null,EVENTS); s.back();
        for (int id = 0; id < Challenge.PRESETS.length; id++) {
            s.overlay = GameScene.CHALLENGES; press(s,"mission_tab_"+(id/3),780); press(s,"challenge_"+id,780);
            check(allText(s,780).contains(Challenge.PRESETS[id].objective()),"Exact objective on initial tab");
            for (int tab = 0; tab < 3; tab++) { press(s,"attempt_tab_"+tab,780); audit(s,780,64); }
            press(s,"brief_back",780);
        }
        s.overlay = GameScene.DAILY; press(s,"today_mission",780); press(s,"attempt_tab_2",780);
        press(s,"help",780); press(s,"manual_practice",780); press(s,"tutorial_skip",780); press(s,"help_back",780);
        press(s,"begin_attempt",780);
        String date = Challenge.date(NOW);
        check(s.model.dailyDate.equals(date) && s.model.seed == Challenge.dailySeed(date) && s.model.difficulty == 1,"Daily identity survives manual/practice navigation");
    }
    private static void routedManual() {
        GameScene.Profile p = new GameScene.Profile(); p.tutorialSeen = true;
        GameScene s = new GameScene(p,Logistics.create(0,1,72),EVENTS); s.overlay = GameScene.LOGISTICS;
        press(s,"help",780);
        check(allText(s,780).contains(Localization.text("en","logistics.rules")),"Routed-origin manual opens routed mode rules");
        press(s,"manual_tab_0",780);
        check(allText(s,780).contains(Localization.text("en","logistics.rules")),"Routed combat travel stays route-specific");
        press(s,"help_back",780); check(s.overlay == GameScene.LOGISTICS,"Routed help returns to Lab");
    }
    private static void coverageGap() {
        for (float height : new float[] {620,680,780,900,1120}) for (float touch : new float[] {64,70,82,96}) {
            GameScene.Profile p = new GameScene.Profile(); p.tutorialSeen = true;
            GameScene s = new GameScene(p,new GameModel(2,1,72),EVENTS); s.back(); s.minimumTouchSize = touch;
            final float[] barBottom = {-1};
            s.render(new GameModelTest.NullGraphics() {
                @Override public void rect(float x,float y,float w,float h,float r,int color) {
                    if (x == 22 && w == 376 && h == 8 && color == GameScene.BORDER) barBottom[0] = y+h;
                }
            },height);
            check(barBottom[0] > 0 && button(s,"resume").y-barBottom[0] >= 13.9f,"Coverage-to-action gap at least fourteen units");
            check(!s.canScrollScreen(),"Home remains non-scrolling");
        }
    }
    private static GameScene.AccessibleButton button(GameScene s,String id) {
        for (GameScene.AccessibleButton b : s.accessibleButtons()) if (b.id.equals(id)) return b;
        return null;
    }
    private static void press(GameScene s,String id,float height) {
        float[] point = UiTestControls.find(s,id,height);
        check(point != null,"Reachable control: "+id);
        s.down(point[0],point[1]); s.up(point[0],point[1]);
    }
    private static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
}
