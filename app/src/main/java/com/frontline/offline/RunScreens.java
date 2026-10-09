package com.frontline.offline;

/** Run presentation; immutable run transitions live outside the renderer. */
final class RunScreens {
    interface Controls {
        void button(String id,String label,float x,float y,float w,float h,boolean primary);
    }
    private RunScreens() {}
    private static String t(String language,String key,Object... args) { return Localization.text(language,key,args); }
    static void draw(GameScene.Graphics g,float height,String language,RunState run,Controls controls) {
        g.text(t(language,"run.title"),22,55,27,GameScene.WHITE,true,0);
        if (run == null) {
            g.text(t(language,"run.empty"),22,110,14,GameScene.MUTED,false,0);
            controls.button("new_run",t(language,"run.new"),22,157,376,48,true);
            controls.button("home",t(language,"menu.main"),52,height-69,316,44,false);
            return;
        }
        if (run.status == RunState.COUNCIL) { council(g,height,language,run,controls); return; }
        if (run.terminal()) { summary(g,height,language,run,controls); return; }
        g.text(t(language,"run.progress",run.battlesCleared(),5),22,94,14,GameScene.COLORS[2],true,0);
        g.text(t(language,"run.retries",run.retriesRemaining()),398,94,12,GameScene.MUTED,false,2);
        for (RunState.Node node : run.nodes()) {
            float y = 143+node.index*58;
            boolean cleared = node.index < run.node || run.status == RunState.COUNCIL && node.index == run.node;
            int color = cleared ? GameScene.COLORS[0] : node.index == run.node ? GameScene.COLORS[2] : GameScene.MUTED;
            g.circle(36,y-5,11,color);
            g.text(Integer.toString(node.index+1),36,y,12,GameScene.BACKGROUND,true,1);
            g.text(Localization.translate(language,node.name),58,y,16,GameScene.WHITE,true,0);
            g.text(t(language,"run.node_details",Localization.translate(language,GameModel.DIFFICULTIES[node.difficulty]),node.opponents),58,y+22,11,color,false,0);
            g.line(22,y+34,398,y+34,1,GameScene.BORDER);
        }
        String id = run.status == RunState.RETRY_AVAILABLE ? "run_retry" : run.status == RunState.BATTLE ? "continue_run" : "begin_run_battle";
        String label = t(language,run.status == RunState.RETRY_AVAILABLE ? "run.use_retry" : run.status == RunState.BATTLE ? "run.continue" : "run.start_battle");
        controls.button(id,label,22,height-184,376,48,true);
        controls.button(run.status == RunState.RETRY_AVAILABLE ? "run_end" : "run_abandon",t(language,run.status == RunState.RETRY_AVAILABLE ? "run.end" : "run.abandon"),22,height-126,182,44,false);
        controls.button("home",t(language,"menu.main"),216,height-126,182,44,false);
        controls.button("new_run",t(language,"run.new"),22,height-69,run.status == RunState.RETRY_AVAILABLE ? 182 : 376,44,false);
        if (run.status == RunState.RETRY_AVAILABLE)
            controls.button("run_abandon",t(language,"run.abandon"),216,height-69,182,44,false);
    }
    private static void council(GameScene.Graphics g,float height,String language,RunState run,Controls controls) {
        g.text(t(language,"run.council"),22,94,20,GameScene.COLORS[2],true,0);
        g.text(t(language,"run.progress",run.battlesCleared(),5),22,127,13,GameScene.MUTED,false,0);
        int index = 0;
        for (int perk : run.councilOffer()) {
            float y = 178+index++*107;
            g.text(perkName(language,perk),22,y,19,GameScene.WHITE,true,0);
            g.text(perkEffect(language,perk),22,y+27,12,GameScene.MUTED,false,0);
            controls.button("perk_"+perk,t(language,"run.choose"),276,y+37,122,42,true);
            g.line(22,y+88,398,y+88,1,GameScene.BORDER);
        }
        controls.button("run_abandon",t(language,"run.abandon"),22,height-69,182,44,false);
        controls.button("home",t(language,"menu.main"),216,height-69,182,44,false);
    }
    private static void summary(GameScene.Graphics g,float height,String language,RunState run,Controls controls) {
        RunState.Summary facts = run.summary();
        g.text(t(language,run.status == RunState.COMPLETED ? "run.completed" : run.status == RunState.DEFEATED ? "run.defeated" : "run.abandoned"),22,97,21,GameScene.COLORS[2],true,0);
        g.text(t(language,"run.progress",facts.battlesCleared,5),22,139,17,GameScene.WHITE,true,0);
        g.text(t(language,"run.retries_used",facts.retriesUsed),22,173,13,GameScene.MUTED,false,0);
        g.text(t(language,"run.elapsed",GameScene.time((float)facts.elapsed)),22,206,13,GameScene.MUTED,false,0);
        g.text(t(language,"run.stats",facts.captures,facts.unitsLost),22,239,12,GameScene.MUTED,false,0);
        int row = 0;
        for (int perk : run.chosenPerks()) g.text(perkName(language,perk),22,286+row++*35,15,GameScene.WHITE,true,0);
        controls.button("new_run",t(language,"run.new"),52,height-132,316,48,true);
        controls.button("home",t(language,"menu.main"),52,height-69,316,44,false);
    }
    static String perkName(String language,int perk) { return t(language,"perk."+perk+".name"); }
    static String perkEffect(String language,int perk) { return t(language,"perk."+perk+".effect"); }
}
