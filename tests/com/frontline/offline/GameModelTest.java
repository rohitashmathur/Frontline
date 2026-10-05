package com.frontline.offline;

import java.io.IOException;
import java.util.Arrays;

public final class GameModelTest {
    private static int checks;
    private static final GameScene.Events SILENT = new GameScene.Events() {
        public void changed() {}
        public void cue(int kind) {}
    };
    public static void main(String[] args) throws Exception {
        productionAndSending(); captureAndReinforcement(); eliminationWithTroopsInFlight();
        savesAndCorruption(); touchAndProgression(); menusAndTutorial(); expandedTouchAndQuarter();
        crownProduction(); aiAttackBudgets(); aiDefenseAndTargets(); coordinatedAttacks(); campaignDataAndProgress(); campaignNavigation(); simulationStability();
        checks += SharedRulesTest.run();
        checks += V6RulesTest.run();
        checks += V7RulesTest.run();
        checks += V10ModelTest.run();
        checks += V10ChallengeTest.run();
        checks += V10ProgressTest.run();
        checks += V10SceneTest.run();
        System.out.println("PASS: " + checks + " checks across battle rules, saves, controls, story/progression, and "+GameModel.LEVELS.length+"-map simulations.");
    }

    private static void productionAndSending() {
        GameModel model = new GameModel(0,1,7);
        double start = model.territories.get(0).troops;
        double neutral = model.territories.get(1).troops;
        model.update(.1f);
        check(model.territories.get(0).troops > start,"Owned territory produces troops");
        check(model.territories.get(1).troops == neutral,"Neutral territory does not produce");
        int sent = model.launch(0,1,.5);
        check(sent == 16,"Half dispatch rounds down");
        check(model.troops.size() == 16,"Each troop is accounted for");
        check(Math.abs(model.territories.get(0).troops - (start+.22-sent)) < .001,"Dispatch deducts troops once");
        check(model.launch(1,0,1) == 0,"Neutral cannot dispatch");
        check(model.launch(0,0,1) == 0,"Self dispatch rejected");
        check(model.launch(0,1,Double.NaN) == 0,"Invalid dispatch rejected");
        model.territories.get(0).troops = 125;
        model.territories.get(1).owner = 0; model.territories.get(1).troops = 20;
        model.update(.1f);
        check(model.territories.get(0).troops == 125,"King production is capped");
    }

    private static void captureAndReinforcement() {
        GameModel model = new GameModel(0,1,7);
        GameModel.Territory target = model.territories.get(1);
        target.troops = 3;
        model.territories.get(0).troops = 20;
        check(model.launch(0,1,.5) == 10,"Attack dispatches ten units");
        advance(model,2.4f);
        check(target.owner == 0,"Attack captures neutral territory");
        check(target.count() >= 7,"Surviving troops garrison captured territory");
        check(model.captures == 1,"Capture recorded once");
        int before = target.count();
        model.territories.get(0).troops = 20;
        model.launch(0,1,.5);
        advance(model,2.3f);
        check(target.count() >= before + 10,"Friendly arrival reinforces");
    }

    private static void eliminationWithTroopsInFlight() {
        GameModel model = new GameModel(0,1,7);
        int enemy = model.territories.size()-1;
        model.launch(enemy,0,1);
        for (GameModel.Territory territory : model.territories) if (territory.owner > 0) territory.owner = 0;
        model.update(.01f);
        check(model.outcome == GameModel.PLAYING,"Enemy army in transit prevents premature victory");
        model.troops.clear(); model.update(.01f);
        check(model.outcome == GameModel.WON,"No surviving enemy results in victory");
        model = new GameModel(0,1,7);
        model.launch(0,1,1);
        model.territories.get(0).owner = 1;
        model.update(.01f);
        check(model.outcome == GameModel.PLAYING,"Player army can recover after losing last territory");
        model.troops.clear(); model.update(.01f);
        check(model.outcome == GameModel.LOST,"No surviving player results in defeat");
    }

