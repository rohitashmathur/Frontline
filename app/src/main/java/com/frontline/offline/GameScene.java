package com.frontline.offline;

import java.util.ArrayList;

public final class GameScene {
    public interface Graphics {
        void rect(float x, float y, float width, float height, float radius, int color);
        void circle(float x, float y, float radius, int color);
        void line(float x1, float y1, float x2, float y2, float width, int color);
        void polygon(float[] points, int fill, int stroke, float strokeWidth);
        void text(String text, float x, float baseline, float size, int color, boolean bold, int align);
        default float measureText(String text, float size, boolean bold) { return text.length()*size*.55f; }
        default void clip(float x, float y, float width, float height) {}
        default void unclip() {}
    }
    public interface Events {
        void changed();
        void cue(int kind);
        default void unlockCodeRequested() {}
        default void exportPlaytestRequested() {}
        default long now() { return System.currentTimeMillis(); }
    }
    public static final class Profile {
        public final int[] best = new int[GameModel.LEVELS.length];
        public final int[] stars = new int[GameModel.LEVELS.length];
        public final float[] times = new float[GameModel.LEVELS.length];
        public int unlocked, selectedSector, difficulty = 1, wins;
        public boolean sound = true, music = true, haptics = true, tutorialSeen;
        public boolean cameraGuideSeen;
        public String language = "en";
        public Progress progress = new Progress();
        public PlaytestLog log = new PlaytestLog();
        public RunState run, lastRun;
        public LogisticsRecords logisticsRecords = new LogisticsRecords();
        public int totalScore() {
            int sum = 0;
            for (int i = 0; i < best.length; i++) {
                int highest = best[i];
                for (int d = 0; d < 3; d++) {
                    highest = Math.max(highest,progress.campaignBest(i,d,GameModel.RULES_VERSION));
                    highest = Math.max(highest,progress.campaignBest(i,d,10));
                    highest = Math.max(highest,progress.campaignBest(i,d,Progress.UNVERSIONED_CAMPAIGN_RULES));
                }
                sum += highest;
            }
            return sum;
        }
        public boolean cleared(int sector) {
            if (stars[sector] > 0 || best[sector] > 0) return true;
            for (int d = 0; d < 3; d++) if (progress.campaignCleared(sector,d)) return true;
            return false;
        }
        public void reconcileProgress() {
            unlocked = Math.max(0,Math.min(best.length-1,unlocked));
            for (int i = 0; i < best.length-1; i++) if (cleared(i)) unlocked = Math.max(unlocked,i+1);
            selectedSector = Math.max(0,Math.min(unlocked,selectedSector));
        }
    }
    public static final int NONE = 0, PAUSE = 1, SECTORS = 2, SETTINGS = 3, RESULT = 4,
        SPLASH = 5, MENU = 6, TUTORIAL = 7, BRIEFING = 8, CONFIRM = 9,
        CHALLENGES = 10, DAILY = 11, MASTERY = 12, HELP = 13, CAMERA_HELP = 14,
        RUN_HOME = 15, RUN_COUNCIL = 16, RUN_SUMMARY = 17, LOGISTICS = 18, LOGISTICS_RESULT = 19;
    public static final int BACKGROUND = 0xFF17191B, PANEL = 0xFF232629;
    public static final int WHITE = 0xFFF0F7F7, MUTED = 0xFFA2ABA9, BORDER = 0xFF41494A;
    public static final int[] COLORS = {0xFF5DE0BA, 0xFFFF816C, 0xFFF6D477, 0xFFAC9EEF, 0xFF65B9F2, 0xFFED99BF};
    public GameModel model;
    public final Profile profile;
    public int overlay = SPLASH;
    public boolean hasBattle;
    public double fraction = 1;
    private final Events events;
    private final CommandScreens commandScreens;
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final CommandScreens.Controls screenControls = (id,label,x,y,w,h) -> buttons.add(new Button(id,label,x,y,w,h));
    private int previousOverlay = NONE, selected = -1, sectorPage;
    private int settingsReturn = MENU, previewReturn = MENU;
    private boolean screenDrag, screenScrolling;
    private float screenDownY;
    private boolean resultRecorded, tutorialStartsBattle, practiceDragging, practiceDone;
    private boolean tutorialTerminal;
    public float minimumTouchSize = 64;
    private int tutorialStep;
    private int fractionVisits, challengePage, helpReturn = MENU;
    private boolean reinforcementDone, kingPracticeGain, kingPracticeLoss;
    private int previewMode, previewSector, previewChallenge;
    private String previewDate = "", confirmationReason = "";
    private int confirmationReturn;
    private Runnable confirmedAction;
    private long renderedCouncilNonce;
    private boolean improvedTime, improvedScore;
    private int feedbackType, feedbackUnits;
    private int feedbackTile;
    private float routeRefusalTimer;
    private float feedbackTimer;
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

