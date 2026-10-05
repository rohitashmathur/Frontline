package com.frontline.offline;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DeviceControls {
    private static final Map<String,float[]> points = new LinkedHashMap<>();
    private static final GameScene.Events SILENT = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
    };

    public static void main(String[] args) {
        GameScene scene = new GameScene(new GameScene.Profile(),null,SILENT);
        scene.back();
        collect(scene,"fresh", "play","sectors","tutorial","settings");
        click(scene,"play");
        collect(scene,"tutorial","tutorial_next","tutorial_skip");
        click(scene,"tutorial_next"); collect(scene,"tutorial","tutorial_prev");
        points.put("practice-from",new float[] {110,225});
        points.put("practice-to",new float[] {310,225});
        scene.down(110,225); scene.up(310,225); click(scene,"tutorial_next");
        collect(scene,"tutorial","demo_quarter","demo_half","demo_all");
        click(scene,"tutorial_skip");
        collect(scene,"battle","quarter","half","all","restart");
        float[] from = scene.position(0), to = scene.position(1);
        float scale = (to[0]-from[0])/1.732f;
        points.put("swipe-from",new float[] {from[0]-.866f*scale-7,from[1]});
        points.put("swipe-to",new float[] {to[0],to[1]-scale-7});
        scene.pause(); collect(scene,"pause","resume","menu","settings","sectors");
        click(scene,"menu"); collect(scene,"menu","play","resume","sectors","tutorial","settings");
        click(scene,"sectors"); collect(scene,"sectors","level_0","back");
        points.put("locked-sector",scene.sectorPosition(1));
        scene.back(); click(scene,"settings");
        collect(scene,"settings","music","sound","haptics","difficulty_2","back");
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
            System.out.printf(java.util.Locale.US,"\"%s\":[%d,%d]",entry.getKey(),Math.round(p[0]*720/420),Math.round(p[1]*720/420));
        }
        System.out.println("}");
    }

    private static void collect(GameScene scene,String prefix,String... ids) {
        scene.render(new GameModelTest.NullGraphics(),700);
        for (String id : ids) points.put(prefix+"-"+id,scene.buttonPosition(id));
    }

    private static void click(GameScene scene,String id) {
        scene.render(new GameModelTest.NullGraphics(),700);
        float[] p = scene.buttonPosition(id); scene.down(p[0],p[1]); scene.up(p[0],p[1]);
    }
}
