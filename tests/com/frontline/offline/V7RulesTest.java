package com.frontline.offline;

import java.util.Arrays;

final class V7RulesTest {
    private static int checks;
    static int run() throws Exception {
        int[] changes = {0}, requests = {0};
        GameScene.Events events = new GameScene.Events() {
            public void changed() { changes[0]++; }
            public void cue(int kind) {}
            public void unlockCodeRequested() { requests[0]++; }
        };
        GameScene.Profile profile = new GameScene.Profile();
        profile.unlocked = 8; profile.selectedSector = 7; profile.wins = 3;
        profile.best[0] = 2100; profile.stars[0] = 3; profile.times[0] = 42;
        profile.music = false; profile.difficulty = 2; profile.tutorialSeen = true;
        GameModel model = new GameModel(7,2,19);
        model.launch(0,1,.25);
        GameScene scene = new GameScene(profile,model,events);
        byte[] battle = model.save();
        check(!scene.redeemUnlockCode("12345"),"Code cannot be applied outside Settings");
        scene.back(); click(scene,"settings");
        check(scene.overlay == GameScene.SETTINGS,"Settings opens from the main menu");
        click(scene,"unlock_code");
        check(requests[0] == 1,"Enter Code requests native input once");
        check(profile.unlocked == 8,"Opening or cancelling input does not unlock sectors");
        int before = changes[0];
        for (String invalid : new String[] {null,"","1234","12346","012345","123450"," 12345","12345 ","abcde"}) {
            check(!scene.redeemUnlockCode(invalid),"Only the exact code is accepted");
            check(profile.unlocked == 8 && changes[0] == before,"Invalid code does not alter or save progression");
        }
        check(scene.redeemUnlockCode("12345"),"The correct code is accepted");
        check(profile.unlocked == GameModel.LEVELS.length-1 && changes[0] == before+1,"All sectors unlock and the profile is saved immediately");
        check(profile.selectedSector == 7 && profile.wins == 3,"Unlock preserves the selected sector and win count");
        check(profile.best[0] == 2100 && profile.stars[0] == 3 && profile.times[0] == 42,"Unlock preserves existing scores, stars and times");
        for (int i = 1; i < GameModel.LEVELS.length; i++) check(!profile.cleared(i),"Unlocking does not mark an unplayed sector cleared");
        check(!profile.music && profile.difficulty == 2 && profile.tutorialSeen,"Unlock preserves all preferences");
        check(scene.hasBattle && Arrays.equals(battle,scene.model.save()),"Unlock leaves a retained battle unchanged");
        check(scene.overlay == GameScene.SETTINGS,"Successful input stays in Settings");
        check(scene.redeemUnlockCode("12345") && profile.unlocked == 59,"Redeeming again is idempotent");
        profile.reconcileProgress();
        check(profile.unlocked == 59,"Progress reconciliation keeps unlocked sectors");
        scene.back(); click(scene,"sectors");
        for (int i = Campaign.chapterIndex(profile.selectedSector); i < Campaign.CHAPTERS.length-1; i++) click(scene,"chapter_next");
        click(scene,"level_59");
        check(profile.selectedSector == 59 && scene.overlay == GameScene.SECTORS,"Sector 60 can be selected on the campaign path without clearing earlier sectors");
        click(scene,"play");
        click(scene,"begin_attempt"); click(scene,"confirm_replace"); click(scene,"camera_ready");
        check(scene.model.levelIndex == 59 && scene.overlay == GameScene.NONE,"The newly unlocked final sector is playable");
        click(scene,"settings"); click(scene,"unlock_code");
        check(scene.overlay == GameScene.SETTINGS && requests[0] == 2,"Code input is available from battle Settings too");
        return checks;
    }
    private static void click(GameScene scene,String id) {
        scene.render(new GameModelTest.NullGraphics(),780);
        float[] point = UiTestControls.find(scene,id,700);
        if (point == null) throw new AssertionError("Missing control: "+id);
        scene.down(point[0],point[1]); scene.up(point[0],point[1]);
    }
    private static void check(boolean condition,String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
