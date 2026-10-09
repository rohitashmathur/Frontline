package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class V11ModelTest {
    private static int checks;
    public static void main(String[] args) throws Exception { System.out.println("PASS: "+run()+" focused V11 model checks."); }
    public static int run() throws Exception {
        checks = 0; deployment(); reasons(); legacy(); equalStartsAndRegression(); runPerks(); frozenMap(); invalid();
        return checks;
    }

    private static void deployment() throws Exception {
        for (int count = 0; count <= 140; count++) for (double fraction : new double[] {0,.01,.25,.5,.75,1,-1,1.01,Double.NaN,Double.POSITIVE_INFINITY}) {
            GameModel model = new GameModel(0,1,1); model.territories.get(0).troops = count;
            int expected = count < 2 || !Double.isFinite(fraction) || fraction <= 0 || fraction > 1 ? 0 : (int)Math.floor(count*fraction);
            check(GameModel.deploymentAmount(count,fraction) == expected,"Shared integer calculation");
            check(model.previewAmount(0,1,fraction) == expected,"Preview matches shared calculation");
            check(model.launch(0,1,fraction) == expected && model.unitsSent == expected,"Actual launch matches preview");
        }
        GameModel model = budget(120); model.territories.get(0).troops = 125;
        check(model.previewAmount(0,1,1) == 125 && model.outcome == GameModel.PLAYING,"Over-budget preview is a non-mutating warning");
        check(model.launch(0,1,1) == 125 && model.unitsSent == 125 && model.terminalReason == GameModel.TERMINAL_BUDGET,"125/120 still launches and loses");
        roundtrip(model);
        model = budget(32); check(model.launch(0,1,.5) == 16 && model.outcome == GameModel.PLAYING,"First budget send");
        model.territories.get(1).owner = 0; model.territories.get(1).troops = 16;
        check(model.launch(1,0,1) == 16 && model.unitsSent == 32 && model.outcome == GameModel.PLAYING,"Friendly reinforcement counts exactly at allowance");
        model.territories.get(1).troops = 2;
        check(model.launch(1,0,.5) == 1 && model.unitsSent == 33 && model.terminalReason == GameModel.TERMINAL_BUDGET,"One troop over loses");
        model = budget(120); byte[] initial = model.save();
        for (int[] action : new int[][] {{-1,1},{0,-1},{0,0},{0,99},{1,0}})
            check(model.previewAmount(action[0],action[1],.5) == 0 && model.launch(action[0],action[1],.5) == 0,"Invalid action is zero");
        check(Arrays.equals(initial,model.save()),"Invalid previews/actions consume no budget or randomness");
        for (int i = 0; i < GameModel.MAX_CONVOYS; i++) model.troops.add(new GameModel.Troop(0,1,0,1,0));
        check(model.previewAmount(0,1,1) == 0 && model.launch(0,1,1) == 0 && model.unitsSent == 0,"Full convoy queue refuses without accounting");
    }

    private static void reasons() throws Exception {
        GameModel model = new GameModel(0,1,2); model.configureChallenge(Challenge.KEEP_KING,-1,.01f,0);
        model.territories.get(0).troops = 0;
        GameModel.Troop enemy = new GameModel.Troop(5,0,1,1,.99f); enemy.units = 3; model.troops.add(enemy);
        model.update(.02f);
        check(model.outcome == GameModel.LOST && model.terminalReason == GameModel.TERMINAL_PROTECTED_KING,"Protected king precedes elimination and simultaneous timer completion");
        roundtrip(model);
        model = new GameModel(0,1,2);
        for (GameModel.Territory territory : model.territories) if (territory.owner == 0) territory.owner = 1;
        model.update(.05f); check(model.terminalReason == GameModel.TERMINAL_ELIMINATED,"Elimination cause"); roundtrip(model);
        model = budget(1); model.surrender(); model.surrender(); model.update(1);
        check(model.terminalReason == GameModel.TERMINAL_SURRENDER && model.elapsed == 0,"Surrender is explicit, idempotent and freezes clocks");
        roundtrip(model); check(model.launch(0,1,1) == 0 && model.terminalReason == GameModel.TERMINAL_SURRENDER,"Terminal reason cannot be overwritten by late input");
        model = new GameModel(0,1,2); model.configureChallenge(Challenge.KEEP_KING,-1,.1f,0); model.update(.1f);
        check(model.outcome == GameModel.WON && model.terminalReason == GameModel.TERMINAL_VICTORY && model.stars() == 0 && model.score() == 0,"Revised mission victory has no campaign scoring");
        roundtrip(model);
    }

    private static void legacy() throws Exception {
        for (int id = 0; id < Challenge.PRESETS.length; id++) for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel old = Challenge.PRESETS[id].createLegacy(difficulty,810+id,id,"2026-10-06");
            for (int tick = 0; tick < 37; tick++) old.update(.05f);
            GameModel restored = GameModel.restore(fl04(old));
            check(restored.rulesVersion == 10 && restored.missionConfigVersion == 1 && restored.missionPressure == 0
                && restored.dailyVersion.equals("daily-v10-1"),"FL04 identity retains old rules and daily identifier");
            check(restored.objectiveSeconds == old.objectiveSeconds && restored.seed == old.seed
                && restored.objectiveProgress == old.objectiveProgress,"Old timer and seed are never retuned");
            for (int tick = 0; tick < 500; tick++) {
                old.update(.05f); restored.update(.05f);
                check(Arrays.equals(old.save(),restored.save()),"FL04 future simulation and RNG match uninterrupted V10");
            }
            GameModel retry = Challenge.PRESETS[id].createVersioned(restored.difficulty,restored.seed,id,restored.dailyDate,restored.missionConfigVersion);
            check(retry.rulesVersion == 10 && retry.missionPressure == 0 && retry.objectiveSeconds == Challenge.PRESETS[id].legacySeconds,"Legacy retry uses original configuration");
        }
        GameModel old = Challenge.PRESETS[6].createLegacy(1,7,6,""); old.territories.get(0).troops = 125; old.launch(0,1,1);
        check(GameModel.restore(fl04(old)).terminalReason == GameModel.TERMINAL_BUDGET,"Recoverable legacy budget cause");
        old = new GameModel(0,1,8,10); old.outcome = GameModel.LOST;
        check(GameModel.restore(fl04(old)).terminalReason == GameModel.TERMINAL_LEGACY,"Unknown legacy defeat is not invented as elimination");
        old = new GameModel(0,1,8,10); old.surrender(); roundtrip(old);
    }

    private static void equalStartsAndRegression() throws Exception {
        for (int level = 0; level < GameModel.LEVELS.length; level++) for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel fresh = new GameModel(level,difficulty,210+level);
            GameModel old = new GameModel(level,difficulty,210+level,10);
            for (int owner = 0; owner <= fresh.level().opponents; owner++) {
                check(fresh.owned(owner) == 1 && fresh.army(owner) == fresh.level().playerTroops,"All factions have identical entire starting armies");
                old.territories.get(old.originalKing(owner)).troops = fresh.territories.get(fresh.originalKing(owner)).troops;
            }
            for (int tick = 0; tick < 400; tick++) {
                if (tick%37 == 0) for (GameModel.Territory source : fresh.territories) if (source.owner == 0) {
                    int target = (source.id+tick/37+1)%fresh.territories.size();
                    check(fresh.launch(source.id,target,.5) == old.launch(source.id,target,.5),"Matched starting counts retain deployment behavior");
                }
                fresh.update(.05f); old.update(.05f);
                check(Arrays.equals(simulationState(fresh),simulationState(old)),"Rules 11 campaign simulation differs only through initial counts");
            }
        }
        for (int id : new int[] {3,4,5}) for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel mission = Challenge.PRESETS[id].create(difficulty,11,id,"");
            for (int owner = 0; owner <= mission.level().opponents; owner++)
                check(mission.owned(owner) == 1 && mission.army(owner) == mission.level().playerTroops,"Mission pressure adds no extra owned tiles/starting armies");
        }
    }

    private static void runPerks() throws Exception {
        GameModel campaign = new GameModel(0,1,3), run = new GameModel(0,1,3);
        run.configureRun(GameModel.ALL_RUN_PERKS,"run-association",2);
        check(run.territories.get(0).count() == 42 && campaign.territories.get(0).count() == 32,"Starting bonus applied once only in selected run");
        check(run.army(1) == campaign.army(1),"Run starting perk never benefits rivals");
        roundtrip(run);
        run.territories.get(1).owner = campaign.territories.get(1).owner = 0;
        run.territories.get(1).troops = campaign.territories.get(1).troops = 10;
        double king = run.territories.get(0).troops; run.update(.1f); campaign.update(.1f);
        check(Math.abs(run.territories.get(0).troops-king-.253) < .000001,"King production +15 percent");
        check(Math.abs(run.territories.get(1).troops-10-.1458) < .000001,"Ordinary production +8 percent");
        check(run.capacity(run.territories.get(0)) == 140 && run.capacity(run.territories.get(5)) == 125,"King cap +15 only for run player");
        run.territories.get(0).troops = 140; roundtrip(run);
        run.launch(0,1,.25); campaign.launch(0,1,.25);
        check(Math.abs(run.troops.get(0).duration*1.12f-campaign.troops.get(0).duration) < .000001,"Convoy speed +12 percent");
        GameModel control = new GameModel(0,1,4); campaign = new GameModel(0,1,4); campaign.runPerks = GameModel.ALL_RUN_PERKS;
        control.update(.1f); campaign.update(.1f);
        check(Arrays.equals(simulationState(control),simulationState(campaign)) && campaign.capacity(campaign.territories.get(0)) == 125,"Stray flags cannot leak modifiers into campaign");
        try { run.configureRun(0,"again",3); throw new AssertionError("Double run setup accepted"); }
        catch (IllegalStateException expected) { checks++; }
    }

    private static void frozenMap() throws Exception {
        GameModel.Level original = GameModel.LEVELS[0];
        GameModel run = new GameModel(0,1,6,original); run.configureRun(0,"frozen",0); byte[] saved = run.save();
        try {
            GameModel.LEVELS[0] = new GameModel.Level("Changed topology",4,3,1,new int[] {5},100);
            GameModel resumed = GameModel.restore(saved);
            check(resumed.territories.size() == 6 && resumed.level().name.equals(original.name),"Saved topology survives later map-definition edits");
            check(Arrays.equals(saved,resumed.save()),"Frozen geometry and run association roundtrip exactly");
            for (int tick = 0; tick < 150; tick++) { run.update(.05f); resumed.update(.05f); check(Arrays.equals(run.save(),resumed.save()),"Frozen map continues deterministically"); }
        } finally { GameModel.LEVELS[0] = original; }
        GameModel custom = new GameModel(0,1,6,new GameModel.Level("Prototype layout",4,3,new int[] {5},100,new int[] {0,11},new int[] {1},32,32,4));
        roundtrip(custom);
    }

    private static void invalid() throws Exception {
        GameModel model = new GameModel(0,1,1); byte[] saved = model.save();
        reject(Arrays.copyOf(saved,saved.length-1)); reject(Arrays.copyOf(saved,saved.length+1));
        byte[] corrupt = saved.clone(); ByteBuffer.wrap(corrupt).putInt(34+model.level().name.length(),0); reject(corrupt);
        model.terminalReason = 99; reject(model.save()); model.terminalReason = GameModel.TERMINAL_BUDGET; reject(model.save()); model.terminalReason = 0;
        model.missionConfigVersion = 1; reject(model.save()); model.missionConfigVersion = 0;
        model.runPerks = 1; reject(model.save()); model.runPerks = 0;
        model.dailyVersion = Challenge.DAILY_VERSION; reject(model.save()); model.dailyVersion = "";
        model.configureRun(8,"cap",0); model.territories.get(0).troops = 140; roundtrip(model);
        model.runPerks = 0; reject(model.save());
    }

    private static GameModel budget(int limit) { GameModel model = new GameModel(0,1,1); model.configureChallenge(Challenge.BUDGET,-1,0,limit); return model; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void roundtrip(GameModel model) throws Exception { check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Complete FL05 metadata roundtrip"); }
    private static void reject(byte[] bytes) throws Exception { try { GameModel.restore(bytes); throw new AssertionError("Invalid save accepted"); } catch (IOException expected) { checks++; } }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static long randomState(GameModel model) throws Exception { Object random = field(GameModel.class,"random").get(model); return field(random.getClass(),"state").getLong(random); }

    private static byte[] simulationState(GameModel model) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeFloat(model.elapsed); out.writeInt(model.outcome); out.writeInt(model.captures); out.writeInt(model.unitsLost); out.writeInt(model.unitsSent);
        for (GameModel.Territory territory : model.territories) { out.writeInt(territory.owner); out.writeDouble(territory.troops); }
        out.writeInt(model.troops.size());
        for (GameModel.Troop troop : model.troops) { out.writeInt(troop.source); out.writeInt(troop.target); out.writeInt(troop.owner); out.writeFloat(troop.duration); out.writeFloat(troop.age); out.writeInt(troop.units); }
        out.writeLong(randomState(model)); for (boolean resigned : model.resigned) out.writeBoolean(resigned);
        out.flush(); return bytes.toByteArray();
    }

    static byte[] fl04(GameModel model) throws Exception {
        check(model.rulesVersion == 10,"FL04 fixture deliberately uses rules 10");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x464C3034); out.writeInt(model.levelIndex); out.writeInt(model.difficulty);
        out.writeFloat(model.elapsed); out.writeInt(model.outcome); out.writeInt(model.captures); out.writeInt(model.unitsLost); out.writeInt(model.unitsSent);
        out.writeInt(model.territories.size()); for (GameModel.Territory territory : model.territories) { out.writeInt(territory.owner); out.writeDouble(territory.troops); }
        out.writeInt(model.troops.size());
        for (GameModel.Troop troop : model.troops) { out.writeInt(troop.source); out.writeInt(troop.target); out.writeInt(troop.owner); out.writeFloat(troop.duration); out.writeFloat(troop.age); out.writeInt(troop.units); }
        for (float timer : (float[])field(GameModel.class,"aiTimers").get(model)) out.writeFloat(timer);
        out.writeFloat(field(GameModel.class,"dominanceSeconds").getFloat(model)); for (boolean resigned : model.resigned) out.writeBoolean(resigned);
        out.writeLong(model.seed); out.writeBoolean(model.seedKnown); out.writeBoolean(model.historyKnown); out.writeBoolean(model.startingKingLost);
        out.writeInt(model.rulesVersion); out.writeInt(model.aiVersion); out.writeInt(model.intercepted); out.writeInt(model.cappedReinforcements);
        out.writeInt(model.objectiveType); out.writeInt(model.objectiveTarget); out.writeFloat(model.objectiveSeconds); out.writeFloat(model.objectiveProgress);
        out.writeInt(model.deploymentBudget); out.writeInt(model.challengeId); out.writeUTF(model.dailyDate); out.writeLong(randomState(model)); out.flush(); return bytes.toByteArray();
    }
}
