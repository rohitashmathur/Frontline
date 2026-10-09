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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native Canvas/touch fixtures, not human gameplay. App classes are reflection-only. */
public final class NativeV11LogisticsTest extends Instrumentation {
    private static final String MARKER = "v11-logistics-original-ready";
    private static final String PREFS = "frontline-v1";
    private static final String RECORDS = "logistics-records-v11";
    private static final String[] LANGUAGES = {"en", "id", "hi"};
    private Activity activity;
    private View view;
    private Object scene, profile;
    private SharedPreferences storage, backup, checkpoint;
    private File output;
    private String mode, shot;
    private boolean keepCheckpoint;
    private int checks, captures;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        Bundle args = arguments == null ? new Bundle() : arguments;
        mode = args.getString("mode", "suite");
        shot = args.getString("shot", "v11-logistics");
        keepCheckpoint = "true".equals(args.getString("checkpoint", "false"));
        if (!Arrays.asList("suite", "relaunch", "restore").contains(mode) || !shot.matches("[a-z0-9-]+"))
            throw new IllegalArgumentException("Logistics harness arguments");
        start();
    }

    @Override public void onStart() {
        storage = getTargetContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        backup = getTargetContext().getSharedPreferences("frontline-v11-logistics-original", Context.MODE_PRIVATE);
        checkpoint = getTargetContext().getSharedPreferences("frontline-v11-logistics-checkpoint", Context.MODE_PRIVATE);
        Throwable failure = null;
        boolean retain = false;
        try {
            if (mode.equals("suite")) {
                require(!backup.getBoolean(MARKER, false), "interrupted fixture requires restore-only first");
                copy(backup, storage.getAll());
                require(backup.edit().putBoolean(MARKER, true).commit(), "original preferences backed up before fixtures");
                require(storage.edit().clear().putBoolean("tutorial-seen", true).putBoolean("camera-guide-seen", true)
                    .putBoolean("sound", false).putBoolean("music", false).putBoolean("haptics", false)
                    .putString("language", "en").putInt("difficulty", 1).putInt("unlocked", 4).putInt("wins", 7)
                    .putInt("best-0", 1500).putInt("stars-0", 2).putFloat("time-0", 80).commit(), "staged preference fixture installed");
                launch();
                ui(() -> {
                    require(constant("GameScene", "LOGISTICS") == 18 && constant("GameScene", "LOGISTICS_RESULT") == 19,
                        "integrated Logistics scene contract");
                    Object progress = read(profile, "progress");
                    ((int[][])read(progress, "best"))[1][1] = 1700;
                    ((int[][])read(progress, "stars"))[1][1] = 2;
                    ((float[][])read(progress, "times"))[1][1] = 75f;
                    call(profile, "reconcileProgress");
                    call(view, "save");
                    for (String language : LANGUAGES) localizedFixtures(language);
                    checkpointFixture();
                });
                close();
                copy(checkpoint, storage.getAll());
                retain = keepCheckpoint;
            } else if (mode.equals("relaunch")) {
                require(backup.getBoolean(MARKER, false), "fresh process has original preference backup");
                require(storage.getAll().equals(checkpoint.getAll()), "process death retains all checkpoint preferences exactly");
                launch();
                ui(() -> {
                    assertCheckpoint();
                    tap("logistics");
                    require((int)read(scene, "overlay") == 18, "fresh process exposes Logistics selector");
                    byte[] before = bytes(read(scene, "model"));
                    tap("continue_logistics");
                    same(before, bytes(read(scene, "model")), "native Continue preserves exact mid-route save");
                    capture("hi", "process-mid-route", true);
                });
                close();
                corruptedRecords();
                corruptedRoute();
            }
        } catch (Throwable error) { failure = error; }
        finally {
            try { close(); }
            catch (Throwable cleanup) { failure = append(failure, cleanup); }
            if (!retain || failure != null) {
                try { restoreOriginal(); }
                catch (Throwable cleanup) { failure = append(failure, cleanup); }
            }
        }
        Bundle result = new Bundle();
        result.putString("stream", failure == null
            ? "PASS: native V11 Logistics " + mode + "; " + checks + " checks; " + captures + " Canvas fixtures; "
                + (retain ? "process checkpoint retained" : "original preferences restored") + "; staged fixtures, not human gameplay\n"
            : "FAIL: " + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void localizedFixtures(String language) throws Exception {
        selectLanguage(language);
        tap("logistics");
        Evidence selector = capture(language, "selector", false);
        require(selector.contains(tr("logistics.title")) && selector.contains(tr("logistics.experimental")), "localized selector title and experimental status");
        for (int map = 0; map < 3; map++) button("logistics_map_" + map);
        for (int difficulty = 0; difficulty < 3; difficulty++) button("difficulty_" + difficulty);
        for (int map = 0; map < 3; map++) {
            byte[] progress = bytes(read(profile, "progress")), records = bytes(read(profile, "logisticsRecords"));
            Map<String, Object> classic = classicPreferences();
            tap("difficulty_" + map);
            tap("logistics_map_" + map);
            assertIdentity(map, map);
            same(progress, bytes(read(profile, "progress")), "map selection preserves nonempty Classic progress");
            same(records, bytes(read(profile, "logisticsRecords")), "map selection grants no Logistics record");
            tap("quarter");
            Object model = read(scene, "model");
            int source = (int)call(model, "originalKing", 0), hostile = (int)call(model, "originalKing", 1);
            require(route(model, source, hostile) == null, "staged initial hostile target has no connected route");
            byte[] before = bytes(model);
            drag(source, hostile, false);
            Evidence refusal = capture(language, "map-" + map + "-refused", true);
            require(refusal.contains(tr("logistics.refused")) || refusal.contains(tr("logistics.refusal.no_route")), "native illegal release renders localized refusal");
            same(before, bytes(model), "illegal native release changes no troops, counters, convoy, RNG or model bytes");
            same(progress, bytes(read(profile, "progress")), "refusal leaves Classic progress untouched");
            same(records, bytes(read(profile, "logisticsRecords")), "refusal leaves separate records untouched");

            int[] path = connectedFixture(model);
            source = path[0]; hostile = path[path.length - 1];
            before = bytes(model);
            float eta = (float)call(model, "routeEta", path);
            drag(source, hostile, true);
            Evidence preview = capture(language, "map-" + map + "-route-preview", true);
            require(preview.contains(tr("logistics.route_preview", path.length - 1, Math.round(eta * 10) / 10.0)),
                "observed localized route ETA equals model.routeEta");
            for (int i = 1; i < path.length; i++) require(preview.edge(position(path[i - 1]), position(path[i])), "native preview draws every actual route edge");
            same(before, bytes(model), "preview is byte-exact and does not advance the simulation");
            touch(MotionEvent.ACTION_UP, position(hostile));
            require((int)read(model, "unitsSent") == 15 && (int)call(tiles(model).get(source), "count") == 45, "native release deploys previewed quarter exactly once");
            require(!troops(model).isEmpty(), "native release creates routed convoys");
            for (Object troop : troops(model)) require(Arrays.equals(path, (int[])read(troop, "route")), "launched convoy retains previewed path");

            terminalFixture(model, true);
            call(scene, "update", .01f);
            require((int)read(scene, "overlay") == 19, "staged victory opens separate Logistics result");
            Evidence won = capture(language, "map-" + map + "-won", false);
            resultFacts(won, model, true);
            Object entry = call(read(profile, "logisticsRecords"), "get", map, map, read(model, "rulesVersion"), read(model, "logisticsConfigVersion"));
            require((boolean)read(entry, "completed") && (float)read(entry, "bestElapsed") == 64f, "win stored under exact map/difficulty/rules/config key");
            Object other = call(read(profile, "logisticsRecords"), "get", map, (map + 1) % 3, read(model, "rulesVersion"), read(model, "logisticsConfigVersion"));
            require(!(boolean)read(other, "completed"), "record does not fall back to another difficulty");
            same(progress, bytes(read(profile, "progress")), "Logistics win grants no Classic progress or mastery");
            require(classic.equals(classicPreferences()), "Logistics win preserves legacy scores, stars, unlocks and wins");
            records = bytes(read(profile, "logisticsRecords"));
            call(scene, "update", .1f);
            same(records, bytes(read(profile, "logisticsRecords")), "result record is idempotent");
            tap("logistics_retry");
            assertIdentity(map, map);
            require((float)read(read(scene, "model"), "elapsed") == 0 && troops(read(scene, "model")).isEmpty(), "native completed retry starts fresh attempt");
            same(records, bytes(read(profile, "logisticsRecords")), "retry retains separate records");
            terminalFixture(read(scene, "model"), false);
            call(scene, "update", .01f);
            require((int)read(scene, "overlay") == 19, "staged defeat opens Logistics result");
            if (map == 0) resultFacts(capture(language, "map-0-lost", false), read(scene, "model"), false);
            same(records, bytes(read(profile, "logisticsRecords")), "defeat grants no Logistics completion");
            same(progress, bytes(read(profile, "progress")), "defeat preserves Classic progress");
            tap("home");
            require((int)read(scene, "overlay") == constant("GameScene", "MENU"), "native result Home returns to main menu");
            tap("logistics");
        }
        tap("home");
    }

    private void resultFacts(Evidence evidence, Object model, boolean won) throws Exception {
        require(evidence.contains(tr(won ? "logistics.result_won" : "logistics.result_lost")), "observed localized result outcome");
        Object result = call(type("ObjectiveResult"), "evaluate", model);
        String reason = tr((String)read(result, "code"), (Object[])call(result, "numericArguments"));
        require(evidence.contains(reason), "actual localized terminal reason observed on native Canvas");
        require(evidence.contains(tr("result.elapsed", call(type("GameScene"), "time", 64f)))
            && evidence.contains(tr("result.battle_stats", 37, 23)) && evidence.contains(translate("91")), "native result shows actual elapsed/capture/loss/deployment facts");
        require((int)call(model, "score") == 0 && (int)call(model, "stars") == 0, "experimental results grant no Classic score or speed stars");
        button("logistics_retry"); button("logistics"); button("home");
    }

    private void checkpointFixture() throws Exception {
        selectLanguage("hi"); tap("logistics"); tap("difficulty_1"); tap("logistics_map_1");
        assertIdentity(1, 1);
        Object model = read(scene, "model"); int[] path = connectedFixture(model);
        tap("quarter"); drag(path[0], path[path.length - 1], false); call(scene, "update", .05f);
        require(!troops(model).isEmpty(), "checkpoint has actual routed convoys in flight");
        boolean moving = false;
        for (Object troop : troops(model)) moving |= (float)read(troop, "age") > 0 && (float)read(troop, "age") < (float)read(troop, "duration");
        require(moving, "checkpoint is strictly mid-route, not just a queued launch");
        byte[] battle = bytes(model), records = bytes(read(profile, "logisticsRecords")), progress = bytes(read(profile, "progress"));
        tap("pause"); tap("menu"); tap("logistics"); tap("difficulty_2");
        require((int)read(profile, "difficulty") == 2 && (int)read(model, "difficulty") == 1, "selector difficulty applies only to next attempt");
        same(battle, bytes(model), "paused selector preserves exact mid-route battle");
        same(records, bytes(read(profile, "logisticsRecords")), "paused selector preserves Logistics records");
        same(progress, bytes(read(profile, "progress")), "paused selector preserves Classic progress");
        capture("hi", "active-selector", false);
        tap("continue_logistics"); same(battle, bytes(model), "native Continue is byte-exact before process death");
        call(view, "save");
        same(battle, saved(storage, "battle"), "production save writes exact routed battle key");
        same(records, saved(storage, RECORDS), "production save writes separate Logistics records key");
    }

    private void assertCheckpoint() throws Exception {
        require("hi".equals(read(profile, "language")), "fresh process restores explicit Hindi");
        assertIdentity(1, 1);
        require((int)read(profile, "difficulty") == 2, "fresh process restores next-attempt preference independently");
        same(saved(checkpoint, "battle"), bytes(read(scene, "model")), "fresh process restores exact routes, ages, legs, troops, time and RNG");
        same(saved(checkpoint, "progress-v10"), bytes(read(profile, "progress")), "fresh process restores nonempty Classic progress");
        same(saved(checkpoint, RECORDS), bytes(read(profile, "logisticsRecords")), "fresh process restores exact independent records");
        require((boolean)read(scene, "hasBattle") && !troops(read(scene, "model")).isEmpty(), "restored mid-route attempt is actually available");
    }

    private void corruptedRecords() throws Exception {
        copy(storage, checkpoint.getAll());
        byte[] corrupt = saved(checkpoint, RECORDS); corrupt[corrupt.length - 1] ^= 1;
        String encoded = Base64.encodeToString(corrupt, Base64.NO_WRAP);
        require(storage.edit().putString(RECORDS, encoded).commit(), "separate record checksum-corruption fixture installed");
        launch();
        ui(() -> {
            same(saved(checkpoint, "battle"), bytes(read(scene, "model")), "corrupt records do not erase valid routed battle");
            same(saved(checkpoint, "progress-v10"), bytes(read(profile, "progress")), "corrupt records do not erase valid Classic progress");
            require((int)call(read(profile, "logisticsRecords"), "size") == 0, "invalid records are not accepted as completions");
            require(encoded.equals(storage.getString("logistics-records-invalid-v11", "")), "invalid record bytes quarantined exactly");
            assertClassicCheckpoint();
            tap("logistics"); render().recycle(); button("continue_logistics"); tap("continue_logistics");
            same(saved(checkpoint, "battle"), bytes(read(scene, "model")), "record recovery still allows exact native Continue");
        });
        close();
    }

    private void corruptedRoute() throws Exception {
        copy(storage, checkpoint.getAll());
        byte[][] corrupt = {null};
        launch();
        ui(() -> { assertCheckpoint(); corrupt[0] = invalidRoute(bytes(read(scene, "model")), read(scene, "model")); });
        close();
        copy(storage, checkpoint.getAll());
        require(storage.edit().putString("battle", Base64.encodeToString(corrupt[0], Base64.NO_WRAP)).commit(), "route-only corruption fixture installed");
        launch();
        ui(() -> {
            require(!(boolean)read(scene, "hasBattle"), "native loader rejects structurally invalid saved route");
            require(!storage.contains("battle"), "invalid route save cannot remain resumable");
            same(saved(checkpoint, "progress-v10"), bytes(read(profile, "progress")), "route corruption preserves exact Classic progress");
            same(saved(checkpoint, RECORDS), bytes(read(profile, "logisticsRecords")), "route corruption preserves independent records");
            assertClassicCheckpoint();
            tap("logistics"); render().recycle();
            require(optionalButton("continue_logistics") == null, "invalid route is not offered by native selector");
            capture("hi", "route-corruption-selector", false);
        });
        close();
    }

    private byte[] invalidRoute(byte[] valid, Object model) throws Exception {
        // FL06 ends with its structured route table. Derive its size from actual convoy paths.
        int tableBytes = 16;
        for (Object troop : troops(model)) tableBytes += 8 + 4 * ((int[])read(troop, "route")).length;
        int offset = valid.length - tableBytes;
        require(ByteBuffer.wrap(valid).getInt() == 0x464C3036 && offset > 0, "route-corruption fixture targets FL06 only");
        DataInputStream table = new DataInputStream(new ByteArrayInputStream(valid, offset, tableBytes));
        require(table.readInt() == (int)read(model, "logisticsId") && table.readInt() == (int)read(model, "logisticsConfigVersion")
            && table.readInt() == (int)read(model, "routingVersion") && table.readInt() == troops(model).size(), "structured route table identity verified before corruption");
        require(table.readInt() >= 3, "corrupting a real multi-hop route, not unrelated save bytes");
        byte[] corrupt = valid.clone(); ByteBuffer.wrap(corrupt).putInt(offset + 20, -1);
        boolean rejected = false;
        try { call(type("GameModel"), "restore", corrupt); }
        catch (IOException invalid) { rejected = true; }
        require(rejected, "model restore explicitly rejects invalid route tile index");
        return corrupt;
    }

    private void assertClassicCheckpoint() {
        require(classicPreferences().equals(classicValues(checkpoint)), "corruption preserves all legacy progress, scores, stars and unlocks");
    }

    private int[] connectedFixture(Object model) throws Exception {
        int source = (int)call(model, "originalKing", 0), target = (int)call(model, "originalKing", 1);
        for (Object tile : tiles(model)) { write(tile, "owner", (int)read(tile, "id") == target ? 1 : 0); write(tile, "troops", 20d); }
        write(tiles(model).get(source), "troops", 60d);
        int[] path = route(model, source, target);
        require(path != null && path.length >= 3, "explicit connected-ownership fixture has multi-hop route");
        return path;
    }

    private void terminalFixture(Object model, boolean won) throws Exception {
        troops(model).clear();
        for (Object tile : tiles(model)) { write(tile, "owner", won ? 0 : 1); write(tile, "troops", 5d); }
        write(model, "outcome", constant("GameModel", won ? "WON" : "LOST"));
        write(model, "terminalReason", constant("GameModel", won ? "TERMINAL_VICTORY" : "TERMINAL_ELIMINATED"));
        write(model, "elapsed", 64f); write(model, "captures", 37); write(model, "unitsSent", 91);
        write(model, "unitsLost", 23); write(model, "intercepted", 13); write(model, "cappedReinforcements", 7);
        write(model, "startingKingLost", !won);
    }

    private void assertIdentity(int map, int difficulty) throws Exception {
        Object model = read(scene, "model");
        require((int)read(model, "battleMode") == 2 && (int)read(model, "logisticsId") == map && (int)call(type("Logistics"), "getIndex", model) == map
            && (int)read(model, "difficulty") == difficulty && (int)read(model, "logisticsConfigVersion") > 0 && (int)read(model, "routingVersion") > 0,
            "actual Logistics map/difficulty/configured routed identity");
    }

    private void selectLanguage(String language) throws Exception {
        tap("settings"); tap("language_" + language); tap("back");
        require(language.equals(read(profile, "language")) && language.equals(storage.getString("language", "")), "native language control applies and persists " + language);
    }

    private static final class Ink {
        final String text; final RectF bounds; final boolean clipped;
        Ink(String text, RectF bounds, boolean clipped) { this.text = text; this.bounds = bounds; this.clipped = clipped; }
    }
    private static final class Evidence {
        final List<Ink> ink = new ArrayList<>();
        final List<float[]> edges = new ArrayList<>();
        String joined() { StringBuilder out = new StringBuilder(); for (Ink line : ink) out.append(line.text).append(' '); return out.toString(); }
        boolean contains(String value) { return joined().contains(value); }
        boolean edge(float[] a, float[] b) {
            for (float[] edge : edges) if (near(edge[0], edge[1], a) && near(edge[2], edge[3], b) || near(edge[0], edge[1], b) && near(edge[2], edge[3], a)) return true;
            return false;
        }
        private static boolean near(float x, float y, float[] p) { return Math.abs(x - p[0]) < .75f && Math.abs(y - p[1]) < .75f; }
    }

    private Evidence capture(String language, String name, boolean battle) throws Exception {
        Bitmap nativeFrame = render();
        Bitmap measuredFrame = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Evidence evidence = new Evidence();
        Object originalCanvas = read(view, "canvas");
        try {
            Canvas canvas = new Canvas(measuredFrame);
            canvas.drawColor(constant("GameScene", "BACKGROUND"));
            canvas.save(); canvas.translate((float)read(view, "offsetX"), (int)read(view, "safeTop"));
            float scale = (float)read(view, "scale"); canvas.scale(scale, scale);
            write(view, "canvas", canvas);
            int[] clips = {0};
            Object graphics = Proxy.newProxyInstance(activity.getClassLoader(), new Class<?>[] {type("GameScene$Graphics")}, (proxy, method, args) -> {
                if (method.getName().equals("text") && !((String)args[0]).isEmpty()) {
                    String text = (String)args[0]; float x = (float)args[1], baseline = (float)args[2];
                    float width = (float)call(view, "measureText", text, args[3], args[5]);
                    Paint paint = (Paint)read(view, "paint"); Rect ink = new Rect(); paint.getTextBounds(text, 0, text.length(), ink);
                    int align = (int)args[6]; float left = x - (align == 1 ? width / 2 : align == 2 ? width : 0);
                    evidence.ink.add(new Ink(text, new RectF(left + ink.left, baseline + ink.top, left + ink.right, baseline + ink.bottom), clips[0] > 0));
                }
                if (method.getName().equals("line")) evidence.edges.add(new float[] {(float)args[0], (float)args[1], (float)args[2], (float)args[3]});
                if (method.getName().equals("clip")) clips[0]++;
                if (method.getName().equals("unclip")) clips[0]--;
                return call(view, method.getName(), args == null ? new Object[0] : args);
            });
            call(scene, "render", graphics, read(scene, "height"));
            require(clips[0] == 0, "production Canvas clip stack balanced"); canvas.restore();
            require(nativeFrame.sameAs(measuredFrame), "Graphics proxy reproduces actual production Canvas pixels exactly");
            checkLayout(evidence, battle);
            int nonblank = 0, background = constant("GameScene", "BACKGROUND");
            for (int y = (int)read(view, "safeTop"); y < view.getHeight() - (int)read(view, "safeBottom"); y += 8)
                for (int x = 0; x < view.getWidth(); x += 8) if (nativeFrame.getPixel(x, y) != background) nonblank++;
            require(nonblank > 100, "native Canvas fixture is nonblank");
            String filename = "fixture-" + language + "-" + name;
            try (FileOutputStream stream = new FileOutputStream(new File(output, filename + ".png"))) {
                require(nativeFrame.compress(Bitmap.CompressFormat.PNG, 100, stream), "native fixture PNG written");
            }
            StringBuilder report = new StringBuilder("STAGED FIXTURE, NOT HUMAN GAMEPLAY; actual Android Canvas and Paint ink bounds.\n");
            report.append("view=").append(view.getWidth()).append('x').append(view.getHeight()).append("; sceneHeight=").append(read(scene, "height")).append('\n');
            for (Ink line : evidence.ink) report.append(line.bounds).append(line.clipped ? "\tboard-clipped\t" : "\tforeground\t").append(line.text).append('\n');
            try (FileOutputStream stream = new FileOutputStream(new File(output, filename + "-layout.txt"))) { stream.write(report.toString().getBytes(StandardCharsets.UTF_8)); }
            captures++;
        } finally { write(view, "canvas", originalCanvas); nativeFrame.recycle(); measuredFrame.recycle(); }
        return evidence;
    }

    private void checkLayout(Evidence evidence, boolean battle) throws Exception {
        float height = (float)read(scene, "height");
        for (Ink line : evidence.ink) if (!line.clipped) {
            require(line.bounds.left >= -1 && line.bounds.right <= 421 && line.bounds.top >= -1 && line.bounds.bottom <= height + 1,
                "native foreground ink fits viewport: " + line.text + " " + line.bounds);
            if (!battle) for (Ink other : evidence.ink) if (line != other && !other.clipped) {
                RectF overlap = new RectF(line.bounds);
                require(!overlap.intersect(other.bounds) || overlap.width() <= 1 || overlap.height() <= 1,
                    "native selector/result text does not overlap: " + line.text + " / " + other.text);
            }
        }
        List<?> controls = buttons();
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider(); require(provider != null, "native accessibility provider present");
        for (Object control : controls) {
            float x = (float)read(control, "x"), y = (float)read(control, "y"), w = (float)read(control, "width"), h = (float)read(control, "height");
            require(x >= 0 && y >= 0 && x + w <= 420 && y + h <= height, "native control fits scene: " + read(control, "id"));
            String id = (String)read(control, "id"), label = (String)read(control, "label");
            AccessibilityNodeInfo node = provider.createAccessibilityNodeInfo(id.hashCode() & 0x7fffffff);
            require(node != null, "actual control has native virtual accessibility node " + id);
            try { require(label.contentEquals(node.getContentDescription()), "native control spoken label matches rendered language " + id); }
            finally { node.recycle(); }
            for (Ink line : evidence.ink) if (!line.clipped && label.contains(line.text) && line.bounds.centerX() >= x && line.bounds.centerX() <= x + w
                && line.bounds.centerY() >= y && line.bounds.centerY() <= y + h)
                require(line.bounds.left >= x - 1 && line.bounds.right <= x + w + 1 && line.bounds.top >= y - 1 && line.bounds.bottom <= y + h + 1,
                    "native button label ink fits control " + id);
        }
        require(!evidence.joined().contains("logistics.route_preview") && !evidence.joined().contains("logistics.result_"), "no untranslated Logistics keys in native text");
    }

    private void launch() throws Exception {
        activity = startActivitySync(new Intent().setClassName("com.frontline.offline", "com.frontline.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            ui(() -> { callActivityOnPause(activity); view = (View)read(activity, "battleView"); scene = read(view, "scene"); profile = read(scene, "profile"); });
            if (view.getWidth() > 0 && view.getHeight() > 0) break;
            SystemClock.sleep(20); waitForIdleSync();
        } while (SystemClock.uptimeMillis() < deadline);
        require(view.getWidth() > 0 && view.getHeight() > 0, "native view laid out");
        ui(() -> {
            require(!(boolean)read(view, "running"), "native animation frozen before fixtures");
            if ((int)read(scene, "overlay") == constant("GameScene", "SPLASH")) call(scene, "back");
            render().recycle();
        });
        output = new File(activity.getExternalFilesDir(null), "v11-logistics-native/" + shot);
        require(output.isDirectory() || output.mkdirs(), "fixture artifact directory ready");
    }

    private void close() throws Exception {
        if (activity == null) return;
        ui(() -> { callActivityOnPause(activity); activity.finish(); });
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!activity.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
        waitForIdleSync(); require(activity.isDestroyed(), "Activity destroyed before preference replacement"); activity = null;
    }

    private void restoreOriginal() {
        require(activity == null, "preference recovery waits for Activity destruction");
        if (backup.getBoolean(MARKER, false)) {
            Map<String, Object> original = new HashMap<>(backup.getAll()); original.remove(MARKER); copy(storage, original);
            require(storage.getAll().equals(original), "all original preference values restored exactly");
            require(backup.edit().clear().commit(), "original backup cleared only after exact restoration");
        }
        require(checkpoint.edit().clear().commit(), "process checkpoint cleared");
    }

    private Bitmap render() { Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap)); return bitmap; }
    private void tap(String id) throws Exception {
        render().recycle(); Object control = button(id);
        float[] point = {(float)read(control, "x") + (float)read(control, "width") / 2, (float)read(control, "y") + (float)read(control, "height") / 2};
        touch(MotionEvent.ACTION_DOWN, point); touch(MotionEvent.ACTION_UP, point);
    }
    private void drag(int source, int target, boolean previewOnly) throws Exception {
        render().recycle(); touch(MotionEvent.ACTION_DOWN, position(source)); touch(MotionEvent.ACTION_MOVE, position(target));
        if (!previewOnly) touch(MotionEvent.ACTION_UP, position(target));
    }
    private void touch(int action, float[] position) throws Exception {
        float scale = (float)read(view, "scale"), x = position[0] * scale + (float)read(view, "offsetX"), y = position[1] * scale + (int)read(view, "safeTop");
        require(x >= 0 && y >= 0 && x < view.getWidth() && y < view.getHeight(), "native touch stays in viewport");
        long now = SystemClock.uptimeMillis(); MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try { require(view.dispatchTouchEvent(event), "native View consumes actual fixture touch"); } finally { event.recycle(); }
    }
    private float[] position(int tile) throws Exception { return (float[])call(scene, "position", tile); }
    private int[] route(Object model, int source, int target) throws Exception { return (int[])call(model, "route", source, target, 0); }
    private List<?> tiles(Object model) throws Exception { return (List<?>)read(model, "territories"); }
    private List<?> troops(Object model) throws Exception { return (List<?>)read(model, "troops"); }
    private List<?> buttons() throws Exception { return (List<?>)call(scene, "accessibleButtons"); }
    private Object optionalButton(String id) throws Exception { for (Object control : buttons()) if (id.equals(read(control, "id"))) return control; return null; }
    private Object button(String id) throws Exception { Object control = optionalButton(id); require(control != null, "actual rendered native control " + id); return control; }
    private String tr(String key, Object... args) throws Exception { return (String)call(type("Localization"), "text", read(profile, "language"), key, args); }
    private String translate(String text) throws Exception { return (String)call(type("Localization"), "translate", read(profile, "language"), text); }
    private Map<String, Object> classicPreferences() { return classicValues(storage); }
    private static Map<String, Object> classicValues(SharedPreferences prefs) {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) if (entry.getKey().matches("best-.*|stars-.*|time-.*|unlocked|wins|progress-v10")) values.put(entry.getKey(), entry.getValue());
        return values;
    }
    private static byte[] saved(SharedPreferences prefs, String key) { return Base64.decode(prefs.getString(key, ""), Base64.DEFAULT); }
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
        Throwable[] failure = {null}; runOnMainSync(() -> { try { step.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new Exception("Native Logistics fixture", failure[0]);
    }
    private void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private void same(byte[] a, byte[] b, String message) { require(Arrays.equals(a, b), message); }
    private Class<?> type(String name) throws Exception { return Class.forName("com.frontline.offline." + name, true, activity.getClassLoader()); }
    private int constant(String type, String name) throws Exception { return (int)read(type(type), name); }
    private static byte[] bytes(Object object) throws Exception { return (byte[])call(object, "save"); }
    private static Throwable append(Throwable failure, Throwable cleanup) { if (failure == null) return cleanup; failure.addSuppressed(cleanup); return failure; }
    private static Field field(Object object, String name) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>)object : object.getClass();
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object read(Object object, String name) throws Exception { return field(object, name).get(object instanceof Class ? null : object); }
    private static void write(Object object, String name, Object value) throws Exception { field(object, name).set(object, value); }
    private static Object call(Object object, String name, Object... args) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>)object : object.getClass();
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name) && matches(method.getParameterTypes(), args)) {
            method.setAccessible(true);
            try { return method.invoke(object instanceof Class ? null : object, args); }
            catch (InvocationTargetException failed) {
                Throwable cause = failed.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw failed;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static boolean matches(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i]; boolean primitive = type.isPrimitive();
            if (primitive) type = type == int.class ? Integer.class : type == float.class ? Float.class : type == double.class ? Double.class
                : type == long.class ? Long.class : type == boolean.class ? Boolean.class : type;
            if (args[i] == null ? primitive : !type.isInstance(args[i])) return false;
        }
        return true;
    }
}
