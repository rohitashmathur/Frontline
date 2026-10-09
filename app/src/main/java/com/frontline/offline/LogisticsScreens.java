package com.frontline.offline;

/** Presentation only; mode activation, records and protected battle transitions belong to the parent. */
final class LogisticsScreens {
    interface Controls {
        void button(String id, String label, float x, float y, float width, float height, boolean primary);
    }

    private static final float LEFT = 22, RIGHT = 398, WIDTH = RIGHT - LEFT;

    private LogisticsScreens() {}

    static void drawHome(GameScene.Graphics g, float height, String language, int difficulty,
                         LogisticsRecords records, boolean active, Controls controls) {
        paragraph(g, t(language, "logistics.title"), LEFT, 55, WIDTH, 27, GameScene.WHITE, true);
        g.text(t(language, "logistics.experimental"), RIGHT, 55, 12, GameScene.COLORS[2], true, 2);
        for (int i = 0; i < 3; i++)
            controls.button("difficulty_" + i, Localization.translate(language, GameModel.DIFFICULTIES[i]),
                LEFT + i * 127, 85, 122, 40, difficulty == i);

        for (Logistics preset : Logistics.PRESETS) {
            float top = 140 + preset.id * 105;
            paragraph(g, Localization.translate(language, preset.name), LEFT, top + 17,
                WIDTH, 18, GameScene.WHITE, true);
            paragraph(g, Localization.translate(language, preset.description), LEFT, top + 36,
                306, 11, GameScene.MUTED, false);
            LogisticsRecords.Record record = records == null ? null
                : records.get(preset.id, difficulty, GameModel.RULES_VERSION, GameModel.LOGISTICS_CONFIG_VERSION);
            boolean completed = record != null && record.completed;
            paragraph(g, t(language, completed ? "record.completed" : "record.unplayed"), LEFT, top + 82,
                306, 11, completed ? GameScene.COLORS[0] : GameScene.MUTED, completed);
            if (completed)
                paragraph(g, t(language, "record.best_elapsed", GameScene.time(record.bestElapsed)), LEFT, top + 98,
                    306, 11, GameScene.MUTED, false);
            controls.button("logistics_map_" + preset.id, ">", 346, top + 56, 52, 48, true);
            g.line(LEFT, top + 105, RIGHT, top + 105, 1, GameScene.BORDER);
        }

        if (active)
            controls.button("continue_logistics", t(language, "logistics.continue"), 52, height - 132, 316, 48, true);
        controls.button("home", t(language, "menu.main"), 22, height - 69, 182, 44, false);
        controls.button("help", Localization.translate(language, "Rules"), 216, height - 69, 182, 44, false);
    }

    static void drawResult(GameScene.Graphics g, float height, String language, GameModel model,
                           LogisticsRecords records, Controls controls) {
        boolean won = model.outcome == GameModel.WON;
        float next = paragraph(g, t(language, won ? "logistics.result_won" : "logistics.result_lost"),
            LEFT, 55, WIDTH, 24, GameScene.WHITE, true);
        ObjectiveResult result = ObjectiveResult.evaluate(model);
        next = paragraph(g, t(language, result.code, (Object[]) result.numericArguments()), LEFT, next + 10,
            WIDTH, 12, won ? GameScene.COLORS[0] : GameScene.COLORS[1], true);

        next = paragraph(g, t(language, "result.elapsed", GameScene.time(model.elapsed)), LEFT, next + 16,
            WIDTH, 14, GameScene.WHITE, true);
        next += 12;
        g.text(t(language, "result.army_label"), LEFT, next, 12, GameScene.MUTED, true, 0);
        g.text(Integer.toString(model.unitsSent), RIGHT, next,
            14, GameScene.WHITE, true, 2);
        next += 32;
        next = paragraph(g, t(language, "result.battle_stats", model.captures, model.unitsLost), LEFT, next,
            WIDTH, 12, GameScene.WHITE, false);
        next = paragraph(g, t(language, "result.intercepted", model.intercepted), LEFT, next + 8,
            WIDTH, 12, GameScene.MUTED, false);
        next = paragraph(g, t(language, "result.cap_losses", model.cappedReinforcements), LEFT, next + 8,
            WIDTH, 12, GameScene.MUTED, false);

        int id = Logistics.getIndex(model);
        LogisticsRecords.Record record = records == null || id < 0 || model.logisticsConfigVersion <= 0 ? null
            : records.get(id, model.difficulty, model.rulesVersion, model.logisticsConfigVersion);
        if (record != null && record.completed)
            paragraph(g, t(language, "record.best_elapsed", GameScene.time(record.bestElapsed)), LEFT, next + 8,
                WIDTH, 12, GameScene.COLORS[2], false);

        controls.button("logistics_retry", t(language, "logistics.retry"), 52, height - 184, 316, 48, true);
        controls.button("logistics", t(language, "logistics.choose_map"), 52, height - 126, 316, 44, false);
        controls.button("home", t(language, "menu.main"), 52, height - 69, 316, 44, false);
    }

    private static String t(String language, String key, Object... args) {
        return Localization.text(language, key, args);
    }

    /** Returns the next baseline so wrapped result facts cannot collide. */
    private static float paragraph(GameScene.Graphics g, String text, float x, float baseline,
                                   float width, float size, int color, boolean bold) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && g.measureText(candidate, size, bold) > width) {
                g.text(line.toString(), x, baseline, size, color, bold, 0);
                baseline += size * 1.45f;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) g.text(line.toString(), x, baseline, size, color, bold, 0);
        return baseline + size * 1.45f;
    }
}
