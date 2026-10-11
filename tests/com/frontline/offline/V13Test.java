package com.frontline.offline;

import java.util.Arrays;
import java.util.List;

public final class V13Test {
    private static int checks;
    private static final GameScene.Events EVENTS = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
        public long now() { return 1791504000000L; }
    };
    public static void main(String[] args) throws Exception {
        homeFits(); moreDetails(); tutorialGate(); translationCache(); tablets();
        check(AppVersion.NAME.equals("0.13.0") && AppVersion.CODE == 15,"V13 identity");
        System.out.println("PASS: "+checks+" V13 layout, onboarding and localization checks.");
    }
    private static void homeFits() throws Exception {
        for (String language : Localization.LANGUAGES) for (float height : new float[] {620,680,780,900,1120})
            for (float touch : new float[] {64,70,82,96}) for (boolean active : new boolean[] {false,true}) {
                GameScene.Profile p = new GameScene.Profile(); p.language = language; p.tutorialSeen = true; p.best[0] = 1700;
                GameScene s = new GameScene(p,active ? new GameModel(1,1,72) : null,EVENTS); s.back(); s.minimumTouchSize = touch;
                byte[] model = s.model.save(), records = p.progress.save();
                GameModelTest.TextGraphics g = new GameModelTest.TextGraphics(); s.render(g,height);
                check(!s.canScrollScreen() && !s.scrollScreen(10000),"Home never scrolls");
                check(g.text.contains(Localization.text(language,"deck.best_total",1700)),"Best total visible on initial render");
                GameScene.AccessibleButton progress = button(s,"campaign_progress");
                check(progress != null && progress.y+progress.height <= height-40,"Campaign progress fits above version footer");
                check(button(s,active ? "resume" : "play") != null,"Primary battle action is initially visible");
                List<GameScene.AccessibleButton> buttons = s.accessibleButtons();
                for (GameScene.AccessibleButton b : buttons) {
                    check(b.y >= 0 && b.y+b.height <= height && b.x >= 0 && b.x+b.width <= 420.1f,"Home target fits");
                    check(b.height >= touch && b.width >= touch,"Home retains target size");
                    if (Arrays.asList("sectors","challenges","mastery","tutorial","help","more").contains(b.id))
                        check(b.y >= progress.y+progress.height,"More follows campaign progress");
                }
                for (int i = 0; i < buttons.size(); i++) for (int j = i+1; j < buttons.size(); j++) {
                    GameScene.AccessibleButton a = buttons.get(i),b = buttons.get(j);
                    check(Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x) <= .1f
                        || Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y) <= .1f,"Home targets do not overlap");
                }
                if (button(s,"more") != null) {
                    press(s,"more",height);
                    for (String id : new String[] {"run","missions","logistics","daily","sectors","challenges","mastery","tutorial","help"})
                        check(UiTestControls.find(s,id,height) != null,"Compact More keeps every mode/shortcut reachable");
                    press(s,"back",height); check(s.overlay == GameScene.MENU,"More Back returns Home");
                }
                check(Arrays.equals(model,s.model.save()) && Arrays.equals(records,p.progress.save()),"Layout/navigation preserves game data");
            }
    }
    private static void moreDetails() {
        for (String language : Localization.LANGUAGES) {
            GameScene.Profile p = new GameScene.Profile(); p.language = language;
            long day = EVENTS.now(); int id = Challenge.dailyId(Challenge.date(day));
            GameModel daily = Challenge.PRESETS[id].create(1,Challenge.dailySeed(Challenge.date(day)),id,Challenge.date(day));
            daily.outcome = GameModel.WON; daily.terminalReason = GameModel.TERMINAL_VICTORY;
            daily.elapsed = 120; daily.objectiveProgress = daily.objectiveSeconds;
            check(p.progress.recordChallenge(daily),"More fixture records actual Daily completion");
            p.run = RunState.newRun(9);
            java.util.Map<String,String> labels = new java.util.HashMap<>();
            CommandScreens.Controls controls = (key,label,x,y,w,h) -> labels.put(key,label);
            CommandScreens screens = new CommandScreens(p);
            screens.more(new GameModelTest.NullGraphics(),controls,day,900,64);
            check(labels.get("daily").contains(Localization.text(language,"deck.daily_done")),"More reflects completed Daily");
            check(labels.get("run").contains(Localization.text(language,"deck.run_battle",1))
                && labels.get("run").contains(Localization.text(language,"run.retries",1)),"More reflects active Run and retry");
            p.lastRun = p.run.abandon(true,p.run.revision); p.run = null;
            screens.more(new GameModelTest.NullGraphics(),controls,day+86_400_000,900,64);
            check(labels.get("daily").contains(Localization.text(language,"deck.daily_available")),"More uses current UTC Daily date");
            check(labels.get("run").contains(Localization.text(language,"deck.run_last",0,5)),"More reflects finished Run");
        }
    }
    private static void tutorialGate() throws Exception {
        for (String language : Localization.LANGUAGES) for (String id : new String[] {"play","run","missions","challenges","daily","logistics"}) {
            GameScene.Profile p = new GameScene.Profile(); p.language = language; p.log.enabled = true;
            GameScene s = new GameScene(p,null,EVENTS); s.back(); byte[] before = s.model.save();
            press(s,id,780); check(s.overlay == GameScene.TUTORIAL && !s.hasBattle,"Every first mode entry is gated");
            s.update(100); check(Arrays.equals(before,s.model.save()),"Onboarding does not advance/create battle");
            press(s,"tutorial_skip",780);
            int expected = id.equals("play") ? GameScene.BRIEFING : id.equals("run") ? GameScene.RUN_HOME
                : id.equals("daily") ? GameScene.DAILY : id.equals("logistics") ? GameScene.LOGISTICS : GameScene.CHALLENGES;
            check(p.tutorialSeen && s.overlay == expected,"Skip returns to chosen mode");
            s.activateButton("tutorial_skip"); check(s.overlay == expected,"Duplicate Skip cannot repeat mode action");
            s.overlay = GameScene.MENU; press(s,id,780); check(s.overlay == expected,"Seen tutorial is not forced again");
            GameScene restored = new GameScene(p,null,EVENTS); restored.back(); press(restored,id,780);
            check(restored.overlay == expected,"Retained tutorial flag bypasses gate after scene restart");
        }
        GameScene.Profile p = new GameScene.Profile(); p.log.enabled = true;
        GameScene s = new GameScene(p,null,EVENTS); s.back(); press(s,"run",780); s.back();
        check(!p.tutorialSeen && s.overlay == GameScene.MENU && p.run == null,"Back cancels first-entry intent without falsely marking seen");
        press(s,"logistics",780); press(s,"tutorial_skip",780); press(s,"logistics_map_0",780);
        check(s.model.battleMode == GameModel.MODE_LOGISTICS && s.overlay == GameScene.NONE,"Chosen Lab remains playable after tutorial Skip");
        GameModel battle = new GameModel(1,1,42); p = new GameScene.Profile();
        s = new GameScene(p,battle,EVENTS); s.back(); byte[] saved = battle.save(); press(s,"resume",780);
        check(s.overlay == GameScene.TUTORIAL,"Unseen restored mode also gets onboarding");
        press(s,"tutorial_skip",780); check(s.overlay == GameScene.NONE && Arrays.equals(saved,battle.save()),"Tutorial resumes exact saved battle");
    }
    private static void translationCache() throws Exception {
        for (String language : new String[] {"id","hi"}) {
            Localization.clearTranslationCache();
            for (String value : new String[] {"Settings","Hard 1,234 / 01:23","YOU 90%","BOOST x1.20","अज्ञात","Unknown text"}) {
                String result = Localization.translate(language,value); long misses = Localization.translationMisses();
                for (int i = 0; i < 100; i++) check(Localization.translate(language,value).equals(result),"Cached translation is exact");
                check(Localization.translationMisses() == misses,"Repeated text avoids recognition scans");
            }
            for (int i = 0; i < 2600; i++) Localization.translate(language,"unknown-"+i);
            check(Localization.cachedTranslations() == Localization.CACHE_LIMIT,"Translation memory is bounded");
            GameScene.Profile p = new GameScene.Profile(); p.language = language; p.tutorialSeen = true;
            GameScene s = new GameScene(p,new GameModel(59,1,42),EVENTS); s.overlay = GameScene.NONE;
            s.render(new GameModelTest.NullGraphics(),900); long misses = Localization.translationMisses();
            for (int i = 0; i < 100; i++) s.render(new GameModelTest.NullGraphics(),900);
            check(Localization.translationMisses() == misses,"Steady gameplay reuses translated render strings");
        }
        String hi = Localization.translate("hi","Settings"), id = Localization.translate("id","Settings");
        check(!hi.equals(id) && Localization.translate("hi","Settings").equals(hi),"Language switches never reuse another catalog");
        check(Localization.translate("en","Settings").equals("Settings"),"English retains fast identity path");
    }
    private static void tablets() throws Exception {
        for (int[] display : new int[][] {{1200,1920},{1920,1200},{1600,2560},{2560,1600}}) {
            Viewport viewport = new Viewport(display[0],display[1],2,true);
            check(viewport.height >= 620 && viewport.minimumTouch*viewport.scale/2 >= 47.99f,"Tablet transform retains logical height and physical touch size");
            GameScene.Profile p = new GameScene.Profile(); p.tutorialSeen = true;
            GameScene s = new GameScene(p,new GameModel(59,1,42),EVENTS); s.overlay = GameScene.NONE;
            s.battlefieldWidth = viewport.battlefieldWidth;
            s.render(new GameModelTest.NullGraphics(),viewport.height);
            float left = (420-viewport.battlefieldWidth)/2;
            for (GameModel.Territory t : s.model.territories) {
                float[] point = s.position(t.id);
                check(point[0] >= left && point[0] <= left+viewport.battlefieldWidth && point[1] >= 0 && point[1] <= viewport.height,"All tablet tiles fit");
            }
            int king = s.model.originalKing(0);
            float[] source = s.position(king), target = s.position(king == 0 ? 1 : 0);
            s.down(source[0],source[1]); s.move(target[0],target[1]); s.up(target[0],target[1]);
            check(s.model.unitsSent > 0,"Full-width tablet board responds to deployment");
            float elapsed = s.model.elapsed; press(s,"pause",viewport.height);
            s.update(1); check(s.overlay == GameScene.PAUSE && s.model.elapsed == elapsed,"Shifted tablet Pause works");
        }
    }
    private static GameScene.AccessibleButton button(GameScene s,String id) {
        for (GameScene.AccessibleButton b : s.accessibleButtons()) if (b.id.equals(id)) return b;
        return null;
    }
    private static void press(GameScene s,String id,float height) {
        float[] p = UiTestControls.find(s,id,height); check(p != null,"Control reachable: "+id);
        s.down(p[0],p[1]); s.up(p[0],p[1]);
    }
    private static void check(boolean condition,String message) { checks++; if (!condition) throw new AssertionError(message); }
}
