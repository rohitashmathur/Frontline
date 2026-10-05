package com.frontline.offline;

import java.util.ArrayList;

public final class GameScene {
    public interface Graphics {
        void rect(float x, float y, float width, float height, float radius, int color);
        void circle(float x, float y, float radius, int color);
        void line(float x1, float y1, float x2, float y2, float width, int color);
        void polygon(float[] points, int fill, int stroke, float strokeWidth);
        void text(String text, float x, float baseline, float size, int color, boolean bold, int align);
        default void clip(float x, float y, float width, float height) {}
        default void unclip() {}
    }
    public interface Events { void changed(); void cue(int kind); }
    public static final class Profile {
        public final int[] best = new int[GameModel.LEVELS.length];
        public final int[] stars = new int[GameModel.LEVELS.length];
        public final float[] times = new float[GameModel.LEVELS.length];
        public int unlocked, selectedSector, difficulty = 1, wins;
        public boolean sound = true, music = true, haptics = true, tutorialSeen;
        public int totalScore() { int sum = 0; for (int score : best) sum += score; return sum; }
        public boolean cleared(int sector) { return stars[sector] > 0 || best[sector] > 0; }
        public void reconcileProgress() {
            unlocked = Math.max(0,Math.min(best.length-1,unlocked));
            for (int i = 0; i < best.length-1; i++) if (cleared(i)) unlocked = Math.max(unlocked,i+1);
            selectedSector = Math.max(0,Math.min(unlocked,selectedSector));
        }
    }
    public static final int NONE = 0, PAUSE = 1, SECTORS = 2, SETTINGS = 3, RESULT = 4,
        SPLASH = 5, MENU = 6, TUTORIAL = 7;
    public static final int BACKGROUND = 0xFF17191B, PANEL = 0xFF232629;
    public static final int WHITE = 0xFFF0F7F7, MUTED = 0xFFA2ABA9, BORDER = 0xFF41494A;
    public static final int[] COLORS = {0xFF5DE0BA, 0xFFFF816C, 0xFFF6D477, 0xFFAC9EEF, 0xFF65B9F2, 0xFFED99BF};
    public GameModel model;
    public final Profile profile;
    public int overlay = SPLASH;
    public boolean hasBattle;
    public double fraction = 1;
    private final Events events;
    private final ArrayList<Button> buttons = new ArrayList<>();
    private int previousOverlay = NONE, selected = -1, sectorPage;
    private boolean resultRecorded, tutorialStartsBattle, practiceDragging, practiceDone;
    private int tutorialStep;
    private float splashElapsed, kingBanner, resultDelay;
    private double tutorialFraction = .25;
    private int tutorialKings;
    private float height = 780, boardScale, boardX, boardY, pointerX, pointerY;
    private float zoom = 1, panX, panY, fitScale, mapWidth, mapHeight;
    private boolean panning;
    private Button pressed;

    private static final class Button {
        final String id, label;
        final float x, y, width, height;
        Button(String id, String label, float x, float y, float width, float height) {
            this.id = id; this.label = label; this.x = x; this.y = y; this.width = width; this.height = height;
        }
        boolean contains(float px, float py) { return px >= x && px <= x + width && py >= y && py <= y + height; }
    }

    public GameScene(Profile profile, GameModel restored, Events events) {
        this.profile = profile; this.events = events;
        model = restored == null ? new GameModel(0, profile.difficulty, System.nanoTime()) : restored;
        hasBattle = restored != null;
        resultRecorded = restored != null && restored.outcome != GameModel.PLAYING;
        profile.reconcileProgress();
    }

    public void update(float dt) {
        if (overlay == SPLASH) {
            splashElapsed += Math.max(0, Math.min(.1f, dt));
            if (splashElapsed >= .85f) overlay = MENU;
            return;
        }
        if (overlay != NONE) return;
        kingBanner = Math.max(0,kingBanner-Math.max(0,dt));
        if (resultDelay > 0) {
            resultDelay = Math.max(0,resultDelay-Math.max(0,dt));
            if (resultDelay == 0) overlay = RESULT;
            return;
        }
        int oldCaptures = model.captures;
        int kings = model.capturedKings(GameModel.PLAYER);
        model.update(dt);
        if (model.capturedKings(GameModel.PLAYER) != kings) kingBanner = 2.4f;
        if (model.captures != oldCaptures) events.cue(1);
        if (model.outcome != GameModel.PLAYING && !resultRecorded) {
            resultRecorded = true;
            if (model.outcome == GameModel.WON) {
                int index = model.levelIndex;
                profile.best[index] = Math.max(profile.best[index], model.score());
                profile.stars[index] = Math.max(profile.stars[index], model.stars());
                if (profile.times[index] == 0 || model.elapsed < profile.times[index]) profile.times[index] = model.elapsed;
                profile.unlocked = Math.max(profile.unlocked, Math.min(GameModel.LEVELS.length - 1, index + 1));
                profile.wins++;
            }
            if (model.outcome == GameModel.WON && kingBanner > 0) resultDelay = 2.4f;
            else overlay = RESULT;
            events.cue(model.outcome == GameModel.WON ? 2 : 3);
            events.changed();
        }
    }

