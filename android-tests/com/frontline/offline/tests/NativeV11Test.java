package com.frontline.offline.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/** Android-only harness. Model/scene APIs deliberately remain reflection-only. */
public final class NativeV11Test extends Instrumentation {
    private static final String MARKER = "v11-native-backup-ready";
    private static final String[] LANGUAGES = {"en", "id", "hi"};
    private Activity activity;
    private View view;
    private Object scene, profile;
    private SharedPreferences storage, backup, checkpoint;
    private File output;
    private String shot, mode, expectedLanguage, nextLanguage;
    private String glyphDiagnostics = "";
    private int checks;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        Bundle args = arguments == null ? new Bundle() : arguments;
        shot = args.getString("shot", "v11-native");
        mode = args.getString("mode", "suite");
        if ("true".equals(args.getString("restore"))) mode = "restore";
        expectedLanguage = args.getString("expectedLanguage", "en");
        nextLanguage = args.getString("nextLanguage", "");
        if (!shot.matches("[a-z0-9-]+") || !Arrays.asList("suite", "relaunch", "restore", "glyph", "v12screens").contains(mode)
            || !supported(expectedLanguage) || !nextLanguage.isEmpty() && !supported(nextLanguage))
            throw new IllegalArgumentException("V11 harness arguments");
        start();
    }

    @Override public void onStart() {
        storage = getTargetContext().getSharedPreferences("frontline-v1", Context.MODE_PRIVATE);
        backup = getTargetContext().getSharedPreferences("frontline-v11-native-original", Context.MODE_PRIVATE);
        checkpoint = getTargetContext().getSharedPreferences("frontline-v11-native-checkpoint", Context.MODE_PRIVATE);
        Throwable failure = null;
        boolean retain = false;
        try {
            if (mode.equals("v12screens")) {
                require(!backup.getBoolean(MARKER,false),"interrupted fixture requires restore-only first");
                copy(backup,storage.getAll());
                require(backup.edit().putBoolean(MARKER,true).commit(),"original preferences backed up");
                require(storage.edit().clear().putBoolean("tutorial-seen",true).putBoolean("camera-guide-seen",true)
                    .putBoolean("sound",false).putBoolean("music",false).putBoolean("haptics",false)
                    .putInt("difficulty",1).putInt("unlocked",2).putInt("best-0",1700).putInt("stars-0",2).commit(),"V12 presentation fixture installed");
                launch(); ui(this::v12Screens);
            } else if (mode.equals("suite")) {
                require(!backup.getBoolean(MARKER, false), "interrupted fixture requires restore-only first");
                copy(backup, storage.getAll());
                require(backup.edit().putBoolean(MARKER, true).commit(), "original preferences backed up");
                freshInstallLanguage();
                nativeProgressMigration();
                require(storage.edit().clear().putBoolean("tutorial-seen", true).putBoolean("camera-guide-seen", true)
                    .putBoolean("sound", false).putBoolean("music", false).putBoolean("haptics", false)
                    .putInt("difficulty", 1).putInt("best-0", 1500).putInt("stars-0", 2).putFloat("time-0", 80).commit(),
                    "old-preference fixture without language installed");
                launch();
                ui(() -> {
                    require("en".equals(read(profile, "language")), "old preferences preserve English");
                    unlockPreservesClears();
                    installMission(3, true);
                    write(scene, "overlay", constant("GameScene", "MENU"));
                    languageInvariants();
                    for (String language : LANGUAGES) localizedScreens(language);
                    installMission(3, true);
                    write(scene, "overlay", constant("GameScene", "MENU"));
                    selectLanguage("en");
                });
                close();
                copy(checkpoint, storage.getAll());
                retain = true;
            } else if (mode.equals("glyph")) {
                require(!backup.getBoolean(MARKER, false), "interrupted fixture requires restore-only before glyph probe");
                copy(backup, storage.getAll());
                require(backup.edit().putBoolean(MARKER, true).commit(), "glyph probe preferences backed up");
                launch();
                ui(() -> { render().recycle(); hindiGlyphs(); });
            } else if (mode.equals("relaunch")) {
                require(backup.getBoolean(MARKER, false), "process checkpoint has original backup");
                require(storage.getAll().equals(checkpoint.getAll()), "force-stop retains exact saved preferences");
                require(expectedLanguage.equals(storage.getString("language", "")), "persisted language before fresh process launch");
                launch();
                ui(() -> {
                    require(expectedLanguage.equals(read(profile, "language")), "fresh process restores explicit language");
                    same(saved(checkpoint, "battle"), bytes(read(scene, "model")), "fresh process restores exact battle/RNG/objective");
                    same(saved(checkpoint, "progress-v10"), bytes(read(profile, "progress")), "fresh process restores progress");
                    homeBounds();
                    if (!nextLanguage.isEmpty()) selectLanguage(nextLanguage);
                });
                close();
                if (!nextLanguage.isEmpty()) { copy(checkpoint, storage.getAll()); retain = true; }
            }
        } catch (Throwable error) { failure = error; }
        finally {
            try {
                close();
                if (!retain || failure != null) restoreOriginal();
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        Bundle result = new Bundle();
        result.putString("stream", glyphDiagnostics + (glyphDiagnostics.isEmpty() ? "" : "\n") + (failure == null
            ? "PASS: native V11 " + mode + "; " + checks + " checks; " + (retain ? "process checkpoint retained" : "original preferences restored")
                + "; fixture screenshots=" + shot + "; automated glyph/layout evidence only\n"
            : "FAIL: " + android.util.Log.getStackTraceString(failure)));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void v12Screens() throws Exception {
        require("0.12.0".equals(read(type("AppVersion"),"NAME")),"V12 footer matches release");
        call(scene,"start",1); Object model = read(scene,"model");
        call(model,"launch",0,1,.25); write(model,"elapsed",17f);
        write(scene,"overlay",constant("GameScene","MENU"));
        byte[] battle = bytes(model), progress = bytes(read(profile,"progress"));
        for (String language : LANGUAGES) {
            write(profile,"language",language); call(view,"changed");
            call(scene,"scrollScreen",-10000f); homeBounds();
            capture(language,"v12-home","drawMenu"); auditV12Pages();
            tap("settings"); capture(language,"v12-settings","drawSettings"); auditV12Pages();
            require(optionalButton("export_log") == null,"Playtest tools initially collapsed");
            tap("music"); require((boolean)read(profile,"music"),"native Music on");
            tap("music"); require(!(boolean)read(profile,"music"),"native Music off");
            tap("difficulty_2"); require((int)read(profile,"difficulty") == 2 && (int)read(model,"difficulty") == 1,"difficulty changes only next battle");
            tap("tools"); tap("playtest_log"); require((boolean)read(read(profile,"log"),"enabled"),"playtest tools remain reachable");
            tap("playtest_log"); tap("tools");
            tap("language_picker"); tap("language_"+language); require(language.equals(read(profile,"language")),"native picker persists chosen language");
            require(language.equals(storage.getString("language","")),"language saved to native preferences");
            tap("back"); tap("sectors"); capture(language,"v12-campaign","drawSectors"); auditV12Pages();
            require(reveal("level_2") != null,"unlocked sector is reachable");
            require(optionalButton("level_3") == null,"locked sector has no virtual action");
            tap("settings"); tap("back"); require((int)read(scene,"overlay") == constant("GameScene","SECTORS"),"Settings returns to campaign");
            tap("back"); require((int)read(scene,"overlay") == constant("GameScene","MENU"),"campaign Back returns home after settings");
            tap("sectors"); tap("level_2"); require((int)read(scene,"overlay") == constant("GameScene","SECTORS"),"selection remains on campaign path");
            tap("play"); tap("brief_back"); require((int)read(scene,"overlay") == constant("GameScene","SECTORS"),"briefing Back retains campaign");
            tap("back"); tap("resume"); require((int)read(scene,"overlay") == constant("GameScene","NONE"),"Home continues actual retained round");
            same(battle,bytes(model),"native presentation preserves exact battle"); same(progress,bytes(read(profile,"progress")),"native presentation preserves records");
            call(scene,"pause"); tap("settings"); tap("back"); require((int)read(scene,"overlay") == constant("GameScene","PAUSE"),"settings returns to pause");
            tap("menu"); write(profile,"selectedSector",1); write(profile,"difficulty",1);
            call(scene,"scrollScreen",-10000f); render().recycle();
            Object primary = reveal("resume"); float px = (float)read(primary,"x")+(float)read(primary,"width")/2;
            float py = (float)read(primary,"y")+(float)read(primary,"height")/2;
            touch(MotionEvent.ACTION_DOWN,new float[] {px,py}); touch(MotionEvent.ACTION_MOVE,new float[] {px,py-90});
            render().recycle(); touch(MotionEvent.ACTION_UP,new float[] {px,py-90});
            require((int)read(scene,"overlay") == constant("GameScene","MENU"),"native swipe cancels touched button");
            same(battle,bytes(model),"native scrolling cannot send troops");
        }
        tap("settings"); require((boolean)call(scene,"redeemUnlockCode","12345"),"V7 unlock retained");
        tap("back"); tap("sectors");
        for (int chapter = 0; chapter < 10; chapter++) {
            write(scene,"sectorPage",chapter); write(profile,"selectedSector",chapter*6+5);
            render().recycle(); require(reveal("level_"+(chapter*6+5)) != null,"all sixty nodes reachable"); auditV12Pages();
        }
        same(battle,bytes(model),"all campaign browsing preserves retained battle");
    }
    private void auditV12Pages() throws Exception {
        call(scene,"scrollScreen",-10000f);
        for (int page = 0; page < 50; page++) {
            render().recycle(); checkLabels();
            String method = (int)read(scene,"overlay") == constant("GameScene","MENU") ? "drawMenu"
                : (int)read(scene,"overlay") == constant("GameScene","SETTINGS") ? "drawSettings" : "drawSectors";
            require(joined(textLayout(method)).contains("0.12.0"),"version footer remains visible when scrolling");
            List<?> controls = buttons(); float density = view.getResources().getDisplayMetrics().density;
            for (Object b : controls) { Rect r = bounds(b); require(r.width()+1 >= 48*density && r.height()+1 >= 48*density,"native controls at least 48dp"); }
            for (int i = 0; i < controls.size(); i++) for (int j = i+1; j < controls.size(); j++)
                require(!Rect.intersects(bounds(controls.get(i)),bounds(controls.get(j))),"native hit targets do not overlap");
            if (!(boolean)call(scene,"scrollScreen",45f)) break;
        }
    }

    private void freshInstallLanguage() throws Exception {
        require(storage.edit().clear().commit(), "fresh preference fixture installed");
        launch();
        ui(() -> {
            String device = Locale.getDefault().getLanguage();
            require((supported(device) ? device : "en").equals(read(profile, "language")), "fresh install accepts supported device language or English fallback");
        });
        close();
    }

    private void nativeProgressMigration() throws Exception {
        launch();
        ui(() -> { call(scene, "start", 0); call(view, "save"); });
        close();
        Map<String, ?> baseline = new HashMap<>(storage.getAll());
        byte[] battle = saved(storage, "battle");
        for (int format : new int[] {1, 2}) {
            copy(storage, baseline);
            require(storage.edit().putString("progress-v10", Base64.encodeToString(historicalProgress(format), Base64.NO_WRAP))
                .putString("language", "id").putBoolean("sound", false).putBoolean("music", false).putBoolean("haptics", false).commit(),
                "historical progress format " + format + " installed");
            launch();
            final int rules = format == 1 ? 10 : 0;
            ui(() -> {
                Object progress = read(profile, "progress");
                require((int)call(progress, "campaignBest", 2, 1, rules) == 1600
                    && (int)call(progress, "campaignStars", 2, 1, rules) == 2
                    && (float)call(progress, "campaignTime", 2, 1, rules) == 70f, "native migration retains historical provenance and records");
                require((int)call(progress, "campaignStars", 2, 1, 11) == 0, "native migration does not invent a current-rules record");
                require((boolean)call(profile, "cleared", 2) && !(boolean)call(profile, "cleared", 3)
                    && (int)read(profile, "unlocked") >= 3, "native Profile reconciles historical clears without manufacturing completion");
                require((int)call(progress, "earnedCount") == 1 && (int)read(progress, "theme") == 1
                    && (int)call(progress, "objectiveProgress") == 1 && (int)call(progress, "dailyBest", "2026-10-08") == 1200,
                    "native migration retains mastery, theme, objective and historical Daily records");
                require(((int[][])read(progress, "challengeStars"))[1][0] == 1, "native migration retains historical mission completion");
                require("id".equals(read(profile, "language")), "native migration preserves explicit language");
                same(battle, bytes(read(scene, "model")), "native migration leaves active battle untouched");
                call(view, "save");
            });
            close();
            byte[] migrated = saved(storage, "progress-v10");
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(migrated))) {
                require(input.readInt() == 0x46503130 && input.readInt() == 3, "native loader writes progress format 3");
            }
            launch();
            ui(() -> {
                same(migrated, bytes(read(profile, "progress")), "second native load is byte-exact and migration is idempotent");
                same(battle, bytes(read(scene, "model")), "second native load retains exact battle");
                require("id".equals(read(profile, "language")), "second native load retains selected language");
            });
            close();
        }
    }

    private static byte[] historicalProgress(int format) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x46503130); out.writeInt(format);
        for (int columns : new int[] {60, 9}) for (int difficulty = 0; difficulty < 3; difficulty++) for (int i = 0; i < columns; i++) {
            boolean earned = difficulty == 1 && i == (columns == 60 ? 2 : 0);
            out.writeInt(earned ? columns == 60 ? 1600 : 1100 : 0);
            out.writeInt(earned ? columns == 60 ? 2 : 1 : 0);
            out.writeFloat(earned ? columns == 60 ? 70f : 55f : 0f);
        }
        out.writeBoolean(true); out.writeBoolean(false); out.writeBoolean(false);
        out.writeInt(1); out.writeInt(1); out.writeInt(1);
        out.writeUTF("daily-v10-1:2026-10-08"); out.writeInt(1200); out.writeInt(1); out.writeFloat(50f);
        if (format == 2) out.writeInt(0); // No revised mission records in this historical fixture.
        out.flush(); CRC32 crc = new CRC32(); crc.update(bytes.toByteArray());
        out.writeInt((int)crc.getValue()); out.flush(); return bytes.toByteArray();
    }

    private void launch() throws Exception {
        activity = startActivitySync(new Intent().setClassName("com.frontline.offline", "com.frontline.offline.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            ui(() -> {
                callActivityOnPause(activity);
                view = (View)read(activity, "battleView"); scene = read(view, "scene"); profile = read(scene, "profile");
            });
            if (view.getWidth() > 0 && view.getHeight() > 0) break;
            SystemClock.sleep(20); waitForIdleSync();
        } while (SystemClock.uptimeMillis() < deadline);
        require(view.getWidth() > 0 && view.getHeight() > 0, "native view laid out");
        ui(() -> { call(scene, "back"); require(!(boolean)read(view, "running"), "native animation frozen for fixtures"); });
        output = new File(activity.getExternalFilesDir(null), "v11-native/" + shot);
        require(output.isDirectory() || output.mkdirs(), "fixture artifact directory ready");
    }

    private void unlockPreservesClears() throws Exception {
        Object progress = read(profile, "progress");
        ((int[][])read(progress, "best"))[1][0] = 1700;
        ((int[][])read(progress, "stars"))[1][0] = 2;
        ((float[][])read(progress, "times"))[1][0] = 75;
        byte[] before = bytes(progress);
        int[] best = ((int[])read(profile, "best")).clone(), stars = ((int[])read(profile, "stars")).clone();
        float[] times = ((float[])read(profile, "times")).clone();
        int count = ((Object[])read(type("GameModel"), "LEVELS")).length;
        boolean[] cleared = new boolean[count];
        for (int i = 0; i < count; i++) cleared[i] = (boolean)call(profile, "cleared", i);
        tap("settings");
        require(!(boolean)call(scene, "redeemUnlockCode", "12344"), "invalid unlock code rejected");
        require((boolean)call(scene, "redeemUnlockCode", "12345"), "existing unlock code accepted");
        require((int)read(profile, "unlocked") == count - 1, "unlock code opens all sectors");
        require(Arrays.equals(best, (int[])read(profile, "best")) && Arrays.equals(stars, (int[])read(profile, "stars"))
            && Arrays.equals(times, (float[])read(profile, "times")), "unlock keeps legacy scores/stars/times");
        same(before, bytes(progress), "unlock preserves difficulty records and mastery");
        for (int i = 0; i < count; i++) require(cleared[i] == (boolean)call(profile, "cleared", i), "unlock does not manufacture clear " + i);
        require((boolean)call(scene, "redeemUnlockCode", "12345"), "unlock is repeatable");
        same(before, bytes(progress), "repeated code leaves progress unchanged");
        tap("back");
    }

    private void languageInvariants() throws Exception {
        Object model = read(scene, "model");
        byte[] battle = bytes(model), progress = bytes(read(profile, "progress"));
        long seed = (long)read(model, "seed");
        int difficulty = (int)read(model, "difficulty"), preference = (int)read(profile, "difficulty");
        String date = (String)read(model, "dailyDate"), version = (String)read(model, "dailyVersion");
        call(view, "save");
        Map<String, Object> prefs = withoutLanguage(storage.getAll());
        render().recycle();
        for (String hidden : new String[] {"language_hi", "confirm_replace", "tutorial_next", "not-a-button"}) call(scene, "activateButton", hidden);
        require("en".equals(read(profile, "language")) && (int)read(scene, "overlay") == constant("GameScene", "MENU"), "hidden and unknown controls cannot activate on menu");
        tap("settings");
        render().recycle();
        int settings = virtualId("settings");
        require(provider().createAccessibilityNodeInfo(settings) == null
            && !provider().performAction(settings, AccessibilityNodeInfo.ACTION_CLICK, null), "underlying home gear is absent from Settings accessibility");
        call(scene, "activateButton", "daily");
        require((int)read(scene, "overlay") == constant("GameScene", "SETTINGS"), "hidden Daily cannot activate from Settings");
        for (String language : new String[] {"id", "hi", "en", "hi", "id", "en"}) {
            activate("language_" + language);
            require(language.equals(read(profile, "language")), "language applies immediately " + language);
            checkLabels(); // No redraw: spoken labels must change in the same open Settings screen.
            require(language.equals(storage.getString("language", "")), "explicit selection synchronously persisted " + language);
            require(read(scene, "model") == model, "language does not replace battle");
            same(battle, bytes(model), "language leaves battle and RNG bytes unchanged");
            same(progress, bytes(read(profile, "progress")), "language leaves progress unchanged");
            require(seed == (long)read(model, "seed") && difficulty == (int)read(model, "difficulty")
                && preference == (int)read(profile, "difficulty") && date.equals(read(model, "dailyDate"))
                && version.equals(read(model, "dailyVersion")), "language preserves difficulty, Daily date/seed/version");
            require(prefs.equals(withoutLanguage(storage.getAll())), "only language preference changes");
            call(scene, "update", .1f);
            same(battle, bytes(model), "open Settings freezes objective and simulation");
        }
        tap("back");
    }

    private void localizedScreens(String language) throws Exception {
        selectLanguage(language);
        tap("settings"); capture(language, "settings", "drawSettings"); tap("back");
        homeBounds(); capture(language, "menu", "drawMenu");
        tap("tutorial");
        for (int step = 0; step < 5; step++) {
            write(scene, "tutorialStep", step);
            capture(language, "tutorial-step-" + (step + 1), "drawTutorial");
        }
        tap("tutorial_skip");
        tap("mastery"); capture(language, "mastery", "drawMastery"); tap("home");
        tap("help"); capture(language, "rules", "drawHelp"); tap("help_back");
        tap("sectors");
        Object[] chapters = (Object[])read(type("Campaign"), "CHAPTERS");
        require(chapters.length == 10, "ten curated chapter pages covered natively");
        for (int chapter = 0; chapter < chapters.length; chapter++) {
            write(scene, "sectorPage", chapter);
            List<TextLine> lines = capture(language, "chapter-" + (chapter + 1), "drawSectors");
            String title = (String)read(chapters[chapter], "name");
            String translated = (String)call(type("Localization"), "translate", language, title);
            require(joined(lines).contains(translated), "native chapter page renders localized title " + (chapter + 1));
            if (!language.equals("en")) require(!translated.equals(title), "chapter title translated " + title);
        }
        tap("back");
        tap("challenges"); capture(language, "challenges", "drawChallenges");
        tap("mission_tab_2"); tap("challenge_6"); capture(language, "budget-briefing", "drawBriefing");
        tap("brief_back"); tap("home");
        tap("daily"); capture(language, "daily", "drawDaily");
        tap("today_mission"); capture(language, "daily-briefing", "drawBriefing");
        byte[] retained = bytes(read(scene, "model"));
        tap("begin_attempt");
        require((int)read(scene, "overlay") == constant("GameScene", "CONFIRM"), "protected attempt requires confirmation");
        capture(language, "confirmation", "drawConfirmation"); tap("cancel_replace");
        same(retained, bytes(read(scene, "model")), "cancel preserves protected Daily battle");
        tap("brief_back"); tap("home");
        budgetWarning(language);
        for (int mission : new int[] {0, 3, 6}) {
            resultFixture(mission, true); capture(language, "mission-" + mission + "-win", "drawMissionResult");
            resultFixture(mission, false); capture(language, "mission-" + mission + "-loss", "drawMissionResult");
        }
        // Restored terminal facts must format in the current language, not the saved language.
        byte[] loss = bytes(read(scene, "model"));
        call(scene, "installAttempt", call(type("GameModel"), "restore", loss));
        write(scene, "overlay", constant("GameScene", "RESULT"));
        capture(language, "restored-budget-loss", "drawMissionResult");
        if (language.equals("hi")) hindiGlyphs();
        installMission(3, true); write(scene, "overlay", constant("GameScene", "MENU"));
    }

    private void budgetWarning(String language) throws Exception {
        installMission(6, false);
        Object model = read(scene, "model");
        int home = (int)call(model, "originalKing", 0);
        List<?> tiles = (List<?>)read(model, "territories");
        write(tiles.get(home), "troops", 125d);
        render().recycle();
        int target = home == 0 ? 1 : 0;
        float[] from = (float[])call(scene, "position", home), to = (float[])call(scene, "position", target);
        touch(MotionEvent.ACTION_DOWN, from); touch(MotionEvent.ACTION_MOVE, to);
        require((int)read(scene, "selected") == home && (int)read(model, "unitsSent") == 0, "native budget preview does not dispatch");
        List<TextLine> lines = capture(language, "budget-warning", null);
        String warning = (String)call(type("Localization"), "text", language, "budget.warning_short", new Object[] {125, 120});
        String all = joined(lines);
        require(all.contains(warning), "localized pre-action budget warning uses actual integer amount");
        touch(MotionEvent.ACTION_CANCEL, to);
        require((int)read(model, "outcome") == constant("GameModel", "PLAYING"), "preview cancellation keeps attempt alive");
        require((int)call(model, "launch", home, target, 1d) == 125, "fixture dispatch uses actual launch calculation");
        require((int)read(model, "outcome") == constant("GameModel", "LOST")
            && (int)read(model, "terminalReason") == constant("GameModel", "TERMINAL_BUDGET"), "over-budget rule and reason preserved");
    }

    private void resultFixture(int mission, boolean won) throws Exception {
        installMission(mission, false);
        Object model = read(scene, "model");
        float seconds = (float)read(model, "objectiveSeconds");
        write(model, "elapsed", won ? Math.max(25f, seconds) : 12f);
        write(model, "objectiveProgress", mission == 6 ? 0f : won ? seconds : 12f);
        write(model, "unitsSent", mission == 6 ? won ? 60 : 125 : 40);
        write(model, "outcome", constant("GameModel", won ? "WON" : "LOST"));
        if (!won && mission == 3) write(model, "startingKingLost", true);
        write(model, "terminalReason", constant("GameModel", won ? "TERMINAL_VICTORY"
            : mission == 6 ? "TERMINAL_BUDGET" : mission == 3 ? "TERMINAL_PROTECTED_KING" : "TERMINAL_SURRENDER"));
        write(scene, "overlay", constant("GameScene", "RESULT"));
        write(scene, "resultRecorded", true);
    }

    private void installMission(int id, boolean daily) throws Exception {
        Class<?> challenge = type("Challenge");
        Object preset = ((Object[])read(challenge, "PRESETS"))[id];
        String date = daily ? "2026-10-09" : "";
        long seed = daily ? (long)call(challenge, "dailySeed", date) : 110000L + id;
        Object model = call(preset, "create", 1, seed, id, date);
        if (id == 3 && daily) { write(model, "elapsed", 12f); write(model, "objectiveProgress", 12f); }
        call(scene, "installAttempt", model);
    }

    private void selectLanguage(String language) throws Exception {
        tap("settings"); render().recycle(); activate("language_" + language);
        require(language.equals(read(profile, "language")), "selected language " + language);
        checkLabels(); tap("back");
    }

    private void homeBounds() throws Exception {
        render().recycle();
        Object gear = button("settings"), daily = button("daily");
        Rect gearPx = bounds(gear), dailyPx = bounds(daily);
        float density = view.getResources().getDisplayMetrics().density;
        float scale = (float)read(view, "scale"), minimum = (float)read(scene, "minimumTouchSize");
        require(Math.abs(minimum * scale / density - 48) < .1f, "scene minimum touch size reflects native density/scale");
        require(gearPx.width() / density >= 48 - .5f && gearPx.height() / density >= 48 - .5f, "home gear target is at least 48 dp");
        require(dailyPx.width() / density >= 48 - .5f && dailyPx.height() / density >= 48 - .5f, "Daily has independent 48 dp target");
        require(gearPx.top >= (int)read(view, "safeTop") && gearPx.bottom < dailyPx.top
            && Math.abs(gearPx.right - dailyPx.right) <= 1 && gearPx.centerX() > view.getWidth() / 2, "Daily is directly below top-right safe-area gear");
        int gears = 0, dailies = 0;
        for (Object b : buttons()) {
            String id = (String)read(b, "id");
            if ("settings".equals(id)) gears++;
            if ("daily".equals(id)) dailies++;
            Rect r = bounds(b);
            require(r.left >= 0 && r.right <= view.getWidth() && r.top >= (int)read(view, "safeTop")
                && r.bottom <= view.getHeight() - (int)read(view, "safeBottom"), "home control stays in safe viewport " + read(b, "id"));
            if (!id.equals("settings") && !id.equals("daily")) require(!Rect.intersects(r, gearPx) && !Rect.intersects(r, dailyPx), "top-right stack avoids primary controls");
        }
        require(gears == 1 && dailies == 1, "home has no duplicate Settings/Daily entry");
        checkLabels();
    }

    private void checkLabels() throws Exception {
        List<?> rendered = buttons();
        AccessibilityNodeInfo host = provider().createAccessibilityNodeInfo(View.NO_ID);
        require(host != null && host.getChildCount() == rendered.size(), "native host exposes exactly rendered controls");
        host.recycle();
        Set<Integer> ids = new HashSet<>();
        for (Object b : rendered) {
            String id = (String)read(b, "id"), label = (String)read(b, "label");
            require(label != null && !label.trim().isEmpty(), "rendered control has spoken label " + id);
            require(ids.add(virtualId(id)), "virtual accessibility IDs are unique");
            AccessibilityNodeInfo node = provider().createAccessibilityNodeInfo(virtualId(id));
            require(node != null, "native node exists " + id);
            try {
                require(label.contentEquals(node.getContentDescription()) && label.contentEquals(node.getText()), "immediate native spoken/text label " + id);
                require(node.isClickable() && node.isEnabled() && "android.widget.Button".contentEquals(node.getClassName()), "native button semantics " + id);
                Rect nativeBounds = new Rect(); node.getBoundsInParent(nativeBounds);
                require(nativeBounds.equals(bounds(b)), "native node bounds follow scene/density/insets " + id);
            } finally { node.recycle(); }
        }
        for (String[] selector : new String[][] {{"language_en", "English"}, {"language_id", "Bahasa Indonesia"}, {"language_hi", "\u0939\u093f\u0928\u094d\u0926\u0940"}}) {
            Object b = optionalButton(selector[0]);
            if (b != null) require(selector[1].equals(read(b, "label")), "selector retains native recovery name " + selector[0]);
        }
        String language = (String)read(profile, "language");
        for (String[] named : new String[][] {{"settings", "Settings"}, {"daily", "Daily Mission"}, {"back", "Back"}}) {
            Object b = optionalButton(named[0]);
            if (b == null) continue;
            String translated = (String)call(type("Localization"), "translate", language, named[1]);
            require(translated.equals(read(b, "label")), "localized accessible name " + named[0]);
            if (!language.equals("en")) require(!translated.equals(named[1]), "non-English spoken name is translated " + named[0]);
        }
        require(!provider().performAction(virtualId("not-a-button"), AccessibilityNodeInfo.ACTION_CLICK, null), "unknown native virtual action rejected");
    }

    private List<TextLine> capture(String language, String name, String drawMethod) throws Exception {
        Bitmap bitmap = render();
        List<TextLine> lines;
        try {
            int populated = 0;
            for (int y = (int)read(view, "safeTop"); y < bitmap.getHeight() - (int)read(view, "safeBottom"); y += 8)
                for (int x = 0; x < bitmap.getWidth(); x += 8) if (bitmap.getPixel(x, y) != 0xFF17191B) populated++;
            require(populated > 100, "nonblank native fixture " + language + "/" + name);
            lines = textLayout(drawMethod);
            checkLabels();
            String visible = joined(lines);
            require(!visible.contains("daily-v10") && !visible.contains("daily-v11") && !visible.contains("playtest candidates")
                && !visible.contains("Initial timers and budgets"), "no engineering tokens in fixture " + name);
            if ("drawMissionResult".equals(drawMethod)) {
                Object result = call(type("ObjectiveResult"), "evaluate", read(scene, "model"));
                String reason = (String)call(type("Localization"), "text", language, read(result, "code"), call(result, "numericArguments"));
                require(visible.contains(reason), "native result renders localized objective/terminal facts " + name);
                require(!visible.contains("TIME / 3-STAR TARGET") && !visible.contains("NO FASTER TIME"), "mission result avoids campaign speed messaging");
            }
            savePng(bitmap, "fixture-" + language + "-" + name + ".png");
            StringBuilder report = new StringBuilder("FIXTURE: reflective deterministic model/scene state; native Android text measurements.\n");
            for (TextLine line : lines) report.append(line.bounds).append('\t').append(line.text).append('\n');
            try (FileOutputStream stream = new FileOutputStream(new File(output, "fixture-" + language + "-" + name + "-layout.txt"))) {
                stream.write(report.toString().getBytes(StandardCharsets.UTF_8));
            }
        } finally { bitmap.recycle(); }
        return lines;
    }

    private static final class TextLine {
        final String text; final RectF bounds;
        TextLine(String text, RectF bounds) { this.text = text; this.bounds = bounds; }
    }

    private List<TextLine> textLayout(String drawMethod) throws Exception {
        List<TextLine> lines = new ArrayList<>();
        int[] clipped = {0};
        List<RectF> clips = new ArrayList<>();
        boolean commandScreen = "drawMenu".equals(drawMethod) || "drawSectors".equals(drawMethod) || "drawSettings".equals(drawMethod);
        Object graphics = Proxy.newProxyInstance(activity.getClassLoader(), new Class<?>[] {type("GameScene$Graphics")}, (proxy, method, args) -> {
            if (method.getName().equals("measureText")) return call(view, "measureText", args);
            if (method.getName().equals("clip")) { clipped[0]++; clips.add(new RectF((float)args[0],(float)args[1],(float)args[0]+(float)args[2],(float)args[1]+(float)args[3])); }
            if (method.getName().equals("unclip")) { clipped[0]--; clips.remove(clips.size()-1); }
            if (method.getName().equals("text") && (clipped[0] == 0 || commandScreen) && !((String)args[0]).isEmpty()) {
                String text = (String)args[0]; float x = (float)args[1], baseline = (float)args[2];
                float width = (float)call(view, "measureText", text, args[3], args[5]);
                Paint paint = (Paint)read(view, "paint"); Rect ink = new Rect();
                paint.getTextBounds(text, 0, text.length(), ink);
                int align = (int)args[6]; float left = x - (align == 1 ? width / 2 : align == 2 ? width : 0);
                RectF bounds = new RectF(left + ink.left, baseline + ink.top, left + ink.right, baseline + ink.bottom);
                if (clipped[0] > 0 && !clips.get(clips.size()-1).contains(bounds)) return null;
                lines.add(new TextLine(text,bounds));
            }
            return null;
        });
        // Foreground-only measurement avoids counting obscured background text or mutating the rendered button list.
        @SuppressWarnings("unchecked") List<Object> actual = (List<Object>)read(scene, "buttons");
        List<Object> original = new ArrayList<>(actual);
        try {
            if (drawMethod == null) call(scene, "render", graphics, read(scene, "height"));
            else { actual.clear(); call(scene, drawMethod, call(scene, "localized", graphics)); }
        } finally { actual.clear(); actual.addAll(original); }
        float height = (float)read(scene, "height");
        for (TextLine line : lines) {
            require(line.bounds.left >= -1 && line.bounds.right <= 421 && line.bounds.top >= -1 && line.bounds.bottom <= height + 1,
                "native text ink inside scene: " + line.text + " " + line.bounds);
        }
        if (drawMethod != null) {
            for (int i = 0; i < lines.size(); i++) for (int j = i + 1; j < lines.size(); j++) {
                RectF overlap = new RectF(lines.get(i).bounds);
                require(!overlap.intersect(lines.get(j).bounds) || overlap.width() <= 1 || overlap.height() <= 1,
                    "native text lines do not overlap: " + lines.get(i).text + " / " + lines.get(j).text);
            }
            for (Object b : buttons()) {
                String label = (String)read(b, "label");
                float x = (float)read(b, "x"), y = (float)read(b, "y"), w = (float)read(b, "width"), h = (float)read(b, "height");
                for (TextLine line : lines) if (label.contains(line.text) && line.bounds.centerX() >= x && line.bounds.centerX() <= x + w
                    && line.bounds.centerY() >= y && line.bounds.centerY() <= y + h)
                    require(line.bounds.left >= x - 1 && line.bounds.right <= x + w + 1 && line.bounds.top >= y - 1 && line.bounds.bottom <= y + h + 1,
                        "native wrapped control label fits " + read(b, "id") + ": " + line.text);
            }
        }
        return lines;
    }

    private void hindiGlyphs() throws Exception {
        String sample = "\u0939\u093f\u0928\u094d\u0926\u0940  \u0915\u093f\u0932\u093e  \u0938\u0941\u0930\u0915\u094d\u0937\u093e";
        float nativeWidth = (float)call(view, "measureText", sample, 32f, false);
        Paint paint = new Paint((Paint)read(view, "paint"));
        for (int i = 0; i < sample.length(); i++) if (sample.charAt(i) != ' ')
            require(paint.hasGlyph(sample.substring(i, i + 1)), "native font contains Hindi letter/mark U+" + Integer.toHexString(sample.charAt(i)));
        float[] advances = new float[sample.length()];
        float shaped = paint.getTextRunAdvances(sample.toCharArray(), 0, sample.length(), 0, sample.length(), false, advances, 0);
        float stringWidth = paint.measureText(sample), charWidth = paint.measureText(sample.toCharArray(), 0, sample.length());
        float caretWidth = paint.getRunAdvance(sample, 0, sample.length(), 0, sample.length(), false, sample.length());
        float sum = 0; for (float advance : advances) sum += advance;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        glyphDiagnostics = "Hindi Paint probe: SDK=" + android.os.Build.VERSION.SDK_INT + "; nativeString=" + nativeWidth
            + "; paintString=" + stringWidth + "; paintChars=" + charWidth + "; run=" + shaped + "; runSum=" + sum
            + "; caret=" + caretWidth + "; ceilRun=" + Math.ceil(shaped) + "; delta=" + (stringWidth - shaped)
            + "; size=" + paint.getTextSize() + "; scaleX=" + paint.getTextScaleX() + "; skewX=" + paint.getTextSkewX()
            + "; letterSpacing=" + paint.getLetterSpacing() + "; wordSpacing=" + paint.getWordSpacing()
            + "; flags=" + paint.getFlags() + "; hinting=" + paint.getHinting() + "; style=" + paint.getStyle()
            + "; align=" + paint.getTextAlign() + "; subpixel=" + paint.isSubpixelText() + "; linear=" + paint.isLinearText()
            + "; elegant=" + paint.isElegantTextHeight() + "; locales=" + paint.getTextLocales()
            + "; typeface=" + paint.getTypeface() + "; typefaceStyle=" + paint.getTypeface().getStyle()
            + "; nativeRegular=" + paint.getTypeface().equals(read(view, "regular"))
            + "; features=" + paint.getFontFeatureSettings() + "; variation=" + paint.getFontVariationSettings()
            + "; metrics=" + metrics.top + "," + metrics.ascent + "," + metrics.descent + "," + metrics.bottom
            + "; advances=" + Arrays.toString(advances);
        recordGlyphDiagnostics();
        require(Float.isFinite(shaped) && shaped > 0 && Float.isFinite(caretWidth) && Float.isFinite(sum), "native Hindi run widths are finite and positive");
        for (float advance : advances) require(Float.isFinite(advance), "native Hindi character advance is finite");
        require(Math.abs(shaped - sum) < .01f && Math.abs(shaped - caretWidth) < .01f, "native Hindi run/advance sum/String caret shaping agree");
        // Android's measureText ceil-rounds advances; run APIs preserve the fractional width.
        require(nativeWidth == stringWidth && charWidth == stringWidth && stringWidth == (float)Math.ceil(shaped),
            "native String/char measurement agrees with ceil-rounded run width; " + glyphDiagnostics);
        int width = (int)stringWidth + 32;
        Bitmap glyphs = Bitmap.createBitmap(width, 96, Bitmap.Config.ARGB_8888);
        Bitmap boxes = Bitmap.createBitmap(width, 96, Bitmap.Config.ARGB_8888);
        try {
            paint.setColor(0xFF000000); paint.setTextAlign(Paint.Align.LEFT);
            Canvas canvas = new Canvas(glyphs); canvas.drawColor(0xFFFFFFFF); canvas.drawText(sample, 16, 60, paint);
            Canvas missing = new Canvas(boxes); missing.drawColor(0xFFFFFFFF);
            missing.drawText(sample.replaceAll("[^ ]", "\uFFFD"), 16, 60, paint);
            int ink = 0, different = 0;
            for (int y = 0; y < 96; y++) for (int x = 0; x < width; x++) {
                if (glyphs.getPixel(x, y) != 0xFFFFFFFF) ink++;
                if (glyphs.getPixel(x, y) != boxes.getPixel(x, y)) different++;
            }
            require(ink > 100 && different > 100, "Hindi shaped pixels are nonblank and not repeated missing-glyph boxes");
            glyphDiagnostics += "; inkPixels=" + ink + "; missingBoxDiffPixels=" + different;
            recordGlyphDiagnostics();
            savePng(glyphs, "fixture-hi-native-glyph-probe.png");
        } finally { glyphs.recycle(); boxes.recycle(); }
    }

    private void recordGlyphDiagnostics() throws Exception {
        android.util.Log.i("FrontlineV11Glyph", glyphDiagnostics);
        try (FileOutputStream stream = new FileOutputStream(new File(output, "fixture-hi-native-glyph-metrics.txt"))) {
            stream.write((glyphDiagnostics + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private void activate(String id) throws Exception {
        reveal(id);
        require(provider().performAction(virtualId(id), AccessibilityNodeInfo.ACTION_CLICK, null), "native accessible activation " + id);
    }
    private void tap(String id) throws Exception {
        Object b = reveal(id);
        float[] point = {(float)read(b, "x") + (float)read(b, "width") / 2, (float)read(b, "y") + (float)read(b, "height") / 2};
        touch(MotionEvent.ACTION_DOWN, point); touch(MotionEvent.ACTION_UP, point);
    }
    private void touch(int action, float[] point) throws Exception {
        float scale = (float)read(view, "scale");
        float x = point[0] * scale + (float)read(view, "offsetX"), y = point[1] * scale + (int)read(view, "safeTop");
        require(x >= 0 && y >= 0 && x < view.getWidth() && y < view.getHeight(), "native touch stays in viewport");
        long now = SystemClock.uptimeMillis(); MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try { require(view.dispatchTouchEvent(event), "native view consumes touch"); } finally { event.recycle(); }
    }
    private Object reveal(String id) throws Exception {
        Object b = visible(id); if (b != null) return b;
        String section = id.startsWith("language_") ? "language_picker"
            : id.equals("playtest_log") || id.equals("export_log") || id.equals("clear_log") ? "tools" : null;
        if (section != null) { Object header = visible(section); if (header != null) call(scene,"activateButton",section); }
        b = visible(id); require(b != null,"scroll-reachable native control "+id); return b;
    }
    private Object visible(String id) throws Exception {
        render().recycle(); Object b = optionalButton(id); if (b != null) return b;
        call(scene,"scrollScreen",-10000f);
        for (int step = 0; step < 50; step++) { render().recycle(); b = optionalButton(id); if (b != null) return b; if (!(boolean)call(scene,"scrollScreen",40f)) break; }
        return null;
    }
    private Bitmap render() { Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap)); return bitmap; }
    private void savePng(Bitmap bitmap, String name) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(new File(output, name))) { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "fixture PNG written"); }
    }
    private AccessibilityNodeProvider provider() { AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider(); require(provider != null, "native accessibility provider installed"); return provider; }
    private static int virtualId(String id) { return id.hashCode() & 0x7fffffff; }
    private List<?> buttons() throws Exception { return (List<?>)call(scene, "accessibleButtons"); }
    private Object optionalButton(String id) throws Exception { for (Object b : buttons()) if (id.equals(read(b, "id"))) return b; return null; }
    private Object button(String id) throws Exception { Object b = optionalButton(id); require(b != null, "actual rendered control " + id); return b; }
    private Rect bounds(Object b) throws Exception {
        float scale = (float)read(view, "scale"), offset = (float)read(view, "offsetX"); int top = (int)read(view, "safeTop");
        float x = (float)read(b, "x"), y = (float)read(b, "y");
        return new Rect(Math.round(offset + x * scale), Math.round(top + y * scale),
            Math.round(offset + (x + (float)read(b, "width")) * scale), Math.round(top + (y + (float)read(b, "height")) * scale));
    }
    private static String joined(List<TextLine> lines) { StringBuilder text = new StringBuilder(); for (TextLine line : lines) { if (text.length() > 0) text.append(' '); text.append(line.text); } return text.toString(); }
    private static boolean supported(String language) { return Arrays.asList(LANGUAGES).contains(language); }
    private static Map<String, Object> withoutLanguage(Map<String, ?> values) { Map<String, Object> copy = new HashMap<>(values); copy.remove("language"); return copy; }
    private static byte[] saved(SharedPreferences prefs, String key) { return Base64.decode(prefs.getString(key, ""), Base64.DEFAULT); }

    private void close() throws Exception {
        if (activity == null) return;
        ui(() -> { callActivityOnPause(activity); activity.finish(); });
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!activity.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
        waitForIdleSync(); require(activity.isDestroyed(), "Activity destroyed before preference replacement"); activity = null;
    }
    private void restoreOriginal() throws Exception {
        if (backup.getBoolean(MARKER, false)) {
            Map<String, Object> original = new HashMap<>(backup.getAll()); original.remove(MARKER);
            copy(storage, original);
            require(storage.getAll().equals(original), "all original preference values restored exactly");
            require(backup.edit().clear().commit(), "backup cleared only after verified restoration");
        }
        require(checkpoint.edit().clear().commit(), "process checkpoint cleared");
    }
    @SuppressWarnings("unchecked") private void copy(SharedPreferences destination, Map<String, ?> values) {
        SharedPreferences.Editor edit = destination.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String) edit.putString(key, (String)value);
            else if (value instanceof Integer) edit.putInt(key, (Integer)value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean)value);
            else if (value instanceof Long) edit.putLong(key, (Long)value);
            else if (value instanceof Float) edit.putFloat(key, (Float)value);
            else if (value instanceof Set) edit.putStringSet(key, new HashSet<>((Set<String>)value));
            else throw new IllegalArgumentException("Unsupported preference type: " + key);
        }
        require(edit.commit(), "preferences synchronously committed");
    }
    private interface Step { void run() throws Exception; }
    private void ui(Step step) throws Exception {
        Throwable[] error = {null}; runOnMainSync(() -> { try { step.run(); } catch (Throwable failed) { error[0] = failed; } });
        if (error[0] != null) throw new Exception("Native V11 UI check", error[0]);
    }
    private void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private void same(byte[] a, byte[] b, String message) { require(Arrays.equals(a, b), message); }
    private Class<?> type(String name) throws Exception { return Class.forName("com.frontline.offline." + name, true, activity.getClassLoader()); }
    private int constant(String type, String name) throws Exception { return (int)read(type(type), name); }
    private static byte[] bytes(Object object) throws Exception { return (byte[])call(object, "save"); }
    private static Field field(Object object, String name) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>)object : object.getClass();
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object read(Object object, String name) throws Exception { return field(object, name).get(object instanceof Class ? null : object); }
    private static void write(Object object, String name, Object value) throws Exception { field(object, name).set(object, value); }
    private static Object call(Object object, String name, Object... args) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>)object : object.getClass();
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name) && matches(method.getParameterTypes(), args)) {
            method.setAccessible(true);
            try { return method.invoke(object instanceof Class ? null : object, args); }
            catch (InvocationTargetException failed) { throw new Exception(type.getSimpleName() + "." + name, failed.getCause()); }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static boolean matches(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type.isPrimitive()) type = type == int.class ? Integer.class : type == float.class ? Float.class
                : type == double.class ? Double.class : type == long.class ? Long.class : type == boolean.class ? Boolean.class : type;
            if (args[i] == null ? type.isPrimitive() : !type.isInstance(args[i])) return false;
        }
        return true;
    }
}
