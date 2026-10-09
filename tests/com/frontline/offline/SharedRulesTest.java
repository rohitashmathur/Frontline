package com.frontline.offline;

import java.util.Arrays;

final class SharedRulesTest {
    private static int checks;
    private static final GameScene.Events SILENT = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
    };

    static int run() throws Exception {
        interceptions(); boundedPackets(); kingGrowth(); tutorialAndRestart(); soundtrack();
        return checks;
    }

    private static GameModel quiet(int level) throws Exception {
        GameModel model = new GameModel(level,1,7);
        java.lang.reflect.Field timers = GameModel.class.getDeclaredField("aiTimers"); timers.setAccessible(true);
        Arrays.fill((float[])timers.get(model),10);
        return model;
    }

    private static int flying(GameModel model,int owner) {
        int count = 0; for (GameModel.Troop troop : model.troops) if (troop.owner == owner) count += troop.units;
        return count;
    }

    private static void interceptions() throws Exception {
        for (float step : new float[] {.01f,.02f,.1f}) {
            GameModel model = quiet(0); int last = model.territories.size()-1;
            model.territories.get(0).troops = 20; model.territories.get(last).troops = 30;
            model.launch(0,last,1); model.launch(last,0,1);
            for (int i = 0; i < Math.round(1.6f/step); i++) model.update(step);
            check(flying(model,0) == 0 && flying(model,1) == 10,"20 versus 30 leaves exactly 10 hostile troops in flight at different tick sizes");
            check(model.unitsLost == 20,"Interception counts the player's canceled troops exactly once");
        }
        GameModel model = quiet(0);
        model.troops.add(new GameModel.Troop(0,5,0,4,1.9f));
        model.troops.add(new GameModel.Troop(2,3,1,4,1.9f)); model.update(.1f);
        check(model.troops.isEmpty() && !model.clashes.isEmpty(),"Different routes that cross at the same time clash and show an effect");
        model = quiet(0);
        model.troops.add(new GameModel.Troop(0,5,0,4,.9f));
        model.troops.add(new GameModel.Troop(2,3,1,4,1.9f)); model.update(.1f);
        check(model.troops.size() == 2,"Crossing routes at different times do not cancel armies");
        model = quiet(0);
        model.troops.add(new GameModel.Troop(0,5,0,4,1.9f));
        model.troops.add(new GameModel.Troop(5,0,0,4,1.9f)); model.update(.1f);
        check(model.troops.size() == 2,"Friendly troops pass each other without losses");
        model = quiet(0);
        model.troops.add(new GameModel.Troop(0,2,0,4,1.9f));
        model.troops.add(new GameModel.Troop(3,5,1,4,1.9f)); model.update(.1f);
        check(model.troops.size() == 2,"Separated parallel paths do not collide");
        model = quiet(0);
        model.troops.add(new GameModel.Troop(0,5,0,.05f,0));
        model.troops.add(new GameModel.Troop(5,0,1,.05f,0)); model.update(.1f);
        check(model.troops.isEmpty() && !model.clashes.isEmpty(),"Swept tests catch troops that pass between rendered frames before arrival");
        model = quiet(0);
        GameModel.Troop a = new GameModel.Troop(0,5,0,4,1.9f), b = new GameModel.Troop(5,0,1,4,1.9f);
        a.units = 20; b.units = 30; model.troops.add(a); model.troops.add(b); model.update(.1f);
        check(flying(model,0) == 0 && flying(model,1) == 10,"Grouped armies obey the same one-for-one cancellation rule");
        check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Partly intercepted large army restores with the surviving count");
    }

    private static void boundedPackets() throws Exception {
        GameModel model = quiet(0);
        GameModel.Territory crown = model.territories.get(0), normal = model.territories.get(1);
        crown.troops = 125; normal.owner = 0; normal.troops = 100; model.update(.1f);
        check(crown.troops == 125 && normal.troops == 100,"Saturated teams never exceed the separate finite caps");
        normal.troops = 20; model.update(.1f);
        check(crown.troops == 125 && normal.troops > 20,"Ordinary production does not depend on team saturation");
        check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"125-troop kings survive exact save and restore");
        model = quiet(0); model.territories.get(0).troops = 125;
        for (int i = 0; i < 899; i++) model.troops.add(new GameModel.Troop(5,5,1,20,-10));
        check(model.launch(0,1,.5) == 62,"Half dispatch from a full king rounds down");
        check(flying(model,0) == 62 && model.territories.get(0).troops == 63 && model.troops.size() == 900,"Limited convoy slots preserve selected troop quantities in packets");
        check(model.army(0) == 125,"Army total counts grouped units rather than particles");
        check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Grouped armies restore all units and timings");
        model = quiet(0); model.territories.get(0).troops = 125;
        check(model.launch(0,1,1) == 125 && model.territories.get(0).troops == 0,"100% sends every troop from a full king");
        model = quiet(0); model.unitsSent = Integer.MAX_VALUE-10; model.territories.get(0).troops = 125;
        model.launch(0,1,1);
        check(model.unitsSent == Integer.MAX_VALUE,"Very long rounds cannot overflow the sent counter and invalidate saves");
        check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Saturated sent counter stays restorable");
        model = quiet(0); model.territories.get(1).owner = 0; model.territories.get(1).troops = 99;
        model.territories.get(0).troops = 125;
        GameModel.Troop packet = new GameModel.Troop(0,1,0,1,.99f); packet.units = 12; model.troops.add(packet); model.update(.02f);
        check(model.territories.get(1).count() == 100,"Reinforcements respect the ordinary tile cap");
        model = quiet(0); model.territories.get(0).troops = GameModel.MAX_TROOPS+1;
        reject(model.save(),"Corrupt oversized garrison rejected");
        model = quiet(0); packet = new GameModel.Troop(0,1,0,1,0); packet.units = -1; model.troops.add(packet);
        reject(model.save(),"Invalid grouped troop quantity rejected");
        model = quiet(0);
        check(java.nio.ByteBuffer.wrap(model.save()).getInt() == 0x464C3035,"V11 saves retain six teams and resignation state with objective/history and mode metadata");
        check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"V6 battle roundtrip remains exact");
    }

    private static void kingGrowth() throws Exception {
        GameModel model = quiet(2); int enemyKing = model.territories.size()-1;
        model.territories.get(0).troops = 10;
        model.territories.get(1).owner = 0; model.territories.get(1).troops = 10;
        model.territories.get(enemyKing).troops = 0;
        model.troops.add(new GameModel.Troop(0,enemyKing,0,1,.99f)); model.update(.02f);
        check(model.capturedKings(0) == 1 && model.teamMultiplier(0) == 1.2,"Capturing an enemy king activates a team-wide 1.2x multiplier");
        double crown = model.territories.get(0).troops, normal = model.territories.get(1).troops;
        model.update(.1f);
        check(Math.abs(model.territories.get(0).troops-crown-.264) < .0001,"King capture boosts the player's original king production");
        check(Math.abs(model.territories.get(1).troops-normal-.162) < .0001,"King capture boosts ordinary team territories too");
        check(GameModel.restore(model.save()).teamMultiplier(0) == 1.2,"King growth bonus survives restore without replaying capture events");
        model.territories.get(enemyKing).owner = 1;
        check(model.teamMultiplier(0) == 1,"Losing the captured king removes its bonus");
        model.territories.get(enemyKing).owner = 0;
        check(model.teamMultiplier(0) == 1.2,"Recapturing the same king cannot repeatedly stack its bonus");
        model.territories.get(4).owner = 0;
        check(Math.abs(model.teamMultiplier(0)-1.44) < .00001,"Holding two different enemy kings multiplies team growth twice");
        model.territories.get(0).owner = 1;
        check(model.teamMultiplier(1) == 1.2,"The same king rule applies fairly to rival teams");
        GameScene scene = new GameScene(new GameScene.Profile(),quiet(2),SILENT); scene.overlay = GameScene.NONE;
        scene.model.territories.get(enemyKing).troops = 0;
        scene.render(new GameModelTest.NullGraphics(),620); float[] position = scene.position(0);
        scene.model.troops.add(new GameModel.Troop(0,enemyKing,0,1,.99f)); scene.update(.02f); scene.update(.1f);
        GameModelTest.TextGraphics labels = new GameModelTest.TextGraphics(); scene.render(labels,620);
        check(labels.text.contains("BOOST x1.20") && labels.text.contains("1 KINGS"),"King capture shows a compact boost indicator above the board");
        check(Arrays.equals(position,scene.position(0)),"Capture announcement does not resize or shift the battlefield");
        for (int i = 0; i < 32; i++) scene.update(.1f);
        labels = new GameModelTest.TextGraphics(); scene.render(labels,620);
        check(labels.text.contains("BOOST x1.20"),"Boost indicator stays visible after the capture animation finishes");
        scene = new GameScene(new GameScene.Profile(),quiet(0),SILENT); scene.overlay = GameScene.NONE;
        scene.model.territories.get(5).troops = 0;
        scene.model.troops.add(new GameModel.Troop(0,5,0,1,.99f)); scene.update(.02f);
        check(scene.model.outcome == GameModel.WON && scene.overlay == GameScene.NONE && scene.profile.wins == 1,"Final enemy king celebrates before the result while recording victory immediately");
        for (int i = 0; i < 25; i++) scene.update(.1f);
        check(scene.overlay == GameScene.RESULT && scene.profile.wins == 1,"King celebration transitions to results without double-recording a win");
    }

    private static void tutorialAndRestart() throws Exception {
        GameScene scene = new GameScene(new GameScene.Profile(),quiet(1),SILENT); scene.back();
        byte[] original = scene.model.save(); click(scene,"tutorial");
        scene.render(new GameModelTest.NullGraphics(),700);
        check(scene.buttonPosition("tutorial_prev") == null,"First tutorial step cannot go before the start");
        click(scene,"tutorial_next"); click(scene,"tutorial_prev");
        GameModelTest.TextGraphics labels = new GameModelTest.TextGraphics(); scene.render(labels,700);
        check(labels.text.contains("Claim Territory"),"Visible previous-step control goes back in the tutorial");
        click(scene,"tutorial_next"); scene.down(110,225); scene.up(310,225); click(scene,"tutorial_next");
        for (String id : new String[] {"demo_quarter","demo_half","demo_all"}) {
            click(scene,id); labels = new GameModelTest.TextGraphics(); scene.render(labels,700);
            int sent = id.equals("demo_quarter") ? 8 : id.equals("demo_half") ? 16 : 32;
            check(labels.text.contains("32 - "+sent+" = "+(32-sent)),"Each tutorial percentage updates visible sent and remaining counts");
        }
        click(scene,"tutorial_prev"); scene.render(new GameModelTest.NullGraphics(),700);
        check(scene.buttonPosition("tutorial_next") != null,"Going back retains completed swipe practice");
        click(scene,"tutorial_next"); scene.down(110,225); scene.up(310,225); click(scene,"tutorial_next");
        click(scene,"demo_capture"); click(scene,"demo_lose");
        labels = new GameModelTest.TextGraphics(); scene.render(labels,700);
        check(!labels.text.contains("Scores and progress stay on this device, offline."),"Requested offline-progress line is removed from How to Play");
        click(scene,"tutorial_prev"); click(scene,"tutorial_next"); click(scene,"tutorial_next"); click(scene,"tutorial_next");
        check(Arrays.equals(original,scene.model.save()),"Tutorial demos and previous navigation leave the retained battle untouched");
        check(scene.profile.music,"Background music defaults on");
        click(scene,"settings"); click(scene,"music");
        check(!scene.profile.music && scene.profile.sound,"Music toggle is independent of sound effects");
        click(scene,"music"); check(scene.profile.music,"Music can be reenabled"); scene.back(); click(scene,"resume");
        scene.model.launch(0,1,.5); scene.model.elapsed = 15; click(scene,"restart");
        click(scene,"confirm_replace");
        check(scene.model.levelIndex == 1 && scene.model.elapsed == 0 && scene.model.unitsSent == 0 && scene.model.troops.isEmpty(),"Toolbar restart starts a clean attempt of the same sector");
    }

    private static void soundtrack() {
        byte[] pcm = MusicGenerator.compose();
        check(pcm.length == 20*22050*2,"Soundtrack contains a complete twenty-second mono loop");
        double square = 0, peak = 0;
        for (int i = 0; i < pcm.length; i += 2) {
            short value = (short)((pcm[i]&255)|(pcm[i+1]<<8)); double amplitude = value/32768.0;
            square += amplitude*amplitude; peak = Math.max(peak,Math.abs(amplitude));
        }
        check(Math.sqrt(square/(pcm.length/2)) > .02 && peak < .65,"Music is non-silent with headroom and no clipping");
        check(pcm[0] == 0 && pcm[1] == 0 && Math.abs((short)((pcm[pcm.length-2]&255)|(pcm[pcm.length-1]<<8))) < 8,"Loop endpoints fade smoothly to avoid a seam click");
    }

    private static void click(GameScene scene,String id) {
        scene.render(new GameModelTest.NullGraphics(),700); float[] point = scene.buttonPosition(id);
        if (point == null) throw new AssertionError("Missing V5 control: "+id);
        scene.down(point[0],point[1]); scene.up(point[0],point[1]);
    }
    private static void reject(byte[] bytes,String message) throws Exception {
        try { GameModel.restore(bytes); throw new AssertionError(message); } catch (java.io.IOException expected) { checks++; }
    }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); checks++; }
}
