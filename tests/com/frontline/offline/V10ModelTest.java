package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class V10ModelTest {
    private static int checks;
    public static int run() throws Exception {
        checks = 0;
        defaultsAndClassic(); actualEvents(); capEvents(); interceptionEvents(); surrenderEvents(); eventBounds(); frozenClocks();
        holdObjective(); keepObjective(); budgetObjective(); objectiveValidation();
        rngCompatibility(); saveResume(); legacyMigration(); invalidStates(); styles();
        return checks;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: "+run()+" focused V10 model checks.");
    }

    private static void defaultsAndClassic() throws Exception {
        int[][] golden = {
            {-1574247572,944483395,-1299451104}, {2093446292,-343939156,370818698},
            {-284519469,-1142979443,615274269}, {-774303230,-1387896996,-584878258}
        };
        int[] levels = {0,12,36,59};
        for (int i = 0; i < levels.length; i++) for (int difficulty = 0; difficulty < 3; difficulty++)
            check(classicFingerprint(levels[i],difficulty) == golden[i][difficulty],"Classic raw simulation stays V6-compatible");
        for (int level = 0; level < GameModel.LEVELS.length; level++) {
            GameModel model = new GameModel(level,1,Long.MIN_VALUE+level);
            check(model.seed == Long.MIN_VALUE+level && model.seedKnown && model.historyKnown && !model.startingKingLost
                && model.rulesVersion == GameModel.RULES_VERSION && model.aiVersion == 0,"New battle metadata is known and classic by default");
            check(model.objectiveType == 0 && model.objectiveTarget == -1 && model.objectiveProgress == 0
                && model.objectiveSeconds == 0 && model.deploymentBudget == 0 && model.challengeId == -1 && model.dailyDate.isEmpty(),"Campaign defaults");
            Set<Integer> kings = new HashSet<>();
            for (int owner = 0; owner <= model.level().opponents; owner++) {
                int king = model.originalKing(owner);
                check(king >= 0 && kings.add(king) && model.territories.get(king).capital && model.territories.get(king).owner == owner,"Original kings map to stable tile IDs");
                check(model.personality(owner) == GameModel.CLASSIC,"Default personalities stay classic");
            }
            check(model.originalKing(-1) == -1 && model.originalKing(6) == -1,"Invalid king owners have no tile");
            check(model.drainEvents().isEmpty(),"New model emits no inferred events");
        }
    }

    private static void actualEvents() throws Exception {
        GameModel model = quiet(2,1,7);
        model.launch(0,1,.25);
        check(model.drainEvents().isEmpty() && model.captures == 0,"Launch does not predict captures");
        model.troops.clear(); model.territories.get(1).troops = 3;
        deliver(model,0,1,0,3);
        check(model.territories.get(1).owner == -1 && model.drainEvents().isEmpty(),"Equal combat units do not capture");
        deliver(model,0,1,0,1);
        ArrayList<GameModel.BattleEvent> events = model.drainEvents();
        check(events.size() == 1 && units(events,GameModel.CAPTURE_EVENT,1) == 1 && model.captures == 1,"Capture reports actual surviving units");
        check(model.drainEvents().isEmpty(),"Draining consumes events");

        int enemyKing = model.originalKing(1), home = model.originalKing(0);
        model.territories.get(enemyKing).troops = 0;
        deliver(model,home,enemyKing,0,2);
        events = model.drainEvents();
        check(units(events,GameModel.KING_GAIN_EVENT,enemyKing) == 1 && units(events,GameModel.CAPTURE_EVENT,enemyKing) == 2
            && Math.abs(model.teamMultiplier(0)-1.2) < .00001,"Actual enemy-king capture updates boost and events");
        model.territories.get(home).troops = 0;
        deliver(model,model.originalKing(2),home,2,2);
        events = model.drainEvents();
        check(units(events,GameModel.HOME_KING_LOSS_EVENT,home) == 1 && units(events,GameModel.KING_LOSS_EVENT,home) == 0
            && model.startingKingLost && Math.abs(model.teamMultiplier(0)-1.2) < .00001,"Original-king loss never removes a held-enemy bonus");
        model.territories.get(home).troops = 0;
        deliver(model,1,home,0,2);
        events = model.drainEvents();
        check(units(events,GameModel.CAPTURE_EVENT,home) == 2 && units(events,GameModel.KING_GAIN_EVENT,home) == 0
            && model.startingKingLost,"Recovering home is not an enemy-king gain or a clean-history reset");
        model.territories.get(enemyKing).troops = 0;
        deliver(model,model.originalKing(2),enemyKing,2,2);
        events = model.drainEvents();
        check(units(events,GameModel.KING_LOSS_EVENT,enemyKing) == 1 && units(events,GameModel.HOME_KING_LOSS_EVENT,enemyKing) == 0
            && model.teamMultiplier(0) == 1,"Held enemy-king loss has distinct feedback");
        model.territories.get(3).troops = 0;
        deliver(model,model.originalKing(2),3,2,2);
        check(model.drainEvents().isEmpty(),"Rival-only captures have no player feedback");
        GameModel restored = GameModel.restore(model.save());
        check(restored.startingKingLost && restored.drainEvents().isEmpty(),"Historical king loss persists without replaying transient feedback");
    }

    private static void capEvents() throws Exception {
        GameModel model = quiet(2,1,8);
        GameModel.Territory normal = model.territories.get(1), home = model.territories.get(model.originalKing(0));
        normal.owner = 0; normal.troops = 100; home.troops = 125;
        model.update(.1f);
        check(model.cappedReinforcements == 0 && model.drainEvents().isEmpty(),"Saturated production is not discarded reinforcement");
        normal.troops = 99.8;
        deliver(model,home.id,normal.id,0,1);
        check(normal.troops == 100 && model.cappedReinforcements == 0 && model.drainEvents().isEmpty(),"Fractional overflow does not invent a whole lost unit");
        normal.troops = 99.8;
        deliver(model,home.id,normal.id,0,4);
        check(model.cappedReinforcements == 3 && units(model.drainEvents(),GameModel.CAP_LOSS_EVENT,normal.id) == 3,"Ordinary cap counts only whole units discarded");
        home.troops = 124.8;
        deliver(model,normal.id,home.id,0,4);
        check(home.troops == 125 && model.cappedReinforcements == 6 && units(model.drainEvents(),GameModel.CAP_LOSS_EVENT,home.id) == 3,"King cap uses the same exact accounting");
        int rivalKing = model.originalKing(1); model.territories.get(rivalKing).troops = 125;
        deliver(model,model.originalKing(2),rivalKing,1,5);
        check(model.cappedReinforcements == 6 && model.drainEvents().isEmpty(),"Rival overflow is not a player loss");
        model.territories.get(3).troops = 0;
        deliver(model,home.id,3,0,300);
        ArrayList<GameModel.BattleEvent> events = model.drainEvents();
        check(model.territories.get(3).troops == 100 && units(events,GameModel.CAP_LOSS_EVENT,3) == 200
            && units(events,GameModel.CAPTURE_EVENT,3) == 300,"Legacy oversized arrivals retain combat strength and report clipped survivors");

        model = quiet(2,1,8); model.territories.get(1).owner = 0; model.territories.get(1).troops = 100;
        for (int i = 0; i < GameModel.MAX_CONVOYS; i++) packet(model,0,1,0,1,.99f,1);
        model.update(.02f); events = model.drainEvents();
        check(events.size() == 1 && units(events,GameModel.CAP_LOSS_EVENT,1) == 900 && model.cappedReinforcements == 900,"Rapid arrivals aggregate rather than growing the event queue");
        check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Cap statistics save exactly");
        model.cappedReinforcements = Integer.MAX_VALUE-2;
        deliver(model,0,1,0,4);
        check(model.cappedReinforcements == Integer.MAX_VALUE,"Lifetime cap statistics saturate without wrapping");
    }

    private static void interceptionEvents() throws Exception {
        GameModel model = quiet(2,1,9);
        for (int i = 0; i < 20; i++) {
            model.troops.clear();
            packet(model,0,model.originalKing(1),0,1,.45f,7);
            packet(model,model.originalKing(1),0,1,1,.45f,3);
            invoke(model,"intercept",new Class<?>[] {float.class},.1f);
            check(model.troops.get(0).units == 4 && model.troops.get(1).units == 0,"Interception cancels actual smaller army");
        }
        ArrayList<GameModel.BattleEvent> events = model.drainEvents();
        check(events.size() == 1 && units(events,GameModel.INTERCEPT_EVENT,model.originalKing(1)) == 60
            && model.intercepted == 60 && model.unitsLost == 60,"Repeated interception is aggregated and counted once for player units");
        model.troops.clear();
        packet(model,0,model.originalKing(1),2,1,.45f,7);
        packet(model,model.originalKing(1),0,1,1,.45f,3);
        invoke(model,"intercept",new Class<?>[] {float.class},.1f);
        check(model.drainEvents().isEmpty() && model.intercepted == 60,"Rival-only interception does not pollute player statistics");
        model.troops.clear();
        check(GameModel.restore(model.save()).intercepted == 60,"Interception totals survive save/resume");
    }

    private static void surrenderEvents() throws Exception {
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel model = dominated(1,difficulty);
            model.update(.1f);
            check(!model.resigned[1] && model.drainEvents().isEmpty(),"Dominance alone emits no prospective surrender event");
            field("dominanceSeconds").setFloat(model,10);
            model.update(.02f);
            ArrayList<GameModel.BattleEvent> events = model.drainEvents();
            int king = model.originalKing(1);
            check(model.resigned[1] && model.outcome == GameModel.WON && model.captures == 1
                && units(events,GameModel.CAPTURE_EVENT,king) == 1 && units(events,GameModel.KING_GAIN_EVENT,king) == 1,"Guarded surrender emits actual capture and king transfer");
        }
        GameModel model = dominated(1,1); field("dominanceSeconds").setFloat(model,10);
        model.territories.get(0).troops = 0; model.update(.02f);
        check(!model.resigned[1] && model.drainEvents().isEmpty(),"Recapturable weak tile still prevents surrender");
        model = dominated(1,1); field("dominanceSeconds").setFloat(model,10);
        packet(model,model.originalKing(1),0,1,20,-20,110); model.update(.02f);
        check(!model.resigned[1] && model.outcome == GameModel.PLAYING,"Queued strong armies still prevent surrender");
        model = dominated(6,1); field("dominanceSeconds").setFloat(model,10); model.update(.02f);
        check(!model.resigned[1],"Exactly 90 percent is not surrender dominance");
    }

    private static void holdObjective() throws Exception {
        GameModel model = quiet(2,1,10); int king = model.originalKing(1);
        model.configureChallenge(1,king,.6f,0);
        advance(model,.2f);
        check(model.objectiveProgress == 0,"Hold timer stays zero before actual control");
        model.territories.get(king).troops = 0; deliver(model,0,king,0,2);
        check(model.objectiveProgress > 0 && model.objectiveProgress < .011f,"Acquisition credits only time after actual arrival");
        advance(model,.2f); check(model.objectiveProgress > .2f && model.outcome == 0,"Hold progresses only during control");
        GameModel resumed = GameModel.restore(model.save());
        check(Arrays.equals(model.save(),resumed.save()),"Hold progress saves exactly");
        resumed.territories.get(king).troops = 0; deliver(resumed,resumed.originalKing(2),king,2,2);
        check(resumed.objectiveProgress == 0 && resumed.outcome == 0,"Loss resets continuous hold progress");
        advance(resumed,.1f); check(resumed.objectiveProgress == 0,"Lost marked king cannot accumulate time");
        resumed.territories.get(king).troops = 0; deliver(resumed,0,king,0,2);
        advance(resumed,.58f); check(resumed.outcome == 0,"Earlier held time is not reused after recapture");
        advance(resumed,.04f); check(resumed.outcome == 1 && resumed.objectiveProgress == .6f,"Continuous marked control completes objective");

        model = quiet(0,1,11); king = model.originalKing(1); model.configureChallenge(1,king,.5f,0);
        model.territories.get(king).troops = 0; deliver(model,0,king,0,2);
        check(model.outcome == 0 && model.owned(1) == 0,"Enemy elimination cannot shortcut hold duration");
        advance(model,.52f); check(model.outcome == 1,"Eliminated-enemy hold still completes by timer");

        model = quiet(0,1,11); king = model.originalKing(1); model.configureChallenge(1,king,.1f,0);
        model.territories.get(king).owner = 0; model.territories.get(king).troops = 0;
        model.territories.get(0).troops = 0;
        packet(model,king,0,1,1,.95f,2); packet(model,0,king,1,1,.95f,2); model.update(.1f);
        check(model.outcome == 2,"Player elimination takes precedence over hold completion");
    }

    private static void eventBounds() throws Exception {
        GameModel model = quiet(59,1,24);
        int normalCount = 0;
        for (GameModel.Territory tile : model.territories) if (!tile.capital) { tile.owner = 1; tile.troops = 100; normalCount++; }
        for (int repeat = 0; repeat < 12; repeat++) {
            for (GameModel.Territory tile : model.territories) {
                if (tile.capital) continue;
                deliver(model,model.originalKing(0),tile.id,0,300);
                deliver(model,model.originalKing(1),tile.id,1,200);
                Arrays.fill((float[])field("aiTimers").get(model),10);
            }
        }
        ArrayList<GameModel.BattleEvent> events = model.drainEvents();
        check(events.size() == normalCount*2 && events.size() <= 432,"Repeated real arrivals retain one event per type/tile within the bounded queue");
        Set<Integer> keys = new HashSet<>();
        for (GameModel.BattleEvent event : events)
            check(keys.add(event.type*100+event.tile) && event.units > 0,"Bounded feedback entries have unique aggregated keys");
        check(model.drainEvents().isEmpty(),"Stress-event draining fully releases the queue");
    }

    private static void frozenClocks() throws Exception {
        for (int type : new int[] {1,2}) {
            GameModel model = quiet(2,1,25);
            model.configureChallenge(type,type == 1 ? model.originalKing(1) : -1,2,0);
            if (type == 1) {
                model.territories.get(model.objectiveTarget).troops = 0;
                deliver(model,0,model.objectiveTarget,0,2);
            }
            advance(model,.2f); byte[] frozen = model.save();
            for (float dt : new float[] {0,-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) model.update(dt);
            check(Arrays.equals(frozen,model.save()),"Paused/invalid clock input never advances objective or battle time");
            GameModel resumed = GameModel.restore(frozen);
            check(resumed.elapsed == model.elapsed && resumed.objectiveProgress == model.objectiveProgress,"Save/resume adds no wall-clock time");
            model.update(.1f); resumed.update(.1f);
            check(Arrays.equals(model.save(),resumed.save()),"Objective clocks resume only with active simulation ticks");
        }
    }

    private static void keepObjective() throws Exception {
        GameModel model = quiet(0,1,12); model.configureChallenge(2,model.originalKing(0),.5f,0);
        model.territories.get(model.originalKing(1)).owner = 0;
        advance(model,.2f); check(model.outcome == 0 && Math.abs(model.objectiveProgress-.2f) < .00001,"Elimination cannot shortcut retention timer");
        GameModel resumed = GameModel.restore(model.save());
        advance(model,.32f); advance(resumed,.32f);
        check(model.outcome == 1 && Arrays.equals(model.save(),resumed.save()),"Retention duration survives save/resume");

        model = quiet(0,1,13); model.configureChallenge(2,-1,.1f,0);
        model.territories.get(1).owner = 0; model.territories.get(0).troops = 0;
        packet(model,1,0,0,1,.99f,3); packet(model,model.originalKing(1),0,1,1,.99f,2);
        model.update(.1f);
        check(model.territories.get(0).owner == 0 && model.startingKingLost && model.outcome == 2,"Same-tick original-king loss wins the tie even after immediate recovery");
        check(GameModel.restore(model.save()).startingKingLost,"Failed retention keeps factual history");
        byte[] frozen = model.save(); model.update(.1f);
        check(Arrays.equals(frozen,model.save()),"Finished objective cannot advance");
    }

    private static void budgetObjective() throws Exception {
        GameModel model = quiet(0,1,14); model.configureChallenge(3,-1,0,16);
        int king = model.originalKing(1); model.territories.get(king).troops = 0;
        check(model.launch(0,king,.5) == 16 && model.outcome == 0,"Exact deployment budget remains playable");
        GameModel resumed = GameModel.restore(model.save()); advance(model,5); advance(resumed,5);
        check(model.outcome == 1 && model.unitsSent == 16 && Arrays.equals(model.save(),resumed.save()),"Budget victory uses ordinary elimination and resumes exactly");
        model = quiet(0,1,15); model.configureChallenge(3,model.originalKing(0),0,16);
        model.launch(0,1,.5); check(model.outcome == 0,"Pending armies do not change spent budget");
        check(model.launch(0,1,.5) == 8 && model.unitsSent == 24 && model.outcome == 2 && model.drainEvents().isEmpty(),"Spending above budget fails immediately without predicting arrivals");
        check(GameModel.restore(model.save()).outcome == 2,"Overspent budget failure saves exactly");
        for (int type = 1; type <= 3; type++) {
            model = quiet(0,1,16);
            model.configureChallenge(type,type == 1 ? model.originalKing(1) : -1,type == 3 ? 0 : 1,type == 3 ? 100 : 0);
            model.territories.get(0).troops = 0; deliver(model,model.originalKing(1),0,1,2);
            check(model.outcome == 2,"Player elimination fails every objective");
        }
    }

    private static void objectiveValidation() throws Exception {
        int[][] bad = {{0,-1,0,0},{4,-1,1,0},{1,0,1,0},{1,1,1,0},{1,99,1,0},{1,5,0,0},
            {2,1,1,0},{2,-1,0,0},{2,-1,1,1},{3,-1,1,10},{3,-1,0,0},{3,-1,0,-1}};
        for (int[] value : bad) {
            GameModel model = quiet(0,1,17);
            configurationRejected(model,value[0],value[1],value[2],value[3]);
        }
        configurationRejected(quiet(0,1,17),2,-1,Float.NaN,0);
        configurationRejected(quiet(0,1,17),2,-1,Float.POSITIVE_INFINITY,0);
        configurationRejected(quiet(0,1,17),2,-1,86401,0);
        GameModel model = quiet(0,1,17); model.update(.01f); configurationRejected(model,2,-1,1,0);
        model = quiet(0,1,17); model.launch(0,1,.5); configurationRejected(model,2,-1,1,0);
        model = quiet(0,1,17); model.configureChallenge(2,-1,1,0); configurationRejected(model,2,-1,1,0);
        for (int id = 0; id < Challenge.PRESETS.length; id++) {
            model = Challenge.PRESETS[id].create(1,1234,id,"2026-10-06");
            check(model.aiVersion == 1 && model.challengeId == id && model.dailyDate.equals("2026-10-06")
                && Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Every agreed challenge preset configures and roundtrips");
        }
    }

    private static void rngCompatibility() throws Exception {
        for (long seed : new long[] {0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,0x123456789ABCDEFL}) {
            GameModel model = new GameModel(36,1,seed); Random expected = new Random(seed);
            for (int i = 0; i < model.territories.size(); i++) expected.nextInt(9);
            Random actual = (Random)field("random").get(model);
            for (int i = 0; i < 100; i++) {
                check(actual.nextInt(1073741825) == expected.nextInt(1073741825),"RNG retains Java rejection sampling");
                check(actual.nextDouble() == expected.nextDouble() && actual.nextFloat() == expected.nextFloat()
                    && actual.nextLong() == expected.nextLong() && actual.nextBoolean() == expected.nextBoolean(),"RNG retains Java core sampling sequence");
            }
            GameModel resumed = GameModel.restore(model.save()); Random restored = (Random)field("random").get(resumed);
            for (int i = 0; i < 100; i++) {
                check(actual.nextInt() == restored.nextInt() && actual.nextDouble() == restored.nextDouble()
                    && actual.nextFloat() == restored.nextFloat(),"Saved 48-bit RNG state resumes exactly");
            }
        }
    }

    private static void saveResume() throws Exception {
        for (int style = 0; style <= 1; style++) for (int level : new int[] {0,12,36,59}) for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel model = new GameModel(level,difficulty,980+level*3+difficulty); model.aiVersion = style;
            for (int tick = 0; tick < 127; tick++) model.update(.07f);
            GameModel resumed = GameModel.restore(model.save());
            check(Arrays.equals(model.save(),resumed.save()),"FL05 roundtrip is exact before continuing");
            for (int tick = 0; tick < 240; tick++) {
                float dt = tick%3 == 0 ? .03f : tick%3 == 1 ? .07f : .1f;
                if (tick%31 == 0) {
                    int source = model.originalKing(0), target = (tick/31+1)%model.territories.size();
                    check(model.launch(source,target,.25) == resumed.launch(source,target,.25),"Resumed deployment uses identical counts");
                }
                model.update(dt); resumed.update(dt);
                check(Arrays.equals(model.save(),resumed.save()),"Save/resume preserves future AI and battle sequence");
                for (GameModel.Territory tile : resumed.territories)
                    check(Double.isFinite(tile.troops) && tile.troops >= 0 && tile.troops <= GameModel.troopCap(tile),"Styled simulations retain fixed caps");
                check(resumed.troops.size() <= GameModel.MAX_CONVOYS,"Styled simulations retain convoy bound");
            }
        }
        GameModel daily = Challenge.PRESETS[4].create(1,Challenge.dailySeed("2026-10-06"),4,"2026-10-06");
        advance(daily,1); GameModel resumed = GameModel.restore(daily.save());
        check(resumed.dailyDate.equals("2026-10-06") && resumed.seed == daily.seed && resumed.challengeId == 4
            && resumed.rulesVersion == GameModel.RULES_VERSION && resumed.missionConfigVersion == Challenge.CONFIG_VERSION
            && resumed.dailyVersion.equals(Challenge.DAILY_VERSION) && resumed.aiVersion == 1 && resumed.historyKnown,"Daily identity and original attempt state persist");
    }

    private static void legacyMigration() throws Exception {
        for (int version = 1; version <= 3; version++) for (int level : new int[] {0,5,12,29}) {
            GameModel old = quietLegacy(level,2,18); old.elapsed = 45; old.captures = 8; old.unitsLost = 11; old.unitsSent = 42;
            old.territories.get(0).troops = version == 2 ? 5000 : 98;
            packet(old,0,1,0,5,.2f,version == 1 ? 1 : 3500);
            GameModel model = GameModel.restore(legacy(old,version));
            check(!model.seedKnown && !model.historyKnown && model.seed == 0 && model.rulesVersion == 0 && model.aiVersion == 0
                && !model.startingKingLost && model.intercepted == 0 && model.cappedReinforcements == 0,"Old saves never invent seed or past king history");
            check(model.elapsed == 45 && model.captures == 8 && model.unitsLost == 11 && model.unitsSent == 42
                && model.territories.get(0).count() == (version == 2 ? 125 : 98) && model.troops.get(0).units == (version == 1 ? 1 : 3500),"FL01/FL02/FL03 retain statistics and legacy convoys");
            check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Migrated unknown history survives FL04 roundtrip");
            configurationRejected(model,2,-1,60,0);
            Random expected = new Random(100+level);
            for (int i = 0; i < model.territories.size(); i++) expected.nextInt(9);
            check(((Random)field("random").get(model)).nextDouble() == expected.nextDouble(),"Old restore keeps original classic reseeding behavior");
        }
        GameModel old = quietLegacy(36,1,18);
        GameModel model = GameModel.restore(legacy(old,3));
        check(model.level().opponents == 5 && model.originalKing(5) == old.originalKing(5),"FL03 expanded faction and king identity migrate");
        old = quietLegacy(0,1,18); old.territories.get(0).owner = 1;
        model = GameModel.restore(legacy(old,3));
        check(!model.historyKnown && !model.startingKingLost,"Legacy current ownership is not fabricated past history");
    }

    private static void invalidStates() throws Exception {
        GameModel model = quiet(0,1,19); byte[] valid = model.save();
        check(ByteBuffer.wrap(valid).getInt() == 0x464C3035,"New format is FL05");
        reject(null); reject(new byte[50001]); reject(Arrays.copyOf(valid,valid.length-1)); reject(Arrays.copyOf(valid,valid.length+1));
        byte[] broken = valid.clone(); broken[0] = 0; reject(broken);
        int metadata = valid.length-89;
        for (int offset : new int[] {8,9,10}) { broken = valid.clone(); broken[metadata+offset] = 2; reject(broken); }
        for (long state : new long[] {-1,1L<<48,Long.MAX_VALUE}) {
            broken = valid.clone(); ByteBuffer.wrap(broken).putLong(broken.length-36,state); reject(broken);
        }
        model.rulesVersion = 9; reject(model.save()); model.rulesVersion = GameModel.RULES_VERSION;
        model.aiVersion = 2; reject(model.save()); model.aiVersion = 0;
        model.historyKnown = false; reject(model.save()); model.historyKnown = true;
        model.seedKnown = false; reject(model.save()); model.seedKnown = true;
        model.intercepted = 1; reject(model.save()); model.intercepted = 0;
        model.cappedReinforcements = -1; reject(model.save()); model.cappedReinforcements = 0;
        model.challengeId = 0; reject(model.save()); model.challengeId = -1;
        model.dailyDate = "2026-10-06"; reject(model.save()); model.dailyDate = "";
        model.territories.get(0).troops = 126; reject(model.save()); model.territories.get(0).troops = 32;
        model.territories.get(1).troops = Double.NaN; reject(model.save()); model.territories.get(1).troops = 10;
        field("dominanceSeconds").setFloat(model,Float.NaN); reject(model.save()); field("dominanceSeconds").setFloat(model,0);
        model.resigned[1] = true; reject(model.save()); model.resigned[1] = false;
        for (float progress : new float[] {-1,Float.NaN,Float.POSITIVE_INFINITY,1}) {
            model.objectiveProgress = progress; reject(model.save());
        }
        model = quiet(0,1,19); model.configureChallenge(1,model.originalKing(1),1,0);
        model.objectiveProgress = .1f; model.elapsed = .1f; reject(model.save()); model.objectiveProgress = 0;
        model.objectiveTarget = 1; reject(model.save()); model.objectiveTarget = model.originalKing(1);
        model.outcome = GameModel.WON; reject(model.save()); model.outcome = GameModel.PLAYING;
        model.challengeId = 9; reject(model.save()); model.challengeId = 0;
        for (String date : new String[] {"2026-02-29","2026-13-01","2026-1-01","0000-01-01","2026-10-06x"}) {
            model.dailyDate = date; reject(model.save());
        }
        model.dailyDate = null;
        try { model.save(); throw new AssertionError("Null daily date accepted"); } catch (IOException expected) { checks++; }
        model.dailyDate = "2024-02-29"; model.dailyVersion = Challenge.DAILY_VERSION;
        check(GameModel.restore(model.save()).dailyDate.equals(model.dailyDate),"Strict leap-day dates remain valid");
        model = quiet(0,1,19); model.configureChallenge(2,-1,1,0);
        model.startingKingLost = true; reject(model.save()); model.startingKingLost = false;
        model.territories.get(0).owner = 1; reject(model.save());
        model = quiet(0,1,19); model.configureChallenge(2,-1,1,0);
        model.elapsed = .2f; reject(model.save());
        model.elapsed = model.objectiveProgress = 1; reject(model.save());
        model = quiet(0,1,19); model.configureChallenge(1,model.originalKing(1),1,0);
        model.territories.get(model.objectiveTarget).owner = 0; model.elapsed = model.objectiveProgress = 1; reject(model.save());
        model = quiet(0,1,19); model.configureChallenge(3,-1,0,10); model.unitsSent = 11; reject(model.save());
        for (int version = 1; version <= 3; version++) {
            broken = legacy(quiet(0,1,19),version); reject(Arrays.copyOf(broken,broken.length-1));
        }
    }

    private static void styles() throws Exception {
        GameModel model = quiet(36,1,20); model.aiVersion = 1;
        check(model.personality(0) == 0 && model.personality(-1) == 0 && model.personality(6) == 0,"Player and invalid owners stay classic");
        for (int owner = 1; owner <= model.level().opponents; owner++) {
            int faction = model.level().faction(owner);
            check(model.personality(owner) == (faction == 1 || faction == 3 ? 1 : 2),"Personality follows faction rather than spawn owner");
        }
        check(GameModel.styleName(0).equals("Classic") && GameModel.styleName(1).equals("Pressure") && GameModel.styleName(2).equals("Guardian"),"Style labels match API");
        for (int level : new int[] {2,36,59}) for (int difficulty = 0; difficulty < 3; difficulty++) for (int seed = 0; seed < 8; seed++) {
            GameModel layout = new GameModel(level,difficulty,seed);
            int pressureOwner = -1, guardianOwner = -1;
            for (int owner = 1; owner <= layout.level().opponents; owner++) {
                int faction = layout.level().faction(owner);
                if (faction == 1 || faction == 3) pressureOwner = owner; else guardianOwner = owner;
            }
            GameModel pressure = aiScenario(level,difficulty,seed,pressureOwner);
            GameModel classic = aiScenario(level,difficulty,seed,pressureOwner); classic.aiVersion = 0;
            int king = pressure.originalKing(0), source = pressureSource(pressure,king);
            pressure.territories.get(pressure.originalKing(pressureOwner)).owner = -1;
            classic.territories.get(classic.originalKing(pressureOwner)).owner = -1;
            pressure.territories.get(source).owner = pressureOwner; pressure.territories.get(source).troops = 80;
            classic.territories.get(source).owner = pressureOwner; classic.territories.get(source).troops = 80;
            int neutral = closestNeutral(pressure,source);
            pressure.territories.get(king).troops = 4; classic.territories.get(king).troops = 4;
            pressure.territories.get(neutral).troops = 1; classic.territories.get(neutral).troops = 1;
            decide(pressure,pressureOwner); decide(classic,pressureOwner);
            check(firstTarget(pressure) == king && firstTarget(classic) == neutral,"Pressure target differs: level="+level+", difficulty="+difficulty+", seed="+seed+", targets="+firstTarget(pressure)+"/"+firstTarget(classic)+", expected="+king+"/"+neutral);

            GameModel guardian = aiScenario(level,difficulty,seed,guardianOwner);
            classic = aiScenario(level,difficulty,seed,guardianOwner); classic.aiVersion = 0;
            int home = guardian.originalKing(guardianOwner); source = closestNeutral(guardian,home);
            guardian.territories.get(home).owner = 0; guardian.territories.get(home).troops = 4;
            classic.territories.get(home).owner = 0; classic.territories.get(home).troops = 4;
            guardian.territories.get(source).owner = guardianOwner; guardian.territories.get(source).troops = 80;
            classic.territories.get(source).owner = guardianOwner; classic.territories.get(source).troops = 80;
            int tempting = closestNeutral(guardian,source);
            guardian.territories.get(tempting).troops = 1; classic.territories.get(tempting).troops = 1;
            decide(guardian,guardianOwner); decide(classic,guardianOwner);
            check(firstTarget(guardian) == home,"Guardian recovers its own king at every difficulty");
            if (difficulty == 0) check(firstTarget(classic) == tempting,"Guardian recovery differs from relaxed classic Easy");
        }
        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel pressure = quiet(36,difficulty,21), guardian = quiet(36,difficulty,21), classic = quiet(36,difficulty,21);
            pressure.aiVersion = guardian.aiVersion = 1;
            for (GameModel candidate : new GameModel[] {pressure,guardian,classic})
                for (GameModel.Territory tile : candidate.territories) tile.troops = tile.owner >= 0 ? 100 : tile.troops;
            int pressSource = pressure.originalKing(3), guardSource = guardian.originalKing(1);
            int pressReserve = reserve(pressure,pressSource,3), guardReserve = reserve(guardian,guardSource,1);
            check(pressReserve < reserve(classic,pressSource,3) && guardReserve > reserve(classic,guardSource,1),"Reserves vary by style while keeping difficulty separate");
            GameModel twin = GameModel.restore(guardian.save()); twin.aiVersion = 0;
            guardian.update(.1f); twin.update(.1f);
            for (int tile = 0; tile < twin.territories.size(); tile++)
                check(guardian.territories.get(tile).troops == twin.territories.get(tile).troops,"Styles never grant production bonuses");
        }
        for (int difficulty : new int[] {1,2}) {
            GameModel guardian = aiScenario(36,difficulty,22,1); int home = guardian.originalKing(1);
            guardian.territories.get(home).owner = 0; guardian.territories.get(home).troops = 125;
            Set<Integer> sources = new HashSet<>();
            for (int i = 1; i <= 3; i++) {
                int source = closestNeutral(guardian,home); guardian.territories.get(source).owner = 1; guardian.territories.get(source).troops = 100;
            }
            decide(guardian,1);
            for (GameModel.Troop troop : guardian.troops) { sources.add(troop.source); check(troop.target == home,"Guardian coordinated recovery targets home king"); }
            check(sources.size() >= 2,"Guardian combines armies to recover a fully defended king");
        }
    }

    private static GameModel aiScenario(int level,int difficulty,long seed,int owner) throws Exception {
        GameModel model = quiet(level,difficulty,seed); model.aiVersion = 1;
        for (GameModel.Territory tile : model.territories) { tile.owner = -1; tile.troops = 99; }
        int source = model.originalKing(owner); model.territories.get(source).owner = owner; model.territories.get(source).troops = 80;
        model.territories.get(model.originalKing(0)).owner = 0;
        return model;
    }

    private static int closestNeutral(GameModel model,int source) {
        int best = -1; double distance = Double.MAX_VALUE;
        GameModel.Territory origin = model.territories.get(source);
        for (GameModel.Territory tile : model.territories) {
            double candidate = Math.hypot(tile.x-origin.x,tile.y-origin.y);
            if (tile.owner == -1 && !tile.capital && candidate < distance) { best = tile.id; distance = candidate; }
        }
        return best;
    }

    private static int pressureSource(GameModel model,int king) {
        int best = -1; double difference = Double.MAX_VALUE;
        GameModel.Territory home = model.territories.get(king);
        for (GameModel.Territory tile : model.territories) {
            double candidate = Math.abs(Math.hypot(tile.x-home.x,tile.y-home.y)-3.464);
            if (tile.owner == -1 && !tile.capital && candidate < difference) { best = tile.id; difference = candidate; }
        }
        return best;
    }

    private static int firstTarget(GameModel model) { return model.troops.isEmpty() ? -1 : model.troops.get(0).target; }
    private static int reserve(GameModel model,int source,int owner) throws Exception {
        return (Integer)invoke(model,"defensiveReserve",new Class<?>[] {GameModel.Territory.class,int.class},model.territories.get(source),owner);
    }
    private static void decide(GameModel model,int owner) throws Exception { invoke(model,"playAi",new Class<?>[] {int.class},owner); }
    private static Object invoke(GameModel model,String name,Class<?>[] types,Object... args) throws Exception {
        Method method = GameModel.class.getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(model,args);
    }
    private static Field field(String name) throws Exception { Field field = GameModel.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private static GameModel quiet(int level,int difficulty,long seed) throws Exception {
        GameModel model = new GameModel(level,difficulty,seed); Arrays.fill((float[])field("aiTimers").get(model),10); return model;
    }
    private static GameModel quietLegacy(int level,int difficulty,long seed) throws Exception {
        GameModel model = new GameModel(level,difficulty,seed,GameModel.LEGACY_RULES_VERSION);
        Arrays.fill((float[])field("aiTimers").get(model),10); return model;
    }
    private static GameModel dominated(int level,int difficulty) throws Exception {
        GameModel model = quiet(level,difficulty,23);
        for (GameModel.Territory tile : model.territories) { tile.owner = 0; tile.troops = GameModel.troopCap(tile); }
        GameModel.Territory last = model.territories.get(model.originalKing(1)); last.owner = 1; last.troops = 1; return model;
    }
    private static void packet(GameModel model,int source,int target,int owner,float duration,float age,int units) {
        GameModel.Troop troop = new GameModel.Troop(source,target,owner,duration,age); troop.units = units; model.troops.add(troop);
    }
    private static void deliver(GameModel model,int source,int target,int owner,int units) {
        packet(model,source,target,owner,1,.99f,units); model.update(.02f);
    }
    private static void advance(GameModel model,float seconds) { for (int i = 0; i < Math.round(seconds/.02f); i++) model.update(.02f); }
    private static int units(ArrayList<GameModel.BattleEvent> events,int type,int tile) {
        int units = 0; for (GameModel.BattleEvent event : events) if (event.type == type && event.tile == tile) units += event.units; return units;
    }
    private static void configurationRejected(GameModel model,int type,int target,float seconds,int budget) {
        try { model.configureChallenge(type,target,seconds,budget); throw new AssertionError("Invalid challenge accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
    }
    private static void reject(byte[] bytes) throws Exception {
        try { GameModel.restore(bytes); throw new AssertionError("Invalid save accepted"); }
        catch (IOException expected) { checks++; }
    }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); checks++; }

    private static byte[] legacy(GameModel model,int version) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x464C3030+version); out.writeInt(model.levelIndex); out.writeInt(model.difficulty);
        out.writeFloat(model.elapsed); out.writeInt(model.outcome); out.writeInt(model.captures); out.writeInt(model.unitsLost); out.writeInt(model.unitsSent);
        out.writeInt(model.territories.size());
        for (GameModel.Territory tile : model.territories) { out.writeInt(tile.owner); out.writeDouble(tile.troops); }
        out.writeInt(model.troops.size());
        for (GameModel.Troop troop : model.troops) {
            out.writeInt(troop.source); out.writeInt(troop.target); out.writeInt(troop.owner); out.writeFloat(troop.duration); out.writeFloat(troop.age);
            if (version >= 2) out.writeInt(troop.units);
        }
        float[] timers = (float[])field("aiTimers").get(model);
        for (int i = 0; i < (version == 3 ? 6 : 4); i++) out.writeFloat(timers[i]);
        if (version == 3) { out.writeFloat(field("dominanceSeconds").getFloat(model)); for (boolean resigned : model.resigned) out.writeBoolean(resigned); }
        out.flush(); return bytes.toByteArray();
    }

    private static int classicFingerprint(int level,int difficulty) throws Exception {
        GameModel model = new GameModel(level,difficulty,710+level,GameModel.LEGACY_RULES_VERSION);
        for (int tick = 0; tick < 900; tick++) {
            if (tick%37 == 0) {
                for (GameModel.Territory source : model.territories) {
                    if (source.owner != GameModel.PLAYER || source.count() < 20) continue;
                    GameModel.Territory target = null;
                    for (GameModel.Territory candidate : model.territories)
                        if (candidate.owner != GameModel.PLAYER && (target == null || candidate.count() < target.count())) target = candidate;
                    if (target != null) model.launch(source.id,target.id,.75);
                }
            }
            model.update(.06f);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeFloat(model.elapsed); out.writeInt(model.outcome); out.writeInt(model.captures);
        out.writeInt(model.unitsSent); out.writeInt(model.unitsLost);
        for (GameModel.Territory tile : model.territories) { out.writeInt(tile.owner); out.writeDouble(tile.troops); }
        for (GameModel.Troop troop : model.troops) {
            out.writeInt(troop.source); out.writeInt(troop.target); out.writeInt(troop.owner);
            out.writeFloat(troop.duration); out.writeFloat(troop.age); out.writeInt(troop.units);
        }
        out.flush(); return Arrays.hashCode(bytes.toByteArray());
    }
}
