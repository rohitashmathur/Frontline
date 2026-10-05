package com.frontline.offline;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DeviceControls {
    private static final Map<String,float[]> points = new LinkedHashMap<>();
    private static float logicalHeight = 700, physicalWidth = 720;
    private static final GameScene.Events SILENT = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
    };

    public static void main(String[] args) {
        if (args.length > 0) logicalHeight = Float.parseFloat(args[0]);
        if (args.length > 1) physicalWidth = Float.parseFloat(args[1]);
        GameScene scene = new GameScene(new GameScene.Profile(),null,SILENT);
        scene.back();
        collect(scene,"fresh", "play","sectors","tutorial","settings","challenges","daily","mastery","help");
        click(scene,"play");
        collect(scene,"brief","begin_attempt","brief_back","help"); click(scene,"begin_attempt");
        collect(scene,"tutorial","tutorial_next","tutorial_skip");
        click(scene,"tutorial_next"); collect(scene,"tutorial","tutorial_prev");
        points.put("practice-from",new float[] {110,225});
        points.put("practice-to",new float[] {310,225});
        scene.down(110,225); scene.up(310,225); click(scene,"tutorial_next");
        collect(scene,"tutorial","demo_quarter","demo_half","demo_all");
        click(scene,"demo_quarter"); click(scene,"demo_half"); click(scene,"demo_all"); scene.down(110,225); scene.up(310,225);
        click(scene,"tutorial_next"); collect(scene,"booster","demo_capture","demo_lose");
        click(scene,"tutorial_skip");
        collect(scene,"battle","quarter","half","all","restart","settings","zoom_in","zoom_out","fit_board");
        click(scene,"restart"); collect(scene,"confirm","confirm_replace","cancel_replace"); click(scene,"cancel_replace"); click(scene,"resume");
        float[] from = scene.position(0), to = scene.position(1);
        float scale = (to[0]-from[0])/1.732f;
        points.put("swipe-from",new float[] {from[0]-.866f*scale-7,from[1]});
        points.put("swipe-to",new float[] {to[0],to[1]-scale-7});
        scene.pause(); collect(scene,"pause","resume","menu","settings","sectors");
        click(scene,"menu"); collect(scene,"menu","play","resume","sectors","tutorial","settings");
        click(scene,"sectors"); collect(scene,"sectors","level_0","back");
        points.put("locked-sector",scene.sectorPosition(1));
        scene.back(); click(scene,"settings");
        collect(scene,"settings","music","sound","haptics","difficulty_2","unlock_code","playtest_log","export_log","clear_log","back");
        scene.back(); click(scene,"challenges"); collect(scene,"challenges","challenge_0","mission_tab_0","mission_tab_1","mission_tab_2","home");
        click(scene,"challenge_0"); collect(scene,"mission-brief","begin_attempt","brief_back"); click(scene,"brief_back"); click(scene,"home");
        click(scene,"daily"); collect(scene,"daily","today_mission","home"); click(scene,"home");
        click(scene,"mastery"); collect(scene,"mastery","theme_0","home"); click(scene,"home");
        scene.overlay = GameScene.CAMERA_HELP; collect(scene,"camera","camera_ready");
        scene.start(0); scene.model.outcome = GameModel.WON; scene.update(.01f);
        collect(scene,"result","menu","next");
        click(scene,"menu"); collect(scene,"cleared-menu","play","sectors","settings");
        click(scene,"sectors"); collect(scene,"cleared-sectors","level_1","back");
        collect(scene,"campaign","chapter_next");
        click(scene,"chapter_next"); collect(scene,"campaign","chapter_prev");
        points.put("campaign-first-row",scene.sectorPosition(6));
        points.put("campaign-final-row",scene.sectorPosition(11));
        System.out.print("{"); boolean comma = false;
        for (Map.Entry<String,float[]> entry : points.entrySet()) {
            if (comma) System.out.print(","); comma = true;
            float[] p = entry.getValue();
            System.out.printf(java.util.Locale.US,"\"%s\":[%d,%d]",entry.getKey(),Math.round(p[0]*physicalWidth/420),Math.round(p[1]*physicalWidth/420));
        }
        System.out.println("}");
    }

    private static void collect(GameScene scene,String prefix,String... ids) {
        scene.render(new GameModelTest.NullGraphics(),logicalHeight);
        for (String id : ids) {
            float[] point = scene.buttonPosition(id);
            if (point == null) throw new AssertionError("Missing native fixture control "+prefix+"-"+id);
            points.put(prefix+"-"+id,point);
        }
    }

    private static void click(GameScene scene,String id) {
        scene.render(new GameModelTest.NullGraphics(),logicalHeight);
        float[] p = scene.buttonPosition(id); scene.down(p[0],p[1]); scene.up(p[0],p[1]);
    }
}