    public void render(Graphics g, float height) {
        this.height = height; buttons.clear();
        g.rect(0, 0, 420, height, 0, BACKGROUND);
        if (overlay == SPLASH) { drawSplash(g); return; }
        if (overlay == TUTORIAL) { drawTutorial(g); return; }
        if (overlay == SECTORS) { drawSectors(g); return; }
        if (overlay == MENU || overlay == SETTINGS && previousOverlay == MENU) {
            drawMenu(g);
            if (overlay == MENU) return;
            buttons.clear();
            g.rect(0, 0, 420, height, 0, 0xE617191B);
            drawSettings(g);
            return;
        }
        g.text("FRONTLINE", 22, 47, 27, WHITE, true, 0);
        addIcon(g, "pause", "Pause", 274, 19, overlay == NONE);
        addIcon(g, "restart", "Restart Round", 322, 19, false);
        addIcon(g, "settings", "Settings", 370, 19, false);
        g.text(String.format(java.util.Locale.US,"%02d / ",model.levelIndex+1)+model.level().name,22,82,17,WHITE,true,0);
        g.text(time(model.elapsed),397,82,17,WHITE,false,2);
        float x = 22;
        for (int owner = -1; owner <= model.level().opponents; owner++) {
            float width = 376f * model.owned(owner) / model.territories.size();
            if (width > 0) g.rect(x,100,width,18,0,owner == -1 ? BORDER : armyColor(owner));
            x += width;
        }
        for (int owner = 0; owner <= model.level().opponents; owner++) {
            float lx = 22+(owner%3)*125, ly = 135+(owner/3)*20;
            g.circle(lx+4,ly-4,3.5f,armyColor(owner));
            String percentage = Math.round(100f*model.owned(owner)/model.territories.size())+"%";
            String label = Campaign.SHORT_NAMES[model.level().faction(owner)]+" "+(model.resigned[owner] ? "OUT" : percentage);
            g.text(label,lx+13,ly,11,model.owned(owner) > 0 ? WHITE : MUTED,owner == 0,0);
        }
        drawKingBanner(g);
        layoutBoard();
        drawBoard(g);
        drawFooter(g);
        if (overlay != NONE) {
            buttons.clear();
            g.rect(0, 0, 420, height, 0, 0xD9081013);
            if (overlay == PAUSE) drawPause(g);
            if (overlay == SETTINGS) drawSettings(g);
            if (overlay == RESULT) drawResult(g);
        }
    }

    private void layoutBoard() {
        float minX = Float.MAX_VALUE, maxX = 0, maxY = 0;
        for (GameModel.Territory territory : model.territories) {
            minX = Math.min(minX, territory.x - .866f);
            maxX = Math.max(maxX, territory.x + .866f);
            maxY = Math.max(maxY, territory.y + 1);
        }
        float top = boardTop(), bottom = boardBottom();
        mapWidth = maxX-minX; mapHeight = maxY;
        fitScale = Math.min(66,Math.min(374/mapWidth,(bottom-top)/mapHeight));
        boardScale = fitScale*zoom;
        clampCamera();
        boardX = (420-mapWidth*boardScale)/2-minX*boardScale+panX;
        boardY = top+(bottom-top-mapHeight*boardScale)/2+panY;
    }

    private float boardTop() { return 204; }
    private float boardBottom() { return height-149; }

    private void clampCamera() {
        float maxX = Math.max(0,(mapWidth*boardScale-374)/2);
        float maxY = Math.max(0,(mapHeight*boardScale-(boardBottom()-boardTop()))/2);
        panX = Math.max(-maxX,Math.min(maxX,panX));
        panY = Math.max(-maxY,Math.min(maxY,panY));
    }

    public void cameraGesture(float x,float y,float factor,float dx,float dy) {
        if (overlay != NONE || !Float.isFinite(factor) || factor <= 0 || !Float.isFinite(x) || !Float.isFinite(y)
            || !Float.isFinite(dx) || !Float.isFinite(dy)) return;
        cancel();
        float next = Math.max(1,Math.min(3,zoom*factor)), ratio = next/zoom;
        float centerY = (boardTop()+boardBottom())/2;
        panX = x-210-(x-210-panX)*ratio+dx;
        panY = y-centerY-(y-centerY-panY)*ratio+dy;
        zoom = next; layoutBoard();
    }

    private float cx(GameModel.Territory territory) { return boardX + territory.x * boardScale; }
    private float cy(GameModel.Territory territory) { return boardY + territory.y * boardScale; }
    private int armyColor(int owner) { return COLORS[model.level().faction(owner)]; }

    private static String multiplier(double value) { return String.format(java.util.Locale.US,"%.2f",value); }

    private void drawKingBanner(Graphics g) {
        float y = 165;
        float pulse = kingBanner > 0 ? 1+(float)Math.sin((2.4f-kingBanner)*9)*.11f : 1;
        int color = model.capturedKings(0) > 0 ? COLORS[2] : MUTED;
        g.rect(22,y,376,31,4,kingBanner > 0 ? mix(PANEL,color,.12f+kingBanner*.05f) : PANEL);
        float x = 39, cy = y+16, r = 8*pulse;
        g.polygon(new float[] {x-r,cy+5,x-r,cy-6,x-r*.45f,cy-1,x,cy-8,x+r*.45f,cy-1,x+r,cy-6,x+r,cy+5},color,0,0);
        g.text("BOOST x"+multiplier(model.teamMultiplier(0)),57,y+21,14,color,true,0);
        g.text(model.capturedKings(0)+" KINGS",235,y+21,11,WHITE,true,1);
        g.text(GameModel.DIFFICULTIES[model.difficulty],385,y+21,11,MUTED,false,2);
    }