    private static void savesAndCorruption() throws Exception {
        GameModel model = new GameModel(5,2,10);
        model.launch(0,1,.5); advance(model,2);
        byte[] bytes = model.save();
        GameModel restored = GameModel.restore(bytes);
        check(Arrays.equals(bytes,restored.save()),"Save roundtrip preserves all battle state");
        byte[] corrupt = bytes.clone(); corrupt[0] = 0;
        reject(corrupt,"Bad save version rejected");
        reject(Arrays.copyOf(bytes,bytes.length-3),"Truncated save rejected");
        reject(new byte[60000],"Oversized save rejected");
        model.territories.get(0).troops = Double.NaN;
        reject(model.save(),"Nonfinite troop data rejected");
    }

    private static void touchAndProgression() {
        GameScene.Profile profile = new GameScene.Profile();
        GameScene scene = new GameScene(profile,null,SILENT);
        scene.start(0);
        scene.render(new NullGraphics(),780);
        float[] from = scene.position(0), to = scene.position(1);
        scene.down(from[0],from[1]); scene.move(to[0],to[1]); scene.up(to[0],to[1]);
        check(scene.model.troops.size() == 32,"Touch drag sends troops");
        scene.pause(); float elapsed = scene.model.elapsed; scene.update(.1f);
        check(scene.model.elapsed == elapsed,"Pause freezes battle");
        scene.back(); check(scene.overlay == GameScene.NONE,"Back resumes paused battle");
        scene.model.outcome = GameModel.WON; scene.update(.1f);
        check(profile.unlocked == 1 && profile.wins == 1,"Victory unlocks next sector");
        check(profile.progress.best[1][0] > 0 && profile.progress.stars[1][0] == 3,"Score and stars recorded by attempt difficulty");
        scene.update(.1f); check(profile.wins == 1,"Result is recorded only once");
        scene.render(new NullGraphics(),780);
        click(scene,"next");
        click(scene,"begin_attempt"); click(scene,"tutorial_skip");
        check(scene.model.levelIndex == 1,"Next sector control launches unlocked map");
    }

