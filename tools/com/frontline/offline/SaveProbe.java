package com.frontline.offline;

import java.util.Base64;

public final class SaveProbe {
    public static void main(String[] args) throws Exception {
        GameModel model = GameModel.restore(Base64.getDecoder().decode(args[0]));
        if (args.length > 1 && args[1].equals("--level")) {
            if (model.levelIndex != Integer.parseInt(args[2])) throw new AssertionError("Unexpected selected sector");
        } else if (args.length > 1 && args[1].equals("--final")) {
            if (model.levelIndex != 29 || model.outcome != GameModel.WON)
                throw new AssertionError("Campaign final did not complete");
        } else if (args.length > 1 && args[1].equals("--intercept")) {
            int friendly = 0, enemy = 0;
            for (GameModel.Troop troop : model.troops) { if (troop.owner == 0) friendly += troop.units; else enemy += troop.units; }
            if (friendly != 0 || enemy != 10 || model.unitsLost != 20) throw new AssertionError("Native 20-versus-30 interception did not leave 10 enemy troops");
        } else if (args.length > 1 && args[1].equals("--king")) {
            if (model.teamMultiplier(0) != 1.5 || model.capturedKings(0) != 1) throw new AssertionError("Native king capture did not activate team-wide 1.5x growth");
        } else if (args.length > 1 && args[1].equals("--overflow")) {
            if (model.territories.get(0).count() <= 99 || model.territories.get(1).count() <= 99) throw new AssertionError("Native saturated tiles did not grow beyond 99");
        } else if (args.length > 1 && args[1].equals("--restart")) {
            if (model.elapsed > 2 || model.unitsSent != 0 || model.owned(0) != 1 || !model.troops.isEmpty()) throw new AssertionError("Toolbar restart did not reset round");
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