    private void drawBoard(Graphics g) {
        g.clip(8,boardTop(),404,boardBottom()-boardTop());
        int aimed = selected < 0 ? -1 : territoryAt(pointerX, pointerY);
        for (GameModel.Territory territory : model.territories) {
            float x = cx(territory), y = cy(territory), radius = boardScale * .94f;
            if (x+radius < 8 || x-radius > 412 || y+radius < boardTop() || y-radius > boardBottom()) continue;
            int color = territory.owner == -1 ? BORDER : armyColor(territory.owner);
            g.polygon(hex(x, y, radius), mix(BACKGROUND, color, territory.owner == -1 ? .42f : .29f),
                selected == territory.id || aimed == territory.id ? WHITE : mix(BACKGROUND, color, .75f),
                selected == territory.id || aimed == territory.id ? 2.7f : 1.2f);
            float node = Math.min(21, boardScale * .45f);
            g.circle(x, y, node + 2, color);
            g.circle(x, y, node, territory.owner == -1 ? 0xFF2C3233 : mix(BACKGROUND, color, .12f));
            String count = Integer.toString(territory.count());
            float size = Math.min(20,boardScale*.45f)*Math.min(1,2.5f/count.length());
            if (x >= 20 && x <= 400) g.text(count,x,y+size*.3f,size,WHITE,true,1);
            if (territory.capital) {
                float baseline = y - node - 8;
                g.polygon(new float[] {x-7,baseline,x-7,baseline-8,x-3,baseline-4,x,baseline-10,x+3,baseline-4,x+7,baseline-8,x+7,baseline}, color, 0, 0);
            } else if (territory.owner != -1 && boardScale > 34) {
                float progress = (float) (territory.troops - territory.count());
                g.line(x-9, y+node+9, x+9, y+node+9, 2, mix(BACKGROUND,color,.35f));
                g.line(x-9, y+node+9, x-9+18*progress, y+node+9, 2, color);
            }
        }
        int index = 0;
        for (GameModel.Troop troop : model.troops) {
            index++;
            if (troop.age < 0) continue;
            GameModel.Territory source = model.territories.get(troop.source), target = model.territories.get(troop.target);
            float ax = cx(source), ay = cy(source), bx = cx(target), by = cy(target), p = troop.progress();
            float length = (float) Math.hypot(bx-ax, by-ay);
            float offset = (index % 5 - 2) * 2.4f * (float) Math.sin(p * Math.PI);
            float dx = (bx-ax) / length, dy = (by-ay) / length;
            float x = ax + (bx-ax)*p - dy*offset, y = ay + (by-ay)*p + dx*offset;
            g.line(x-dx*5, y-dy*5, x, y, 1.4f, mix(BACKGROUND,armyColor(troop.owner),.6f));
            g.circle(x, y, troop.units > 1 ? 3.6f : 2.7f, armyColor(troop.owner));
        }
        for (GameModel.Clash clash : model.clashes) {
            float x = boardX+clash.x*boardScale, y = boardY+clash.y*boardScale;
            float radius = 3+(1-clash.remaining/.35f)*8;
            int color = ((int)(clash.remaining/.35f*255)<<24)|(WHITE&0xFFFFFF);
            for (int ray = 0; ray < 4; ray++) {
                float angle = (float)(Math.PI/4+ray*Math.PI/2), dx = (float)Math.cos(angle), dy = (float)Math.sin(angle);
                g.line(x+dx*radius*.35f,y+dy*radius*.35f,x+dx*radius,y+dy*radius,1.8f,color);
            }
        }
        if (selected >= 0) {
            GameModel.Territory source = model.territories.get(selected);
            g.line(cx(source), cy(source), pointerX, pointerY, 2, WHITE);
            float angle = (float) Math.atan2(pointerY-cy(source), pointerX-cx(source));
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            g.polygon(new float[] {pointerX,pointerY,pointerX-dx*12-dy*6,pointerY-dy*12+dx*6,
                pointerX-dx*12+dy*6,pointerY-dy*12-dx*6}, WHITE, 0, 0);
            g.text(Integer.toString((int) (source.count() * fraction)),Math.max(24,Math.min(396,(cx(source)+pointerX)/2)),
                (cy(source)+pointerY)/2-10, 15, WHITE, true, 1);
        }
        g.unclip();
    }

    private void drawFooter(Graphics g) {
        float top = height-139;
        g.rect(0, top, 420, 139, 0, PANEL);
        g.line(0, top, 420, top, 1, BORDER);
        g.text("ARMY", 22, top+28, 10, MUTED, true, 0);
        g.text(Integer.toString(model.army(0)), 22, top+61, 27, WHITE, true, 0);
        g.text("DEPLOY", 195, top+28, 10, MUTED, true, 0);
        button(g, "quarter", "25%", 193, top+39, 64, 48, fraction == .25, false);
        button(g, "half", "50%", 263, top+39, 64, 48, fraction == .5, false);
        button(g, "all", "100%", 333, top+39, 64, 48, fraction == 1, false);
        g.text("BEST  " + profile.best[model.levelIndex], 22, height-22, 11, MUTED, false, 0);
        cameraButton(g,"zoom_out","Zoom Out",254,height-48);
        cameraButton(g,"fit_board","Fit Battlefield",304,height-48);
        cameraButton(g,"zoom_in","Zoom In",354,height-48);
    }

    private void cameraButton(Graphics g,String id,String label,float x,float y) {
        buttons.add(new Button(id,label,x,y,44,40));
        g.rect(x,y,44,40,4,BACKGROUND);
        float cx = x+22, cy = y+20;
        if (id.equals("fit_board")) {
            for (int sx : new int[] {-1,1}) for (int sy : new int[] {-1,1}) {
                g.line(cx+sx*9,cy+sy*9,cx+sx*3,cy+sy*9,1.8f,WHITE);
                g.line(cx+sx*9,cy+sy*9,cx+sx*9,cy+sy*3,1.8f,WHITE);
            }
        } else {
            g.line(cx-8,cy,cx+8,cy,2,WHITE);
            if (id.equals("zoom_in")) g.line(cx,cy-8,cx,cy+8,2,WHITE);
        }
    }

    private void drawSplash(Graphics g) {
        emblem(g, 210, height/2-50, 130);
        g.text("FRONTLINE", 210, height/2+70, 30, WHITE, true, 1);
        g.text("OFFLINE TACTICS", 210, height/2+98, 11, MUTED, true, 1);
        g.line(150, height/2+126, 270, height/2+126, 3, BORDER);
        g.line(150, height/2+126, 150+120*Math.min(1,splashElapsed/.85f), height/2+126, 3, COLORS[0]);
    }