    public static final class AccessibleButton {
        public final String id, label;
        public final float x, y, width, height;
        AccessibleButton(Button b, String language) {
            id = b.id; label = Localization.translate(language,b.label);
            x = b.x; y = b.y; width = b.width; height = b.height;
        }
    }
    public java.util.List<AccessibleButton> accessibleButtons() {
        ArrayList<AccessibleButton> result = new ArrayList<>();
        for (Button b : buttons) result.add(new AccessibleButton(b,profile.language));
        return result;
    }
    public void activateButton(String id) {
        for (Button b : buttons) if (b.id.equals(id)) { command(id); return; }
    }
    private String tr(String key,Object... args) { return Localization.text(profile.language,key,args); }
    private Graphics localized(final Graphics target) {
        return new Graphics() {
            public void rect(float x,float y,float w,float h,float r,int c) { target.rect(x,y,w,h,r,c); }
            public void circle(float x,float y,float r,int c) { target.circle(x,y,r,c); }
            public void line(float x,float y,float x2,float y2,float w,int c) { target.line(x,y,x2,y2,w,c); }
            public void polygon(float[] p,int f,int s,float w) { target.polygon(p,f,s,w); }
            public void clip(float x,float y,float w,float h) { target.clip(x,y,w,h); }
            public void unclip() { target.unclip(); }
            public float measureText(String text,float size,boolean bold) { return target.measureText(text,size,bold); }
            public void text(String text,float x,float baseline,float size,int c,boolean bold,int align) {
                target.text(Localization.translate(profile.language,text),x,baseline,size,c,bold,align);
            }
        };
    }
    private void paragraph(Graphics g,String text,float x,float baseline,float width,float size,int color,boolean bold,int align) {
        String translated = Localization.translate(profile.language,text);
        StringBuilder line = new StringBuilder(); int row = 0;
        for (String word : translated.split(" ")) {
            String candidate = line.length() == 0 ? word : line+" "+word;
            if (line.length() > 0 && g.measureText(candidate,size,bold) > width) {
                g.text(line.toString(),x,baseline+row++*size*1.45f,size,color,bold,align); line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) g.text(line.toString(),x,baseline+row*size*1.45f,size,color,bold,align);
    }

    public GameScene(Profile profile, GameModel restored, Events events) {
        this.profile = profile; this.events = events;
        commandScreens = new CommandScreens(profile);
        model = restored == null ? new GameModel(0, profile.difficulty, System.nanoTime()) : restored;
        hasBattle = restored != null;
        resultRecorded = restored != null && restored.outcome != GameModel.PLAYING;
        if (restored != null && restored.battleMode == GameModel.MODE_RUN) {
            if (profile.run == null) hasBattle = false;
            else if (profile.run.matchesActiveBattle(restored)) resultRecorded = false;
            else if (restored.outcome == GameModel.PLAYING) hasBattle = false;
        }
        profile.reconcileProgress();
    }

    public void update(float dt) {
        if (overlay == SPLASH) {
            splashElapsed += Math.max(0, Math.min(.1f, dt));
            if (splashElapsed >= .85f) overlay = MENU;
            return;
        }
        if (overlay != NONE) return;
        feedbackTimer = Math.max(0,feedbackTimer-Math.max(0,dt));
        routeRefusalTimer = Math.max(0,routeRefusalTimer-Math.max(0,dt));
        kingBanner = Math.max(0,kingBanner-Math.max(0,dt));
        if (resultDelay > 0) {
            resultDelay = Math.max(0,resultDelay-Math.max(0,dt));
            if (resultDelay == 0) overlay = RESULT;
            return;
        }
        int oldCaptures = model.captures;
        int kings = model.capturedKings(GameModel.PLAYER);
        model.update(dt);
        for (GameModel.BattleEvent event : model.drainEvents()) {
            if (event.type == GameModel.KING_GAIN_EVENT || event.type == GameModel.KING_LOSS_EVENT || event.type == GameModel.HOME_KING_LOSS_EVENT)
                log(event.type == GameModel.KING_GAIN_EVENT ? "king_gain" : "king_loss","tile="+event.tile+";home="+(event.type == GameModel.HOME_KING_LOSS_EVENT));
            int priority = feedbackPriority(event.type);
            if (feedbackTimer == 0 || priority >= feedbackPriority(feedbackType)) {
                feedbackUnits = event.type == feedbackType && feedbackTimer > 0 ? feedbackUnits+event.units : event.units;
                feedbackType = event.type; feedbackTimer = 2.5f;
                feedbackTile = event.tile;
            }
        }
        if (model.capturedKings(GameModel.PLAYER) != kings) kingBanner = 2.4f;
        if (model.captures != oldCaptures) events.cue(1);
        if (model.outcome != GameModel.PLAYING && !resultRecorded) {
            resultRecorded = true;
            if (model.battleMode == GameModel.MODE_RUN) {
                if (profile.run != null && profile.run.matchesActiveBattle(model)) {
                    profile.run = profile.run.finishBattle(model);
                    if (profile.run.terminal()) profile.lastRun = profile.run;
                }
                log("attempt_result",model.outcome == GameModel.WON ? "won" : "lost");
                openRun(); events.cue(model.outcome == GameModel.WON ? 2 : 3);
                events.changed(); return;
            }
            if (model.battleMode == GameModel.MODE_LOGISTICS) {
                improvedTime = profile.logisticsRecords.record(model,model.logisticsConfigVersion);
                log("attempt_result",model.outcome == GameModel.WON ? "won" : "lost");
                overlay = LOGISTICS_RESULT; events.cue(model.outcome == GameModel.WON ? 2 : 3);
                events.changed(); return;
            }
            if (model.outcome == GameModel.WON) {
                int index = model.levelIndex;
                if (model.objectiveType != 0) {
                    boolean improved = profile.progress.recordChallenge(model);
                    improvedTime = improved && ObjectiveResult.policy(model) == ObjectiveResult.Policy.ELAPSED_TIME;
                    improvedScore = improved && ObjectiveResult.policy(model) == ObjectiveResult.Policy.DEPLOYED_TROOPS;
                } else if (model.rulesVersion == 0) {
                    improvedScore = model.score() > profile.best[index];
                    improvedTime = profile.times[index] == 0 || model.elapsed < profile.times[index];
                    profile.best[index] = Math.max(profile.best[index], model.score());
                    profile.stars[index] = Math.max(profile.stars[index], model.stars());
                    if (improvedTime) profile.times[index] = model.elapsed;
                } else {
                    improvedScore = model.score() > profile.progress.campaignBest(index,model.difficulty,model.rulesVersion);
                    improvedTime = profile.progress.recordCampaign(model);
                }
                if (model.objectiveType == 0) profile.unlocked = Math.max(profile.unlocked,Math.min(GameModel.LEVELS.length-1,index+1));
                profile.wins++;
            }
            log("attempt_result",model.outcome == GameModel.WON ? "won" : "lost");
            if (model.outcome == GameModel.WON && kingBanner > 0) resultDelay = 2.4f;
            else overlay = RESULT;
            events.cue(model.outcome == GameModel.WON ? 2 : 3);
            events.changed();
        }
    }

    public void render(Graphics g, float height) {
        g = localized(g);
        this.height = height; buttons.clear();
        g.rect(0, 0, 420, height, 0, BACKGROUND);
        if (overlay == SPLASH) { drawSplash(g); return; }
        if (overlay == TUTORIAL) { drawTutorial(g); return; }
        if (overlay == SECTORS) { drawSectors(g); return; }
        if (overlay == SETTINGS) { drawSettings(g); return; }
        if (overlay == BRIEFING) { drawBriefing(g); return; }
        if (overlay == CHALLENGES) { drawChallenges(g); return; }
        if (overlay == DAILY) { drawDaily(g); return; }
        if (overlay == MASTERY) { drawMastery(g); return; }
        if (overlay == HELP) { drawHelp(g); return; }
        if (overlay == CONFIRM) { drawConfirmation(g); return; }
        if (overlay == CAMERA_HELP) { drawCameraHelp(g); return; }
        if (overlay == RUN_HOME || overlay == RUN_COUNCIL || overlay == RUN_SUMMARY) {
            drawRun(g); return;
        }
        if (overlay == LOGISTICS || overlay == LOGISTICS_RESULT) {
            drawLogistics(g); return;
        }
        if (overlay == MENU) {
            drawMenu(g);
            return;
        }
        g.text("FRONTLINE", 22, 47, 27, WHITE, true, 0);
        addIcon(g, "pause", "Pause", 274, 19, overlay == NONE);
        addIcon(g, "restart", "Restart Round", 322, 19, false);
        addIcon(g, "settings", "Settings", 370, 19, false);
        String battleTitle = model.battleMode == GameModel.MODE_LOGISTICS
            ? tr("logistics.map."+Logistics.getIndex(model)) : String.format(java.util.Locale.US,"%02d / ",model.levelIndex+1)+model.level().name;
        g.text(battleTitle,22,82,17,WHITE,true,0);
        g.text(time(model.elapsed),397,82,17,WHITE,false,2);
        float x = 22;
        for (int owner = -1; owner <= model.level().opponents; owner++) {
            float width = 376f * model.owned(owner) / model.territories.size();
            if (width > 0) g.rect(x,100,width,18,0,owner == -1 ? BORDER : armyColor(owner));
            x += width;
        }
        for (int owner = 0; owner <= model.level().opponents; owner++) {
            float lx = 22+(owner%3)*125, ly = 135+(owner/3)*20;
            factionMark(g,lx+4,ly-4,4,model.level().faction(owner),armyColor(owner));
            String percentage = Math.round(100f*model.owned(owner)/model.territories.size())+"%";
            String label = Campaign.SHORT_NAMES[model.level().faction(owner)]+" "+(model.resigned[owner] ? "OUT" : percentage);
            g.text(label,lx+13,ly,11,model.owned(owner) > 0 ? WHITE : MUTED,owner == 0,0);
        }
        drawKingBanner(g);
        g.text(objectiveLabel(),22,214,11,model.objectiveType == 0 ? MUTED : COLORS[2],true,0);
        boolean warning = selected >= 0 && model.objectiveType == Challenge.BUDGET
            && model.unitsSent+aimedAmount() > model.deploymentBudget;
        if (warning) g.text(tr("budget.warning_short",aimedAmount(),Math.max(0,model.deploymentBudget-model.unitsSent)),22,232,11,COLORS[1],true,0);
        else if (model.battleMode == GameModel.MODE_LOGISTICS && selected >= 0 && territoryAt(pointerX,pointerY) >= 0 && territoryAt(pointerX,pointerY) != selected) {
            int[] path = model.route(selected,territoryAt(pointerX,pointerY),0);
            g.text(path == null ? tr("logistics.refused") : tr("logistics.route_preview",path.length-1,Math.round(model.routeEta(path)*10)/10.0),22,232,11,path == null ? COLORS[1] : WHITE,true,0);
        }
        else if (routeRefusalTimer > 0) g.text(tr("logistics.refused"),22,232,11,COLORS[1],true,0);
        else if (feedbackTimer > 0) g.text(feedbackText(),22,232,11,WHITE,false,0);
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

    private float boardTop() { return 244; }
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
        int aimColor = model.objectiveType == Challenge.BUDGET && model.unitsSent+aimedAmount() > model.deploymentBudget ? COLORS[1] : WHITE;
        boolean logistics = model.battleMode == GameModel.MODE_LOGISTICS;
        int[] aimedRoute = logistics && selected >= 0 ? model.route(selected,aimed,0) : null;
        if (logistics && aimed >= 0 && aimed != selected && aimedRoute == null) aimColor = COLORS[1];
        for (GameModel.Territory territory : model.territories) {
            float x = cx(territory), y = cy(territory), radius = boardScale * .94f;
            if (x+radius < 8 || x-radius > 412 || y+radius < boardTop() || y-radius > boardBottom()) continue;
            int color = territory.owner == -1 ? BORDER : armyColor(territory.owner);
            g.polygon(hex(x, y, radius), mix(BACKGROUND, color, territory.owner == -1 ? .42f : .29f),
                selected == territory.id || aimed == territory.id ? aimColor : mix(BACKGROUND, color, .75f),
                selected == territory.id || aimed == territory.id ? 2.7f : 1.2f);
            if (profile.progress.theme == 1) g.circle(x,y,radius*.8f,0x143AAB8F);
            if (profile.progress.theme == 2) {
                g.line(x-radius*.55f,y+radius*.48f,x+radius*.55f,y+radius*.48f,1,0x665E8BA3);
                g.line(x,y-radius*.7f,x,y-radius*.42f,1,0x665E8BA3);
            }
            float node = Math.min(21, boardScale * .45f);
            g.circle(x, y, node + 2, color);
            g.circle(x, y, node, territory.owner == -1 ? 0xFF2C3233 : mix(BACKGROUND, color, .12f));
            String count = Integer.toString(territory.count());
            float size = Math.min(20,boardScale*.45f)*Math.min(1,2.5f/count.length());
            if (x >= 20 && x <= 400) g.text(count,x,y+size*.3f,size,WHITE,true,1);
            if (territory.owner >= 0) factionMark(g,x,y+node+6,Math.min(5,boardScale*.13f),model.level().faction(territory.owner),color);
            if (logistics && selected >= 0 && territory.id != selected && model.route(selected,territory.id,0) != null)
                g.circle(x+radius*.5f,y-radius*.45f,3,COLORS[0]);
            if (territory.count() >= model.capacity(territory)) g.text("MAX",x,y-node+1,7,WHITE,true,1);
            if (territory.id == model.objectiveTarget && model.objectiveType == Challenge.HOLD_KING)
                g.polygon(new float[] {x,y-radius,x+5,y-radius+6,x,y-radius+12,x-5,y-radius+6},COLORS[2],WHITE,1);
            if (territory.capital) {
                float baseline = y - node - 8;
                g.polygon(new float[] {x-7,baseline,x-7,baseline-8,x-3,baseline-4,x,baseline-10,x+3,baseline-4,x+7,baseline-8,x+7,baseline}, color, 0, 0);
            } else if (territory.owner != -1 && boardScale > 34 && territory.count() < model.capacity(territory)) {
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
            if (aimedRoute != null) {
                for (int i = 1; i < aimedRoute.length; i++) {
                    GameModel.Territory a = model.territories.get(aimedRoute[i-1]), b = model.territories.get(aimedRoute[i]);
                    g.line(cx(a),cy(a),cx(b),cy(b),2.5f,aimColor);
                }
            } else g.line(cx(source), cy(source), pointerX, pointerY, 2, aimColor);
            float angle = (float) Math.atan2(pointerY-cy(source), pointerX-cx(source));
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            g.polygon(new float[] {pointerX,pointerY,pointerX-dx*12-dy*6,pointerY-dy*12+dx*6,
                pointerX-dx*12+dy*6,pointerY-dy*12-dx*6}, aimColor, 0, 0);
            if (boardScale >= 36) {
                float labelX = Math.max(24,Math.min(396,(cx(source)+pointerX)/2));
                float labelY = Math.max(boardTop()+20,Math.min(boardBottom()-5,(cy(source)+pointerY)/2-10));
                g.rect(labelX-15,labelY-16,30,20,3,BACKGROUND);
                g.text(Integer.toString(aimedAmount()),labelX,labelY,15,WHITE,true,1);
            }
        }
        g.unclip();
    }

    private void drawFooter(Graphics g) {
        float top = height-139;
        g.rect(0, top, 420, 139, 0, PANEL);
        g.line(0, top, 420, top, 1, BORDER);
        GameModel.Territory source = selected < 0 ? null : model.territories.get(selected);
        g.text(source == null ? "ARMY" : "TILE "+(source.id+1)+(source.count() >= model.capacity(source) ? " / MAX" : ""),22,top+28,10,MUTED,true,0);
        g.text(Integer.toString(source == null ? model.army(0) : source.count()),22,top+61,27,WHITE,true,0);
        g.text("DEPLOY", 195, top+28, 10, MUTED, true, 0);
        button(g, "quarter", "25%", 193, top+39, 64, 48, fraction == .25, false);
        button(g, "half", "50%", 263, top+39, 64, 48, fraction == .5, false);
        button(g, "all", "100%", 333, top+39, 64, 48, fraction == 1, false);
        if (source != null) {
            int sent = aimedAmount();
            boolean over = model.objectiveType == Challenge.BUDGET && model.unitsSent+sent > model.deploymentBudget;
            g.text(tr("deploy.preview",sent,source.count()-sent),22,height-22,11,over ? COLORS[1] : WHITE,true,0);
        } else if (model.battleMode == GameModel.MODE_RUN) g.text(tr("run.retries",profile.run == null ? 0 : profile.run.retriesRemaining()),22,height-22,11,MUTED,false,0);
        else if (model.battleMode == GameModel.MODE_LOGISTICS) g.text(tr("logistics.experimental"),22,height-22,11,COLORS[2],false,0);
        else if (model.objectiveType == 0) g.text("3 STARS  "+time(model.level().parSeconds),22,height-22,11,MUTED,false,0);
        cameraButton(g,"zoom_out","Zoom Out",254,height-48);
        cameraButton(g,"fit_board","Fit Battlefield",304,height-48);
        cameraButton(g,"zoom_in","Zoom In",354,height-48);
    }

    private int aimedAmount() {
        if (selected < 0) return 0;
        int target = territoryAt(pointerX,pointerY);
        return model.previewAmount(selected,target,fraction);
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
        commandScreens.home(g,screenControls,model,activeBattle(),events.now(),height,minimumTouchSize);
    }

    private void addGear(Graphics g,float x,float y) {
        g.circle(x,y,11,WHITE); g.circle(x,y,7,BACKGROUND); g.circle(x,y,3,WHITE);
        for (int i = 0; i < 8; i++) {
            double a = i*Math.PI/4;
            g.line(x+(float)Math.cos(a)*11,y+(float)Math.sin(a)*11,x+(float)Math.cos(a)*15,y+(float)Math.sin(a)*15,4,WHITE);
        }
    }

    private void drawTutorial(Graphics g) {
        String[] headings = {"Claim Territory", "Capture a Tile", "Reinforce and Defend", "King Boost", "Win and Advance"};
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
            demoTerritory(g,110,y,reinforcementDone ? 32-sent : 32,COLORS[0],false);
            demoTerritory(g,310,y,reinforcementDone ? 8+sent : 8,COLORS[0],false);
            g.line(174,y,246,y,2,WHITE);
            g.polygon(new float[] {253,y,241,y-6,241,y+6},WHITE,0,0);
            if (practiceDragging) g.line(110,y,pointerX,pointerY,3,COLORS[0]);
            g.text("32 - "+sent+" = "+(32-sent),210,y+64,16,WHITE,true,1);
            String[] labels = {"25%", "50%", "100%"};
            String[] ids = {"demo_quarter","demo_half","demo_all"};
            double[] fractions = {.25,.5,1};
            for (int i = 0; i < 3; i++) {
                button(g,ids[i],labels[i],87+i*85,y+82,76,40,tutorialFraction == fractions[i],false);
            }
            tutorialCopy(g,"Tap all three amounts, then swipe to reinforce.",
                (int)(tutorialFraction*100)+"% sends "+sent+"; "+(32-sent)+" remain to defend.","25% saves reserves. 100% leaves this tile exposed.");
        } else if (tutorialStep == 3) {
            demoTerritory(g,92,y,125,COLORS[0],true);
            demoTerritory(g,210,y,100,COLORS[0],false);
            demoTerritory(g,328,y,75,tutorialKings > 0 ? COLORS[0] : COLORS[1],true);
            double boost = tutorialKings < 4 ? Math.pow(1.2,tutorialKings) : 3*Math.pow(1.2,tutorialKings-4);
            g.text("BOOST x"+multiplier(boost),210,y+77,18,COLORS[2],true,1);
            button(g,"demo_capture","Capture King",32,y+93,173,42,true,true);
            button(g,"demo_lose","Lose King",215,y+93,173,42,false,true);
            tutorialCopy(g,"Capture and lose a king in this practice.","One held enemy king gives your whole team x1.2 growth.",
                "Losing it removes that boost. Full table is in Rules.");
        } else {
            for (int i = 0; i < 3; i++) star(g, 172+i*38, y-20, 14, COLORS[2]);
            demoTerritory(g, 130, y+55, 42, COLORS[0], true);
            demoTerritory(g, 290, y+55, 18, COLORS[0], false);
            tutorialCopy(g,"Campaign: eliminate rival tiles and armies.","Faster wins earn stars. See the target before play.","Campaign wins unlock sectors; missions stay separate.");
        }
        for (int i = 0; i < 5; i++) g.circle(178+i*16,height-160,3.5f,i == tutorialStep ? COLORS[0] : BORDER);
        boolean ready = tutorialReady();
        pageArrow(g,"tutorial_prev","Previous Step",52,height-132,false,tutorialStep > 0);
        if (ready) button(g,"tutorial_next",tutorialStep == 4 ? tutorialStartsBattle ? "Start Battle" : "Done" : "Next",118,height-132,250,48,true,true);
        else {
            g.rect(118,height-132,250,48,6,PANEL);
            g.text(tutorialStep == 3 ? "Try capture and loss above" : tutorialStep == 2 && fractionVisits != 7 ? "Try all three amounts" : "Swipe above to continue",243,height-103,13,MUTED,true,1);
        }
        button(g,"tutorial_skip","Skip Tutorial",52,height-75,316,48,false,true);
    }

    private float tutorialY() { return Math.min(225, (height-400)/2+85); }
    private boolean tutorialReady() {
        return tutorialStep == 1 ? practiceDone : tutorialStep == 2 ? reinforcementDone && fractionVisits == 7 : tutorialStep != 3 || kingPracticeGain && kingPracticeLoss;
    }

    private void tutorialCopy(Graphics g, String first, String second, String third) {
        float baseline = height-288;
        paragraph(g,first,210,baseline,360,13,WHITE,false,1);
        paragraph(g,second,210,baseline+40,360,13,MUTED,false,1);
        paragraph(g,third,210,baseline+80,360,12,MUTED,false,1);
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
        commandScreens.campaign(g,screenControls,model,activeBattle(),sectorPage,height,minimumTouchSize);
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
        commandScreens.settings(g,screenControls,height,minimumTouchSize);
    }

    private void drawResult(Graphics g) {
        boolean won = model.outcome == GameModel.WON;
        boolean mission = model.objectiveType != 0;
        if (mission) { drawMissionResult(g); return; }
        boolean chapterWon = won && !mission && (model.levelIndex+1)%Campaign.SECTORS_PER_CHAPTER == 0;
        boolean campaignWon = won && !mission && model.levelIndex == GameModel.LEVELS.length-1;
        float y = modal(g, 535);
        g.text(mission ? won ? "Objective Complete" : "Objective Failed" : won ? campaignWon ? "Campaign Complete" : chapterWon ? "Chapter Secured" : "Sector Secured" : "Sector Lost",210,y+48,25,WHITE,true,1);
        if (won) for (int i = 0; i < 3; i++) star(g, 173+i*37, y+84, 13, i < model.stars() ? COLORS[2] : BORDER);
        else paragraph(g,resultReason(),210,y+82,316,13,COLORS[1],true,1);
        if (chapterWon) g.text(campaignWon ? "THE FRONTIER IS UNITED" : Campaign.chapter(model.levelIndex+1).name+" unlocked",210,y+111,11,COLORS[0],true,1);
        else if (won) for (boolean surrendered : model.resigned) if (surrendered) { g.text("RIVAL SURRENDER",210,y+111,11,COLORS[2],true,1); break; }
        g.text(won ? "SCORE" : "ARMY DEPLOYED", 210, y+130, 11, MUTED, true, 1);
        g.text(Integer.toString(won ? model.score() : model.unitsSent), 210, y+175, 42, won ? COLORS[0] : COLORS[1], true, 1);
        g.text("TIME / 3-STAR TARGET",210,y+212,10,MUTED,true,1);
        g.text(time(model.elapsed)+" / "+time(model.level().parSeconds),210,y+238,21,WHITE,true,1);
        float bestTime = model.rulesVersion == 0 ? profile.times[model.levelIndex] : profile.progress.campaignTime(model.levelIndex,model.difficulty,model.rulesVersion);
        g.text(model.rulesVersion == 0 ? "LEGACY / DIFFICULTY UNKNOWN" : GameModel.DIFFICULTIES[model.difficulty].toUpperCase()+" / PB "+(bestTime == 0 ? "--:--" : time(bestTime))+" / "+(won && improvedTime ? "TIME IMPROVED" : "NO FASTER TIME"),210,y+266,11,COLORS[0],true,1);
        if (model.rulesVersion == 10) g.text("Historical record",210,y+286,10,MUTED,true,1);
        if (won && improvedScore && model.rulesVersion != 10) g.text("NEW BEST SCORE",210,y+288,10,COLORS[2],true,1);
        String insight = !model.historyKnown ? "" : model.startingKingLost ? "Your starting king was lost during this attempt." : model.intercepted > 0 ? model.intercepted+" troops canceled in crossing attacks." : model.cappedReinforcements > 0 ? model.cappedReinforcements+" reinforcements were lost at troop caps." : model.captures+" territories captured this attempt.";
        g.text(insight,210,y+309,11,MUTED,false,1);
        String primary = mission ? model.dailyDate.isEmpty() ? "challenges" : "daily" : won && model.levelIndex < GameModel.LEVELS.length-1 ? "next" : won ? "sectors" : "restart";
        button(g,primary,mission ? "Missions" : primary.equals("next") ? "Next Sector" : won ? "All Sectors" : "Try Again",52,y+324,316,48,true,true);
        button(g,"restart",mission ? "Retry Mission" : "Replay Sector",52,y+382,316,48,false,true);
        button(g,"menu","Main Menu",52,y+440,316,48,false,true);
    }

    private String resultReason() {
        ObjectiveResult result = ObjectiveResult.evaluate(model);
        return tr(result.code,(Object[])result.numericArguments());
    }
    private String missionRecordText(Progress.MissionRecord record) {
        if (!record.completed) return record.historicalCompleted ? tr("record.historical_completed") : tr("record.unplayed");
        if (record.policy == ObjectiveResult.Policy.ELAPSED_TIME) return tr("record.best_elapsed",time(record.bestElapsed));
        if (record.policy == ObjectiveResult.Policy.DEPLOYED_TROOPS) return tr("record.fewest_troops",record.bestUnits,time(record.bestElapsed));
        if (record.policy == ObjectiveResult.Policy.COMPLETION_ONLY) return tr("record.completed");
        return tr("record.historical");
    }
    private void drawMissionResult(Graphics g) {
        boolean won = model.outcome == GameModel.WON;
        float y = modal(g,550);
        g.text(won ? "Objective Complete" : "Objective Failed",210,y+43,25,WHITE,true,1);
        paragraph(g,resultReason(),210,y+84,316,13,won ? COLORS[0] : COLORS[1],true,1);
        Progress.MissionRecord record = profile.progress.missionRecord(model);
        if (model.objectiveType == Challenge.BUDGET) {
            g.text(tr("result.deployed_label"),210,y+147,11,MUTED,true,1);
            g.text(model.unitsSent+" / "+model.deploymentBudget,210,y+190,31,WHITE,true,1);
            g.text(tr("result.budget_remaining",Math.max(0,model.deploymentBudget-model.unitsSent)),210,y+220,13,MUTED,false,1);
        } else {
            g.text(tr(model.objectiveType == Challenge.KEEP_KING ? "result.survival_label" : "result.hold_label"),210,y+147,11,MUTED,true,1);
            g.text(time(model.objectiveType == Challenge.KEEP_KING ? model.elapsed : model.objectiveProgress)+" / "+time(model.objectiveSeconds),210,y+190,31,WHITE,true,1);
            g.text(tr("result.elapsed",time(model.elapsed)),210,y+220,13,MUTED,false,1);
        }
        paragraph(g,missionRecordText(record),210,y+254,316,12,COLORS[2],true,1);
        if (won && (improvedTime || improvedScore)) g.text(tr("record.improved"),210,y+286,11,COLORS[0],true,1);
        g.text(tr("result.battle_stats",model.captures,model.unitsLost),210,y+314,11,MUTED,false,1);
        button(g,model.dailyDate.isEmpty() ? "challenges" : "daily","Missions",52,y+337,316,48,true,true);
        button(g,"restart","Retry Mission",52,y+395,316,48,false,true);
        button(g,"menu","Main Menu",52,y+453,316,48,false,true);
    }

    private void button(Graphics g, String id, String label, float x, float y, float w, float h, boolean active, boolean command) {
        buttons.add(new Button(id, label, x, y, w, h));
        g.rect(x, y, w, h, 6, active ? COLORS[0] : command ? 0xFF353C3D : BACKGROUND);
        String translated = Localization.translate(profile.language,label);
        float size = command ? 15 : 13;
        if (g.measureText(translated,size,true) > w-16)
            paragraph(g,translated,x+w/2,y+h/2-3,w-16,size,active ? BACKGROUND : WHITE,true,1);
        else g.text(translated,x+w/2,y+h/2+5,size,active ? BACKGROUND : WHITE,true,1);
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
        screenDrag = managedScreen() && commandScreens.inContent(y);
        screenScrolling = false; screenDownY = y; pointerX = x; pointerY = y;
        for (Button button : buttons) if (button.contains(x,y)) { pressed = button; return; }
        if (overlay == TUTORIAL && (tutorialStep == 1 && !practiceDone || tutorialStep == 2) && Math.hypot(x-110,y-tutorialY()) <= 66) {
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

    private boolean activeBattle() { return hasBattle && model.outcome == GameModel.PLAYING; }
    private boolean unfinishedRun() { return profile.run != null && !profile.run.terminal(); }
    private void openRun() {
        cancel();
        overlay = profile.run == null ? RUN_HOME : profile.run.status == RunState.COUNCIL ? RUN_COUNCIL
            : profile.run.terminal() ? RUN_SUMMARY : RUN_HOME;
    }
    private void drawRun(final Graphics g) {
        renderedCouncilNonce = profile.run == null ? 0 : profile.run.councilNonce;
        final boolean unavailable = profile.run != null && profile.run.status == RunState.BATTLE
            && (!hasBattle || !profile.run.matchesActiveBattle(model));
        RunScreens.draw(g,height,profile.language,profile.run,(id,label,x,y,w,h,primary) -> {
            if (!unavailable || !id.equals("continue_run")) button(g,id,label,x,y,w,h,primary,true);
        });
        if (unavailable) paragraph(g,tr("run.recovery"),22,height-190,376,12,COLORS[1],true,0);
    }
    private void drawLogistics(final Graphics g) {
        LogisticsScreens.Controls controls = (id,label,x,y,w,h,primary) -> {
            if (id.startsWith("logistics_map_")) {
                buttons.add(new Button(id,tr("logistics.map."+id.substring(14)),x,y,w,h));
                g.rect(x,y,w,h,4,COLORS[0]);
                float cx = x+w/2, cy = y+h/2;
                g.line(cx-8,cy,cx+8,cy,2,BACKGROUND);
                g.line(cx+2,cy-6,cx+8,cy,2,BACKGROUND);
                g.line(cx+2,cy+6,cx+8,cy,2,BACKGROUND);
            } else button(g,id,label,x,y,w,h,primary,true);
        };
        if (overlay == LOGISTICS_RESULT) LogisticsScreens.drawResult(g,height,profile.language,model,profile.logisticsRecords,controls);
        else LogisticsScreens.drawHome(g,height,profile.language,profile.difficulty,profile.logisticsRecords,
            activeBattle() && model.battleMode == GameModel.MODE_LOGISTICS,controls);
    }
    private String attemptName() {
        if (unfinishedRun()) return tr("run.progress",profile.run.battlesCleared(),5);
        if (model.battleMode == GameModel.MODE_LOGISTICS) return tr("logistics.map."+Logistics.getIndex(model));
        return model.dailyDate.isEmpty() ? model.objectiveType == 0 ? "Sector "+(model.levelIndex+1) : "Challenge / Sector "+(model.levelIndex+1) : "Daily "+model.dailyDate;
    }
    private void log(String event,String detail) { profile.log.add(events.now(),event,hasBattle ? model : null,detail); }
    private static int feedbackPriority(int type) {
        return type == GameModel.HOME_KING_LOSS_EVENT ? 6 : type == GameModel.KING_GAIN_EVENT || type == GameModel.KING_LOSS_EVENT ? 5 : type == GameModel.ROUTE_INTERRUPTED_EVENT || type == GameModel.CAP_LOSS_EVENT ? 4 : type == GameModel.INTERCEPT_EVENT ? 3 : 1;
    }
    private String feedbackText() {
        if (feedbackType == GameModel.ROUTE_INTERRUPTED_EVENT) return tr("logistics.route_stopped",feedbackTile+1);
        if (feedbackType == GameModel.HOME_KING_LOSS_EVENT) return "Starting king lost / enemy-king bonus unaffected.";
        if (feedbackType == GameModel.KING_GAIN_EVENT) return "Enemy king secured / team growth updated.";
        if (feedbackType == GameModel.KING_LOSS_EVENT) return "Held enemy king lost / team growth reduced.";
        if (feedbackType == GameModel.CAP_LOSS_EVENT) return feedbackUnits+" reinforcement troops lost to the cap.";
        if (feedbackType == GameModel.INTERCEPT_EVENT) return feedbackUnits+" troops canceled in interception.";
        return "Capture complete / "+feedbackUnits+" troops survived.";
    }
    private String objectiveLabel() {
        if (model.battleMode == GameModel.MODE_LOGISTICS) return tr("logistics.experimental")+" / "+tr("logistics.title");
        if (model.objectiveType == Challenge.HOLD_KING) return "HOLD MARKED KING  "+time(model.objectiveProgress)+" / "+time(model.objectiveSeconds);
        if (model.objectiveType == Challenge.KEEP_KING) return "KEEP STARTING KING  "+time(model.elapsed)+" / "+time(model.objectiveSeconds);
        if (model.objectiveType == Challenge.BUDGET) return tr("budget.hud",model.unitsSent,model.deploymentBudget,Math.max(0,model.deploymentBudget-model.unitsSent));
        return model.levelIndex == 0 ? "Capture neutral space. Keep troops at home." : model.levelIndex == 1 ? "Reinforce your front; 100% leaves no defenders." :
            model.levelIndex == 2 ? "Hold enemy kings to boost your whole team." : "Eliminate every rival territory and surviving army.";
    }
    private void drawBriefing(Graphics g) {
        float y = (height-550)/2;
        int difficulty = previewMode == 2 ? 1 : profile.difficulty;
        Challenge challenge = previewMode == 0 ? null : Challenge.PRESETS[previewChallenge];
        int sector = challenge == null ? previewSector : challenge.sector;
        g.text(previewMode == 2 ? "Daily Mission" : challenge == null ? "New Attempt" : challenge.name,30,y+35,26,WHITE,true,0);
        g.text(GameModel.LEVELS[sector].name,30,y+66,18,COLORS[0],true,0);
        g.text(GameModel.DIFFICULTIES[difficulty]+" / "+(previewMode == 2 ? previewDate+" / UTC" : "Sector "+(sector+1)),30,y+92,12,MUTED,false,0);
        if (challenge == null) {
            g.text("3 STARS <= "+time(GameModel.LEVELS[sector].parSeconds)+" / 2 STARS <= "+time(GameModel.LEVELS[sector].parSeconds*1.6f),30,y+122,12,COLORS[2],true,0);
            float bestTime = profile.progress.campaignTime(sector,difficulty,GameModel.RULES_VERSION);
            g.text("PERSONAL BEST  "+(bestTime > 0 ? time(bestTime) : "Unplayed"),30,y+146,12,WHITE,false,0);
        } else {
            GameModel reference = challenge.create(difficulty,previewMode == 2 ? Challenge.dailySeed(previewDate) : 0,previewChallenge,previewMode == 2 ? previewDate : "");
            paragraph(g,missionRecordText(profile.progress.missionRecord(reference)),30,y+130,360,12,COLORS[2],true,0);
        }
        if (challenge == null && profile.best[sector] > 0) g.text("LEGACY  "+profile.best[sector]+" score / difficulty unknown",30,y+168,11,MUTED,false,0);
        paragraph(g,challenge == null ? "Eliminate rivals. Any tile can be targeted." : challenge.objective(),30,y+183,360,12,WHITE,true,0);
        paragraph(g,challenge == null ? "Gaps do not block troop travel." : challenge.type == Challenge.HOLD_KING ? "Losing the marked king resets the hold timer." : challenge.type == Challenge.KEEP_KING ? "Any loss of your starting king fails this mission." : "Exceeding the troop budget fails this mission.",30,y+221,360,11,MUTED,false,0);
        for (int owner = 1; owner <= GameModel.LEVELS[sector].opponents; owner++) {
            int faction = GameModel.LEVELS[sector].faction(owner);
            g.text(Campaign.SHORT_NAMES[faction]+" / "+GameModel.styleName(faction == 1 || faction == 3 ? GameModel.PRESSURE : GameModel.GUARDIAN),30,y+245+(owner-1)*19,12,COLORS[faction],true,0);
        }
        paragraph(g,challenge != null && challenge.type == Challenge.KEEP_KING && difficulty > 0 && (sector == 0 || sector == 8)
            ? tr("brief.defense_pressure") : "Pressure targets crowns; Guardians keep larger reserves.",30,y+344,360,11,MUTED,false,0);
        button(g,"begin_attempt","Start Attempt",52,y+369,316,46,true,true);
        button(g,"brief_back","Back",52,y+426,152,44,false,true);
        button(g,"help","Rules",216,y+426,152,44,false,true);
    }
    private void drawConfirmation(Graphics g) {
        float y = modal(g,300);
        g.text("Replace Battle?",52,y+48,24,WHITE,true,0);
        g.text(attemptName()+" / "+time(model.elapsed),52,y+81,13,COLORS[2],true,0);
        paragraph(g,unfinishedRun() ? tr(confirmationReason.equals("run_abandon") ? "run.abandon_confirm" : confirmationReason.equals("run_restart") ? "run.restart_confirm" : "run.replace_confirm") : "The unfinished attempt will be discarded.",52,y+111,316,12,WHITE,false,0);
        g.text("Scores and completed sectors are kept.",52,y+153,12,MUTED,false,0);
        button(g,"confirm_replace",confirmationReason.equals("restart") ? "Restart" : "Replace",52,y+169,316,46,true,true);
        button(g,"cancel_replace","Keep Battle",52,y+226,316,46,false,true);
    }
    private void drawChallenges(Graphics g) {
        g.text("Challenges",22,55,27,WHITE,true,0);
        String[] names = {"Hold King","Home Guard","Troop Budget"};
        for (int i = 0; i < 3; i++) button(g,"mission_tab_"+i,names[i],22+i*127,85,122,40,challengePage == i,false);
        for (int row = 0; row < 3; row++) {
            int id = challengePage*3+row; Challenge challenge = Challenge.PRESETS[id]; float y = 159+row*100;
            buttons.add(new Button("challenge_"+id,challenge.name,22,y-22,376,89));
            g.text(challenge.name,22,y,19,WHITE,true,0);
            g.text(challenge.objective(),22,y+24,11,MUTED,false,0);
            GameModel reference = challenge.create(profile.difficulty,0,id,"");
            g.text(missionRecordText(profile.progress.missionRecord(reference)),22,y+47,11,COLORS[2],false,0);
            g.line(22,y+65,398,y+65,1,BORDER);
        }
        paragraph(g,"Choose an objective. Improve your strategy on each attempt.",22,height-151,376,11,MUTED,false,0);
        for (int i = 0; i < 3; i++) button(g,"difficulty_"+i,GameModel.DIFFICULTIES[i],22+i*127,height-120,122,38,profile.difficulty == i,false);
        button(g,"home","Main Menu",52,height-69,316,44,false,true);
    }
    private void drawDaily(Graphics g) {
        String date = Challenge.date(events.now()); int id = Challenge.dailyId(date);
        g.text("Daily Mission",22,55,27,WHITE,true,0);
        g.text(date+" / UTC",22,87,12,COLORS[2],true,0);
        g.text(Challenge.PRESETS[id].name,22,139,23,WHITE,true,0);
        g.text(Challenge.PRESETS[id].objective(),22,178,11,MUTED,false,0);
        g.text("NORMAL / fixed difficulty",22,218,13,WHITE,true,0);
        GameModel reference = Challenge.PRESETS[id].create(1,Challenge.dailySeed(date),id,date);
        paragraph(g,missionRecordText(profile.progress.missionRecord(reference)),22,258,376,13,WHITE,false,0);
        long remaining = Challenge.untilReset(events.now())/1000;
        String countdown = String.format(java.util.Locale.US,"%02d:%02d:%02d",remaining/3600,remaining%3600/60,remaining%60);
        g.text("Resets 00:00 UTC / in "+countdown,22,331,12,COLORS[2],true,0);
        if (!model.dailyDate.isEmpty() && activeBattle()) g.text("Saved daily: "+model.dailyDate,22,366,12,WHITE,false,0);
        button(g,"today_mission","Today's Mission",52,height-190,316,46,true,true);
        if (!model.dailyDate.isEmpty() && activeBattle()) button(g,"resume","Continue Saved Daily",52,height-132,316,46,false,true);
        button(g,"home","Main Menu",52,height-69,316,44,false,true);
    }
    private void drawMastery(Graphics g) {
        g.text("Mastery",22,55,27,WHITE,true,0);
        String[] names = {"Crown Keeper","Normal Chapter","Objective Specialist"};
        String[] detail = {"Win a new attempt without losing your starting king.","Win all six Border Sparks sectors on Normal.","Win all three objective types."};
        boolean[] earned = {profile.progress.crownKeeper,profile.progress.chapterNormal,profile.progress.objectiveMaster};
        String[] counts = {earned[0] ? "Earned" : "0 / 1",profile.progress.chapterProgress()+" / 6",profile.progress.objectiveProgress()+" / 3"};
        for (int i = 0; i < 3; i++) {
            float y = 126+i*100;
            star(g,34,y-6,10,earned[i] ? COLORS[2] : BORDER);
            g.text(names[i],56,y,18,WHITE,true,0); g.text(counts[i],398,y,12,earned[i] ? COLORS[2] : MUTED,true,2);
            paragraph(g,detail[i],22,y+28,376,11,MUTED,false,0); g.line(22,y+64,398,y+64,1,BORDER);
        }
        g.text("COSMETIC THEMES",22,height-184,12,MUTED,true,0);
        String[] themes = {"Classic","Signal","Blueprint"};
        for (int i = 0; i < 3; i++) {
            if (profile.progress.themeUnlocked(i)) button(g,"theme_"+i,themes[i],22+i*127,height-165,122,42,profile.progress.theme == i,false);
            else { g.rect(22+i*127,height-165,122,42,4,PANEL); g.text(themes[i]+" / Locked",83+i*127,height-139,11,MUTED,true,1); }
        }
        button(g,"home","Main Menu",52,height-69,316,44,false,true);
    }
    private void drawHelp(Graphics g) {
        g.text("Field Manual",22,55,27,WHITE,true,0);
        if (helpReturn == LOGISTICS || helpReturn == LOGISTICS_RESULT
            || helpReturn == PAUSE && model.battleMode == GameModel.MODE_LOGISTICS) {
            String[] routedRules = {tr("logistics.rules"),tr("logistics.reinforce_rule"),tr("logistics.attack_rule"),
                tr("logistics.transit_rule"),tr("logistics.no_attrition"),"Hostile armies meeting in flight cancel one-for-one.",
                "Reinforcements beyond 100 / 125 are discarded.",tr("logistics.separate_records")};
            for (int i = 0; i < routedRules.length; i++) paragraph(g,routedRules[i],22,108+i*48,376,11,i%2 == 0 ? WHITE : MUTED,false,0);
            button(g,"help_back","Back",52,height-69,316,44,false,true); return;
        }
        String[] lines = {"Any tile can be targeted. Gaps do not block travel.","Reinforcements beyond 100 / 125 are discarded.","Enemy kings held: 1 x1.2 / 2 x1.44 / 3 x1.728.","Four enemy kings: x3. Each additional king: x1.2.","Your original king never counts toward that boost.","Hostile armies meeting in flight cancel one-for-one.","Surrender: >90% control for ten active seconds,", "and rivals cannot recapture any exposed tile.","3 stars: at/below par. 2 stars: at/below 1.6x par.","Score: 1000 + 50 per capture + time bonus", "(up to 1200, minus 6 per second) + 250 per difficulty.","Legacy records have unknown historical difficulty."};
        for (int i = 0; i < lines.length; i++) paragraph(g,lines[i],22,108+i*33,376,11,i%2 == 0 ? WHITE : MUTED,false,0);
        button(g,"help_back","Back",52,height-69,316,44,false,true);
    }
    private void drawCameraHelp(Graphics g) {
        float y = modal(g,300);
        g.text("Larger Battlefield",52,y+47,23,WHITE,true,0);
        g.text("Pinch to zoom; use two fingers to pan.",52,y+91,13,WHITE,false,0);
        g.text("One finger on your tile deploys troops.",52,y+121,12,MUTED,false,0);
        g.text("When zoomed, drag other space to pan.",52,y+151,12,MUTED,false,0);
        g.text("The fit icon restores the full board.",52,y+181,12,MUTED,false,0);
        button(g,"camera_ready","Continue",52,y+220,316,46,true,true);
    }
    private static void factionMark(Graphics g,float x,float y,float r,int faction,int color) {
        if (faction == 0) g.circle(x,y,r,color);
        else if (faction == 1) { g.line(x-r,y+r*.5f,x,y-r*.5f,1.5f,color); g.line(x,y-r*.5f,x+r,y+r*.5f,1.5f,color); }
        else if (faction == 2) g.polygon(new float[] {x,y-r,x+r,y,x,y+r,x-r,y},0,color,1.3f);
        else if (faction == 3) { g.circle(x,y,r,color); g.circle(x+r*.5f,y-r*.3f,r*.8f,BACKGROUND); }
        else if (faction == 4) g.polygon(new float[] {x-r,y-r,x+r,y-r,x+r,y,x,y+r,x-r,y},0,color,1.3f);
        else { g.line(x-r,y,x+r,y,1.4f,color); g.line(x,y-r,x,y+r,1.4f,color); g.line(x-r*.7f,y-r*.7f,x+r*.7f,y+r*.7f,1.3f,color); }
    }

    public void move(float x, float y) {
        if (screenDrag && (screenScrolling || Math.abs(y-screenDownY) > 8)) {
            screenScrolling = true; pressed = null; commandScreens.scroll(pointerY-y);
        }
        if (panning) { panX += x-pointerX; panY += y-pointerY; layoutBoard(); }
        pointerX = x; pointerY = y;
    }

    public void up(float x, float y) {
        if (screenScrolling) { cancel(); return; }
        screenDrag = false;
        if (pressed != null) {
            Button button = pressed; pressed = null;
            if (button.contains(x,y)) command(button.id);
        } else if (overlay == SPLASH) overlay = MENU;
        else if (practiceDragging && overlay == TUTORIAL) {
            if (Math.hypot(x-310,y-tutorialY()) <= 66) {
                if (tutorialStep == 1) practiceDone = true; else reinforcementDone = true;
                events.cue(1); log("tutorial_practice",tutorialStep == 1 ? "capture" : "reinforcement");
            }
        } else if (selected >= 0 && overlay == NONE) {
            int target = territoryAt(x,y);
            if (target >= 0 && model.territories.get(selected).owner == GameModel.PLAYER) {
                if (model.battleMode == GameModel.MODE_LOGISTICS && target != selected && model.route(selected,target,0) == null) routeRefusalTimer = 2.5f;
                if (model.launch(selected,target,fraction) > 0) events.cue(0);
            }
        }
        selected = -1; practiceDragging = false; panning = false;
    }

    public void cancel() { selected = -1; pressed = null; practiceDragging = false; panning = false; screenDrag = screenScrolling = false; }
    private boolean managedScreen() { return overlay == MENU || overlay == SECTORS || overlay == SETTINGS; }
    public boolean canScrollScreen() { return managedScreen() && commandScreens.scrollable(); }
    public boolean scrollScreen(float delta) { if (!managedScreen() || !Float.isFinite(delta)) return false; cancel(); return commandScreens.scroll(delta); }
    public String pressedLabel() { return pressed == null ? null : Localization.translate(profile.language,pressed.label); }

    public void back() {
        cancel();
        if (overlay == NONE) pause();
        else if (overlay == PAUSE) { overlay = NONE; log("resume","back"); }
        else if (overlay == SETTINGS && commandScreens.languages) commandScreens.languages = false;
        else if (overlay == SETTINGS) overlay = settingsReturn;
        else if (overlay == SECTORS) overlay = previousOverlay;
        else if (overlay == CONFIRM) cancelReplacement();
        else if (overlay == HELP) overlay = helpReturn;
        else if (overlay == BRIEFING) overlay = previewMode == 0 ? previewReturn : previewMode == 1 ? CHALLENGES : DAILY;
        else if (overlay == CHALLENGES || overlay == DAILY || overlay == MASTERY
            || overlay == RUN_HOME || overlay == RUN_COUNCIL || overlay == RUN_SUMMARY || overlay == LOGISTICS || overlay == LOGISTICS_RESULT) overlay = MENU;
        else if (overlay == CAMERA_HELP) overlay = PAUSE;
        else if (overlay == SPLASH || overlay == RESULT) overlay = MENU;
        else if (overlay == TUTORIAL) {
            if (tutorialStep > 0) { tutorialStep--; log("tutorial_step","step="+(tutorialStep+1)); }
            else { tutorialStartsBattle = false; overlay = MENU; }
        }
        events.changed();
    }

    public void pause() { cancel(); if (overlay == NONE) { overlay = PAUSE; log("pause",""); } }

    private void command(String id) {
        int before = overlay;
        events.cue(0);
        if (id.equals("pause")) pause();
        else if (id.equals("resume")) { overlay = NONE; log("resume",""); }
        else if (id.equals("menu") || id.equals("home")) { cancel(); overlay = MENU; }
        else if (id.equals("run")) openRun();
        else if (id.equals("logistics")) overlay = LOGISTICS;
        else if (id.startsWith("logistics_map_")) {
            int map = Integer.parseInt(id.substring(14));
            requestReplacement(() -> installAttempt(Logistics.create(map,profile.difficulty,System.nanoTime())),"new_logistics");
        }
        else if (id.equals("continue_logistics") && activeBattle() && model.battleMode == GameModel.MODE_LOGISTICS) overlay = NONE;
        else if (id.equals("logistics_retry")) requestReplacement(this::restartAttempt,"restart");
        else if (id.equals("new_run")) requestReplacement(() -> {
            profile.run = RunState.newRun(System.nanoTime()); hasBattle = false; openRun();
        },"new_run");
        else if (id.equals("begin_run_battle") && profile.run != null && profile.run.status == RunState.READY) {
            profile.run = profile.run.beginBattle(); installAttempt(profile.run.createBattle());
        }
        else if (id.equals("continue_run") && profile.run != null && profile.run.matchesActiveBattle(model) && hasBattle) overlay = NONE;
        else if (id.startsWith("perk_") && overlay == RUN_COUNCIL && profile.run != null) {
            try { profile.run = profile.run.choosePerk(renderedCouncilNonce,Integer.parseInt(id.substring(5))); openRun(); }
            catch (IllegalArgumentException | IllegalStateException stale) { /* The visible offer was superseded. */ }
        }
        else if (id.equals("run_retry") && profile.run != null && profile.run.status == RunState.RETRY_AVAILABLE) {
            RunState.NodeOutcome[] outcomes = profile.run.outcomes();
            profile.run = profile.run.retry(outcomes[outcomes.length-1].battle.nonce);
            installAttempt(profile.run.createBattle());
        }
        else if (id.equals("run_end") && profile.run != null && profile.run.status == RunState.RETRY_AVAILABLE) {
            profile.run = profile.run.abandon(true,profile.run.revision); profile.lastRun = profile.run; openRun();
        }
        else if (id.equals("run_abandon") && unfinishedRun()) requestReplacement(() -> {
            profile.run = profile.run.abandon(true,profile.run.revision); profile.lastRun = profile.run;
            hasBattle = false; openRun();
        },"run_abandon");
        else if (id.equals("play")) {
            previewMode = 0; previewSector = profile.selectedSector; previewReturn = overlay; overlay = BRIEFING;
        }
        else if (id.equals("begin_attempt")) requestReplacement(this::beginPreview,"new_attempt");
        else if (id.equals("confirm_replace") && overlay == CONFIRM && confirmedAction != null) {
            Runnable action = confirmedAction; confirmedAction = null;
            log(confirmationReason+"_confirm",""); log("abandon",confirmationReason);
            if (action != null) action.run();
        }
        else if (id.equals("cancel_replace")) cancelReplacement();
        else if (id.equals("brief_back")) back();
        else if (id.equals("challenges") || id.equals("missions")) overlay = CHALLENGES;
        else if (id.equals("daily")) overlay = DAILY;
        else if (id.equals("mastery")) overlay = MASTERY;
        else if (id.equals("help")) { helpReturn = overlay; overlay = HELP; }
        else if (id.equals("help_back")) overlay = helpReturn;
        else if (id.equals("camera_ready")) { profile.cameraGuideSeen = true; overlay = NONE; }
        else if (id.equals("today_mission")) {
            previewMode = 2; previewDate = Challenge.date(events.now()); previewChallenge = Challenge.dailyId(previewDate);
            previewSector = Challenge.PRESETS[previewChallenge].sector; overlay = BRIEFING;
        }
        else if (id.startsWith("challenge_")) {
            previewMode = 1; previewChallenge = Integer.parseInt(id.substring(10));
            previewSector = Challenge.PRESETS[previewChallenge].sector; overlay = BRIEFING;
        }
        else if (id.startsWith("mission_tab_")) challengePage = Integer.parseInt(id.substring(12));
        else if (id.startsWith("theme_")) {
            int theme = Integer.parseInt(id.substring(6));
            if (profile.progress.themeUnlocked(theme)) profile.progress.theme = theme;
        }
        else if (id.equals("tutorial")) openTutorial(false);
        else if (id.equals("tutorial_prev")) { tutorialStep = Math.max(0,tutorialStep-1); log("tutorial_step","step="+(tutorialStep+1)); }
        else if (id.equals("demo_quarter")) { tutorialFraction = .25; fractionVisits |= 1; }
        else if (id.equals("demo_half")) { tutorialFraction = .5; fractionVisits |= 2; }
        else if (id.equals("demo_all")) { tutorialFraction = 1; fractionVisits |= 4; }
        else if (id.equals("demo_capture")) { tutorialKings = Math.min(5,tutorialKings+1); kingPracticeGain = true; }
        else if (id.equals("demo_lose")) {
            if (tutorialKings > 0) { tutorialKings--; kingPracticeLoss = true; }
        }
        else if (id.equals("tutorial_next") && overlay == TUTORIAL && !tutorialTerminal) {
            if (tutorialStep == 4) finishTutorial(true);
            else if (tutorialReady()) { tutorialStep++; log("tutorial_step","step="+(tutorialStep+1)); }
        }
        else if (id.equals("tutorial_skip") && overlay == TUTORIAL && !tutorialTerminal) finishTutorial(false);
        else if (id.equals("restart")) {
            if (model.battleMode == GameModel.MODE_RUN && unfinishedRun()) requestReplacement(() -> {
                model.surrender(); resultRecorded = false; overlay = NONE; update(0);
            },"run_restart");
            else { log("retry",""); requestReplacement(this::restartAttempt,"restart"); }
        }
        else if (id.equals("next")) {
            log("next_sector",""); previewMode = 0; previewSector = Math.min(GameModel.LEVELS.length-1,model.levelIndex+1);
            profile.selectedSector = previewSector; overlay = BRIEFING;
        }
        else if (id.equals("settings")) {
            settingsReturn = overlay; commandScreens.languages = false; commandScreens.tools = false;
            commandScreens.reset(SETTINGS); overlay = SETTINGS;
        } else if (id.equals("sectors")) {
            previousOverlay = overlay; sectorPage = Campaign.chapterIndex(profile.selectedSector); overlay = SECTORS;
        } else if (id.equals("back")) back();
        else if (id.equals("language_picker")) { commandScreens.languages = !commandScreens.languages; buttons.clear(); }
        else if (id.equals("tools")) { commandScreens.tools = !commandScreens.tools; buttons.clear(); }
        else if (id.equals("chapter_prev")) sectorPage = Math.max(0,sectorPage-1);
        else if (id.equals("chapter_next")) sectorPage = Math.min(Campaign.CHAPTERS.length-1,sectorPage+1);
        else if (id.equals("quarter")) fraction = .25;
        else if (id.equals("half")) fraction = .5;
        else if (id.equals("all")) fraction = 1;
        else if (id.equals("zoom_in")) cameraGesture(210,(boardTop()+boardBottom())/2,1.25f,0,0);
        else if (id.equals("zoom_out")) cameraGesture(210,(boardTop()+boardBottom())/2,.8f,0,0);
        else if (id.equals("fit_board")) { zoom = 1; panX = panY = 0; layoutBoard(); }
        else if (id.equals("unlock_code")) events.unlockCodeRequested();
        else if (id.equals("sound")) profile.sound = !profile.sound;
        else if (id.equals("music")) profile.music = !profile.music;
        else if (id.equals("haptics")) profile.haptics = !profile.haptics;
        else if (id.equals("playtest_log")) profile.log.enabled = !profile.log.enabled;
        else if (id.equals("export_log")) events.exportPlaytestRequested();
        else if (id.equals("clear_log")) profile.log.clear();
        else if (id.startsWith("language_") && overlay == SETTINGS) {
            String language = id.substring(9);
            if (language.equals("en") || language.equals("id") || language.equals("hi")) profile.language = language;
            commandScreens.languages = false;
            buttons.clear();
        }
        else if (id.startsWith("level_")) {
            int level = Integer.parseInt(id.substring(6));
            if (overlay == SECTORS && level >= 0 && level < GameModel.LEVELS.length && level <= profile.unlocked) { profile.selectedSector = level; buttons.clear(); }
        } else if (id.startsWith("difficulty_")) {
            profile.difficulty = Integer.parseInt(id.substring(11));
        }
        if (before != overlay) buttons.clear();
        events.changed();
    }

    public boolean redeemUnlockCode(String code) {
        if (overlay != SETTINGS || !"12345".equals(code)) return false;
        profile.unlocked = GameModel.LEVELS.length-1;
        events.changed();
        return true;
    }

    public void start(int level) {
        installAttempt(new GameModel(level,profile.difficulty,System.nanoTime()));
        profile.selectedSector = level;
    }

    private void installAttempt(GameModel next) {
        model = next; model.aiVersion = 1; hasBattle = true;
        resultRecorded = false; improvedTime = improvedScore = false;
        kingBanner = resultDelay = feedbackTimer = 0; feedbackType = feedbackUnits = 0;
        routeRefusalTimer = 0;
        zoom = 1; panX = panY = 0; overlay = NONE; cancel(); log("attempt_start","");
    }

    private void requestReplacement(Runnable action,String reason) {
        if (!activeBattle() && !unfinishedRun()) { action.run(); return; }
        log(reason+"_request",""); cancel();
        confirmationReturn = overlay == NONE ? PAUSE : overlay;
        confirmationReason = reason;
        final RunState protectedRun = unfinishedRun() ? profile.run : null;
        confirmedAction = () -> {
            if (protectedRun != null && (profile.run == null || !profile.run.id.equals(protectedRun.id)
                || profile.run.revision != protectedRun.revision)) { openRun(); return; }
            if (protectedRun != null && !reason.equals("run_abandon") && !reason.equals("run_restart")) {
                profile.lastRun = protectedRun.abandon(true,protectedRun.revision); profile.run = null;
                hasBattle = false;
            }
            action.run();
        };
        overlay = CONFIRM;
    }

    private void cancelReplacement() {
        log(confirmationReason+"_cancel",""); confirmedAction = null; overlay = confirmationReturn;
    }

    private void beginPreview() {
        if (previewMode == 0) start(previewSector);
        else installAttempt(Challenge.PRESETS[previewChallenge].create(previewMode == 2 ? 1 : profile.difficulty,
            previewMode == 2 ? Challenge.dailySeed(previewDate) : System.nanoTime(),previewChallenge,previewMode == 2 ? previewDate : ""));
        if (!profile.tutorialSeen) openTutorial(true); else showCameraGuide();
    }

    private void restartAttempt() {
        GameModel old = model;
        int difficulty = old.dailyDate.isEmpty() ? profile.difficulty : old.difficulty;
        long seed = old.dailyDate.isEmpty() ? System.nanoTime() : old.seed;
        GameModel next;
        if (old.battleMode == GameModel.MODE_LOGISTICS) next = Logistics.create(Logistics.getIndex(old),difficulty,seed);
        else if (old.objectiveType != 0) {
            Challenge definition = new Challenge("Saved Mission",old.levelIndex,old.objectiveType,old.objectiveSeconds,old.deploymentBudget);
            next = definition.createVersioned(difficulty,seed,old.challengeId,old.dailyDate,
                old.missionConfigVersion == Challenge.LEGACY_CONFIG_VERSION ? Challenge.LEGACY_CONFIG_VERSION : Challenge.CONFIG_VERSION);
            next.dailyVersion = old.dailyVersion;
        } else next = new GameModel(old.levelIndex,difficulty,seed);
        installAttempt(next); showCameraGuide();
    }

    private void showCameraGuide() {
        if (model.levelIndex >= 30 && !profile.cameraGuideSeen) overlay = CAMERA_HELP;
    }

    private void openTutorial(boolean startsBattle) {
        cancel(); tutorialStep = 0; practiceDone = reinforcementDone = kingPracticeGain = kingPracticeLoss = false;
        tutorialFraction = .25; tutorialKings = 0; fractionVisits = 0;
        tutorialStartsBattle = startsBattle; overlay = TUTORIAL;
        tutorialTerminal = false;
        log("tutorial_start",startsBattle ? "first_attempt" : "replay");
    }

    private void finishTutorial(boolean completed) {
        if (tutorialTerminal) return;
        tutorialTerminal = true;
        profile.tutorialSeen = true;
        log(completed ? "tutorial_complete" : "tutorial_skip","step="+(tutorialStep+1)+";semantics=v11");
        if (tutorialStartsBattle) { overlay = NONE; showCameraGuide(); } else overlay = MENU;
        tutorialStartsBattle = false;
    }

    public boolean needsAnimation() { return overlay == NONE || overlay == SPLASH || overlay == DAILY; }

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
        return commandScreens.sectorPosition(sector);
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