    private static void menusAndTutorial() throws Exception {
        GameScene.Profile profile = new GameScene.Profile();
        GameScene scene = new GameScene(profile,null,SILENT);
        check(scene.overlay == GameScene.SPLASH && !scene.hasBattle,"Fresh launch shows splash without inventing a saved battle");
        for (int i = 0; i < 10; i++) scene.update(.1f);
        check(scene.overlay == GameScene.MENU && !scene.needsAnimation(),"Splash transitions to stationary menu");
        check(scene.model.elapsed == 0,"Splash and menu freeze simulation");
        click(scene,"sectors");
        scene.render(new NullGraphics(),700);
        check(scene.buttonPosition("level_0") != null && scene.buttonPosition("level_1") == null,"Only unlocked sector rows are interactive");
        float[] locked = scene.sectorPosition(1);
        scene.down(locked[0],locked[1]); scene.up(locked[0],locked[1]);
        check(scene.overlay == GameScene.SECTORS,"Tapping locked sector cannot start a battle");
        scene.back(); check(scene.overlay == GameScene.MENU,"Sector Back returns to menu");
        click(scene,"play");
        check(scene.overlay == GameScene.BRIEFING && !scene.hasBattle,"New Attempt shows targets before a run");
        click(scene,"begin_attempt");
        check(scene.overlay == GameScene.TUTORIAL && scene.model.elapsed == 0,"First attempt starts with a paused practical tutorial");
        click(scene,"tutorial_next");
        scene.render(new NullGraphics(),700);
        check(scene.buttonPosition("tutorial_next") == null,"Practice step requires a successful swipe");
        scene.down(110,225); scene.up(110,225);
        scene.render(new NullGraphics(),700);
        check(scene.buttonPosition("tutorial_next") == null,"Tap on practice source is not a swipe");
        scene.down(110,225); scene.move(310,225); scene.up(310,225);
        check(scene.model.unitsSent == 0 && scene.model.elapsed == 0,"Tutorial practice does not modify real battle");
        click(scene,"tutorial_next"); click(scene,"demo_quarter"); click(scene,"demo_half"); click(scene,"demo_all");
        scene.down(110,225); scene.up(310,225); click(scene,"tutorial_next");
        click(scene,"demo_capture"); click(scene,"demo_lose"); click(scene,"tutorial_next"); click(scene,"tutorial_next");
        check(profile.tutorialSeen && scene.hasBattle && scene.overlay == GameScene.NONE,"Tutorial completion starts chosen sector");
        scene.model.launch(0,1,.25);
        scene.pause(); click(scene,"menu");
        byte[] before = scene.model.save();
        scene.update(.1f);
        check(Arrays.equals(before,scene.model.save()),"Quit to menu freezes and retains battle");
        click(scene,"tutorial");
        click(scene,"tutorial_skip");
        check(scene.overlay == GameScene.MENU && Arrays.equals(before,scene.model.save()),"Repeat tutorial and skip preserve unfinished battle");
        click(scene,"settings"); scene.back();
        check(scene.overlay == GameScene.MENU,"Settings returns to correct menu");
        click(scene,"resume");
        check(scene.overlay == GameScene.NONE && Arrays.equals(before,scene.model.save()),"Resume keeps exact battle state");
        scene.pause(); click(scene,"menu");
        GameScene restored = new GameScene(profile,GameModel.restore(before),SILENT);
        restored.back();
        check(restored.overlay == GameScene.MENU,"Process restore opens menu rather than running battle");
        click(restored,"resume");
        check(Arrays.equals(before,restored.model.save()),"Restored Resume preserves troops in transit");
        profile.unlocked = 2; profile.best[0] = 100; profile.stars[0] = 2;
        scene.overlay = GameScene.MENU; click(scene,"sectors");
        TextGraphics labels = new TextGraphics(); scene.render(labels,700);
        check(labels.text.contains("Cleared / No Normal record / Legacy 100"),"Completed historical sectors explicitly show Cleared and Legacy");
        check(labels.text.contains("Locked - clear Sector 03"),"Locked sectors explain their unlock requirement");
        click(scene,"level_2");
        check(profile.selectedSector == 2 && scene.overlay == GameScene.MENU,"Selecting sector updates menu selection");
        check(Arrays.equals(before,scene.model.save()),"Selecting a sector alone does not discard saved battle");
        click(scene,"play");
        click(scene,"begin_attempt"); click(scene,"confirm_replace");
        check(scene.model.levelIndex == 2,"Play launches selected sector");
        scene.model.outcome = GameModel.LOST; scene.update(.1f); click(scene,"menu");
        scene.render(new NullGraphics(),700);
        check(scene.buttonPosition("resume") == null,"Finished battles do not offer Resume");
        GameScene first = new GameScene(new GameScene.Profile(),null,SILENT);
        first.back(); click(first,"play"); click(first,"begin_attempt"); click(first,"tutorial_skip");
        check(first.profile.tutorialSeen && first.overlay == GameScene.NONE,"First-time tutorial can be skipped");
    }

    private static void expandedTouchAndQuarter() {
        for (int level : new int[] {0,5}) {
            GameScene scene = new GameScene(new GameScene.Profile(),null,SILENT); scene.start(level);
            scene.render(new NullGraphics(),620);
            click(scene,"quarter");
            check(scene.fraction == .25,"25% deployment selected");
            scene.render(new NullGraphics(),620);
            float[] source = scene.position(0), target = scene.position(1);
            float scale = (target[0]-source[0])/1.7320508f;
            float sx = source[0]-scale*.8660254f-7;
            float tx = target[0], ty = target[1]-scale-7;
            scene.down(sx,source[1]); scene.move(tx,ty); scene.up(tx,ty);
            check(scene.model.unitsSent == 8 && scene.model.territories.get(0).count() == 24,"Expanded source and target areas dispatch exactly one quarter");
            scene.model.troops.clear();
            int sent = scene.model.unitsSent;
            scene.down(source[0],source[1]); scene.up(210,120);
            check(scene.model.unitsSent == sent,"Off-board release does not dispatch troops");
            scene.down(source[0],source[1]); scene.cancel(); scene.up(target[0],target[1]);
            check(scene.model.unitsSent == sent,"Cancelled swipe does not dispatch");
            int enemy = scene.model.territories.size()-1;
            float[] enemyPoint = scene.position(enemy);
            scene.down(enemyPoint[0],enemyPoint[1]); scene.up(target[0],target[1]);
            check(scene.model.unitsSent == sent,"Exact enemy hit cannot select nearby player territory");
            scene.down(source[0],source[1]); scene.up(target[0],target[1]);
            check(scene.model.unitsSent == sent+6,"Exact tile still resolves correctly with expanded gutters");
        }
        GameModel model = new GameModel(0,1,7); model.territories.get(0).troops = 35;
        check(model.launch(0,1,.25) == 8,"Quarter dispatch rounds down");
        model.territories.get(0).troops = 3;
        check(model.launch(0,1,.25) == 0,"Zero-unit quarter dispatch is rejected");
    }