    private void drawMenu(Graphics g) {
        float top = (height-560)/2;
        emblem(g, 210, top+55, 92);
        g.text("FRONTLINE", 210, top+132, 30, WHITE, true, 1);
        g.text(String.format(java.util.Locale.US,"CHAPTER %02d  /  ",Campaign.chapterIndex(profile.selectedSector)+1)
            + Campaign.chapter(profile.selectedSector).name,210,top+156,11,COLORS[Campaign.chapter(profile.selectedSector).rulerFaction],true,1);
        g.text(String.format(java.util.Locale.US, "SECTOR %02d  /  ", profile.selectedSector+1)
            + GameModel.LEVELS[profile.selectedSector].name, 210, top+180, 13, MUTED, false, 1);
        button(g, "play", "Play", 52, top+203, 316, 48, true, true);
        float row = top+260;
        if (hasBattle && model.outcome == GameModel.PLAYING) {
            button(g, "resume", "Resume Sector " + String.format(java.util.Locale.US,"%02d",model.levelIndex+1), 52, row, 316, 48, false, true);
            row += 57;
        }
        button(g, "sectors", "Select Sector", 52, row, 316, 48, false, true);
        button(g, "tutorial", "How to Play", 52, row+57, 316, 48, false, true);
        button(g, "settings", "Settings", 52, row+114, 316, 48, false, true);
        int cleared = 0;
        for (int i = 0; i < profile.best.length; i++) if (profile.cleared(i)) cleared++;
        g.text(cleared + " / "+GameModel.LEVELS.length+" CLEARED", 52, height-40, 11, MUTED, true, 0);
        g.text("BEST TOTAL  " + profile.totalScore(), 368, height-40, 11, COLORS[0], true, 2);
    }

    private void drawTutorial(Graphics g) {
        String[] headings = {"Claim Territory", "Send Troops", "Choose Your Army", "King Boost", "Win and Advance"};
        g.text("HOW TO PLAY", 30, 43, 11, COLORS[0], true, 0);
        g.text((tutorialStep+1)+" / 5",390,43,12,MUTED,true,2);
        g.text(headings[tutorialStep], 30, 95, 27, WHITE, true, 0);
        float y = tutorialY();
        if (tutorialStep == 0) {
            demoTerritory(g, 92, y, 24, COLORS[0], true);
            demoTerritory(g, 210, y, 8, BORDER, false);
            demoTerritory(g, 328, y, 28, COLORS[1], true);
            g.text("YOU", 92, y+80, 11, COLORS[0], true, 1);
            g.text("NEUTRAL", 210, y+80, 11, MUTED, true, 1);
            g.text("RIVAL", 328, y+80, 11, COLORS[1], true, 1);
            tutorialCopy(g,"Green territories are yours. They produce troops.","King territories grow faster than ordinary ones.","Troop limits: ordinary tiles 100, king tiles 125.");
        } else if (tutorialStep == 1) {
            demoTerritory(g, 110, y, practiceDone ? 20 : 40, COLORS[0], false);
            demoTerritory(g, 310, y, practiceDone ? 12 : 8, practiceDone ? COLORS[0] : BORDER, false);
            if (!practiceDone) {
                g.line(174,y,246,y,2,WHITE);
                g.polygon(new float[] {253,y,241,y-6,241,y+6},WHITE,0,0);
                if (practiceDragging) g.line(110,y,pointerX,pointerY,3,COLORS[0]);
            }
            g.text(practiceDone ? "CAPTURED" : "TRY A SWIPE", 210, y+85, 12, practiceDone ? COLORS[0] : COLORS[2], true, 1);
            tutorialCopy(g,"Drag from your tile to attack or reinforce another.","Spend one troop per defender. Survivors capture it.","Crossing enemy troops cancel one-for-one.");
        } else if (tutorialStep == 2) {
            int sent = (int)(32*tutorialFraction);
            demoTerritory(g,110,y,32-sent,COLORS[0],false);
            demoTerritory(g,310,y,sent,COLORS[0],false);
            g.line(174,y,246,y,2,WHITE);
            g.polygon(new float[] {253,y,241,y-6,241,y+6},WHITE,0,0);
            g.text("32 - "+sent+" = "+(32-sent),210,y+64,16,WHITE,true,1);
            String[] labels = {"25%", "50%", "100%"};
            String[] ids = {"demo_quarter","demo_half","demo_all"};
            double[] fractions = {.25,.5,1};
            for (int i = 0; i < 3; i++) {
                button(g,ids[i],labels[i],87+i*85,y+82,76,40,tutorialFraction == fractions[i],false);
            }
            tutorialCopy(g,"Choose 25%, 50%, or 100% before you swipe.",
                (int)(tutorialFraction*100)+"% of 32 sends "+sent+" and leaves "+(32-sent)+" to defend.","Amounts round down. At least 1 troop must be sent.");
        } else if (tutorialStep == 3) {
            demoTerritory(g,92,y,125,COLORS[0],true);
            demoTerritory(g,210,y,100,COLORS[0],false);
            demoTerritory(g,328,y,75,tutorialKings > 0 ? COLORS[0] : COLORS[1],true);
            double boost = tutorialKings < 4 ? Math.pow(1.2,tutorialKings) : 3*Math.pow(1.2,tutorialKings-4);
            g.text("BOOST x"+multiplier(boost),210,y+77,18,COLORS[2],true,1);
            button(g,"demo_capture","Capture King",32,y+93,173,42,true,true);
            button(g,"demo_lose","Lose King",215,y+93,173,42,false,true);
            tutorialCopy(g,"Enemy kings held boost growth across your team.","1: x1.2 / 2: x1.44 / 3: x1.73 / 4: x3 / 5: x3.6",
                "Losing a king reduces the boost. Your own is excluded.");
        } else {
            for (int i = 0; i < 3; i++) star(g, 172+i*38, y-20, 14, COLORS[2]);
            demoTerritory(g, 130, y+55, 42, COLORS[0], true);
            demoTerritory(g, 290, y+55, 18, COLORS[0], false);
            tutorialCopy(g,"Eliminate rivals or hold over 90% for 10 seconds.","Rivals concede only when unable to recapture a tile.","Winning clears this sector and unlocks the next.");
        }
        for (int i = 0; i < 5; i++) g.circle(178+i*16,height-160,3.5f,i == tutorialStep ? COLORS[0] : BORDER);
        boolean ready = tutorialStep != 1 || practiceDone;
        pageArrow(g,"tutorial_prev","Previous Step",52,height-132,false,tutorialStep > 0);
        if (ready) button(g,"tutorial_next",tutorialStep == 4 ? tutorialStartsBattle ? "Start Battle" : "Done" : "Next",118,height-132,250,48,true,true);
        else {
            g.rect(118,height-132,250,48,6,PANEL);
            g.text("Swipe above to continue",243,height-103,13,MUTED,true,1);
        }
        button(g,"tutorial_skip","Skip Tutorial",52,height-75,316,48,false,true);
    }

