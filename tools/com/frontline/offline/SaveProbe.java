package com.frontline.offline;

import java.util.Base64;

public final class SaveProbe {
    public static void main(String[] args) throws Exception {
        GameModel model = GameModel.restore(Base64.getDecoder().decode(args[0]));
        if (args.length > 1 && args[1].equals("--level")) {
            if (model.levelIndex != Integer.parseInt(args[2])) throw new AssertionError("Unexpected selected sector");
        } else if (args.length > 1 && args[1].equals("--final")) {
            if (model.levelIndex != 59 || model.outcome != GameModel.WON)
                throw new AssertionError("Campaign final did not complete");
        } else if (args.length > 1 && args[1].equals("--intercept")) {
            int friendly = 0, enemy = 0;
            for (GameModel.Troop troop : model.troops) { if (troop.owner == 0) friendly += troop.units; else enemy += troop.units; }
            if (friendly != 0 || enemy != 10 || model.unitsLost != 20) throw new AssertionError("Native 20-versus-30 interception did not leave 10 enemy troops");
        } else if (args.length > 1 && args[1].equals("--king")) {
            if (model.teamMultiplier(0) != 1.2 || model.capturedKings(0) != 1) throw new AssertionError("Native king capture did not activate team-wide 1.2x growth");
        } else if (args.length > 1 && args[1].equals("--overflow")) {
            if (model.territories.get(0).count() != 125 || model.territories.get(1).count() != 100) throw new AssertionError("Native fixed troop caps were not retained");
        } else if (args.length > 1 && args[1].equals("--four-kings")) {
            if (model.capturedKings(0) != 4 || model.teamMultiplier(0) != 3) throw new AssertionError("Four enemy kings did not activate 3x growth");
        } else if (args.length > 1 && args[1].equals("--five-kings")) {
            if (model.capturedKings(0) != 5 || Math.abs(model.teamMultiplier(0)-3.6) > .00001) throw new AssertionError("Fifth enemy king did not activate 3.6x growth");
        } else if (args.length > 1 && args[1].equals("--resign")) {
            if (model.outcome != GameModel.WON || !model.resigned[1] || model.owned(1) != 0) throw new AssertionError("Hopeless rival did not resign");
        } else if (args.length > 1 && args[1].equals("--migration")) {
            if (model.levelIndex != 29 || model.elapsed != 42 || model.unitsSent != 20 || model.territories.get(0).count() != GameModel.troopCap(model.territories.get(0))
                || model.territories.get(1).count() != 100 || model.troops.get(0).units != 1100) throw new AssertionError("V5 overflow migration lost round state or failed to apply caps");
        } else if (args.length > 1 && args[1].equals("--restart")) {
            if (model.elapsed > 2 || model.unitsSent != 0 || model.owned(0) != 1 || !model.troops.isEmpty()) throw new AssertionError("Toolbar restart did not reset round");
        } else if (args.length > 1 && args[1].equals("--camera")) {
            if (model.levelIndex != 59 || model.unitsSent != 0 || model.level().opponents != 5) throw new AssertionError("Camera navigation accidentally dispatched an army");
        } else if (args.length > 1 && args[1].equals("--ai")) {
            GameModel.Territory source = model.territories.get(model.territories.size()-1);
            GameModel.Territory target = model.territories.get(model.territories.size()-2);
            if (model.elapsed < 5 || source.owner != 1 || source.count() < 30 || target.owner != 1)
                throw new AssertionError("Native AI did not capture the weak tile while retaining its source garrison");
            System.out.println("Native AI verified: capitalReserve="+source.count()+", capturedNormal="+target.count());
        } else {
            if (model.unitsSent <= 0) throw new AssertionError("Android drag did not dispatch troops");
            if (args.length > 1 && model.unitsSent != Integer.parseInt(args[1])) throw new AssertionError("Unexpected dispatch fraction");
        }
        System.out.println("Android save verified: dispatched=" + model.unitsSent + ", inTransit=" + model.troops.size()
            + ", elapsed=" + model.elapsed + ", outcome=" + model.outcome);
    }
}
