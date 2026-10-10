package com.frontline.offline;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class RenderPreview implements GameScene.Graphics {
    private final Graphics2D g;
    private java.awt.Shape previousClip;
    private RenderPreview(Graphics2D graphics) { g = graphics; }
    public static void main(String[] args) throws Exception {
        File output = new File(args.length == 0 ? "build/previews" : args[0]); output.mkdirs();
        GameScene.Events events = new GameScene.Events() { public void changed() {} public void cue(int kind) {} };
        GameScene.Profile profile = new GameScene.Profile();
        GameScene menu = new GameScene(profile,null,events);
        render(menu,360,532,new File(output,"splash.png"));
        menu.back();
        render(menu,360,532,new File(output,"main-menu-small.png"));
        render(menu,420,900,new File(output,"main-menu-tall.png"));
        press(menu,"tutorial");
        for (int step = 0; step < 5; step++) {
            render(menu,360,532,new File(output,"tutorial-"+(step+1)+".png"));
            render(menu,420,900,new File(output,"tutorial-tall-"+(step+1)+".png"));
            if (step == 1) {
                menu.render(new GameModelTest.NullGraphics(),700);
                menu.down(110,225); menu.up(310,225);
                render(menu,360,532,new File(output,"tutorial-practice-done.png"));
            }
            if (step == 2) {
                press(menu,"demo_half"); render(menu,360,532,new File(output,"tutorial-half.png"));
                press(menu,"demo_all"); render(menu,360,532,new File(output,"tutorial-all.png"));
                press(menu,"demo_quarter");
                menu.render(new GameModelTest.NullGraphics(),700);
                menu.down(110,225); menu.up(310,225);
            }
            if (step == 3) {
                for (int i = 0; i < 4; i++) press(menu,"demo_capture");
                render(menu,360,532,new File(output,"tutorial-four-kings.png"));
                press(menu,"demo_capture"); render(menu,420,900,new File(output,"tutorial-five-kings.png"));
                press(menu,"demo_lose");
            }
            press(menu,"tutorial_next");
        }
        GameScene scene = new GameScene(profile,new GameModel(0,1,42),events);
        scene.overlay = GameScene.NONE;
        scene.fraction = .25;
        render(scene,360,640,new File(output,"battle-small.png"));
        render(scene,420,900,new File(output,"battle-tall.png"));
        render(scene,720,1280,new File(output,"battle-tablet.png"));
        scene.model.launch(0,1,1);
        for (int i = 0; i < 28; i++) scene.update(.025f);
        render(scene,420,840,new File(output,"battle-action.png"));
        scene.overlay = GameScene.PAUSE;
        render(scene,360,640,new File(output,"pause.png"));
        scene.overlay = GameScene.SETTINGS;
        render(scene,360,532,new File(output,"settings-small.png"));
        render(scene,420,900,new File(output,"settings-tall.png"));
        render(scene,360,640,new File(output,"settings.png"));
        profile.unlocked = 2; profile.best[0] = 2180; profile.stars[0] = 3; profile.times[0] = 47;
        scene.overlay = GameScene.SECTORS;
        render(scene,360,640,new File(output,"sectors.png"));
        scene.overlay = GameScene.MENU;
        render(scene,360,532,new File(output,"main-menu-resume.png"));
        scene = new GameScene(profile,new GameModel(5,2,42),events); scene.overlay = GameScene.NONE;
        render(scene,420,840,new File(output,"last-stand.png"));
        scene = new GameScene(profile,new GameModel(0,1,42),events); scene.overlay = GameScene.NONE;
        scene.model.elapsed = 47; scene.model.captures = 4; scene.model.outcome = GameModel.WON;
        scene.update(.01f);
        render(scene,360,640,new File(output,"victory.png"));
        for (int level = 0; level < GameModel.LEVELS.length; level++) {
            scene = new GameScene(profile,new GameModel(level,1,42),events);
            scene.overlay = GameScene.NONE;
            render(scene,360,532,new File(output,String.format("sector-%02d-small.png",level+1)));
            render(scene,420,900,new File(output,String.format("sector-%02d-tall.png",level+1)));
        }
        for (int chapter = 0; chapter < Campaign.CHAPTERS.length; chapter++) {
            GameScene.Profile campaignProfile = new GameScene.Profile();
            campaignProfile.unlocked = chapter*6+1; campaignProfile.selectedSector = chapter*6;
            campaignProfile.best[chapter*6] = 2180; campaignProfile.stars[chapter*6] = 3;
            scene = new GameScene(campaignProfile,null,events); scene.back(); press(scene,"sectors");
            render(scene,360,532,new File(output,"chapter-"+(chapter+1)+"-small.png"));
            render(scene,420,900,new File(output,"chapter-"+(chapter+1)+"-tall.png"));
            scene = new GameScene(campaignProfile,new GameModel(chapter*6+5,1,42),events);
            scene.overlay = GameScene.NONE; scene.model.outcome = GameModel.WON; scene.update(.01f);
            render(scene,360,532,new File(output,"chapter-"+(chapter+1)+"-victory.png"));
        }
        scene = new GameScene(profile,new GameModel(2,1,42),events); scene.overlay = GameScene.NONE;
        int king = scene.model.territories.size()-1; scene.model.territories.get(king).troops = 0;
        scene.model.troops.add(new GameModel.Troop(0,king,0,1,.99f)); scene.update(.02f);
        for (int i = 0; i < 4; i++) scene.update(.1f);
        render(scene,360,532,new File(output,"king-capture-small.png"));
        render(scene,420,900,new File(output,"king-capture-tall.png"));
        for (GameModel.Territory territory : scene.model.territories) if (territory.owner == 0) territory.troops = GameModel.troopCap(territory);
        render(scene,360,532,new File(output,"large-counts-small.png"));
        scene = new GameScene(profile,new GameModel(59,1,42),events); scene.overlay = GameScene.NONE;
        render(scene,360,532,new File(output,"wide-map-fit.png"));
        scene.cameraGesture(210,350,2,0,0); render(scene,360,532,new File(output,"wide-map-zoom.png"));
        scene.cameraGesture(210,350,1,150,-120); render(scene,420,900,new File(output,"wide-map-pan.png"));
        for (int view : new int[] {GameScene.CHALLENGES,GameScene.DAILY,GameScene.MASTERY,GameScene.HELP}) {
            scene.overlay = view;
            render(scene,360,532,new File(output,"v10-view-"+view+"-small.png"));
            render(scene,420,900,new File(output,"v10-view-"+view+"-tall.png"));
            render(scene,1200,800,new File(output,"v10-view-"+view+"-tablet.png"));
        }
        scene.overlay = GameScene.MENU; press(scene,"play");
        render(scene,360,532,new File(output,"v10-briefing-small.png"));
        render(scene,420,900,new File(output,"v10-briefing-tall.png"));
        press(scene,"begin_attempt");
        render(scene,360,532,new File(output,"v10-confirm-small.png"));
        scene.overlay = GameScene.CAMERA_HELP;
        render(scene,360,532,new File(output,"v10-camera-guide.png"));
        for (String language : new String[] {"en","id","hi"}) {
            GameScene.Profile localized = new GameScene.Profile(); localized.language = language;
            localized.tutorialSeen = true; localized.unlocked = 59;
            GameScene translated = new GameScene(localized,null,events); translated.back();
            for (int view : new int[] {GameScene.MENU,GameScene.SETTINGS,GameScene.CHALLENGES,GameScene.DAILY,GameScene.MASTERY,GameScene.HELP}) {
                translated.overlay = view;
                render(translated,360,532,new File(output,"v11-"+language+"-view-"+view+"-small.png"));
                render(translated,420,900,new File(output,"v11-"+language+"-view-"+view+"-tall.png"));
            }
            translated.overlay = GameScene.MENU; press(translated,"tutorial");
            java.lang.reflect.Field step = GameScene.class.getDeclaredField("tutorialStep"); step.setAccessible(true);
            for (int i = 0; i < 5; i++) {
                step.setInt(translated,i);
                render(translated,360,532,new File(output,"v11-"+language+"-tutorial-"+(i+1)+".png"));
            }
            for (int level = 0; level < 60; level++) {
                translated.start(level);
                render(translated,360,532,new File(output,"v11-"+language+"-sector-"+(level+1)+".png"));
            }
            localized.run = RunState.newRun(701);
            for (int node = 0; node < 5; node++) {
                GameScene runScene = new GameScene(localized,null,events); runScene.overlay = GameScene.RUN_HOME;
                render(runScene,360,532,new File(output,"v11-"+language+"-run-ready-"+node+".png"));
                localized.run = localized.run.beginBattle();
                GameModel runModel = localized.run.createBattle();
                runScene = new GameScene(localized,runModel,events); runScene.overlay = GameScene.NONE;
                render(runScene,360,532,new File(output,"v11-"+language+"-run-battle-"+node+".png"));
                runModel.outcome = GameModel.WON; runModel.terminalReason = GameModel.TERMINAL_VICTORY;
                runModel.elapsed = 25+node; localized.run = localized.run.finishBattle(runModel);
                runScene.overlay = node == 4 ? GameScene.RUN_SUMMARY : GameScene.RUN_COUNCIL;
                render(runScene,360,532,new File(output,"v11-"+language+"-run-result-"+node+".png"));
                render(runScene,420,900,new File(output,"v11-"+language+"-run-result-tall-"+node+".png"));
                if (node < 4) localized.run = localized.run.choosePerk(localized.run.councilNonce,localized.run.councilOffer()[0]);
            }
            localized.run = null;
            for (int map = 0; map < Logistics.PRESETS.length; map++) {
                GameModel routed = Logistics.create(map,1,803);
                GameScene logisticsScene = new GameScene(localized,routed,events);
                logisticsScene.overlay = GameScene.LOGISTICS;
                render(logisticsScene,360,532,new File(output,"v11-"+language+"-logistics-home-"+map+".png"));
                logisticsScene.overlay = GameScene.NONE;
                render(logisticsScene,360,532,new File(output,"v11-"+language+"-logistics-map-"+map+".png"));
                int source = routed.originalKing(0);
                int target = LogisticsRoutes.legalTargets(routed,source,0)[0];
                float[] a = logisticsScene.position(source), b = logisticsScene.position(target);
                logisticsScene.down(a[0],a[1]); logisticsScene.move(b[0],b[1]);
                render(logisticsScene,360,532,new File(output,"v11-"+language+"-logistics-route-"+map+".png"));
                logisticsScene.cancel(); routed.outcome = GameModel.WON;
                routed.terminalReason = GameModel.TERMINAL_VICTORY; routed.elapsed = 64;
                logisticsScene.update(0);
                render(logisticsScene,360,532,new File(output,"v11-"+language+"-logistics-win-"+map+".png"));
                routed.outcome = GameModel.LOST; routed.terminalReason = GameModel.TERMINAL_ELIMINATED;
                render(logisticsScene,420,900,new File(output,"v11-"+language+"-logistics-defeat-"+map+".png"));
            }
        }
        System.out.println("Rendered all 60 maps, ten chapter pages/endings, five tutorial steps, and zoom/pan states at compact and tall sizes.");
    }

    private static void press(GameScene scene,String id) {
        scene.render(new GameModelTest.NullGraphics(),700);
        float[] point = UiTestControls.find(scene,id,700);
        if (point == null) throw new IllegalStateException("Missing preview control: " + id);
        scene.down(point[0],point[1]); scene.up(point[0],point[1]);
    }

    private static void render(GameScene scene,int width,int height,File file) throws Exception {
        BufferedImage image = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(GameScene.BACKGROUND,true)); graphics.fillRect(0,0,width,height);
        float scale = Math.min(width/420f,height/620f);
        graphics.translate((width-420*scale)/2,0); graphics.scale(scale,scale);
        scene.render(new RenderPreview(graphics),height/scale);
        graphics.dispose(); ImageIO.write(image,"png",file);
    }

    private void color(int color) { g.setColor(new Color(color,true)); }
    public float measureText(String text,float size,boolean bold) {
        g.setFont(new Font("SansSerif",bold ? Font.BOLD : Font.PLAIN,1).deriveFont(size));
        return g.getFontMetrics().stringWidth(text);
    }
    public void clip(float x,float y,float width,float height) { previousClip = g.getClip(); g.clip(new java.awt.geom.Rectangle2D.Float(x,y,width,height)); }
    public void unclip() { g.setClip(previousClip); }
    public void rect(float x,float y,float w,float h,float radius,int color) {
        color(color); g.fill(new RoundRectangle2D.Float(x,y,w,h,radius*2,radius*2));
    }
    public void circle(float x,float y,float radius,int color) {
        color(color); g.fill(new Ellipse2D.Float(x-radius,y-radius,radius*2,radius*2));
    }
    public void line(float x1,float y1,float x2,float y2,float width,int color) {
        color(color); g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x1,y1,x2,y2));
    }
    public void polygon(float[] points,int fill,int stroke,float width) {
        Path2D.Float path = new Path2D.Float(); path.moveTo(points[0],points[1]);
        for (int i = 2; i < points.length; i+=2) path.lineTo(points[i],points[i+1]); path.closePath();
        if (fill != 0) { color(fill); g.fill(path); }
        if (stroke != 0 && width > 0) { color(stroke); g.setStroke(new BasicStroke(width)); g.draw(path); }
    }
    public void text(String text,float x,float baseline,float size,int color,boolean bold,int align) {
        color(color); g.setFont(new Font("SansSerif",bold ? Font.BOLD : Font.PLAIN,1).deriveFont(size));
        float width = g.getFontMetrics().stringWidth(text);
        if (align == 1) x -= width/2; else if (align == 2) x -= width;
        if (x < 0 || x+width > 420) throw new AssertionError("Text exceeds viewport: "+text);
        g.drawString(text,x,baseline);
    }
}