    private float tutorialY() { return Math.min(225, (height-400)/2+85); }

    private void tutorialCopy(Graphics g, String first, String second, String third) {
        float baseline = height-276;
        g.text(first,210,baseline,13,WHITE,false,1);
        g.text(second,210,baseline+25,13,MUTED,false,1);
        g.text(third,210,baseline+50,12,MUTED,false,1);
    }

    private static void demoTerritory(Graphics g, float x, float y, int count, int color, boolean crown) {
        g.polygon(hex(x,y,53),mix(BACKGROUND,color,.29f),color,1.5f);
        g.circle(x,y,23,color); g.circle(x,y,21,BACKGROUND);
        g.text(Integer.toString(count),x,y+7,22,WHITE,true,1);
        if (crown) g.polygon(new float[] {x-9,y-32,x-9,y-44,x-4,y-38,x,y-46,x+4,y-38,x+9,y-44,x+9,y-32},color,0,0);
    }

    private static void emblem(Graphics g, float x, float y, float size) {
        float s = size/108, left = x-size/2, top = y-size/2;
        float[][] shapes = {
            {54,15,84,32,84,67,54,84,24,67,24,32},
            {42,34,68,34,68,42,50,42,50,49,65,49,65,57,50,57,50,72,42,72},
            {78,71,91,78,91,92,78,99,66,92,66,78}
        };
        int[] colors = {COLORS[0],BACKGROUND,COLORS[1]};
        for (int i = 0; i < shapes.length; i++) {
            float[] points = shapes[i];
            for (int j = 0; j < points.length; j+=2) { points[j] = left+points[j]*s; points[j+1] = top+points[j+1]*s; }
            g.polygon(points,colors[i],0,0);
        }
    }

    private float modal(Graphics g, float h) {
        float y = (height-h)/2;
        g.rect(29, y-1, 362, h+2, 8, BORDER);
        g.rect(30, y, 360, h, 8, PANEL);
        return y;
    }

    private void drawPause(Graphics g) {
        float y = modal(g, 430);
        g.text("Paused", 210, y+49, 28, WHITE, true, 1);
        g.text(model.level().name, 210, y+73, 13, MUTED, false, 1);
        button(g, "resume", "Resume", 52, y+99, 316, 48, true, true);
        button(g, "restart", "Restart Sector", 52, y+156, 316, 48, false, true);
        button(g, "sectors", "Sectors", 52, y+213, 316, 48, false, true);
        button(g, "settings", "Settings", 52, y+270, 316, 48, false, true);
        button(g, "menu", "Main Menu", 52, y+327, 316, 48, false, true);
    }

    private void drawSectors(Graphics g) {
        Campaign.Chapter chapter = Campaign.CHAPTERS[sectorPage];
        int accent = COLORS[chapter.rulerFaction];
        g.text("CAMPAIGN",22,43,11,accent,true,0);
        g.text("BEST TOTAL  "+profile.totalScore(),398,43,11,MUTED,true,2);
        g.text(String.format(java.util.Locale.US,"CHAPTER %02d / %02d",sectorPage+1,Campaign.CHAPTERS.length),22,77,11,MUTED,true,0);
        g.text(chapter.name,22,109,27,WHITE,true,0);
        if (chapter.rulerFaction == 0) {
            g.text(sectorPage < 5 ? "VOSS / SOL / VEIL" : "FIVE RIVAL RULERS",22,139,11,accent,true,0);
            int count = sectorPage < 5 ? 3 : 5;
            for (int faction = 1; faction <= count; faction++) factionEmblem(g,398-(count-faction)*27-13,103,24,faction);
        } else {
            g.text(Campaign.RULERS[chapter.rulerFaction]+" / "+Campaign.FACTIONS[chapter.rulerFaction],22,139,11,accent,true,0);
            factionEmblem(g,367,100,50,chapter.rulerFaction);
        }
        g.text(chapter.firstLine,22,166,12,WHITE,false,0);
        g.text(chapter.secondLine,22,186,12,MUTED,false,0);
        int first = sectorPage*Campaign.SECTORS_PER_CHAPTER;
        int end = Math.min(first+Campaign.SECTORS_PER_CHAPTER,GameModel.LEVELS.length);
        float rowHeight = Math.min(61,(height-352)/Campaign.SECTORS_PER_CHAPTER);
        for (int i = first; i < end; i++) {
            float row = 208+(i-first)*rowHeight;
            boolean locked = i > profile.unlocked;
            boolean cleared = profile.cleared(i);
            if (i == profile.selectedSector && !locked) g.rect(20,row,380,rowHeight-2,6,0xFF303E39);
            if (!locked) buttons.add(new Button("level_"+i,GameModel.LEVELS[i].name,20,row,380,rowHeight-2));
            g.text(String.format(java.util.Locale.US,"%02d",i+1),24,row+23,16,locked ? MUTED : COLORS[0],true,0);
            g.text(GameModel.LEVELS[i].name,64,row+19,15,locked ? MUTED : WHITE,true,0);
            g.text(locked ? "Locked - clear Sector " + String.format(java.util.Locale.US, "%02d", i) :
                cleared ? "Cleared  /  Best " + profile.best[i] : i == profile.selectedSector ? "Selected  /  Ready to play" : "Unlocked",
                64,row+36,10,MUTED,false,0);
            if (locked) {
                g.circle(366,row+15,6,MUTED); g.circle(366,row+15,3,BACKGROUND);
                g.rect(358,row+15,16,12,2,MUTED); g.circle(366,row+20,1.5f,BACKGROUND);
            } else for (int s = 0; s < 3; s++) star(g,331+s*17,row+20,5.5f,s < profile.stars[i] ? COLORS[2] : BORDER);
            g.line(22,row+rowHeight-1,398,row+rowHeight-1,1,BORDER);
        }
        pageArrow(g,"chapter_prev","Previous Chapter",22,height-132,false,sectorPage > 0);
        pageArrow(g,"chapter_next","Next Chapter",342,height-132,true,sectorPage < Campaign.CHAPTERS.length-1);
        int cleared = 0;
        for (int i = first; i < end; i++) if (profile.cleared(i)) cleared++;
        g.text(cleared+" / "+(end-first)+" CLEARED",210,height-104,11,MUTED,true,1);
        button(g,"back","Back",52,height-74,316,48,false,true);
    }

