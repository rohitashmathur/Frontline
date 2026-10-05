package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

// Test fixture writer matching the V4/V5 layouts; never packaged into the game.
public final class LegacySave {
    public static byte[] encode(GameModel model,boolean extended) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(extended ? 0x464C3032 : 0x464C3031);
        out.writeInt(model.levelIndex); out.writeInt(model.difficulty);
        out.writeFloat(model.elapsed); out.writeInt(model.outcome); out.writeInt(model.captures);
        out.writeInt(model.unitsLost); out.writeInt(model.unitsSent);
        out.writeInt(model.territories.size());
        for (GameModel.Territory territory : model.territories) { out.writeInt(territory.owner); out.writeDouble(territory.troops); }
        out.writeInt(model.troops.size());
        for (GameModel.Troop troop : model.troops) {
            out.writeInt(troop.source); out.writeInt(troop.target); out.writeInt(troop.owner);
            out.writeFloat(troop.duration); out.writeFloat(troop.age);
            if (extended) out.writeInt(troop.units);
        }
        java.lang.reflect.Field field = GameModel.class.getDeclaredField("aiTimers"); field.setAccessible(true);
        float[] timers = (float[])field.get(model);
        for (int i = 0; i < 4; i++) out.writeFloat(timers[i]);
        out.flush(); return bytes.toByteArray();
    }
    private LegacySave() {}
}
