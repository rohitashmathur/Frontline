package com.frontline.offline;

import java.util.Arrays;
import java.util.HashSet;

public final class V12ScreensTest {
    private static int checks;
    private static final GameScene.Events EVENTS = new GameScene.Events() { public void changed() {} public void cue(int kind) {} };
    public static void main(String[] args) throws Exception {
        deckDetails(); previewCache(); campaignTrail(); logUsage();
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
        check(AppVersion.NAME.equals("0.12.2") && AppVersion.CODE == 14,"V12.2 release number");
        System.out.println("PASS: "+checks+" V12 screen checks.");
    }
    private static final class Capture implements CommandScreens.Controls {
        final java.util.Map<String,String> labels = new java.util.HashMap<>();
        final java.util.Map<String,float[]> bounds = new java.util.HashMap<>();
        public void add(String id,String label,float x,float y,float w,float h) {
            labels.put(id,label); bounds.put(id,new float[] {x,y,w,h});
        }
    }
    private static Capture home(CommandScreens screens,GameScene.Profile p,long now) {
        Capture controls = new Capture();
        screens.home(new GameModelTest.NullGraphics(),controls,null,false,now,1120,64);
        return controls;
    }
    private static void deckDetails() {
        long day = 1791504000000L;
        for (String language : Localization.LANGUAGES) {
            GameScene.Profile p = new GameScene.Profile(); p.language = language; p.unlocked = 59;
            CommandScreens screens = new CommandScreens(p);
            Capture start = home(screens,p,day);
            String available = Localization.text(language,"deck.daily_available");
            check(start.labels.get("daily").equals(Localization.text(language,"deck.daily_label",available,
                Localization.text(language,"deck.daily_resets",24,0))),"Daily countdown uses UTC reset");
            check(start.bounds.get("daily")[0] == 22 && start.bounds.get("daily")[2] == 376,"Daily uses full-width target");
            check(start.labels.get("campaign_progress").equals(Localization.text(language,"deck.progress_label",0,60,0)),"Unlock code is not a campaign clear");
            int id = Challenge.dailyId(Challenge.date(day));
            GameModel daily = Challenge.PRESETS[id].create(1,Challenge.dailySeed(Challenge.date(day)),id,Challenge.date(day));
            daily.outcome = GameModel.WON; daily.terminalReason = GameModel.TERMINAL_VICTORY;
            daily.elapsed = 120; daily.objectiveProgress = daily.objectiveSeconds;
            check(p.progress.recordChallenge(daily),"Staged Daily completion records");
            Capture done = home(screens,p,day+86_399_999);
            check(done.labels.get("daily").equals(Localization.text(language,"deck.daily_label",
                Localization.text(language,"deck.daily_done"),Localization.text(language,"deck.daily_minutes",1))),"Completed Daily retains last-minute countdown");
            check(home(screens,p,day+86_400_000).labels.get("daily").equals(start.labels.get("daily")),"New UTC date is available again");
            p.best[0] = 1700;
            check(home(screens,p,day).labels.get("campaign_progress").equals(Localization.text(language,"deck.progress_label",1,60,1700)),"Ribbon includes earned historical progress");
            p.run = RunState.newRun(9);
            check(home(screens,p,day).labels.get("run").contains(Localization.text(language,"run.retries",1)),"Run retry count has its own detail");
            p.lastRun = p.run.abandon(true,p.run.revision); p.run = null;
            check(home(screens,p,day).labels.get("run").contains(Localization.text(language,"deck.run_last",0,5)),"Terminal run shows factual cleared count");
        }
    }
    private static void previewCache() throws Exception {
        GameScene.Profile p = new GameScene.Profile(); CommandScreens screens = new CommandScreens(p);
        GameModel first = screens.homePreview(0,1); byte[] before = first.save();
        home(screens,p,0); home(screens,p,60_000); p.language = "hi"; home(screens,p,120_000);
        check(screens.homePreview(0,1) == first && Arrays.equals(before,first.save()),"Redraw/time/language reuse immutable preview");
        GameModel actual = new GameModel(2,1,9); actual.elapsed = 7; byte[] active = actual.save();
        screens.home(new GameModelTest.NullGraphics(),new Capture(),actual,true,0,1120,64);
        check(screens.homePreview(0,1) == first && Arrays.equals(active,actual.save()),"Active preview neither mutates battle nor replaces cached map");
        p.selectedSector = 1; home(screens,p,0); GameModel changed = screens.homePreview(1,1);
        check(changed != first,"Sector change replaces preview");
        p.difficulty = 2; home(screens,p,0);
        check(screens.homePreview(1,2) != changed && screens.homePreview(1,2).difficulty == 2,"Difficulty change replaces preview");
    }
    private static void campaignTrail() {
        for (float touch : new float[] {64,82}) {
            GameScene.Profile p = new GameScene.Profile(); p.unlocked = 59;
            CommandScreens screens = new CommandScreens(p); Capture controls = new Capture();
            screens.campaign(new GameModelTest.NullGraphics(),controls,null,false,0,1120,touch);
            float previous = 0;
            for (int sector = 0; sector < 6; sector++) {
                float[] point = screens.sectorPosition(sector), bounds = controls.bounds.get("level_"+sector);
                check(bounds != null && point[0] == 64,"Vertical trail has one shared column");
                check(sector == 0 || Math.abs(point[1]-previous-touch-12) < .01f,"Trail keeps compact stable spacing");
                check(point[0] >= bounds[0] && point[0] <= bounds[0]+bounds[2] && point[1] >= bounds[1] && point[1] <= bounds[1]+bounds[3],"Node positions remain inside row touch targets");
                previous = point[1];
            }
        }
    }
    private static void logUsage() {
        for (String language : Localization.LANGUAGES) for (int count : new int[] {0,449,450,500}) {
            GameScene.Profile p = new GameScene.Profile(); p.language = language; p.log.enabled = true;
            for (int i = 0; i < count; i++) p.log.add(i,"fixture",null,"");
            p.log.enabled = false;
            CommandScreens screens = new CommandScreens(p); screens.tools = true;
            GameModelTest.TextGraphics g = new GameModelTest.TextGraphics();
            screens.settings(g,new Capture(),1800,64);
            check(g.text.contains(Localization.text(language,"playtest.entries",count,500)),"Usage stays visible with logging disabled");
            String warning = Localization.text(language,count == 500 ? "playtest.log_full" : "playtest.log_warning");
            check(g.text.contains(warning) == (count >= 450),"Warnings start at 450 and explain eviction at 500");
            check(p.log.size() == count && !p.log.enabled,"Rendering never clears entries or opts in");
        }
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