    private void pageArrow(Graphics g, String id, String label, float x, float y, boolean right, boolean enabled) {
        if (enabled) buttons.add(new Button(id,label,x,y,56,48));
        g.rect(x,y,56,48,6,PANEL);
        int color = enabled ? WHITE : BORDER;
        float center = x+28, direction = right ? 1 : -1;
        g.line(center-direction*9,y+24,center+direction*9,y+24,2,color);
        g.line(center+direction*9,y+24,center,y+15,2,color);
        g.line(center+direction*9,y+24,center,y+33,2,color);
    }

    private static void factionEmblem(Graphics g, float x, float y, float size, int faction) {
        int color = COLORS[faction];
        float r = size/2;
        g.polygon(hex(x,y,r),mix(BACKGROUND,color,.16f),color,1.5f);
        if (faction == 1) {
            for (int i = -1; i <= 1; i++) {
                float top = y+i*r*.38f;
                g.line(x-r*.4f,top+r*.2f,x,top-r*.1f,2,color);
                g.line(x,top-r*.1f,x+r*.4f,top+r*.2f,2,color);
            }
        } else if (faction == 2) {
            g.polygon(new float[] {x,y-r*.55f,x+r*.38f,y,x,y+r*.55f,x-r*.38f,y},0,color,2);
            g.line(x-r*.5f,y,x+r*.5f,y,1.5f,color);
        } else if (faction == 4) {
            g.polygon(new float[] {x-r*.42f,y-r*.5f,x+r*.42f,y-r*.5f,x+r*.42f,y+r*.25f,x,y+r*.55f,x-r*.42f,y+r*.25f},0,color,2);
            g.line(x,y-r*.3f,x,y+r*.3f,2,color);
        } else if (faction == 5) {
            for (int i = 0; i < 3; i++) {
                double angle = i*Math.PI/3;
                float dx = (float)Math.cos(angle)*r*.55f, dy = (float)Math.sin(angle)*r*.55f;
                g.line(x-dx,y-dy,x+dx,y+dy,2,color);
            }
        } else {
            g.circle(x,y,r*.43f,color); g.circle(x+r*.17f,y-r*.1f,r*.34f,BACKGROUND);
            g.circle(x-r*.15f,y+r*.05f,r*.08f,color);
        }
    }

    private void drawSettings(Graphics g) {
        float y = modal(g, 427);
        g.text("Settings", 52, y+47, 27, WHITE, true, 0);
        toggle(g,"music","Music",y+79,profile.music);
        toggle(g,"sound","Sound Effects",y+134,profile.sound);
        toggle(g,"haptics","Vibration",y+189,profile.haptics);
        g.text("Difficulty",52,y+271,14,WHITE,true,0);
        for (int i = 0; i < 3; i++) button(g,"difficulty_"+i,GameModel.DIFFICULTIES[i],52+i*108,y+288,100,40,profile.difficulty == i,false);
        button(g,"back","Back",52,y+358,316,46,false,true);
    }

    private void toggle(Graphics g, String id, String name, float y, boolean enabled) {
        buttons.add(new Button(id, name, 52, y, 316, 47));
        g.text(name, 52, y+29, 16, WHITE, false, 0);
        g.rect(308, y+9, 56, 28, 14, enabled ? COLORS[0] : BORDER);
        g.circle(enabled ? 350 : 322, y+23, 10, enabled ? BACKGROUND : MUTED);
        g.line(52, y+48, 368, y+48, 1, BORDER);
    }

    private void drawResult(Graphics g) {
        boolean won = model.outcome == GameModel.WON;
        boolean chapterWon = won && (model.levelIndex+1)%Campaign.SECTORS_PER_CHAPTER == 0;
        boolean campaignWon = won && model.levelIndex == GameModel.LEVELS.length-1;
        float y = modal(g, 465);
        g.text(won ? campaignWon ? "Campaign Complete" : chapterWon ? "Chapter Secured" : "Sector Secured" : "Sector Lost",210,y+48,27,WHITE,true,1);
        if (won) for (int i = 0; i < 3; i++) star(g, 173+i*37, y+84, 13, i < model.stars() ? COLORS[2] : BORDER);
        else g.text("Regroup. Try a new approach.", 210, y+86, 13, MUTED, false, 1);
        if (chapterWon) g.text(campaignWon ? "THE FRONTIER IS UNITED" : Campaign.chapter(model.levelIndex+1).name+" unlocked",210,y+111,11,COLORS[0],true,1);
        else if (won) for (boolean surrendered : model.resigned) if (surrendered) { g.text("RIVAL SURRENDER",210,y+111,11,COLORS[2],true,1); break; }
        g.text(won ? "SCORE" : "ARMY DEPLOYED", 210, y+130, 11, MUTED, true, 1);
        g.text(Integer.toString(won ? model.score() : model.unitsSent), 210, y+175, 42, won ? COLORS[0] : COLORS[1], true, 1);
        g.text("TIME", 117, y+212, 10, MUTED, true, 1);
        g.text(time(model.elapsed), 117, y+236, 20, WHITE, true, 1);
        g.text("CAPTURES", 304, y+212, 10, MUTED, true, 1);
        g.text(Integer.toString(model.captures), 304, y+236, 20, WHITE, true, 1);
        String primary = won && model.levelIndex < GameModel.LEVELS.length-1 ? "next" : won ? "sectors" : "restart";
        button(g, primary, primary.equals("next") ? "Next Sector" : won ? "All Sectors" : "Try Again", 52, y+266, 316, 48, true, true);
        button(g, won ? "restart" : "sectors", won ? "Replay Sector" : "Sectors", 52, y+324, 316, 48, false, true);
        button(g, "menu", "Main Menu", 52, y+381, 316, 48, false, true);
    }

