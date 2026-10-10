package com.frontline.offline;

import java.util.Arrays;

final class V6RulesTest {
    private static int checks;
    private static final GameScene.Events SILENT = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
    };
    static int run() throws Exception {
        boosts(); capsAndMigration(); recoveryAi(); resignations(); tutorialAndCamera(); coverage();
        return checks;
    }
    public static void main(String[] args) throws Exception { System.out.println("PASS: "+run()+" focused V6 checks."); }
    private static GameModel quiet(int level,int difficulty) throws Exception {
        GameModel model = new GameModel(level,difficulty,17);
        java.lang.reflect.Field field = GameModel.class.getDeclaredField("aiTimers"); field.setAccessible(true);
        Arrays.fill((float[])field.get(model),10);
        return model;
    }
    private static void advance(GameModel model,float seconds) { for (int i = 0; i < Math.round(seconds/.02f); i++) model.update(.02f); }
    private static void boosts() throws Exception {
        GameModel model = quiet(36,1);
        double[] expected = {1,1.2,1.44,1.728,3,3.6};
        check(model.level().opponents == 5,"Expanded maps support five distinct opponents");
        check(model.capturedKings(0) == 0 && model.teamMultiplier(0) == 1,"Starting king does not count toward the boost");
        java.util.ArrayList<GameModel.Territory> kings = new java.util.ArrayList<>();
        for (GameModel.Territory tile : model.territories) if (tile.capital && tile.owner > 0) kings.add(tile);
        for (int i = 0; i < kings.size(); i++) {
            kings.get(i).owner = 0;
            check(model.capturedKings(0) == i+1 && Math.abs(model.teamMultiplier(0)-expected[i+1]) < .00001,"Each held king follows the confirmed V6 boost table");
            check(Math.abs(GameModel.restore(model.save()).teamMultiplier(0)-expected[i+1]) < .00001,"Held-king boost survives save restoration");
        }
        kings.get(4).owner = 1; check(model.teamMultiplier(0) == 3,"Losing the fifth captured king returns to 3x");
        kings.get(3).owner = 1; check(Math.abs(model.teamMultiplier(0)-1.728) < .00001,"Losing the fourth king removes the four-king bonus");
        kings.get(3).owner = 0; check(model.teamMultiplier(0) == 3,"Recapturing the fourth king returns to 3x without stacking");
        for (GameModel.Territory tile : model.territories) if (tile.capital) tile.owner = 1;
        check(model.capturedKings(1) == 5 && Math.abs(model.teamMultiplier(1)-3.6) < .00001,"All rival teams receive the same fair held-king rule");
    }
    private static void capsAndMigration() throws Exception {
        GameModel model = quiet(0,1);
        model.territories.get(0).troops = 124.9; model.territories.get(1).owner = 0; model.territories.get(1).troops = 99.9;
        model.update(.1f);
        check(model.territories.get(0).troops == 125 && model.territories.get(1).troops == 100,"Production uses fixed 125/100 caps");
        advance(model,12);
        check(model.territories.get(0).troops <= 125 && model.territories.get(1).troops <= 100,"No saturated-team infinite growth remains");
        model = quiet(2,1);
        for (GameModel.Territory tile : model.territories) if (tile.owner == 0) tile.troops = 10;
        GameModel.Territory tile = model.territories.get(1); tile.owner = 0; tile.troops = 99;
        GameModel.Troop packet = new GameModel.Troop(0,1,0,1,.99f); packet.units = 80; model.troops.add(packet); model.update(.02f);
        check(tile.troops == 100,"Incoming reinforcement cannot exceed an ordinary tile's cap");
        model.territories.get(0).troops = 124;
        packet = new GameModel.Troop(1,0,0,1,.99f); packet.units = 80; model.troops.add(packet); model.update(.02f);
        check(model.territories.get(0).troops == 125,"King reinforcements also respect the fixed cap");
        model = quiet(2,1); tile = model.territories.get(1); tile.troops = 0;
        packet = new GameModel.Troop(0,1,0,1,.99f); packet.units = 300; model.troops.add(packet); model.update(.02f);
        check(tile.owner == 0 && tile.troops == 100,"Captured ordinary tiles cannot overfill from legacy oversized armies");
        for (boolean extended : new boolean[] {false,true}) for (int level : new int[] {0,5,12,29}) {
            GameModel old = quiet(level,1); old.elapsed = 45; old.unitsSent = 12;
            if (extended) { old.territories.get(0).troops = 5000; old.territories.get(1).troops = 3000; }
            packet = new GameModel.Troop(0,1,0,5,.2f); packet.units = extended ? 3500 : 1; old.troops.add(packet);
            GameModel migrated = GameModel.restore(LegacySave.encode(old,extended));
            check(migrated.levelIndex == level && migrated.elapsed == 45 && migrated.unitsSent == 12,"Legacy V4/V5 migration preserves round and statistics");
            check(migrated.territories.get(0).count() == (extended ? GameModel.troopCap(migrated.territories.get(0)) : old.territories.get(0).count()),"Legacy overflow is clamped to the new per-tile cap");
            check(migrated.troops.get(0).units == packet.units,"Already-sent legacy armies retain all units in flight");
            check(Arrays.equals(migrated.save(),GameModel.restore(migrated.save()).save()),"Migrated battles roundtrip in V6 format");
        }
        model = quiet(0,1); model.territories.get(0).troops = 126; reject(model.save(),"Over-cap V6 king save is rejected");
        model = quiet(0,1); model.territories.get(1).troops = 101; reject(model.save(),"Over-cap V6 ordinary save is rejected");
        model = quiet(0,1); setTimer(model,Float.NaN); reject(model.save(),"Invalid dominance timer is rejected");
        model = quiet(0,1); model.resigned[1] = true; reject(model.save(),"Resigned owner cannot retain a garrison in a save");
        GameScene.Profile profile = new GameScene.Profile(); profile.best[29] = 2700; profile.stars[29] = 3; profile.unlocked = 29; profile.selectedSector = 29;
        new GameScene(profile,null,SILENT);
        check(profile.unlocked == 30 && profile.selectedSector == 29 && profile.best[29] == 2700,"A cleared V5 campaign unlocks Sector 31 without losing progress");
    }
    private static void recoveryAi() throws Exception {
        for (int difficulty = 0; difficulty < 3; difficulty++) for (int seed = 0; seed < 12; seed++) {
            GameModel model = new GameModel(0,difficulty,seed);
            for (GameModel.Territory tile : model.territories) { tile.owner = -1; tile.troops = 99; }
            model.territories.get(0).owner = 0; model.territories.get(0).troops = 99;
            model.territories.get(5).owner = 0; model.territories.get(5).troops = 14;
            model.territories.get(3).owner = 1; model.territories.get(3).troops = 80;
            model.territories.get(1).troops = 3;
            decide(model,1);
            check(!model.troops.isEmpty() && model.troops.get(0).target == (difficulty == 0 ? 1 : 5),"Normal/Hard prioritize recapturing their original king; Easy retains relaxed expansion");
            check(model.territories.get(3).count() >= 18,"King recovery still retains a defensive reserve");
        }
        for (int difficulty : new int[] {1,2}) {
            GameModel model = quiet(0,difficulty);
            for (GameModel.Territory tile : model.territories) { tile.owner = -1; tile.troops = 99; }
            model.territories.get(0).owner = 0; model.territories.get(0).troops = 125;
            model.territories.get(5).owner = 0; model.territories.get(5).troops = 125;
            for (int id : new int[] {2,3,4}) { model.territories.get(id).owner = 1; model.territories.get(id).troops = 100; }
            decide(model,1); java.util.Set<Integer> sources = new java.util.HashSet<>();
            for (GameModel.Troop troop : model.troops) { sources.add(troop.source); check(troop.target == 5,"Coordinated king recovery shares the lost king as target"); }
            check(sources.size() >= 2,"Rivals combine multiple capped armies to recover a fully defended king");
        }
    }
    private static GameModel dominated(int level,int difficulty) throws Exception {
        GameModel model = quiet(level,difficulty);
        for (GameModel.Territory tile : model.territories) { tile.owner = 0; tile.troops = GameModel.troopCap(tile); }
        GameModel.Territory last = model.territories.get(model.territories.size()-1); last.owner = 1; last.troops = 1;
        return model;
    }
    private static void resignations() throws Exception {
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel model = dominated(1,difficulty); advance(model,9.9f);
            check(!model.resigned[1] && model.outcome == GameModel.PLAYING,"More than 90% ownership does not immediately force resignation");
            GameModel restored = GameModel.restore(model.save()); advance(restored,.2f);
            check(restored.resigned[1] && restored.outcome == GameModel.WON && restored.owned(0) == restored.territories.size(),"Ten sustained seconds plus a hopeless army cause fair surrender");
            check(Arrays.equals(restored.save(),GameModel.restore(restored.save()).save()),"Surrender state remains exact across saves");
        }
        GameModel model = dominated(6,1); advance(model,10.2f);
        check(!model.resigned[1],"Exactly 90% coverage is not greater than 90%");
        model = dominated(1,1); advance(model,8);
        model.territories.get(1).owner = 1; model.update(.02f); model.territories.get(1).owner = 0; advance(model,3);
        check(!model.resigned[1],"Losing dominance resets the sustained-control timer");
        model = dominated(1,1); model.territories.get(0).troops = 0; setTimer(model,10); model.update(.02f);
        check(!model.resigned[1],"A rival with a recapturable weak player tile does not resign");
        model = dominated(1,1);
        GameModel.Troop packet = new GameModel.Troop(10,0,1,20,-20); packet.units = 110; model.troops.add(packet); setTimer(model,10); model.update(.02f);
        check(!model.resigned[1] && model.outcome == GameModel.PLAYING,"A strong queued army prevents false resignation");
        model = dominated(1,1); packet = new GameModel.Troop(10,0,1,20,-20); packet.units = 2; model.troops.add(packet); setTimer(model,10); model.update(.02f);
        check(model.resigned[1] && model.troops.isEmpty(),"Hopeless convoys stand down when their owner resigns");
        model = dominated(36,1); setTimer(model,10); model.update(.02f);
        check(model.resigned[1] && model.outcome == GameModel.WON,"Resignation also works on six-team maps");
    }
    private static void tutorialAndCamera() throws Exception {
        GameScene scene = new GameScene(new GameScene.Profile(),quiet(36,1),SILENT); scene.back();
        byte[] retained = scene.model.save(); click(scene,"tutorial"); click(scene,"tutorial_next");
        scene.down(110,225); scene.up(310,225); click(scene,"tutorial_next");
        click(scene,"demo_quarter"); click(scene,"demo_half"); click(scene,"demo_all"); scene.down(110,225); scene.up(310,225); click(scene,"tutorial_next");
        for (int i = 1; i <= 5; i++) {
            click(scene,"demo_capture"); GameModelTest.TextGraphics labels = new GameModelTest.TextGraphics(); scene.render(labels,700);
            String expected = new String[] {"1.20","1.44","1.73","3.00","3.60"}[i-1];
            check(labels.text.contains("BOOST x"+expected),"Interactive booster tutorial follows every milestone");
        }
        click(scene,"demo_lose"); click(scene,"demo_lose");
        GameModelTest.TextGraphics labels = new GameModelTest.TextGraphics(); scene.render(labels,700);
        check(labels.text.contains("BOOST x1.73"),"Tutorial demonstrates losing the four-king milestone");
        click(scene,"tutorial_prev"); click(scene,"tutorial_next"); click(scene,"tutorial_next"); click(scene,"tutorial_next");
        check(scene.overlay == GameScene.MENU && Arrays.equals(retained,scene.model.save()),"Booster tutorial does not change a retained battle");
        scene.start(59); scene.render(new GameModelTest.NullGraphics(),700);
        float[] a = scene.position(0), b = scene.position(1); float fitted = distance(a,b);
        click(scene,"zoom_in");
        check(distance(scene.position(0),scene.position(1)) > fitted,"Zoom control enlarges actual map tiles");
        float[] before = scene.position(0); scene.down(210,400); scene.move(270,450); scene.up(270,450); scene.render(new GameModelTest.NullGraphics(),700);
        check(scene.model.unitsSent == 0,"Panning never sends troops from neutral/enemy starts");
        check(!Arrays.equals(before,scene.position(0)),"Dragging neutral space actually moves the zoomed camera");
        scene.cameraGesture(210,350,3,900,-900); scene.render(new GameModelTest.NullGraphics(),700);
        check(Float.isFinite(scene.position(0)[0]) && Float.isFinite(scene.position(0)[1]),"Camera clamps extreme zoom and pan to finite coordinates");
        scene.cameraGesture(210,350,1,Float.NaN,0);
        check(Float.isFinite(scene.position(0)[0]),"Invalid gesture data is ignored");
        click(scene,"fit_board"); check(Math.abs(distance(scene.position(0),scene.position(1))-fitted) < .001,"Fit resets zoom and pan to the whole map");
        a = scene.position(0); b = scene.position(1); scene.model.territories.get(0).owner = 0;
        scene.down(a[0],a[1]); scene.cameraGesture(210,350,1.1f,0,0); scene.up(b[0],b[1]);
        check(scene.model.unitsSent == 0,"A pinch gesture cancels any armed troop drag");
        click(scene,"restart"); click(scene,"confirm_replace"); click(scene,"camera_ready"); scene.render(new GameModelTest.NullGraphics(),700);
        check(Math.abs(distance(scene.position(0),scene.position(1))-fitted) < .001,"Restart also resets camera framing");
    }
    private static void coverage() throws Exception {
        GameScene scene = new GameScene(new GameScene.Profile(),quiet(36,1),SILENT); scene.overlay = GameScene.NONE;
        GameModelTest.TextGraphics labels = new GameModelTest.TextGraphics(); scene.render(labels,620);
        for (int faction = 0; faction < 6; faction++) {
            final String name = Campaign.SHORT_NAMES[faction]+" "; boolean found = false;
            for (String label : labels.text) if (label.startsWith(name) && label.matches(".* [0-9]{1,3}%")) found = true;
            check(found,"Every active team has a whole-number coverage label");
        }
        for (GameModel.Territory tile : scene.model.territories) tile.owner = 0;
        labels = new GameModelTest.TextGraphics(); scene.render(labels,620);
        check(labels.text.contains("YOU 100%"),"Complete coverage is truthfully displayed as 100%");
    }
    private static float distance(float[] a,float[] b) { return (float)Math.hypot(a[0]-b[0],a[1]-b[1]); }
    private static void decide(GameModel model,int owner) throws Exception {
        java.lang.reflect.Method method = GameModel.class.getDeclaredMethod("playAi",int.class); method.setAccessible(true); method.invoke(model,owner);
    }
    private static void setTimer(GameModel model,float value) throws Exception {
        java.lang.reflect.Field field = GameModel.class.getDeclaredField("dominanceSeconds"); field.setAccessible(true); field.setFloat(model,value);
    }
    private static void click(GameScene scene,String id) {
        scene.render(new GameModelTest.NullGraphics(),700); float[] point = UiTestControls.find(scene,id,700);
        if (point == null) throw new AssertionError("Missing V6 control: "+id);
        scene.down(point[0],point[1]); scene.up(point[0],point[1]);
    }
    private static void reject(byte[] bytes,String message) throws Exception {
        try { GameModel.restore(bytes); throw new AssertionError(message); } catch (java.io.IOException expected) { checks++; }
    }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); checks++; }
}
