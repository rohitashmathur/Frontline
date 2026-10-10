package com.frontline.offline;

import static com.frontline.offline.GameScene.*;

/** Presentation and scrolling for the Command Deck, campaign path, and settings. */
final class CommandScreens {
    interface Controls { void add(String id,String label,float x,float y,float w,float h); }
    private final Profile profile;
    private final float[] offsets = new float[3];
    private int slot, page = -1;
    private GameModel cachedPreview;
    private static final float CAMPAIGN_TRAIL_TOP = 106, CAMPAIGN_NODE_X = 64;
    private float top, bottom, limit, touch = 64;
    boolean languages, tools;
    CommandScreens(Profile profile) { this.profile = profile; }
    private String tr(String key,Object... args) { return Localization.text(profile.language,key,args); }
    private String translated(String text) { return Localization.translate(profile.language,text); }
    private float y(float value) { return top+value-offsets[slot]; }
    boolean scroll(float delta) {
        float old = offsets[slot]; offsets[slot] = Math.max(0,Math.min(limit,old+delta));
        return old != offsets[slot];
    }
    boolean scrollable() { return limit > 0; }
    boolean inContent(float py) { return py >= top && py < bottom; }
    void reset(int overlay) { offsets[index(overlay)] = 0; }
    private static int index(int overlay) { return overlay == SECTORS ? 1 : overlay == SETTINGS ? 2 : 0; }
    private void begin(Graphics g,int overlay,float height,float minTouch,float header,float content) {
        slot = index(overlay); touch = Math.max(64,minTouch); top = header; bottom = height-40;
        limit = Math.max(0,content-(bottom-top)); offsets[slot] = Math.min(offsets[slot],limit);
        g.clip(0,top,420,bottom-top);
    }
    private void end(Graphics g,float height) {
        g.unclip();
        g.rect(0,height-40,420,40,0,BACKGROUND);
        g.line(22,height-40,398,height-40,1,BORDER);
        g.text(AppVersion.NAME,210,height-14,11,MUTED,false,1);
        if (limit > 0) {
            float length = Math.max(24,(bottom-top)*(bottom-top)/(bottom-top+limit));
            g.rect(414,top+(bottom-top-length)*offsets[slot]/limit,2,length,1,BORDER);
        }
    }
    private void hit(Controls controls,String id,String label,float x,float py,float w,float h) {
        if (py >= top-.1f && py+h <= bottom+.1f) controls.add(id,label,x,py,w,h);
    }
    private void text(Graphics g,String value,float x,float baseline,float size,float width,int color,boolean bold,int align) {
        value = translated(value);
        while (size > 11 && g.measureText(value,size,bold) > width) size -= .5f;
        if (g.measureText(value,size,bold) > width) {
            while (value.length() > 1 && g.measureText(value+"...",size,bold) > width) value = value.substring(0,value.offsetByCodePoints(value.length(),-1));
            value += "...";
        }
        g.text(value,x,baseline,size,color,bold,align);
    }
    private void action(Graphics g,Controls controls,String id,String label,float x,float py,float w,float h,boolean primary) {
        action(g,controls,id,label,label,x,py,w,h,primary);
    }
    private void action(Graphics g,Controls controls,String id,String label,String spoken,float x,float py,float w,float h,boolean primary) {
        hit(controls,id,spoken,x,py,w,h);
        if (!primary) g.rect(x,py,w,h,6,BORDER);
        g.rect(x+(primary ? 0 : 1),py+(primary ? 0 : 1),w-(primary ? 0 : 2),h-(primary ? 0 : 2),6,primary ? COLORS[0] : BACKGROUND);
        boolean playIcon = primary && (id.equals("play") || id.equals("resume"));
        if (playIcon) g.polygon(new float[] {x+19,py+h/2-7,x+19,py+h/2+7,x+31,py+h/2},BACKGROUND,0,0);
        String display = id.equals("resume") && slot == 0 ? "Continue" : label;
        text(g,display,x+w/2+(playIcon ? 8 : 0),py+h/2+5,primary ? 17 : 13,w-(playIcon ? 58 : 20),primary ? BACKGROUND : WHITE,true,1);
    }
    private void header(Graphics g,Controls controls,String title,boolean brand,boolean settings) {
        if (brand) {
            g.polygon(hex(39,42,22),COLORS[0],0,0); g.text("F",39,49,23,BACKGROUND,true,1);
            g.text("FRONTLINE",70,51,25,WHITE,true,0);
        } else {
            controls.add("back","Back",22,10,touch,touch); icon(g,"back",22+touch/2,10+touch/2,WHITE);
            text(g,title,34+touch,10+touch/2+8,25,270-touch,WHITE,true,0);
        }
        if (settings) {
            controls.add("settings","Settings",398-touch,10,touch,touch);
            g.rect(398-touch,10,touch,touch,6,PANEL); icon(g,"settings",398-touch/2,10+touch/2,WHITE);
        }
    }
    void home(Graphics g,Controls controls,GameModel model,boolean active,long now,float height,float minTouch) {
        touch = Math.max(64,minTouch); header(g,controls,"",true,true);
        float dailyY = 18+touch;
        boolean done = profile.progress.dailyCompleted(Challenge.date(now),1,Challenge.DAILY_VERSION,GameModel.RULES_VERSION,Challenge.CONFIG_VERSION);
        String dailyState = tr(done ? "deck.daily_done" : "deck.daily_available");
        String reset = dailyReset(now);
        controls.add("daily",tr("deck.daily_label",dailyState,reset),22,dailyY,376,touch);
        g.rect(22,dailyY,376,touch,8,BORDER); g.rect(23,dailyY+1,374,touch-2,7,PANEL);
        icon(g,"daily",42,dailyY+touch/2,done ? COLORS[0] : WHITE);
        text(g,"Daily Mission",65,dailyY+touch/2-5,15,290,WHITE,true,0);
        text(g,dailyState+" / "+reset,65,dailyY+touch/2+16,12,300,MUTED,false,0);
        chevron(g,382,dailyY+touch/2,MUTED);
        int columns = Math.min(5,Math.max(1,(int)(376/touch)));
        float shortcuts = ((5+columns-1)/columns)*(touch+24);
        float headerHeight = dailyY+touch+10;
        float heroExtra = Math.min(24,Math.max(0,height-40-headerHeight-424));
        float coverageY = 129+heroExtra, barY = 141+heroExtra, actionY = 157+heroExtra;
        float modeY = actionY+touch+12, modeHeight = Math.max(104,touch);
        float labY = modeY+modeHeight+12, labHeight = Math.max(74,touch);
        float shortcutY = labY+labHeight+36, progressY = shortcutY+shortcuts+20;
        float progressHeight = Math.max(80,touch);
        begin(g,MENU,height,minTouch,headerHeight,progressY+progressHeight+16);
        int sector = active ? model.levelIndex : profile.selectedSector;
        String category = active && model.battleMode == GameModel.MODE_RUN ? tr("run.title")
            : active && model.battleMode == GameModel.MODE_LOGISTICS ? tr("deck.lab")
            : active && model.objectiveType != 0 ? "Missions" : Campaign.chapter(sector).name;
        text(g,category,22,y(13),11,230,MUTED,true,0);
        if (!active || model.battleMode == GameModel.MODE_CAMPAIGN && model.objectiveType == 0)
            text(g,tr("deck.chapter",Campaign.chapterIndex(sector)+1,Campaign.CHAPTERS.length),398,y(13),11,140,MUTED,false,2);
        preview(g,active ? model : homePreview(sector,profile.difficulty),24,y(26),124,96+heroExtra);
        String title = active && model.battleMode == GameModel.MODE_LOGISTICS ? tr("logistics.map."+Logistics.getIndex(model))
            : active && model.objectiveType != 0 ? Challenge.PRESETS[Math.max(0,Math.min(Challenge.PRESETS.length-1,model.challengeId))].name : GameModel.LEVELS[sector].name;
        text(g,title,166,y(45),24,230,WHITE,true,0);
        String detail = active && model.battleMode == GameModel.MODE_RUN && profile.run != null ? tr("deck.run_battle",profile.run.node+1)
            : tr("deck.sector",sector+1,GameModel.LEVELS.length);
        text(g,detail,166,y(66),12,230,MUTED,false,0);
        text(g,GameModel.DIFFICULTIES[active ? model.difficulty : profile.difficulty],166,y(86),12,230,MUTED,false,0);
        text(g,tr(active ? "deck.in_progress" : "deck.ready"),166,y(106),12,230,COLORS[0],false,0);
        int coverage = active ? Math.round(100f*model.owned(0)/model.territories.size()) : 0;
        text(g,active ? tr("deck.coverage",coverage) : tr("deck.star_target",GameScene.time(GameModel.LEVELS[sector].parSeconds)),22,y(coverageY),13,270,WHITE,true,0);
        if (active) g.text(GameScene.time(model.elapsed),398,y(coverageY),13,MUTED,false,2);
        if (active) {
            g.rect(22,y(barY),376,8,2,BORDER);
            float x = 22;
            for (int owner = 0; owner <= model.level().opponents; owner++) {
                float w = 376f*model.owned(owner)/model.territories.size();
                if (w > 0) g.rect(x,y(barY),Math.max(0,w-1),8,0,COLORS[model.level().faction(owner)]);
                x += w;
            }
        }
        if (active) {
            action(g,controls,"resume","Continue Battle",22,y(actionY),238,touch,true);
            action(g,controls,"play","New Attempt",272,y(actionY),126,touch,false);
        } else action(g,controls,"play","Play",22,y(actionY),376,touch,true);
        boolean liveRun = profile.run != null && !profile.run.terminal();
        RunState lastRun = profile.lastRun != null ? profile.lastRun : profile.run;
        String runDetail = liveRun ? tr("deck.run_battle",profile.run.node+1)
            : lastRun != null && lastRun.terminal() ? tr("deck.run_last",lastRun.battlesCleared(),RunState.BATTLE_COUNT) : tr("deck.five_battles");
        String retryDetail = liveRun ? tr("run.retries",profile.run.retriesRemaining()) : "";
        mode(g,controls,"run",tr("deck.classic_run"),runDetail,retryDetail,22,y(modeY),182,modeHeight);
        mode(g,controls,"missions","Missions",tr("deck.objectives"),"",216,y(modeY),182,modeHeight);
        hit(controls,"logistics",tr("deck.lab"),22,y(labY),376,labHeight);
        icon(g,"logistics",39,y(labY+labHeight/2),MUTED); text(g,tr("deck.lab"),65,y(labY+22),17,290,WHITE,true,0);
        text(g,tr("deck.routed_maps",Logistics.PRESETS.length),65,y(labY+43),12,290,MUTED,false,0);
        text(g,tr("logistics.experimental"),65,y(labY+62),11,290,COLORS[2],true,0); chevron(g,385,y(labY+labHeight/2),MUTED);
        g.line(22,y(labY+labHeight+8),398,y(labY+labHeight+8),1,BORDER);
        text(g,tr("deck.more"),22,y(shortcutY-9),11,376,MUTED,true,0);
        String[] ids = {"sectors","challenges","mastery","tutorial","help"};
        String[] labels = {"Sectors","Challenges","Mastery","How to Play","Rules"};
        float width = 376f/columns;
        for (int i = 0; i < ids.length; i++) {
            float x = 22+(i%columns)*width, py = y(shortcutY+(i/columns)*(touch+24));
            hit(controls,ids[i],labels[i],x,py,width,touch+24);
            icon(g,ids[i],x+width/2,py+touch/2,i == 0 ? COLORS[0] : WHITE);
            text(g,labels[i],x+width/2,py+touch+13,11,width-6,MUTED,false,1);
        }
        campaignProgress(g,controls,progressY,progressHeight);
        end(g,height);
    }
    GameModel homePreview(int sector,int difficulty) {
        if (cachedPreview == null || cachedPreview.levelIndex != sector || cachedPreview.difficulty != difficulty)
            cachedPreview = new GameModel(sector,difficulty,0);
        return cachedPreview;
    }
    private String dailyReset(long now) {
        long remaining = Challenge.untilReset(now);
        long minutes = (remaining+59_999)/60_000;
        return minutes < 60 ? tr("deck.daily_minutes",minutes) : tr("deck.daily_resets",minutes/60,minutes%60);
    }
    private void campaignProgress(Graphics g,Controls controls,float row,float h) {
        int cleared = 0;
        for (int i = 0; i < GameModel.LEVELS.length; i++) if (profile.cleared(i)) cleared++;
        int score = profile.totalScore();
        hit(controls,"campaign_progress",tr("deck.progress_label",cleared,GameModel.LEVELS.length,score),22,y(row),376,h);
        g.line(22,y(row),398,y(row),1,BORDER);
        text(g,"Campaign",22,y(row+20),11,190,MUTED,true,0);
        text(g,tr("deck.cleared",cleared,GameModel.LEVELS.length),398,y(row+20),11,180,MUTED,false,2);
        float width = (376f-4*(Campaign.CHAPTERS.length-1))/Campaign.CHAPTERS.length;
        for (int chapter = 0; chapter < Campaign.CHAPTERS.length; chapter++) {
            int count = 0;
            for (int sector = chapter*6; sector < chapter*6+6; sector++) if (profile.cleared(sector)) count++;
            int color = COLORS[chapter == 0 ? 0 : Campaign.CHAPTERS[chapter].rulerFaction];
            float x = 22+chapter*(width+4);
            g.rect(x,y(row+32),width,8,2,mix(PANEL,color,.35f));
            if (count > 0) g.rect(x,y(row+32),width*count/6,8,2,color);
        }
        text(g,tr("deck.best_total",score),22,y(row+65),12,376,COLORS[0],true,0);
    }
    private void mode(Graphics g,Controls controls,String id,String label,String detail,String extra,float x,float py,float w,float h) {
        hit(controls,id,translated(label)+" / "+detail+(extra.isEmpty() ? "" : " / "+extra),x,py,w,h);
        g.rect(x,py,w,h,8,BORDER); g.rect(x+1,py+1,w-2,h-2,7,PANEL);
        icon(g,id,x+27,py+20,COLORS[0]); text(g,label,x+14,py+50,17,w-28,WHITE,true,0);
        text(g,detail,x+14,py+72,12,w-28,MUTED,false,0);
        if (!extra.isEmpty()) text(g,extra,x+14,py+92,12,w-28,COLORS[2],false,0);
    }
    void campaign(Graphics g,Controls controls,GameModel model,boolean active,int chapterIndex,float height,float minTouch) {
        touch = Math.max(64,minTouch); header(g,controls,"Campaign",false,true);
        Campaign.Chapter chapter = Campaign.CHAPTERS[chapterIndex];
        int first = chapterIndex*6, cleared = 0;
        for (int i = first; i < first+6; i++) if (profile.cleared(i)) cleared++;
        float header = 26+touch, footer = 40+touch+53;
        pageArrow(g,controls,"chapter_prev","Previous Chapter",22,header,chapterIndex > 0);
        pageArrow(g,controls,"chapter_next","Next Chapter",398-touch,header,chapterIndex < Campaign.CHAPTERS.length-1);
        text(g,tr("deck.chapter",chapterIndex+1,Campaign.CHAPTERS.length),210,header+touch/2+5,12,376-2*touch,MUTED,true,1);
        header += touch+8;
        bottom = height-footer; top = header; slot = 1;
        float nodeSize = Math.min(64,touch-8), row = campaignRow(), content = CAMPAIGN_TRAIL_TOP+5*row+touch;
        limit = Math.max(0,content-(bottom-top));
        boolean selectedInChapter = Campaign.chapterIndex(profile.selectedSector) == chapterIndex;
        if (page != chapterIndex) {
            page = chapterIndex;
            offsets[1] = selectedInChapter ? Math.max(0,CAMPAIGN_TRAIL_TOP+(profile.selectedSector-first)*row+touch/2-(bottom-top)/2) : 0;
        }
        offsets[1] = Math.min(offsets[1],limit); g.clip(0,top,420,bottom-top);
        text(g,"Campaign",22,y(14),11,220,MUTED,true,0);
        text(g,tr("deck.cleared",cleared,6),398,y(14),11,130,MUTED,false,2);
        text(g,chapter.name,22,y(44),26,376,WHITE,true,0);
        text(g,Campaign.FACTIONS[chapter.rulerFaction]+" / "+Campaign.RULERS[chapter.rulerFaction],22,y(67),12,376,COLORS[chapter.rulerFaction == 0 ? 1 : chapter.rulerFaction],false,0);
        text(g,chapter.firstLine,22,y(88),12,376,MUTED,false,0);
        for (int n = 0; n < 5; n++) {
            float sy = y(CAMPAIGN_TRAIL_TOP+n*row+touch/2+nodeSize/2+2);
            float ey = y(CAMPAIGN_TRAIL_TOP+(n+1)*row+touch/2-nodeSize/2-2);
            boolean open = first+n+1 <= profile.unlocked;
            if (open) g.line(CAMPAIGN_NODE_X,sy,CAMPAIGN_NODE_X,ey,2,COLORS[0]);
            else for (float dy = sy; dy < ey; dy += 10) g.line(CAMPAIGN_NODE_X,dy,CAMPAIGN_NODE_X,Math.min(ey,dy+5),2,BORDER);
        }
        for (int n = 0; n < 6; n++) {
            int sector = first+n; float x = CAMPAIGN_NODE_X, py = y(CAMPAIGN_TRAIL_TOP+n*row), cy = py+touch/2;
            boolean locked = sector > profile.unlocked, selected = sector == profile.selectedSector, done = profile.cleared(sector);
            if (selected && !locked) g.rect(22,py,376,touch,0,mix(PANEL,COLORS[0],.1f));
            g.polygon(hex(x,cy,nodeSize/2),selected && !locked ? COLORS[0] : done ? 0xFF253E36 : PANEL,locked ? BORDER : COLORS[0],selected ? 4 : 2);
            g.text(Integer.toString(sector+1),x-(locked ? 8 : 0),cy+7,23,selected && !locked ? BACKGROUND : locked ? MUTED : WHITE,true,1);
            if (locked) icon(g,"lock",x+17,cy,MUTED);
            text(g,GameModel.LEVELS[sector].name,112,cy-5,16,276,locked ? MUTED : WHITE,true,0);
            int best = profile.progress.campaignBest(sector,profile.difficulty,GameModel.RULES_VERSION);
            int historical = profile.progress.campaignBest(sector,profile.difficulty,10);
            String status = locked ? "Locked" : done ? best > 0 ? tr("deck.best",best) : profile.best[sector] > 0 ? tr("deck.legacy",profile.best[sector]) : historical > 0 ? tr("deck.historical",historical) : "Cleared"
                : active && model.battleMode == GameModel.MODE_CAMPAIGN && model.objectiveType == 0 && model.levelIndex == sector ? tr("deck.in_progress") : "Available";
            text(g,status,112,cy+17,12,276,MUTED,false,0);
            if (!locked) hit(controls,"level_"+sector,translated(GameModel.LEVELS[sector].name)+" / "+translated(status),22,py,376,n == 5 ? touch : row);
        }
        g.unclip();
        g.rect(0,bottom,420,footer-40,0,PANEL);
        if (selectedInChapter) {
            boolean resume = active && model.battleMode == GameModel.MODE_CAMPAIGN && model.objectiveType == 0 && model.levelIndex == profile.selectedSector;
            controls.add(resume ? "resume" : "play",resume ? "Continue Battle" : "Play",22,bottom+14,376,touch);
            g.rect(22,bottom+14,376,touch,6,COLORS[0]);
            g.polygon(new float[] {40,bottom+touch/2+7,40,bottom+touch/2+21,52,bottom+touch/2+14},BACKGROUND,0,0);
            text(g,tr(resume ? "deck.continue_sector" : "deck.play_sector",profile.selectedSector+1),218,bottom+14+touch/2+6,17,310,BACKGROUND,true,1);
            text(g,GameModel.DIFFICULTIES[resume ? model.difficulty : profile.difficulty],210,bottom+touch+40,12,360,MUTED,false,1);
        } else text(g,tr("deck.choose_sector"),210,bottom+touch/2+23,14,360,MUTED,false,1);
        g.rect(0,height-40,420,40,0,BACKGROUND); g.text(AppVersion.NAME,210,height-14,11,MUTED,false,1);
        if (limit > 0) g.rect(414,top+(bottom-top-28)*offsets[1]/limit,2,28,1,BORDER);
    }
    private void pageArrow(Graphics g,Controls c,String id,String label,float x,float py,boolean enabled) {
        if (enabled) c.add(id,label,x,py,touch,touch);
        icon(g,id.equals("chapter_prev") ? "back" : "next",x+touch/2,py+touch/2,enabled ? WHITE : BORDER);
    }
    float[] sectorPosition(int sector) {
        return new float[] {CAMPAIGN_NODE_X,y(CAMPAIGN_TRAIL_TOP+(sector%6)*campaignRow()+touch/2)};
    }
    private float campaignRow() { return touch+12; }
    void settings(Graphics g,Controls controls,float height,float minTouch) {
        touch = Math.max(64,minTouch); header(g,controls,"Settings",false,false);
        float languageRows = languages ? 3*touch : 0;
        boolean logWarning = profile.log.size() >= PlaytestLog.ENTRY_LIMIT*9/10;
        float logDetails = tools ? 32+(logWarning ? 48 : 0) : 0;
        float content = 312+7*touch+(tools ? 2*touch : 0)+languageRows+logDetails;
        begin(g,SETTINGS,height,minTouch,26+touch,content);
        float row = 24; text(g,tr("deck.audio"),22,y(row),11,376,MUTED,true,0); row += 16;
        settingsToggle(g,controls,"music","Music",row,profile.music); row += touch;
        settingsToggle(g,controls,"sound","Sound Effects",row,profile.sound); row += touch;
        settingsToggle(g,controls,"haptics","Vibration",row,profile.haptics); row += touch+17;
        divider(g,row); row += 32; text(g,tr("deck.gameplay"),22,y(row),11,376,MUTED,true,0); row += 39;
        text(g,"Difficulty",53,y(row),14,200,WHITE,false,0); icon(g,"challenges",33,y(row-5),MUTED);
        text(g,tr("deck.next_battle"),398,y(row),12,130,MUTED,false,2); row += 20;
        g.rect(22,y(row),376,touch+8,6,PANEL);
        for (int i = 0; i < 3; i++) {
            float x = 26+i*124; hit(controls,"difficulty_"+i,GameModel.DIFFICULTIES[i],x,y(row+4),120,touch);
            if (profile.difficulty == i) g.rect(x,y(row+4),120,touch,4,COLORS[0]);
            text(g,GameModel.DIFFICULTIES[i],x+60,y(row+4+touch/2+5),13,108,profile.difficulty == i ? BACKGROUND : WHITE,profile.difficulty == i,1);
        }
        row += touch+26; divider(g,row); row += 31; text(g,tr("deck.language"),22,y(row),11,376,MUTED,true,0); row += 12;
        settingsLink(g,controls,"language_picker",tr("deck.language"),Localization.nativeName(profile.language),row); row += touch;
        if (languages) for (String language : Localization.LANGUAGES) {
            action(g,controls,"language_"+language,Localization.nativeName(language),53,y(row),345,touch,profile.language.equals(language)); row += touch;
        }
        row += 12; divider(g,row); row += 31; text(g,tr("deck.extras"),22,y(row),11,376,MUTED,true,0); row += 12;
        settingsLink(g,controls,"unlock_code","Enter Code","",row); row += touch+12; divider(g,row); row += 12;
        settingsLink(g,controls,"tools",tr("deck.tools"),"",row); row += touch;
        if (tools) {
            settingsToggle(g,controls,"playtest_log","Local Playtest Log",row,profile.log.enabled); row += touch;
            String usage = tr("playtest.entries",profile.log.size(),PlaytestLog.ENTRY_LIMIT);
            text(g,usage,22,y(row+16),12,376,MUTED,false,0); row += 32;
            String warning = "";
            if (logWarning) {
                warning = tr(profile.log.size() == PlaytestLog.ENTRY_LIMIT ? "playtest.log_full" : "playtest.log_warning");
                text(g,warning,22,y(row+13),12,376,COLORS[2],true,0);
                text(g,tr("playtest.export_reminder"),22,y(row+33),12,376,MUTED,false,0); row += 48;
            }
            String exportLabel = translated("Export CSV")+" / "+usage+(warning.isEmpty() ? "" : " / "+warning+" / "+tr("playtest.export_reminder"));
            action(g,controls,"export_log","Export CSV",exportLabel,22,y(row),182,touch,false);
            action(g,controls,"clear_log","Clear Log",216,y(row),182,touch,false); row += touch;
        }
        limit = Math.max(0,row+16-(bottom-top)); offsets[2] = Math.min(offsets[2],limit);
        end(g,height);
    }
    private void divider(Graphics g,float row) { g.line(22,y(row),398,y(row),1,BORDER); }
    private void settingsToggle(Graphics g,Controls controls,String id,String label,float row,boolean enabled) {
        hit(controls,id,tr("deck.toggle",translated(label),tr(enabled ? "deck.on" : "deck.off")),22,y(row),376,touch);
        icon(g,id,34,y(row+touch/2),MUTED); text(g,label,57,y(row+touch/2+5),14,264,WHITE,false,0);
        g.rect(350,y(row+touch/2-13),46,26,13,enabled ? COLORS[0] : BORDER);
        g.circle(enabled ? 383 : 363,y(row+touch/2),9,enabled ? BACKGROUND : MUTED);
    }
    private void settingsLink(Graphics g,Controls controls,String id,String label,String value,float row) {
        hit(controls,id,label,22,y(row),376,touch); icon(g,id,34,y(row+touch/2),MUTED);
        text(g,label,57,y(row+touch/2+5),14,210,WHITE,false,0);
        text(g,value,373,y(row+touch/2+5),12,125,MUTED,false,2); chevron(g,389,y(row+touch/2),MUTED);
    }
    private static void preview(Graphics g,GameModel model,float x,float y,float w,float h) {
        float minX = Float.MAX_VALUE,maxX = -Float.MAX_VALUE,minY = Float.MAX_VALUE,maxY = -Float.MAX_VALUE;
        for (GameModel.Territory t : model.territories) { minX = Math.min(minX,t.x-.866f); maxX = Math.max(maxX,t.x+.866f); minY = Math.min(minY,t.y-1); maxY = Math.max(maxY,t.y+1); }
        float scale = Math.min(w/(maxX-minX),h/(maxY-minY)),left = x+(w-(maxX-minX)*scale)/2,top = y+(h-(maxY-minY)*scale)/2;
        for (GameModel.Territory t : model.territories) {
            float cx = left+(t.x-minX)*scale,cy = top+(t.y-minY)*scale;
            int color = t.owner < 0 ? BORDER : COLORS[model.level().faction(t.owner)];
            g.polygon(hex(cx,cy,scale*.93f),mix(PANEL,color,.16f),color,1);
            if (scale >= 12) g.text(Integer.toString(t.count()),cx,cy+3,10,WHITE,true,1);
            if (t.capital) {
                float mark = Math.min(1,scale/12);
                g.polygon(new float[] {cx-4*mark,cy-5*mark,cx-4*mark,cy-10*mark,cx,cy-7*mark,cx+4*mark,cy-10*mark,cx+4*mark,cy-5*mark},color,0,0);
            }
        }
    }
    private static float[] hex(float x,float y,float radius) {
        float[] p = new float[12]; for (int i = 0; i < 6; i++) { double a = -Math.PI/2+i*Math.PI/3; p[i*2] = x+(float)Math.cos(a)*radius; p[i*2+1] = y+(float)Math.sin(a)*radius; } return p;
    }
    private static int mix(int a,int b,float amount) { int result = 0xff000000; for (int shift : new int[] {0,8,16}) result |= (int)(((a>>shift)&255)*(1-amount)+((b>>shift)&255)*amount)<<shift; return result; }
    private static void chevron(Graphics g,float x,float y,int color) { g.line(x-3,y-5,x+3,y,1.7f,color); g.line(x+3,y,x-3,y+5,1.7f,color); }
    private static void icon(Graphics g,String id,float x,float y,int c) {
        if (id.equals("settings")) { g.circle(x,y,10,c); g.circle(x,y,6,PANEL); g.circle(x,y,2,c); for (int i = 0; i < 8; i++) { double a = i*Math.PI/4; g.line(x+(float)Math.cos(a)*10,y+(float)Math.sin(a)*10,x+(float)Math.cos(a)*14,y+(float)Math.sin(a)*14,3,c); } }
        else if (id.equals("back") || id.equals("next")) { float d = id.equals("back") ? -1 : 1; g.line(x-d*8,y,x+d*8,y,2,c); g.line(x+d*8,y,x,y-8,2,c); g.line(x+d*8,y,x,y+8,2,c); }
        else if (id.equals("sectors")) g.polygon(hex(x,y,12),0,c,2);
        else if (id.equals("mastery")) { float[] p = new float[20]; for (int i = 0; i < 10; i++) { double a = -Math.PI/2+i*Math.PI/5; float r = i%2 == 0 ? 12 : 5; p[2*i] = x+(float)Math.cos(a)*r; p[2*i+1] = y+(float)Math.sin(a)*r; } g.polygon(p,0,c,2); }
        else if (id.equals("tutorial")) { g.circle(x,y,11,c); g.circle(x,y,9,BACKGROUND); g.text("?",x,y+5,15,c,true,1); }
        else if (id.equals("challenges") || id.equals("missions")) { g.circle(x,y,10,c); g.circle(x,y,8,BACKGROUND); g.circle(x,y,5,c); g.circle(x,y,3,BACKGROUND); }
        else if (id.equals("lock") || id.equals("unlock_code")) { g.circle(x,y-4,5,c); g.circle(x,y-4,3,BACKGROUND); g.rect(x-7,y-2,14,11,2,c); }
        else if (id.equals("music")) { g.line(x+5,y-10,x+5,y+5,2,c); g.line(x+5,y-10,x+11,y-6,2,c); g.circle(x,y+7,4,c); }
        else if (id.equals("sound")) { g.polygon(new float[] {x-9,y-4,x-4,y-4,x+2,y-9,x+2,y+9,x-4,y+4,x-9,y+4},0,c,1.5f); g.line(x+7,y-5,x+10,y,1.5f,c); g.line(x+10,y,x+7,y+5,1.5f,c); }
        else if (id.equals("haptics")) { g.rect(x-5,y-10,10,20,2,c); g.rect(x-3,y-8,6,16,1,BACKGROUND); g.line(x-10,y-5,x-10,y+5,2,c); g.line(x+10,y-5,x+10,y+5,2,c); }
        else if (id.equals("logistics")) g.polygon(new float[] {x-3,y-10,x+3,y-10,x+3,y-1,x+9,y+10,x-9,y+10,x-3,y-1},0,c,1.8f);
        else if (id.equals("run")) { g.circle(x-8,y+7,3,c); g.circle(x+8,y-7,3,c); g.line(x-8,y+3,x-8,y-4,2,c); g.line(x-8,y-4,x+8,y+4,2,c); g.line(x+8,y+4,x+8,y-3,2,c); }
        else if (id.equals("daily")) { g.rect(x-10,y-9,20,18,2,c); g.rect(x-8,y-7,16,14,1,BACKGROUND); g.line(x-8,y-3,x+8,y-3,1.5f,c); g.line(x-5,y-12,x-5,y-6,2,c); g.line(x+5,y-12,x+5,y-6,2,c); g.circle(x-3,y+2,1.5f,c); g.circle(x+3,y+2,1.5f,c); }
        else if (id.equals("language_picker")) g.text("A",x,y+6,18,c,false,1);
        else { for (int i = -1; i <= 1; i++) g.line(x-9,y+i*6,x+9-(i+1)*3,y+i*6,1.8f,c); }
    }
}
