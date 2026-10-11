package com.frontline.offline;

/** CPU-only render probe; does not measure Android drawing or device frame rate. */
public final class RenderBenchmark {
    private static final GameScene.Events EVENTS = new GameScene.Events() {
        public void changed() {} public void cue(int kind) {}
    };
    private static final GameScene.Graphics GRAPHICS = new GameScene.Graphics() {
        public void rect(float x,float y,float w,float h,float r,int c) {}
        public void circle(float x,float y,float r,int c) {}
        public void line(float x,float y,float x2,float y2,float w,int c) {}
        public void polygon(float[] p,int f,int s,float w) {}
        public void text(String s,float x,float y,float size,int c,boolean b,int a) {}
    };
    public static void main(String[] args) {
        int frames = args.length == 0 ? 400 : Integer.parseInt(args[0]);
        System.out.println("language,screen,frames,microseconds_per_render");
        for (String language : Localization.LANGUAGES) {
            GameScene.Profile p = new GameScene.Profile(); p.language = language;
            p.tutorialSeen = true; p.unlocked = 59;
            GameScene scene = new GameScene(p,new GameModel(59,1,42),EVENTS);
            for (int overlay : new int[] {GameScene.MENU,GameScene.SETTINGS,GameScene.NONE}) {
                scene.overlay = overlay;
                for (int i = 0; i < 80; i++) scene.render(GRAPHICS,900);
                long start = System.nanoTime();
                for (int i = 0; i < frames; i++) scene.render(GRAPHICS,900);
                double micros = (System.nanoTime()-start)/1000.0/frames;
                System.out.printf(java.util.Locale.ROOT,"%s,%s,%d,%.2f%n",language,
                    overlay == GameScene.MENU ? "home" : overlay == GameScene.SETTINGS ? "settings" : "battle",frames,micros);
            }
        }
    }
}