    private static void click(GameScene scene, String id) {
        scene.render(new NullGraphics(),700);
        float[] point = scene.buttonPosition(id);
        if (point == null) throw new AssertionError("Missing control: " + id);
        scene.down(point[0],point[1]); scene.up(point[0],point[1]);
    }

    static final class TextGraphics extends NullGraphics {
        final java.util.ArrayList<String> text = new java.util.ArrayList<>();
        @Override public void text(String value,float x,float baseline,float size,int color,boolean bold,int align) { text.add(value); }
    }

    private static void coordinatedAttacks() {
        GameModel model = new GameModel(1,2,7);
        for (GameModel.Territory territory : model.territories) { territory.owner = 0; territory.troops = 99; }
        model.territories.get(2).owner = 1; model.territories.get(2).troops = 90;
        model.territories.get(3).owner = 1; model.territories.get(3).troops = 90;
        decide(model,1);
        boolean fromTwo = false, fromThree = false, commonTarget = true;
        int target = model.troops.isEmpty() ? -1 : model.troops.get(0).target;
        for (GameModel.Troop troop : model.troops) {
            if (troop.owner == 1 && troop.source == 2) fromTwo = true;
            if (troop.owner == 1 && troop.source == 3) fromThree = true;
            if (troop.target != target) commonTarget = false;
        }
        check(commonTarget,"Coordinated troops share a target");
        check(fromTwo && fromThree,"AI combines armies to attack a stronger territory");
        check(model.territories.get(2).count() >= 25 && model.territories.get(3).count() >= 25,"Coordinated attacks retain defensive garrisons");
        advance(model,2.8f);
        check(model.territories.get(target).owner == 1,"Coordinated attack budget captures a growing defended target");

        model = new GameModel(1,2,7);
        for (GameModel.Territory territory : model.territories) { territory.owner = 0; territory.troops = 99; }
        for (int id : new int[] {2,3}) { model.territories.get(id).owner = 1; model.territories.get(id).troops = 65; }
        decide(model,1);
        check(model.troops.isEmpty(),"AI does not pool insufficient forces into a doomed attack");
    }

    private static void crownProduction() throws Exception {
        for (float step : new float[] {.01f,.02f,.1f}) {
            GameModel model = new GameModel(0,1,7);
            model.territories.get(0).troops = 10;
            model.territories.get(1).owner = 0; model.territories.get(1).troops = 10;
            int ticks = Math.round(1/step);
            for (int i = 0; i < ticks; i++) model.update(step);
            double crown = model.territories.get(0).troops-10, normal = model.territories.get(1).troops-10;
            check(Math.abs(crown-2.2) < .0001 && Math.abs(normal-1.35) < .0001,"Crown and normal growth rates remain distinct at different tick sizes");
            check(crown/normal > 1.62,"Crown produces at least 62% faster than normal territory");
            GameModel restored = GameModel.restore(model.save());
            check(restored.territories.get(0).capital && !restored.territories.get(1).capital,"Save restores crown identity");
            restored.territories.get(0).owner = 1; restored.territories.get(0).troops = 10;
            restored.update(.1f);
            check(Math.abs(restored.territories.get(0).troops-10.264) < .0001,"Captured crown keeps its faster production and grants a team bonus");
            model.territories.get(0).troops = 124.9; model.territories.get(1).troops = 99.9;
            model.update(.1f);
            check(model.territories.get(0).troops == 125 && model.territories.get(1).troops == 100,"King and ordinary production obey their distinct maxima");
        }
    }

