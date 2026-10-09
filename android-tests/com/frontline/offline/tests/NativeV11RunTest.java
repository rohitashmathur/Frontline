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
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import java.io.ByteArrayOutputStream;
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
import java.util.Map;
import java.util.Set;

/** Native Run evidence only. Terminal outcomes are explicitly staged fixtures. */
public final class NativeV11RunTest extends Instrumentation {
    private static final String MARKER = "v11-run-original-ready";
    private static final String[] LANGUAGES = {"en", "id", "hi"};
    private Activity activity;
    private View view;
    private Object scene, profile;
    private SharedPreferences storage, backup, checkpoint;
    private String mode, shot;
    private File output;
    private int checks, captures;
    private final List<String> layoutErrors = new ArrayList<>();
    private interface Step { void run() throws Exception; }

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        Bundle args = arguments == null ? new Bundle() : arguments;
        mode = args.getString("mode", "suite"); shot = args.getString("shot", "run-small");
        if (!Arrays.asList("suite", "relaunch", "restore").contains(mode) || !shot.matches("[a-z0-9-]+"))
            throw new IllegalArgumentException("Run harness arguments");
        start();
    }

    @Override public void onStart() {
        storage = getTargetContext().getSharedPreferences("frontline-v1", Context.MODE_PRIVATE);
        backup = getTargetContext().getSharedPreferences("frontline-v11-run-original", Context.MODE_PRIVATE);
        checkpoint = getTargetContext().getSharedPreferences("frontline-v11-run-checkpoint", Context.MODE_PRIVATE);
        Throwable failure = null; boolean retain = false;
        try {
            if (mode.equals("suite")) {
                require(!backup.getBoolean(MARKER, false), "restore interrupted Run fixture first");
                copy(backup, storage.getAll());
                require(backup.edit().putBoolean(MARKER, true).commit(), "original preferences backed up");
                require(storage.edit().clear().putBoolean("tutorial-seen", true).putBoolean("camera-guide-seen", true)
                    .putBoolean("sound", false).putBoolean("music", false).putBoolean("haptics", false)
                    .putString("language", "en").putInt("unlocked", 4).putInt("wins", 7)
                    .putInt("best-0", 1500).putInt("stars-0", 2).putFloat("time-0", 90f).commit(), "fixture preferences installed");
                launch();
                ui(() -> {
                    require(constant("GameScene", "RUN_HOME") == 15 && constant("GameScene", "RUN_COUNCIL") == 16
                        && constant("GameScene", "RUN_SUMMARY") == 17, "Phase 3 scene contract");
                    Object progress = read(profile, "progress");
                    ((int[][])read(progress, "best"))[1][0] = 1700;
                    ((int[][])read(progress, "stars"))[1][0] = 2;
                    ((float[][])read(progress, "times"))[1][0] = 75;
                    call(view, "save");
                    for (String language : LANGUAGES) screens(language);
                    // Leave an actual native council transaction for the next instrumentation process.
                    fixtureReady(9911L); click("begin_run_battle"); finishFixture(true);
                    capture("process-council-before"); persisted();
                    require(layoutErrors.isEmpty(), "Run native layout errors: " + String.join(" | ", layoutErrors));
                });
                close(); copy(checkpoint, storage.getAll()); retain = true;
            } else if (mode.equals("relaunch")) {
                require(backup.getBoolean(MARKER, false), "separate process has original preference backup");
                require(storage.getAll().equals(checkpoint.getAll()), "force-stop preserves complete native preference transaction");
                launch();
                ui(() -> {
                    require("hi".equals(read(profile, "language")), "explicit Hindi survives process death");
                    same(saved(checkpoint, "run-v11"), bytes(read(profile, "run")), "council state and ordered offer restored exactly");
                    same(saved(checkpoint, "last-run-v11"), bytes(read(profile, "lastRun")), "last summary independently restored");
                    same(saved(checkpoint, "battle"), bytes(read(scene, "model")), "terminal associated battle restored exactly");
                    same(saved(checkpoint, "progress-v10"), bytes(read(profile, "progress")), "campaign progress restored exactly");
                    byte[] records = records(); byte[] run = bytes(read(profile, "run"));
                    click("run"); require(number(read(profile, "run"), "status") == 2, "council restored without replaying victory");
                    capture("process-council-after");
                    int[] offer = (int[])call(read(profile, "run"), "councilOffer");
                    call(scene, "update", 0f); same(run, bytes(read(profile, "run")), "repeated update does not reroll council");
                    click("perk_" + offer[0]);
                    require(number(read(profile, "run"), "status") == 0 && number(read(profile, "run"), "node") == 1,
                        "restored visible council choice advances once to READY");
                    persisted(); same(records, records(), "council choice leaves campaign records untouched");
                    capture("process-choice-ready");
                    require(layoutErrors.isEmpty(), "restored Run native layout errors: " + String.join(" | ", layoutErrors));
                });
            }
        } catch (Throwable error) { failure = error; }
        finally {
            try { close(); if (!retain || failure != null) restoreOriginal(); }
            catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        }
        Bundle result = new Bundle();
        result.putString("stream", failure == null
            ? "PASS: native V11 Run " + mode + "; checks=" + checks + "; captures=" + captures
                + "; separateProcessCheckpoints=" + (mode.equals("relaunch") ? 1 : 0) + "; viewport=" + shot
                + "; " + (retain ? "council checkpoint retained" : "original preferences restored") + "; staged fixtures, not gameplay\n"
            : "FAIL: native V11 Run " + mode + "; checks=" + checks + "; captures=" + captures + "; viewport=" + shot
                + "\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void screens(String language) throws Exception {
        write(scene, "overlay", constant("GameScene", "MENU")); render().recycle(); click("settings");
        render().recycle(); click("language_" + language);
        require(language.equals(read(profile, "language")) && language.equals(storage.getString("language", "")), "native language selected " + language);
        labels(); click("back");
        byte[] records = records();
        fixtureReady(7100L); capture("ready"); persisted();
        click("begin_run_battle");
        Object run = read(profile, "run"), model = read(scene, "model");
        require(number(run, "status") == 1 && number(model, "battleMode") == 1
            && (boolean)call(run, "matchesActiveBattle", model), "native Begin installs associated Run battle");
        persisted();
        finishFixture(true); require(number(read(profile, "run"), "status") == 2, "staged native win opens council");
        capture("council"); persisted();
        int[] offer = (int[])call(read(profile, "run"), "councilOffer");
        require(offer.length == 3, "first council has three actual offers");
        for (int perk : offer) button("perk_" + perk);
        click("perk_" + offer[0]); persisted(); capture("chosen-ready");
        fixtureReady(7200L); click("begin_run_battle"); finishFixture(false);
        require(number(read(profile, "run"), "status") == 3 && number(read(profile, "run"), "retriesUsed") == 0,
            "first staged loss offers unused retry");
        capture("retry"); persisted();
        Object lostModel = read(scene, "model"); long seed = (long)read(lostModel, "seed"); String association = (String)read(lostModel, "runId");
        click("run_retry"); model = read(scene, "model");
        require(number(read(profile, "run"), "retriesUsed") == 1 && seed == (long)read(model, "seed")
            && !association.equals(read(model, "runId")), "retry persists token and new association with same seed");
        persisted(); finishFixture(false); require(number(read(profile, "run"), "status") == 5, "second loss has terminal defeated summary");
        capture("defeated-summary"); persisted();
        byte[] last = bytes(read(profile, "lastRun")); fixtureReady(7300L);
        same(last, bytes(read(profile, "lastRun")), "new READY run retains last summary");
        click("run_abandon"); capture("abandon-confirmation"); click("cancel_replace");
        require(number(read(profile, "run"), "status") == 0, "cancel abandonment preserves READY");
        click("run_abandon"); click("confirm_replace"); capture("abandoned-summary"); persisted();
        // Portable tests own exhaustive five-battle logic; this creates a labeled completed display fixture.
        Object completed = call(type("RunState"), "newRun", 7400L);
        for (int node = 0; node < 5; node++) {
            completed = call(completed, "beginBattle"); Object battle = call(completed, "createBattle");
            terminalFixture(battle, true); completed = call(completed, "finishBattle", battle);
            if (node < 4) completed = call(completed, "choosePerk", read(completed, "councilNonce"), ((int[])call(completed, "councilOffer"))[0]);
        }
        write(profile, "run", completed); write(profile, "lastRun", completed); write(scene, "hasBattle", false);
        write(scene, "overlay", constant("GameScene", "MENU")); call(view, "save"); click("run");
        capture("completed-summary"); persisted();
        same(records, records(), "Run fixtures do not grant campaign, mission, Daily, mastery or legacy rewards " + language);
    }

    private void fixtureReady(long seed) throws Exception {
        write(profile, "run", call(type("RunState"), "newRun", seed)); write(scene, "hasBattle", false);
        write(scene, "overlay", constant("GameScene", "MENU")); call(view, "save"); click("run");
    }

    private void finishFixture(boolean won) throws Exception {
        terminalFixture(read(scene, "model"), won); call(scene, "update", 0f);
    }

    private void terminalFixture(Object model, boolean won) throws Exception {
        ((List<?>)read(model, "troops")).clear();
        for (Object territory : (List<?>)read(model, "territories")) { write(territory, "owner", won ? 0 : 1); write(territory, "troops", 5d); }
        write(model, "outcome", won ? 1 : 2); write(model, "terminalReason", constant("GameModel", won ? "TERMINAL_VICTORY" : "TERMINAL_ELIMINATED"));
        write(model, "elapsed", 20f + number(model, "runNode")); write(model, "captures", 2); write(model, "unitsSent", 10);
        write(model, "unitsLost", 3); write(model, "intercepted", 1); write(model, "cappedReinforcements", 2);
        write(model, "startingKingLost", !won);
    }

    private void persisted() throws Exception {
        same(bytes(read(profile, "run")), saved(storage, "run-v11"), "native event commits complete run snapshot");
        same(bytes(read(profile, "lastRun")), saved(storage, "last-run-v11"), "native event commits independent last summary");
        same(bytes(read(profile, "progress")), saved(storage, "progress-v10"), "same native save contains unchanged campaign progress");
        if ((boolean)read(scene, "hasBattle")) same(bytes(read(scene, "model")), saved(storage, "battle"), "same native save contains exact associated battle");
        else require(!storage.contains("battle"), "no fabricated battle in READY or abandoned fixture");
    }

    private byte[] records() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.write(bytes(read(profile, "progress"))); out.writeInt(number(profile, "wins")); out.writeInt(number(profile, "unlocked"));
        for (int v : (int[])read(profile, "best")) out.writeInt(v);
        for (int v : (int[])read(profile, "stars")) out.writeInt(v);
        for (float v : (float[])read(profile, "times")) out.writeFloat(v);
        out.flush(); return bytes.toByteArray();
    }

    private static final class Line {
        final String text; final RectF bounds;
        Line(String text, RectF bounds) { this.text = text; this.bounds = bounds; }
    }

    private void capture(String name) throws Exception {
        String language = (String)read(profile, "language"); Bitmap bitmap = render();
        String prefix = "fixture-" + language + "-" + name;
        try {
            int ink = 0;
            for (int y = 0; y < bitmap.getHeight(); y += 8) for (int x = 0; x < bitmap.getWidth(); x += 8)
                if (bitmap.getPixel(x, y) != 0xFF17191B) ink++;
            require(ink > 100, "native Run bitmap nonblank " + prefix); labels();
            List<Line> lines = layout(); float height = (float)read(scene, "height");
            StringBuilder report = new StringBuilder("FIXTURE: reflective Run states and staged terminal outcomes, not gameplay or human validation.\n");
            for (Line line : lines) {
                report.append(line.bounds).append('\t').append(line.text).append('\n');
                layoutCheck(line.bounds.left >= -1 && line.bounds.right <= 421 && line.bounds.top >= -1 && line.bounds.bottom <= height + 1,
                    prefix + ": text outside scene " + line.text + " " + line.bounds);
            }
            for (int i = 0; i < lines.size(); i++) for (int j = i + 1; j < lines.size(); j++) {
                RectF overlap = new RectF(lines.get(i).bounds);
                layoutCheck(!overlap.intersect(lines.get(j).bounds) || overlap.width() <= 1 || overlap.height() <= 1,
                    prefix + ": text overlap " + lines.get(i).text + " / " + lines.get(j).text);
            }
            for (Object b : buttons()) {
                RectF box = new RectF((float)read(b, "x"), (float)read(b, "y"),
                    (float)read(b, "x") + (float)read(b, "width"), (float)read(b, "y") + (float)read(b, "height"));
                String label = (String)read(b, "label");
                layoutCheck(box.left >= -1 && box.right <= 421 && box.top >= -1 && box.bottom <= height + 1,
                    prefix + ": control outside scene " + read(b, "id"));
                boolean foundLabel = false;
                for (Line line : lines) if (label.equals(line.text) && box.contains(line.bounds)) foundLabel = true;
                layoutCheck(foundLabel, prefix + ": no matching label inside control " + read(b, "id") + " / " + label);
            }
            for (String error : layoutErrors) if (error.startsWith(prefix + ":")) report.append("FAIL: ").append(error).append('\n');
            try (FileOutputStream stream = new FileOutputStream(new File(output, prefix + ".png"))) {
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "Run PNG saved");
            }
            try (FileOutputStream stream = new FileOutputStream(new File(output, prefix + "-layout.txt"))) {
                stream.write(report.toString().getBytes(StandardCharsets.UTF_8));
            }
            captures++;
        } finally { bitmap.recycle(); }
    }

    private List<Line> layout() throws Exception {
        List<Line> lines = new ArrayList<>();
        Object graphics = Proxy.newProxyInstance(activity.getClassLoader(), new Class<?>[] {type("GameScene$Graphics")}, (proxy, method, args) -> {
            if (method.getName().equals("measureText")) return call(view, "measureText", args);
            if (method.getName().equals("text") && !((String)args[0]).isEmpty()) {
                String text = (String)args[0]; float x = (float)args[1], y = (float)args[2];
                float width = (float)call(view, "measureText", text, args[3], args[5]);
                Paint paint = (Paint)read(view, "paint"); Rect ink = new Rect(); paint.getTextBounds(text, 0, text.length(), ink);
                int align = (int)args[6]; float left = x - (align == 1 ? width / 2 : align == 2 ? width : 0);
                lines.add(new Line(text, new RectF(left + ink.left, y + ink.top, left + ink.right, y + ink.bottom)));
            }
            return null;
        });
        @SuppressWarnings("unchecked") List<Object> actual = (List<Object>)read(scene, "buttons");
        List<Object> original = new ArrayList<>(actual);
        try {
            actual.clear();
            String method = number(scene, "overlay") == constant("GameScene", "CONFIRM") ? "drawConfirmation" : "drawRun";
            call(scene, method, call(scene, "localized", graphics));
        } finally { actual.clear(); actual.addAll(original); }
        return lines;
    }

    private void layoutCheck(boolean good, String message) { checks++; if (!good) layoutErrors.add(message); }

    private void labels() throws Exception {
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider(); require(provider != null, "native accessibility provider");
        AccessibilityNodeInfo host = provider.createAccessibilityNodeInfo(View.NO_ID);
        require(host != null && host.getChildCount() == buttons().size(), "host exposes only actual Run controls"); host.recycle();
        Set<Integer> ids = new HashSet<>();
        for (Object b : buttons()) {
            String id = (String)read(b, "id"), label = (String)read(b, "label"); int key = id.hashCode() & 0x7fffffff;
            require(ids.add(key) && label != null && !label.trim().isEmpty(), "unique spoken Run control " + id);
            AccessibilityNodeInfo node = provider.createAccessibilityNodeInfo(key); require(node != null, "native node " + id);
            try {
                require(label.contentEquals(node.getText()) && label.contentEquals(node.getContentDescription()), "immediate native label " + id);
                require(node.isEnabled() && node.isClickable(), "native actionable control " + id);
                Rect bounds = new Rect(); node.getBoundsInParent(bounds);
                float scale = (float)read(view, "scale"), offset = (float)read(view, "offsetX"); int top = number(view, "safeTop");
                float x = (float)read(b, "x"), y = (float)read(b, "y"), w = (float)read(b, "width"), h = (float)read(b, "height");
                require(bounds.equals(new Rect(Math.round(offset + x*scale), Math.round(top + y*scale),
                    Math.round(offset + (x+w)*scale), Math.round(top + (y+h)*scale))), "native density/inset control bounds " + id);
            } finally { node.recycle(); }
        }
        require(!provider.performAction(1234567, AccessibilityNodeInfo.ACTION_CLICK, null), "unknown native control rejected");
    }

    private void click(String id) throws Exception {
        render().recycle(); button(id);
        require(view.getAccessibilityNodeProvider().performAction(id.hashCode() & 0x7fffffff, AccessibilityNodeInfo.ACTION_CLICK, null), "native rendered activation " + id);
    }
    private List<?> buttons() throws Exception { return (List<?>)call(scene, "accessibleButtons"); }
    private Object button(String id) throws Exception {
        for (Object b : buttons()) if (id.equals(read(b, "id"))) return b;
        throw new AssertionError("Missing actual rendered control " + id + " overlay=" + read(scene, "overlay"));
    }
    private Bitmap render() { Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap)); return bitmap; }

    private void launch() throws Exception {
        activity = startActivitySync(new Intent().setClassName("com.frontline.offline", "com.frontline.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync(); long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            ui(() -> { callActivityOnPause(activity); view = (View)read(activity, "battleView"); scene = read(view, "scene"); profile = read(scene, "profile"); });
            if (view.getWidth() > 0 && view.getHeight() > 0) break;
            SystemClock.sleep(20); waitForIdleSync();
        } while (SystemClock.uptimeMillis() < deadline);
        require(view.getWidth() > 0 && view.getHeight() > 0, "native Run view laid out");
        ui(() -> { call(scene, "back"); require(!(boolean)read(view, "running"), "native fixture animation frozen"); });
        output = new File(activity.getExternalFilesDir(null), "v11-run-native/" + shot);
        require(output.isDirectory() || output.mkdirs(), "Run artifact directory");
    }
    private void close() throws Exception {
        if (activity == null) return;
        ui(() -> { callActivityOnPause(activity); activity.finish(); });
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!activity.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
        waitForIdleSync(); require(activity.isDestroyed(), "Activity destroyed before preference restoration"); activity = null;
    }
    private void restoreOriginal() {
        if (backup.getBoolean(MARKER, false)) {
            Map<String, Object> original = new HashMap<>(backup.getAll()); original.remove(MARKER); copy(storage, original);
            require(storage.getAll().equals(original), "all original preferences restored exactly");
            require(backup.edit().clear().commit(), "backup cleared after verified restoration");
        }
        require(checkpoint.edit().clear().commit(), "Run checkpoint cleared");
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
            else throw new IllegalArgumentException("Unsupported preference: " + key);
        }
        require(edit.commit(), "preferences synchronously committed");
    }
    private void ui(Step step) throws Exception {
        Throwable[] failed = {null}; runOnMainSync(() -> { try { step.run(); } catch (Throwable error) { failed[0] = error; } });
        if (failed[0] != null) throw new Exception("Run native UI fixture", failed[0]);
    }
    private void require(boolean good, String message) { if (!good) throw new AssertionError(message); checks++; }
    private void same(byte[] left, byte[] right, String message) { require(Arrays.equals(left, right), message); }
    private static byte[] saved(SharedPreferences prefs, String key) { return prefs.contains(key) ? Base64.decode(prefs.getString(key, ""), Base64.DEFAULT) : null; }
    private static byte[] bytes(Object object) throws Exception { return object == null ? null : (byte[])call(object, "save"); }
    private int constant(String name, String field) throws Exception { return number(type(name), field); }
    private Class<?> type(String name) throws Exception { return Class.forName("com.frontline.offline." + name, true, activity.getClassLoader()); }
    private static int number(Object object, String name) throws Exception { return (int)read(object, name); }
    private static Field field(Object object, String name) throws Exception {
        Field field = (object instanceof Class ? (Class<?>)object : object.getClass()).getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object read(Object object, String name) throws Exception { return field(object, name).get(object instanceof Class ? null : object); }
    private static void write(Object object, String name, Object value) throws Exception { field(object, name).set(object, value); }
    private static Object call(Object object, String name, Object... args) throws Exception {
        Class<?> type = object instanceof Class ? (Class<?>)object : object.getClass();
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name) && matches(method.getParameterTypes(), args)) {
            method.setAccessible(true);
            try { return method.invoke(object instanceof Class ? null : object, args); }
            catch (InvocationTargetException error) { throw new Exception(type.getSimpleName() + "." + name, error.getCause()); }
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
