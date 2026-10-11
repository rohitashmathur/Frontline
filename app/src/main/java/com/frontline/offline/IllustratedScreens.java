package com.frontline.offline;

import static com.frontline.offline.GameScene.*;

/** Illustrated reference pages; demos never mutate the live battle or saved progress. */
final class IllustratedScreens {
    private final Profile profile;
    private final float[] offsets = new float[2];
    private int slot, manualTab, attemptTab;
    private boolean collisionResolved;
    private double fraction = .25;
    private int kings = 1;
    private float top, bottom, limit, touch, previousHeight, previousTouch;
    private GameModel preview;
    private Challenge previewChallenge;
    private String previewDate = "";
    private int previewId;

    IllustratedScreens(Profile profile) { this.profile = profile; }
    private String tr(String key,Object... args) { return Localization.text(profile.language,key,args); }
    private String translated(String value) { return Localization.translate(profile.language,value); }
    private float y(float row) { return top+row-offsets[slot]; }
    boolean inContent(float py) { return py >= top && py < bottom; }
    boolean scrollable() { return limit > 0; }
    boolean scroll(float delta) {
        float old = offsets[slot]; offsets[slot] = Math.max(0,Math.min(limit,old+delta));
        return old != offsets[slot];
    }
    void openManual(boolean logistics) {
        manualTab = logistics ? 2 : 0; offsets[0] = 0; collisionResolved = false;
        fraction = .25; kings = 1;
    }
    void openAttempt() { attemptTab = 0; offsets[1] = 0; }
    boolean command(String id) {
        for (int i = 0; i < 3; i++) {
            if (id.equals("manual_tab_"+i)) { manualTab = i; offsets[0] = 0; return true; }
            if (id.equals("attempt_tab_"+i)) { attemptTab = i; offsets[1] = 0; return true; }
        }
        if (id.equals("manual_collision")) collisionResolved = !collisionResolved;
        else if (id.equals("manual_quarter")) fraction = .25;
        else if (id.equals("manual_half")) fraction = .5;
        else if (id.equals("manual_all")) fraction = 1;
        else if (id.equals("manual_gain")) kings = Math.min(5,kings+1);
        else if (id.equals("manual_lose")) kings = Math.max(0,kings-1);
        else return false;
        return true;
    }
    GameModel reference(int sector,int difficulty,Challenge challenge,int id,String date) {
        if (preview == null || preview.levelIndex != sector || preview.difficulty != difficulty
            || previewChallenge != challenge || previewId != id || !previewDate.equals(date)) {
            previewChallenge = challenge; previewId = id; previewDate = date;
            preview = challenge == null ? new GameModel(sector,difficulty,0)
                : challenge.create(difficulty,date.isEmpty() ? 0 : Challenge.dailySeed(date),id,date);
        }
        return preview;
    }
    private void begin(Graphics g,CommandScreens.Controls controls,int page,String title,String[] tabs,
                       float height,float minTouch) {
        slot = page; touch = Math.max(64,minTouch);
        if (height != previousHeight || touch != previousTouch) {
            offsets[0] = offsets[1] = 0; previousHeight = height; previousTouch = touch;
        }
        controls.add(page == 0 ? "help_back" : "brief_back","Back",22,10,touch,touch);
        CommandScreens.icon(g,"back",22+touch/2,10+touch/2,WHITE);
        text(g,title,34+touch,10+touch/2+6,22,page == 0 ? 364-touch : 352-2*touch,WHITE,true,0);
        if (page == 1) {
            controls.add("help",tr("manual.open"),398-touch,10,touch,touch);
            CommandScreens.icon(g,"help",398-touch/2,10+touch/2,MUTED);
        }
        float tabsY = 22+touch;
        g.rect(22,tabsY,376,touch,6,PANEL);
        int selected = page == 0 ? manualTab : attemptTab;
        for (int i = 0; i < tabs.length; i++) {
            float x = 22+i*376f/3, w = 376f/3;
            String label = tr(tabs[i]);
            controls.add((page == 0 ? "manual_tab_" : "attempt_tab_")+i,label,x,tabsY,w,touch);
            if (selected == i) g.rect(x+3,tabsY+3,w-6,touch-6,4,mix(PANEL,COLORS[0],.17f));
            text(g,label,x+w/2,tabsY+touch/2+5,14,w-14,selected == i ? COLORS[0] : MUTED,true,1);
            if (selected == i) g.rect(x+12,tabsY+touch-4,w-24,2,0,COLORS[0]);
        }
        top = tabsY+touch+12; bottom = height-touch-64;
        g.clip(0,top,420,Math.max(0,bottom-top));
    }
    private void end(Graphics g,CommandScreens.Controls controls,float content,float height,
                     String id,String label,boolean primary) {
        limit = Math.max(0,content-(bottom-top));
        offsets[slot] = Math.min(offsets[slot],limit);
        g.unclip();
        g.rect(0,bottom,420,height-bottom,0,BACKGROUND);
        g.line(22,bottom+8,398,bottom+8,1,BORDER);
        action(g,controls,id,label,22,bottom+18,376,touch,primary);
        g.text(AppVersion.NAME,210,height-14,11,MUTED,false,1);
        if (limit > 0 && bottom > top) {
            float length = Math.max(24,(bottom-top)*(bottom-top)/(bottom-top+limit));
            g.rect(414,top+(bottom-top-length)*offsets[slot]/limit,2,length,1,BORDER);
        }
    }
    private void hit(CommandScreens.Controls controls,String id,String label,float x,float py,float w,float h) {
        if (py >= top && py+h <= bottom) controls.add(id,label,x,py,w,h);
    }
    private void action(Graphics g,CommandScreens.Controls controls,String id,String label,
                        float x,float py,float w,float h,boolean primary) {
        controls.add(id,label,x,py,w,h);
        g.rect(x,py,w,h,6,primary ? COLORS[0] : BORDER);
        if (!primary) g.rect(x+1,py+1,w-2,h-2,5,BACKGROUND);
        text(g,label,x+w/2,py+h/2+5,15,w-22,primary ? BACKGROUND : WHITE,true,1);
    }
    private void bodyAction(Graphics g,CommandScreens.Controls controls,String id,String label,
                            float x,float row,float w,boolean enabled) {
        if (enabled) hit(controls,id,label,x,y(row),w,touch);
        g.rect(x,y(row),w,touch,6,enabled ? BORDER : PANEL);
        g.rect(x+1,y(row)+1,w-2,touch-2,5,BACKGROUND);
        text(g,label,x+w/2,y(row)+touch/2+5,13,w-18,enabled ? COLORS[0] : MUTED,true,1);
    }
    private void text(Graphics g,String value,float x,float baseline,float size,float width,int color,boolean bold,int align) {
        value = translated(value);
        while (size > 10 && g.measureText(value,size,bold) > width) size -= .5f;
        if (g.measureText(value,size,bold) > width) {
            while (value.length() > 1 && g.measureText(value+"...",size,bold) > width)
                value = value.substring(0,value.offsetByCodePoints(value.length(),-1));
            value += "...";
        }
        g.text(value,x,baseline,size,color,bold,align);
    }
    private float paragraph(Graphics g,String value,float x,float row,float width,float size,int color,boolean bold) {
        String[] words = translated(value).split(" ");
        for (String word : words) while (size > 10 && g.measureText(word,size,bold) > width) size -= .5f;
        StringBuilder line = new StringBuilder(); int lines = 0;
        for (String word : words) {
            String next = line.length() == 0 ? word : line+" "+word;
            if (line.length() > 0 && g.measureText(next,size,bold) > width) {
                g.text(line.toString(),x,y(row+size+lines++*size*1.5f),size,color,bold,0); line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) g.text(line.toString(),x,y(row+size+lines++*size*1.5f),size,color,bold,0);
        return row+lines*size*1.5f+8;
    }
    private float heading(Graphics g,String title,String detail) {
        float row = paragraph(g,title,22,8,376,24,WHITE,true);
        return paragraph(g,detail,22,row,376,12,MUTED,false)+10;
    }
    private float fact(Graphics g,String icon,String title,String detail,float row,int color) {
        CommandScreens.icon(g,icon,36,y(row+14),color);
        row = paragraph(g,title,62,row,336,15,WHITE,true);
        row = paragraph(g,detail,62,row-3,336,12,MUTED,false);
        g.line(22,y(row+3),398,y(row+3),1,BORDER);
        return row+19;
    }
    private void frame(Graphics g,float row,float h) {
        g.rect(22,y(row),376,h,8,BORDER); g.rect(23,y(row)+1,374,h-2,7,PANEL);
    }
    private static float[] hex(float x,float y,float radius) {
        float[] p = new float[12];
        for (int i = 0; i < 6; i++) { double a = -Math.PI/2+i*Math.PI/3; p[i*2] = x+(float)Math.cos(a)*radius; p[i*2+1] = y+(float)Math.sin(a)*radius; }
        return p;
    }
    private void tile(Graphics g,float x,float row,int color,boolean king,String count) {
        g.polygon(hex(x,y(row),25),mix(PANEL,color,.2f),color,2);
        if (king) crown(g,x,y(row)-6,color);
        if (!count.isEmpty()) text(g,count,x,y(row)+(king ? 14 : 5),14,36,WHITE,true,1);
    }
    private static void crown(Graphics g,float x,float cy,int color) {
        g.polygon(new float[] {x-9,cy+5,x-9,cy-6,x-3,cy-1,x,cy-9,x+3,cy-1,x+9,cy-6,x+9,cy+5},0,color,1.8f);
    }
    private static int mix(int a,int b,float amount) {
        int result = 0xff000000;
        for (int shift : new int[] {0,8,16}) result |= (int)(((a>>shift)&255)*(1-amount)+((b>>shift)&255)*amount)<<shift;
        return result;
    }
    private void arrow(Graphics g,float x,float to,float row,int color) {
        g.line(x,y(row),to,y(row),2,color);
        float dir = to > x ? 1 : -1;
        g.line(to,y(row),to-dir*7,y(row)-5,2,color); g.line(to,y(row),to-dir*7,y(row)+5,2,color);
    }
    private static String boost(int count) {
        double value = count < 4 ? Math.pow(1.2,count) : 3*Math.pow(1.2,count-4);
        return java.math.BigDecimal.valueOf(value).setScale(3,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    void manual(Graphics g,CommandScreens.Controls controls,boolean logistics,float height,float minTouch) {
        begin(g,controls,0,"Field Manual",new String[] {"manual.combat","manual.kings","manual.modes"},height,minTouch);
        float content = manualTab == 0 ? combat(g,controls,logistics) : manualTab == 1 ? kings(g,controls) : modes(g,logistics);
        end(g,controls,content+16,height,"manual_practice",tr("manual.practice"),true);
    }
    private float combat(Graphics g,CommandScreens.Controls controls,boolean logistics) {
        float row = heading(g,tr("manual.combat_title"),tr("manual.combat_intro"));
        frame(g,row,150);
        text(g,tr(collisionResolved ? "manual.after" : "manual.before"),40,y(row+24),11,340,MUTED,true,0);
        tile(g,70,row+76,COLORS[0],false,""); tile(g,350,row+76,COLORS[1],false,"");
        if (collisionResolved) {
            arrow(g,244,112,row+76,COLORS[1]);
            text(g,"10",245,y(row+65),20,75,COLORS[1],true,1);
        } else {
            arrow(g,111,182,row+76,COLORS[0]); arrow(g,309,238,row+76,COLORS[1]);
            text(g,"20",145,y(row+65),20,70,COLORS[0],true,1);
            text(g,"30",276,y(row+65),20,70,COLORS[1],true,1);
            g.line(204,y(row+69),216,y(row+83),2,COLORS[2]); g.line(216,y(row+69),204,y(row+83),2,COLORS[2]);
        }
        text(g,tr(collisionResolved ? "manual.collision_result" : "manual.collision_example"),210,y(row+128),12,344,WHITE,true,1);
        row += 162;
        bodyAction(g,controls,"manual_collision",tr(collisionResolved ? "manual.replay" : "manual.resolve"),22,row,376,true);
        row += touch+20;
        row = paragraph(g,tr("manual.deployment"),22,row,376,16,WHITE,true);
        int sent = (int)(40*fraction);
        row = paragraph(g,tr("manual.deployment_count",sent,40-sent),22,row,376,13,COLORS[0],true);
        String[] ids = {"manual_quarter","manual_half","manual_all"}; double[] fractions = {.25,.5,1};
        for (int i = 0; i < 3; i++) {
            float x = 22+i*128;
            hit(controls,ids[i],tr("manual.fraction_label",(int)(fractions[i]*100),(int)(40*fractions[i])),x,y(row),120,touch);
            g.rect(x,y(row),120,touch,6,fraction == fractions[i] ? COLORS[0] : BORDER);
            if (fraction != fractions[i]) g.rect(x+1,y(row)+1,118,touch-2,5,BACKGROUND);
            text(g,((int)(fractions[i]*100))+"%",x+60,y(row+touch/2+5),17,100,fraction == fractions[i] ? BACKGROUND : WHITE,true,1);
        }
        row += touch+20;
        row = fact(g,"sectors",tr("manual.capture"),tr("manual.capture_detail"),row,COLORS[0]);
        row = fact(g,"run",tr("manual.reinforce"),tr("manual.reinforce_detail"),row,COLORS[4]);
        return fact(g,"sectors",tr(logistics ? "manual.lab_title" : "manual.travel"),tr(logistics ? "logistics.rules" : "brief.gaps"),row,MUTED);
    }
    private float kings(Graphics g,CommandScreens.Controls controls) {
        float row = heading(g,tr("manual.king_title"),tr("manual.king_intro"));
        frame(g,row,144); tile(g,89,row+63,COLORS[1],true,""); arrow(g,128,182,row+63,COLORS[0]);
        tile(g,216,row+63,COLORS[0],true,"");
        text(g,tr("manual.boost_value",boost(kings)),308,y(row+68),23,139,COLORS[0],true,1);
        text(g,tr("manual.held_count",kings),210,y(row+121),12,344,WHITE,true,1);
        row += 156;
        bodyAction(g,controls,"manual_lose",tr("manual.lose"),22,row,182,kings > 0);
        bodyAction(g,controls,"manual_gain",tr("manual.gain"),216,row,182,kings < 5);
        row += touch+20;
        row = paragraph(g,tr("manual.milestones"),22,row,376,16,WHITE,true);
        for (int i = 1; i <= 5; i++) {
            float x = 22+(i-1)*76;
            crown(g,x+35,y(row+14),i == kings ? COLORS[0] : COLORS[2]);
            text(g,Integer.toString(i),x+35,y(row+43),12,70,MUTED,true,1);
            text(g,tr("manual.boost_value",boost(i)),x+35,y(row+65),13,70,WHITE,true,1);
        }
        row += 90;
        row = fact(g,"mastery",tr("manual.team_boost"),tr("manual.team_boost_detail"),row,COLORS[2]);
        row = fact(g,"sectors",tr("manual.caps"),tr("manual.caps_detail"),row,COLORS[0]);
        return fact(g,"mastery",tr("manual.king_speed"),tr("manual.king_speed_detail"),row,COLORS[2]);
    }
    private float modes(Graphics g,boolean logistics) {
        float row = heading(g,tr(logistics ? "manual.lab_title" : "manual.modes_title"),tr("manual.modes_intro"));
        frame(g,row,126);
        text(g,tr("deck.classic_run"),40,y(row+24),13,330,WHITE,true,0);
        for (int i = 0; i < RunState.BATTLE_COUNT; i++) {
            float x = 60+i*75;
            if (i < RunState.BATTLE_COUNT-1) { g.line(x+19,y(row+65),x+56,y(row+65),2,BORDER); crown(g,x+37,y(row+64),COLORS[2]); }
            tile(g,x,row+65,COLORS[0],false,Integer.toString(i+1));
        }
        text(g,tr("deck.five_battles"),210,y(row+109),12,344,MUTED,false,1);
        row += 145;
        row = fact(g,"run",tr("manual.run"),tr("manual.run_detail"),row,COLORS[0]);
        row = fact(g,"missions","Hold King",tr("manual.hold_detail"),row,COLORS[2]);
        row = fact(g,"missions","Home Guard",tr("manual.keep_detail"),row,COLORS[4]);
        row = fact(g,"missions","Troop Budget",tr("manual.budget_detail"),row,COLORS[1]);
        row = fact(g,"daily","Daily Mission",tr("manual.daily_detail"),row,COLORS[0]);
        row = paragraph(g,tr("manual.lab_title"),22,row,376,19,WHITE,true);
        frame(g,row,113);
        tile(g,70,row+49,COLORS[0],false,""); tile(g,210,row+49,COLORS[0],false,""); tile(g,350,row+49,COLORS[1],false,"");
        arrow(g,102,178,row+49,COLORS[0]); arrow(g,242,318,row+49,COLORS[1]);
        text(g,tr("manual.route_caption"),210,y(row+94),12,344,MUTED,false,1);
        row += 133;
        for (String key : new String[] {"logistics.rules","logistics.reinforce_rule","logistics.attack_rule","logistics.transit_rule","logistics.no_attrition","logistics.separate_records"})
            row = paragraph(g,tr(key),22,row,376,12,MUTED,false)+4;
        row = fact(g,"challenges",tr("manual.surrender"),tr("manual.surrender_detail"),row,COLORS[1]);
        row = fact(g,"mastery",tr("manual.records"),tr("manual.records_detail"),row,COLORS[2]);
        return fact(g,"mastery",tr("manual.score"),tr("manual.score_detail"),row,MUTED);
    }
    void attempt(Graphics g,CommandScreens.Controls controls,GameModel reference,String title,String detail,
                 String record,String legacy,Challenge challenge,float height,float minTouch) {
        begin(g,controls,1,title,new String[] {"manual.battle","manual.objective","manual.opponents"},height,minTouch);
        float row = heading(g,reference.level().name,detail);
        row = paragraph(g,challenge == null ? tr("brief.campaign") : challenge.objective(),22,row,376,12,COLORS[0],true)+6;
        if (attemptTab == 0) {
            frame(g,row,194); CommandScreens.preview(g,reference,38,y(row+14),344,146);
            text(g,tr("manual.preview"),210,y(row+178),12,344,MUTED,false,1);
            row += 210;
            row = fact(g,"sectors",tr("manual.battlefield"),tr("manual.map_stats",reference.territories.size(),reference.level().opponents),row,COLORS[0]);
            if (reference.dailyDate.isEmpty()) row = paragraph(g,tr("manual.preview_note"),22,row,376,11,MUTED,false);
            row = fact(g,"mastery",tr("manual.record_title"),record,row,COLORS[2]);
            if (!legacy.isEmpty()) row = paragraph(g,legacy,22,row,376,11,MUTED,false);
            if (challenge == null) row = fact(g,"mastery",tr("manual.star_targets"),tr("brief.targets",GameScene.time(reference.level().parSeconds),GameScene.time(reference.level().parSeconds*1.6f)),row,COLORS[2]);
        } else if (attemptTab == 1) {
            int type = challenge == null ? Challenge.CAMPAIGN : challenge.type;
            frame(g,row,146);
            if (type == Challenge.BUDGET) {
                tile(g,79,row+65,COLORS[0],false,""); arrow(g,115,209,row+65,COLORS[0]); tile(g,244,row+65,COLORS[1],false,"");
                text(g,Integer.toString(challenge.budget),327,y(row+71),25,85,COLORS[2],true,1);
            } else if (type == Challenge.KEEP_KING) {
                tile(g,88,row+65,COLORS[0],true,""); arrow(g,254,125,row+65,COLORS[1]);
                tile(g,291,row+65,COLORS[1],false,"");
                g.polygon(hex(88,y(row+65),34),0,COLORS[4],2);
            } else {
                tile(g,88,row+65,COLORS[0],true,""); arrow(g,125,254,row+65,COLORS[0]);
                tile(g,291,row+65,COLORS[1],true,"");
                if (type == Challenge.HOLD_KING) g.circle(291,y(row+65),34,COLORS[2]);
                if (type == Challenge.HOLD_KING) tile(g,291,row+65,COLORS[1],true,"");
            }
            text(g,tr("manual.objective"),210,y(row+127),12,344,COLORS[2],true,1);
            row += 164;
            row = fact(g,"missions",tr("manual.win"),challenge == null ? tr("brief.campaign") : challenge.objective(),row,COLORS[0]);
            row = fact(g,"sectors",tr("manual.watch"),challenge == null ? tr("brief.gaps") : tr(type == Challenge.HOLD_KING ? "brief.hold_reset" : type == Challenge.KEEP_KING ? "brief.king_failure" : "brief.budget_failure"),row,COLORS[1]);
            row = fact(g,"help","Field Manual",tr("manual.rules_link"),row,MUTED);
        } else {
            frame(g,row,121);
            float spacing = 344f/(reference.level().opponents+1);
            for (int owner = 0; owner <= reference.level().opponents; owner++)
                tile(g,38+spacing*(owner+.5f),row+46,COLORS[reference.level().faction(owner)],true,"");
            text(g,tr("manual.equal_start"),210,y(row+101),12,344,MUTED,false,1);
            row += 141;
            for (int owner = 1; owner <= reference.level().opponents; owner++) {
                int faction = reference.level().faction(owner);
                row = fact(g,"missions",Campaign.FACTIONS[faction],Campaign.RULERS[faction]+" / "+GameModel.styleName(faction == 1 || faction == 3 ? GameModel.PRESSURE : GameModel.GUARDIAN),row,COLORS[faction]);
            }
            row = paragraph(g,challenge != null && challenge.type == Challenge.KEEP_KING && reference.difficulty > 0 && (reference.levelIndex == 0 || reference.levelIndex == 8)
                ? tr("brief.defense_pressure") : tr("brief.ai"),22,row,376,12,MUTED,false);
        }
        end(g,controls,row+16,height,"begin_attempt","Start Attempt",true);
    }
}