    private void button(Graphics g, String id, String label, float x, float y, float w, float h, boolean active, boolean command) {
        buttons.add(new Button(id, label, x, y, w, h));
        g.rect(x, y, w, h, 6, active ? COLORS[0] : command ? 0xFF353C3D : BACKGROUND);
        g.text(label, x+w/2, y+h/2+5, command ? 15 : 13, active ? BACKGROUND : WHITE, true, 1);
    }

    private void addIcon(Graphics g, String id, String label, float x, float y, boolean pause) {
        buttons.add(new Button(id,label,x,y,38,38));
        float centerX = x+19, centerY = y+19;
        if (id.equals("pause")) {
            g.rect(centerX-7, centerY-8, 4, 16, 1, WHITE);
            g.rect(centerX+3, centerY-8, 4, 16, 1, WHITE);
        } else if (id.equals("restart")) {
            float previousX = 0, previousY = 0;
            for (int i = 0; i <= 18; i++) {
                double angle = Math.toRadians(40+i*275f/18);
                float x1 = centerX+(float)Math.cos(angle)*10, y1 = centerY+(float)Math.sin(angle)*10;
                if (i > 0) g.line(previousX,previousY,x1,y1,2,WHITE);
                previousX = x1; previousY = y1;
            }
            g.polygon(new float[] {centerX+13,centerY-4,centerX+4,centerY-6,centerX+10,centerY-13},WHITE,0,0);
        } else {
            g.circle(centerX, centerY, 9, MUTED);
            g.circle(centerX, centerY, 6, BACKGROUND);
            g.circle(centerX, centerY, 2.5f, MUTED);
            for (int i = 0; i < 8; i++) {
                double a = i*Math.PI/4;
                g.line(centerX+(float)Math.cos(a)*9,centerY+(float)Math.sin(a)*9,
                    centerX+(float)Math.cos(a)*12,centerY+(float)Math.sin(a)*12,3,MUTED);
            }
        }
    }

    public void down(float x, float y) {
        pressed = null; selected = -1;
        practiceDragging = false; panning = false;
        for (Button button : buttons) if (button.contains(x,y)) { pressed = button; return; }
        if (overlay == TUTORIAL && tutorialStep == 1 && !practiceDone && Math.hypot(x-110,y-tutorialY()) <= 66) {
            practiceDragging = true; pointerX = x; pointerY = y; return;
        }
        if (overlay != NONE) return;
        int id = territoryAt(x,y);
        if (id >= 0 && model.territories.get(id).owner == GameModel.PLAYER) {
            selected = id; pointerX = x; pointerY = y;
        } else if (y >= boardTop() && y <= boardBottom() && zoom > 1) {
            panning = true; pointerX = x; pointerY = y;
        }
    }

    public void move(float x, float y) {
        if (panning) { panX += x-pointerX; panY += y-pointerY; layoutBoard(); }
        pointerX = x; pointerY = y;
    }

    public void up(float x, float y) {
        if (pressed != null) {
            Button button = pressed; pressed = null;
            if (button.contains(x,y)) command(button.id);
        } else if (overlay == SPLASH) overlay = MENU;
        else if (practiceDragging && overlay == TUTORIAL) {
            if (Math.hypot(x-310,y-tutorialY()) <= 66) { practiceDone = true; events.cue(1); }
        } else if (selected >= 0 && overlay == NONE) {
            int target = territoryAt(x,y);
            if (target >= 0 && model.territories.get(selected).owner == GameModel.PLAYER
                && model.launch(selected,target,fraction) > 0) events.cue(0);
        }
        selected = -1; practiceDragging = false; panning = false;
    }

    public void cancel() { selected = -1; pressed = null; practiceDragging = false; panning = false; }
    public String pressedLabel() { return pressed == null ? null : pressed.label; }

    public void back() {
        cancel();
        if (overlay == NONE) overlay = PAUSE;
        else if (overlay == PAUSE) overlay = NONE;
        else if (overlay == SETTINGS || overlay == SECTORS) overlay = previousOverlay;
        else if (overlay == SPLASH || overlay == RESULT) overlay = MENU;
        else if (overlay == TUTORIAL) {
            if (tutorialStep > 0) tutorialStep--;
            else { tutorialStartsBattle = false; overlay = MENU; }
        }
    }

    public void pause() { cancel(); if (overlay == NONE) overlay = PAUSE; }

