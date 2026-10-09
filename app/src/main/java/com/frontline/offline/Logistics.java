package com.frontline.offline;

/** Purpose-built maps for the separate experimental routed-rules prototype. */
public final class Logistics {
    public static final int MODE_LOGISTICS = GameModel.MODE_LOGISTICS;
    public static final Logistics[] PRESETS = {
        new Logistics(0, "Twin Causeways", "Connect your footholds around the central gap.",
            new GameModel.Level("Logistics: Twin Causeways", 4, 3, new int[] {5, 6}, 120,
                new int[] {0, 11}, new int[] {1}, 32, 32, 7)),
        new Logistics(1, "Broken Junction", "Secure the upper and lower crossings between three fronts.",
            new GameModel.Level("Logistics: Broken Junction", 5, 4, new int[] {7, 12}, 180,
                new int[] {0, 4, 17}, new int[] {1, 2}, 34, 34, 8)),
        new Logistics(2, "Crown Circuit", "Four kings contest the circuit around a blocked centre.",
            new GameModel.Level("Logistics: Crown Circuit", 6, 4, new int[] {8, 9, 14, 15}, 220,
                new int[] {0, 23, 5, 18}, new int[] {1, 2, 3}, 36, 36, 9))
    };

    public final int id;
    public final String name, description;
    public final GameModel.Level level;

    private Logistics(int id, String name, String description, GameModel.Level level) {
        this.id = id; this.name = name; this.description = description; this.level = level;
    }

    public static GameModel create(int id, int difficulty, long seed) {
        if (id < 0 || id >= PRESETS.length) throw new IllegalArgumentException("Logistics map");
        GameModel model = new GameModel(0, difficulty, seed, PRESETS[id].level);
        model.configureLogistics(id);
        return model;
    }

    public static int getIndex(GameModel model) {
        if (model == null || model.battleMode != MODE_LOGISTICS) return -1;
        if (model.logisticsId >= 0) return model.logisticsId;
        String frozenName = model.level().name;
        for (Logistics preset : PRESETS)
            if (preset.level.name.equals(frozenName)) return preset.id;
        return -1;
    }
}
