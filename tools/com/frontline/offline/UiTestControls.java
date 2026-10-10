package com.frontline.offline;

/** Finds a control as a user would: expand its section, then scroll it into view. */
final class UiTestControls {
    static float[] find(GameScene scene,String id,float height) {
        float[] point = visible(scene,id,height);
        if (point != null) return point;
        String section = id.startsWith("language_") ? "language_picker"
            : id.equals("playtest_log") || id.equals("export_log") || id.equals("clear_log") ? "tools" : null;
        if (section != null && scene.overlay == GameScene.SETTINGS) {
            point = visible(scene,section,height);
            if (point != null) { scene.down(point[0],point[1]); scene.up(point[0],point[1]); }
        }
        return visible(scene,id,height);
    }
    private static float[] visible(GameScene scene,String id,float height) {
        scene.render(new GameModelTest.NullGraphics(),height);
        float[] point = scene.buttonPosition(id);
        if (point != null) return point;
        scene.scrollScreen(-10000);
        for (int i = 0; i < 50; i++) {
            scene.render(new GameModelTest.NullGraphics(),height); point = scene.buttonPosition(id);
            if (point != null) return point;
            if (!scene.scrollScreen(40)) break;
        }
        return null;
    }
    private UiTestControls() {}
}