    private static void aiAttackBudgets() {
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel model = aiFixture(difficulty);
            GameModel.Territory source = model.territories.get(3), target = model.territories.get(1);
            target.owner = -1; target.troops = 8;
            decide(model,1);
            check(model.troops.size() >= 12 && model.troops.size() <= 15,"AI sends a capture-sized force, not most of its army");
            check(source.count() >= 65,"Weak-target attack leaves strong defensive reserve");
            check(model.troops.get(0).target == target.id,"AI expands into a weak ordinary territory");
            int sent = model.troops.size(); decide(model,1);
            check(model.troops.size() == sent,"AI avoids duplicate attacks when enough troops are already incoming");
            advance(model,2.8f);
            check(target.owner == 1,"Sized neutral attack actually captures its target");

            for (boolean crown : new boolean[] {false,true}) {
                model = aiFixture(difficulty); target = model.territories.get(1);
                target.owner = 0; target.troops = 18; target.capital = crown;
                decide(model,1);
                check(!model.troops.isEmpty() && model.troops.size() < 40,"Defended attack accounts for growth without draining its source");
                advance(model,2.8f);
                check(target.owner == 1,"Sized attack captures normal and crown targets that grow during travel");
            }
        }
        GameModel model = aiFixture(1);
        GameModel.Territory target = model.territories.get(1); target.owner = 0; target.troops = 8;
        model.launch(0,1,20.01/99);
        int before = model.troops.size(); decide(model,1);
        check(model.troops.size()-before > 30,"Attack budget includes defender reinforcements already in transit");
        advance(model,2.8f);
        check(target.owner == 1,"Attack defeats the reinforced target with its budgeted force");

        model = aiFixture(1); model.territories.get(1).troops = 8;
        for (int i = 0; i < 20; i++) model.troops.add(new GameModel.Troop(5,1,1,15,0));
        before = model.troops.size(); decide(model,1);
        check(model.troops.size()-before == 13,"Distant future armies do not suppress a current capture opportunity");
        advance(model,2.8f);
        check(model.territories.get(1).owner == 1,"Current capture succeeds without depending on distant future support");