    private void command(String id) {
        events.cue(0);
        if (id.equals("pause")) overlay = PAUSE;
        else if (id.equals("resume")) overlay = NONE;
        else if (id.equals("menu")) { cancel(); overlay = MENU; }
        else if (id.equals("play")) {
            if (profile.tutorialSeen) start(profile.selectedSector);
            else openTutorial(true);
        }
        else if (id.equals("tutorial")) openTutorial(false);
        else if (id.equals("tutorial_prev")) tutorialStep = Math.max(0,tutorialStep-1);
        else if (id.equals("demo_quarter")) tutorialFraction = .25;
        else if (id.equals("demo_half")) tutorialFraction = .5;
        else if (id.equals("demo_all")) tutorialFraction = 1;
        else if (id.equals("demo_capture")) tutorialKings = Math.min(5,tutorialKings+1);
        else if (id.equals("demo_lose")) tutorialKings = Math.max(0,tutorialKings-1);
        else if (id.equals("tutorial_next")) {
            if (tutorialStep == 4) finishTutorial();
            else if (tutorialStep != 1 || practiceDone) tutorialStep++;
        }
        else if (id.equals("tutorial_skip")) finishTutorial();
        else if (id.equals("restart")) start(model.levelIndex);
        else if (id.equals("next")) start(Math.min(GameModel.LEVELS.length-1, model.levelIndex+1));
        else if (id.equals("settings") || id.equals("sectors")) {
            previousOverlay = overlay; overlay = id.equals("settings") ? SETTINGS : SECTORS;
            if (overlay == SECTORS) sectorPage = Campaign.chapterIndex(profile.selectedSector);
        } else if (id.equals("back")) overlay = previousOverlay;
        else if (id.equals("chapter_prev")) sectorPage = Math.max(0,sectorPage-1);
        else if (id.equals("chapter_next")) sectorPage = Math.min(Campaign.CHAPTERS.length-1,sectorPage+1);
        else if (id.equals("quarter")) fraction = .25;
        else if (id.equals("half")) fraction = .5;
        else if (id.equals("all")) fraction = 1;
        else if (id.equals("zoom_in")) cameraGesture(210,(boardTop()+boardBottom())/2,1.25f,0,0);
        else if (id.equals("zoom_out")) cameraGesture(210,(boardTop()+boardBottom())/2,.8f,0,0);
        else if (id.equals("fit_board")) { zoom = 1; panX = panY = 0; layoutBoard(); }
        else if (id.equals("sound")) profile.sound = !profile.sound;
        else if (id.equals("music")) profile.music = !profile.music;
        else if (id.equals("haptics")) profile.haptics = !profile.haptics;
        else if (id.startsWith("level_")) {
            int level = Integer.parseInt(id.substring(6));
            if (level <= profile.unlocked) { profile.selectedSector = level; overlay = MENU; }
        } else if (id.startsWith("difficulty_")) {
            profile.difficulty = Integer.parseInt(id.substring(11));
            model.difficulty = profile.difficulty;
        }
        events.changed();
    }

    public void start(int level) {
        model = new GameModel(level, profile.difficulty, System.nanoTime());
        profile.selectedSector = level; hasBattle = true;
        resultRecorded = false; kingBanner = 0; resultDelay = 0; zoom = 1; panX = panY = 0; overlay = NONE; cancel();
    }

    private void openTutorial(boolean startsBattle) {
        cancel(); tutorialStep = 0; practiceDone = false; tutorialFraction = .25; tutorialKings = 0;
        tutorialStartsBattle = startsBattle; overlay = TUTORIAL;
    }

    private void finishTutorial() {
        profile.tutorialSeen = true;
        if (tutorialStartsBattle) start(profile.selectedSector); else overlay = MENU;
        tutorialStartsBattle = false;
    }

    public boolean needsAnimation() { return overlay == NONE || overlay == SPLASH; }

    private int territoryAt(float x, float y) {
        if (y < boardTop()-7 || y > boardBottom()+8 || x < 8 || x > 412) return -1;
        for (GameModel.Territory territory : model.territories) {
            if (insideHex(x,y,cx(territory),cy(territory),boardScale*.95f)) return territory.id;
        }
        // Exact tile hits win. Only the gutters use expanded, nearest-tile targets.
        int nearest = -1; double distance = Double.MAX_VALUE;
        float radius = Math.max(32,boardScale*.95f+14);
        for (GameModel.Territory territory : model.territories) {
            if (!insideHex(x,y,cx(territory),cy(territory),radius)) continue;
            double candidate = Math.hypot(x-cx(territory),y-cy(territory));
            if (candidate < distance) { distance = candidate; nearest = territory.id; }
        }
        return nearest;
    }

    private static boolean insideHex(float x, float y, float cx, float cy, float radius) {
        float dx = Math.abs(x-cx), dy = Math.abs(y-cy);
        return dx <= .8660254f*radius && dy <= radius && dy+dx/.8660254f*.5f <= radius;
    }

    float[] buttonPosition(String id) {
        for (Button button : buttons) if (button.id.equals(id)) return new float[] {button.x+button.width/2,button.y+button.height/2};
        return null;
    }

    float[] sectorPosition(int sector) {
        if (sector < 0 || sector >= GameModel.LEVELS.length || Campaign.chapterIndex(sector) != sectorPage) return null;
        float rowHeight = Math.min(61,(height-352)/Campaign.SECTORS_PER_CHAPTER);
        return new float[] {210,208+(sector%Campaign.SECTORS_PER_CHAPTER)*rowHeight+(rowHeight-2)/2};
    }

    public float[] position(int id) { GameModel.Territory territory = model.territories.get(id); return new float[] {cx(territory),cy(territory)}; }

    public static String time(float seconds) {
        int total = (int) seconds;
        return String.format(java.util.Locale.US, "%02d:%02d", total/60,total%60);
    }

    private static float[] hex(float x, float y, float radius) {
        float[] points = new float[12];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI/6+i*Math.PI/3;
            points[i*2] = x+(float)Math.cos(angle)*radius;
            points[i*2+1] = y+(float)Math.sin(angle)*radius;
        }
        return points;
    }

    private static void star(Graphics g, float x, float y, float radius, int color) {
        float[] points = new float[20];
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI/2+i*Math.PI/5;
            float r = i%2 == 0 ? radius : radius*.45f;
            points[i*2] = x+(float)Math.cos(angle)*r;
            points[i*2+1] = y+(float)Math.sin(angle)*r;
        }
        g.polygon(points,color,0,0);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a>>16)&255)*(1-t)+((b>>16)&255)*t);
        int green = (int) (((a>>8)&255)*(1-t)+((b>>8)&255)*t);
        int blue = (int) ((a&255)*(1-t)+(b&255)*t);
        return 0xFF000000 | (r<<16) | (green<<8) | blue;
    }
}
