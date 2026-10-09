package com.frontline.offline;

import java.util.Arrays;

public final class V11BalanceTest {
    private static int checks;
    public static void main(String[] args) throws Exception { System.out.println("PASS: "+run()+" focused balance lab checks."); }
    public static int run() throws Exception {
        checks = 0;
        for (int id : new int[] {3,4}) for (int difficulty : new int[] {1,2}) for (int group = 0; group < 2; group++) {
            int wins = 0;
            for (int seed : DefenseBalance.SEEDS) if (group == 0 ? seed < 1000 : seed >= 1000) {
                GameModel model = DefenseBalance.simulate(id,difficulty,seed,false,"passive");
                check(model.outcome != GameModel.PLAYING,"Defence simulation does not disguise timeout as a win");
                if (model.outcome == GameModel.WON) wins++;
            }
            check(wins <= 2,"Home/Province passive <=2/20 in each independent seed set and difficulty");
        }
        for (int id : new int[] {3,4}) for (String profile : new String[] {"fortify","counterattack"}) {
            GameModel model = DefenseBalance.simulate(id,1,id == 3 ? 0 : 13,false,profile);
            check(model.outcome == GameModel.WON && model.unitsSent > 0,"Two distinct legal-launch strategies succeed per mission");
        }
        for (int map : new int[] {0,12,36}) {
            GameModel first = new GameModel(map,1,99), second = new GameModel(map,1,99);
            int[] styles = new int[first.level().opponents+1]; for (int i = 0; i < styles.length; i++) styles[i] = i%3;
            GameModel.LabAi a = first.labAi(styles,false), b = second.labAi(styles.clone(),false); Arrays.fill(styles,2);
            for (int tick = 0; tick < 600; tick++) {
                a.step(.05f); b.step(.05f);
                check(Arrays.equals(first.save(),second.save()) && a.winner() == b.winner(),"Lab style inputs copied; job is deterministic");
                check(first.outcome == GameModel.PLAYING && first.terminalReason == GameModel.TERMINAL_NONE,"Lab leaves player-facing outcome semantics untouched");
                check(first.personality(GameModel.PLAYER) == GameModel.CLASSIC,"Lab override never escapes a step");
            }
        }
        GameModel model = new GameModel(0,1,99); GameModel.LabAi lab = model.labAi(new int[] {1,2},false);
        for (GameModel.Territory tile : model.territories) if (tile.owner == 0) tile.owner = 1;
        lab.step(.05f); check(lab.finished() && lab.winner() == 1 && !lab.resignationUsed() && model.outcome == GameModel.PLAYING,"Natural elimination has independent lab winner");
        model = new GameModel(36,1,99); lab = model.labAi(new int[] {1,1,1,1,1,1},true);
        for (GameModel.Territory tile : model.territories) { tile.owner = 2; tile.troops = 125; }
        GameModel.Territory survivor = model.territories.get(model.originalKing(1)); survivor.owner = 1; survivor.troops = 0;
        java.lang.reflect.Field timers = GameModel.class.getDeclaredField("aiTimers"); timers.setAccessible(true);
        Arrays.fill((float[])timers.get(model),10);
        for (int tick = 0; tick < 220 && !lab.finished(); tick++) lab.step(.05f);
        check(lab.finished() && lab.winner() == 2 && lab.resignationUsed() && model.outcome == GameModel.PLAYING,"Guarded resignation works for non-player dominant lab seat");
        return checks;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