        model = aiFixture(1); model.territories.get(1).troops = 8;
        for (int i = 0; i < 895; i++) model.troops.add(new GameModel.Troop(3,3,1,20,-10));
        decide(model,1);
        int units = 0; for (int i = 895; i < model.troops.size(); i++) units += model.troops.get(i).units;
        check(model.troops.size() == 900 && units == 13,"Limited visual slots pack a complete AI capture force without losing units");
    }

    private static void aiDefenseAndTargets() {
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel model = aiFixture(difficulty);
            model.territories.get(1).troops = 8;
            model.launch(0,3,75.01/99);
            int before = model.troops.size(); decide(model,1);
            check(model.troops.size() == before && model.territories.get(3).count() == 80,"Threatened source does not send away troops needed against an incoming attack");

            model = aiFixture(difficulty);
            model.territories.get(1).owner = 1; model.territories.get(1).troops = 5;
            model.launch(0,1,20.01/99);
            before = model.troops.size(); decide(model,1);
            check(model.troops.size()-before == 19,"AI sends only the reinforcement shortfall plus a safety margin");
            check(model.troops.get(before).target == 1,"AI prioritizes saving its threatened ordinary territory");
            advance(model,2.8f);
            check(model.territories.get(1).owner == 1,"Timely reinforcements actually defend the friendly target");
        }
        for (int difficulty : new int[] {1,2}) {
            for (int seed = 0; seed < 12; seed++) {
                GameModel model = aiFixture(difficulty,seed);
                model.territories.get(0).troops = 22;
                model.territories.get(1).owner = 0; model.territories.get(1).troops = 8;
                decide(model,1);
                check(model.troops.get(0).target == 1,"Weak ordinary target beats a similarly distant, stronger crown");
            }
            GameModel model = aiFixture(difficulty);
            model.territories.get(0).troops = 4; model.territories.get(1).owner = 0; model.territories.get(1).troops = 70;
            decide(model,1);
            check(model.troops.get(0).target == 0,"AI still exploits a genuinely vulnerable crown");
        }
        GameModel model = aiFixture(1);
        model.territories.get(1).troops = 8;
        model.launch(0,3,75.01/99);
        for (int i = 0; i < 70; i++) model.troops.add(new GameModel.Troop(5,3,1,15,0));
        int before = model.troops.size(); decide(model,1);
        check(model.troops.size() == before,"Late incoming allies cannot justify sending away immediate defenders");

        model = aiFixture(1);
        model.territories.get(1).owner = 1; model.territories.get(1).troops = 5;
        model.launch(0,1,20.01/99);
        for (int i = 0; i < 30; i++) model.troops.add(new GameModel.Troop(5,1,1,15,0));
        before = model.troops.size(); decide(model,1);
        check(model.troops.size()-before == 19,"Late incoming allies do not cancel an urgent reinforcement request");

        model = aiFixture(1);
        model.territories.get(3).owner = -1; model.territories.get(3).troops = 99;
        model.territories.get(5).owner = 1; model.territories.get(5).troops = 80;
        model.territories.get(1).owner = 1; model.territories.get(1).troops = 5;
        model.launch(0,1,20.01/99);
        before = model.troops.size(); decide(model,1);
        check(model.troops.size() == before,"AI avoids reinforcements that cannot arrive before the attack ends");

        model = new GameModel(2,2,7);
        for (GameModel.Territory territory : model.territories) { territory.owner = -1; territory.troops = 99; }
        model.territories.get(0).owner = 0;
        model.territories.get(3).owner = 1; model.territories.get(3).troops = 80;
        model.territories.get(2).owner = 2; model.territories.get(2).troops = 8;
        decide(model,1);
        check(model.troops.get(0).target == 2,"AI can attack another rival instead of fixating on the player's crown");
    }

    private static GameModel aiFixture(int difficulty) { return aiFixture(difficulty,7); }
    private static GameModel aiFixture(int difficulty,int seed) {
        GameModel model = new GameModel(0,difficulty,seed);
        for (GameModel.Territory territory : model.territories) { territory.owner = -1; territory.troops = 99; territory.capital = false; }
        model.territories.get(0).owner = 0; model.territories.get(0).capital = true;
        model.territories.get(3).owner = 1; model.territories.get(3).troops = 80;
        return model;
    }

    private static void decide(GameModel model,int owner) {
        try {
            java.lang.reflect.Method method = GameModel.class.getDeclaredMethod("playAi",int.class);
            method.setAccessible(true); method.invoke(model,owner);
        } catch (ReflectiveOperationException error) { throw new AssertionError("AI decision failed",error); }
    }

    private static void campaignDataAndProgress() throws Exception {
        check(GameModel.LEVELS.length == 60 && Campaign.CHAPTERS.length == 10,"Campaign has ten chapters and sixty sectors");
        java.util.Set<String> names = new java.util.HashSet<>(), layouts = new java.util.HashSet<>();
        for (int sector = 0; sector < GameModel.LEVELS.length; sector++) {
            GameModel.Level level = GameModel.LEVELS[sector];
            check(names.add(level.name),"Every sector has a unique name");
            java.util.Set<Integer> holes = new java.util.HashSet<>();
            for (int hole : level.holes) check(hole >= 0 && hole < level.rows*level.columns && holes.add(hole),"Map holes are unique and inside the grid");
            check(layouts.add(level.columns+"x"+level.rows+Arrays.toString(level.holes)+Arrays.toString(level.startingCells)),"Campaign varies shapes or starting positions between sectors");
            GameModel model = new GameModel(sector,1,7);
            check(model.territories.size() == level.rows*level.columns-holes.size(),"Each map contains its expected territories");
            java.util.Set<Integer> factions = new java.util.HashSet<>();
            for (int owner = 0; owner <= level.opponents; owner++) {
                int capitals = 0;
                for (GameModel.Territory territory : model.territories) if (territory.owner == owner && territory.capital) capitals++;
                check(capitals == 1 && model.owned(owner) == 1,"Every side starts at exactly one distinct crown");
                check(level.faction(owner) >= 0 && level.faction(owner) < Campaign.FACTIONS.length && factions.add(level.faction(owner)),"Faction identities are valid and distinct");
                if (level.startingCells != null) check(!holes.contains(level.startingCells[owner]),"New-map starting positions never land in gaps");
            }
            if (sector >= 6 && sector < 24) check(level.faction(1) == Campaign.chapter(sector).rulerFaction,"Primary enemy matches the chapter's ruler");
            check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Every campaign map restores exact battle state");
        }
        String[] original = {"First Contact","The Crossing","Three Fronts","Broken Coast","Crossfire","Last Stand"};
        for (int i = 0; i < original.length; i++) check(GameModel.LEVELS[i].name.equals(original[i]) && GameModel.LEVELS[i].startingCells == null,"Original sector indexes and spawn layouts remain compatible");
        GameScene.Profile legacy = new GameScene.Profile(); legacy.unlocked = 5; legacy.selectedSector = 5;
        legacy.best[5] = 1234; legacy.stars[5] = 3; legacy.times[5] = 80;
        new GameScene(legacy,null,SILENT);
        check(legacy.unlocked == 6 && legacy.selectedSector == 5,"Cleared old final sector unlocks the appended campaign without changing selection");
        check(legacy.best[5] == 1234 && legacy.stars[5] == 3 && legacy.times[5] == 80,"Campaign migration retains old scores, stars, and times");
        GameScene.Profile unfinished = new GameScene.Profile(); unfinished.unlocked = 5;
        new GameScene(unfinished,null,SILENT);
        check(unfinished.unlocked == 5,"Reaching but not clearing old final sector does not unlock the next chapter");
        for (int sector : new int[] {5,11,17,23,29,35,41,47,53,59}) {
            GameScene.Profile profile = new GameScene.Profile(); profile.unlocked = sector;
            GameScene scene = new GameScene(profile,null,SILENT); scene.start(sector);
            scene.model.outcome = GameModel.WON; scene.update(.01f);
            check(profile.unlocked == Math.min(59,sector+1),"Chapter-ending victory unlocks the correct next sector without overflowing");
            TextGraphics labels = new TextGraphics(); scene.render(labels,620);
            check(labels.text.contains(sector == 59 ? "Campaign Complete" : "Chapter Secured"),"Chapter and final victories display the correct ending");
            check((scene.buttonPosition("next") != null) == (sector < 59),"Final sector does not offer a nonexistent next level");
            if (sector < 59) { click(scene,"next"); click(scene,"begin_attempt"); check(scene.model.levelIndex == sector+1,"Next Sector crosses chapter boundaries correctly"); }
        }
    }

    private static void campaignNavigation() throws Exception {
        GameScene.Profile profile = new GameScene.Profile(); profile.tutorialSeen = true;
        GameScene scene = new GameScene(profile,new GameModel(0,1,7),SILENT); scene.back();
        byte[] before = scene.model.save(); click(scene,"sectors");
        java.util.Set<String> visible = new java.util.HashSet<>();
        for (int chapter = 0; chapter < 10; chapter++) {
            TextGraphics labels = new TextGraphics(); scene.render(labels,620);
            check(labels.text.contains(Campaign.CHAPTERS[chapter].name) && labels.text.contains(Campaign.CHAPTERS[chapter].firstLine),"Each campaign page displays its chapter and story briefing");
            check((scene.buttonPosition("chapter_prev") != null) == (chapter > 0),"Previous chapter control respects first-page boundary");
            check((scene.buttonPosition("chapter_next") != null) == (chapter < 9),"Next chapter control respects final-page boundary");
            for (GameModel.Level level : GameModel.LEVELS) if (labels.text.contains(level.name)) visible.add(level.name);
            if (chapter > 0) check(scene.buttonPosition("level_"+(chapter*6)) == null,"Browsing a locked chapter does not unlock its sectors");
            if (chapter < 9) click(scene,"chapter_next");
        }
        check(visible.size() == 60,"All sixty sectors are reachable through chapter pages");
        check(Arrays.equals(before,scene.model.save()) && profile.selectedSector == 0,"Chapter browsing freezes battle and preserves selected sector");
        for (int i = 0; i < 9; i++) click(scene,"chapter_prev");
        scene.back(); profile.unlocked = 59; profile.selectedSector = 59; click(scene,"sectors");
        scene.render(new NullGraphics(),620);
        check(scene.buttonPosition("level_59") != null && scene.buttonPosition("level_0") == null,"Campaign opens at the selected sector's chapter");
        click(scene,"level_59"); click(scene,"play"); click(scene,"begin_attempt"); click(scene,"confirm_replace"); click(scene,"camera_ready");
        check(scene.model.levelIndex == 59 && scene.overlay == GameScene.NONE,"Selecting final-sector row launches the final map");
        GameModel.Territory source = null, target = null;
        for (GameModel.Territory territory : scene.model.territories) {
            if (territory.owner == 0) source = territory;
            if (territory.owner == -1) target = territory;
        }
        click(scene,"quarter");
        float[] a = scene.position(source.id), b = scene.position(target.id);
        scene.down(a[0],a[1]); scene.up(b[0],b[1]);
        check(scene.model.unitsSent == 13,"Player can dispatch from a shifted starting location on a new map");
        for (int sector : new int[] {12,18,25}) {
            scene.start(sector);
            TextGraphics labels = new TextGraphics(); scene.render(labels,620);
            for (int owner = 1; owner <= scene.model.level().opponents; owner++) {
                int faction = scene.model.level().faction(owner);
                check(labels.text.contains(Campaign.SHORT_NAMES[faction]+" "+Math.round(100f/scene.model.territories.size())+"%"),"Battle legends identify every named rival faction with integer coverage");
                GameModel.Territory enemy = null;
                for (GameModel.Territory territory : scene.model.territories) if (territory.owner == owner) enemy = territory;
                float[] point = scene.position(enemy.id);
                FactionGraphics colors = new FactionGraphics(point[0],point[1],GameScene.COLORS[faction]);
                scene.render(colors,620); check(colors.matched,"Named faction retains its colour even when enemy-owner order changes");
            }
        }
    }

    private static final class FactionGraphics extends NullGraphics {
        final float x,y; final int color; boolean matched;
        FactionGraphics(float x,float y,int color) { this.x = x; this.y = y; this.color = color; }
        @Override public void circle(float x,float y,float radius,int color) {
            if (Math.abs(x-this.x) < .01 && Math.abs(y-this.y) < .01 && color == this.color) matched = true;
        }
    }

    private static void simulationStability() throws Exception {
        for (int level = 0; level < GameModel.LEVELS.length; level++) {
            for (int difficulty = 0; difficulty < 3; difficulty++) {
                GameModel model = new GameModel(level,difficulty,700+level);
                for (int tick = 0; tick < 4500; tick++) {
                    if (tick%25 == 0 && model.outcome == GameModel.PLAYING) {
                        for (GameModel.Territory source : model.territories) {
                            if (source.owner != 0 || source.count() < 20) continue;
                            GameModel.Territory best = null; double value = Double.MAX_VALUE;
                            for (GameModel.Territory target : model.territories) {
                                if (target.owner == 0) continue;
                                double cost = target.troops + Math.hypot(source.x-target.x,source.y-target.y)*3;
                                if (cost < value) { best = target; value = cost; }
                            }
                            if (best != null && source.count() > best.count()+6) model.launch(source.id,best.id,.85);
                        }
                    }
                    model.update(.06f);
                    for (GameModel.Territory territory : model.territories) {
                        if (!Double.isFinite(territory.troops) || territory.troops < 0 || territory.troops > GameModel.troopCap(territory))
                            throw new AssertionError("Invalid army during simulation");
                    }
                    if (model.outcome != GameModel.PLAYING) break;
                }
                check(model.troops.size() <= 900,"Simulation troop limit");
                check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Simulation save roundtrip");
            }
        }
    }

    private static void advance(GameModel model,float seconds) { for (int i = 0; i < (int)(seconds/.02f); i++) model.update(.02f); }
    private static void reject(byte[] bytes,String message) throws Exception {
        try { GameModel.restore(bytes); throw new AssertionError(message); }
        catch (IOException expected) { checks++; }
    }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); checks++; }
    static class NullGraphics implements GameScene.Graphics {
        public void rect(float x,float y,float w,float h,float radius,int color) {}
        public void circle(float x,float y,float radius,int color) {}
        public void line(float x1,float y1,float x2,float y2,float width,int color) {}
        public void polygon(float[] points,int fill,int stroke,float width) {}
        public void text(String text,float x,float baseline,float size,int color,boolean bold,int align) {}
    }
}
