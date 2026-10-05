package com.frontline.offline.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NativeV10Test extends Instrumentation {
    private static final String MARKER = "v10-native-backup-ready";
    private Activity activity; private View view; private Object scene, profile;
    private SharedPreferences storage, backup, fixture; private File output;
    private String shot, exportMode = "not reached"; private boolean restoreOnly; private int checks;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments); shot = arguments.getString("shot","v10-native");
        if (!shot.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Screenshot name");
        restoreOnly = arguments.getString("restore","false").equals("true"); start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Throwable failure = null;
        storage = getTargetContext().getSharedPreferences("frontline-v1",Context.MODE_PRIVATE);
        backup = getTargetContext().getSharedPreferences("frontline-v10-native-original",Context.MODE_PRIVATE);
        fixture = getTargetContext().getSharedPreferences("frontline-v10-native-fixture",Context.MODE_PRIVATE);
        try {
            if (!restoreOnly) {
                if (backup.getBoolean(MARKER,false)) throw new IllegalStateException("Interrupted fixture: run restore-only first");
                copy(backup,storage.getAll()); require(backup.edit().putBoolean(MARKER,true).commit(),"backup marker committed");
                require(storage.edit().clear().putBoolean("tutorial-seen",true).putBoolean("camera-guide-seen",true)
                    .putBoolean("sound",false).putBoolean("music",false).putBoolean("haptics",false).putInt("difficulty",1).commit(),"fixture installed");
                launch(); ui(this::flows); export(); roundtripAndCorruption();
                ui(() -> {
                    call(scene,"start",59); Object dense = read(scene,"model"); int home = (Integer)call(dense,"originalKing",0);
                    List<?> tiles = (List<?>)read(dense,"territories"); write(tiles.get(home),"troops",125d);
                    write(tiles.get(home+1),"owner",0); write(tiles.get(home+1),"troops",100d);
                    tap("quarter"); preview("dense-selection-fit",false); preview("dense-selection-zoom",true);
                });
            }
        } catch (Throwable error) { failure = error; }
        finally {
            try { close(); restoreOriginal(); } catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        result.putString("stream",failure == null ? "PASS: native V10 "+checks+" checks; "+exportMode+"; fixture restored; screenshots="+shot+"\n"
            : "FAIL: "+android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
    }
    private void launch() throws Exception {
        activity = startActivitySync(new Intent().setClassName("com.frontline.offline","com.frontline.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync(); ui(() -> { callActivityOnPause(activity); view = (View)read(activity,"battleView"); scene = read(view,"scene"); profile = read(scene,"profile"); call(scene,"back"); });
        output = new File(activity.getExternalFilesDir(null),"v10-native/"+shot);
        require(output.isDirectory() || output.mkdirs(),"screenshot fixture directory");
    }
    private void flows() throws Exception {
        capture("menu"); tap("settings"); require((Boolean)call(scene,"redeemUnlockCode","12345"),"V7 code method accepts fixture code");
        require((int)read(profile,"unlocked") == 59,"V7 unlock retained"); tap("playtest_log"); capture("settings"); tap("back");
        tap("play"); capture("brief"); tap("begin_attempt"); Object original = read(scene,"model");
        tap("quarter"); preview("selection-fit",false); preview("selection-zoom",true);
        int home = (Integer)call(original,"originalKing",0); float[] from = (float[])call(scene,"position",home), to = (float[])call(scene,"position",1);
        touch(MotionEvent.ACTION_DOWN,from); touch(MotionEvent.ACTION_MOVE,to); touch(MotionEvent.ACTION_UP,to); tick(.06f);
        require((int)read(original,"unitsSent") > 0,"native drag dispatched troops"); byte[] retained = bytes(original);
        tap("pause"); tap("menu"); capture("menu-continue"); tick(.1f); same(retained,bytes(original),"menu freezes exact battle");
        tap("resume"); same(retained,bytes(read(scene,"model")),"Continue preserves exact battle");
        tap("pause"); tap("settings"); tap("difficulty_2"); same(retained,bytes(original),"settings leave attempt difficulty fixed");
        require((int)read(original,"difficulty") == 1 && (int)read(profile,"difficulty") == 2,"difficulty applies to next attempt"); tap("back");
        tap("restart"); tap("cancel_replace"); same(retained,bytes(read(scene,"model")),"cancelled restart retains state");
        tap("restart"); tap("confirm_replace"); Object replacement = read(scene,"model");
        require(replacement != original && (float)read(replacement,"elapsed") == 0 && (int)read(replacement,"unitsSent") == 0 && (int)read(replacement,"difficulty") == 2,"confirmed restart creates fresh next-difficulty attempt");
        tap("pause"); tap("menu"); tap("challenges"); capture("challenges"); tap("mission_tab_1"); tap("challenge_3");
        tap("begin_attempt"); tap("confirm_replace"); Object challenge = read(scene,"model");
        require((int)read(challenge,"objectiveType") == 2 && (int)read(challenge,"challengeId") == 3,"native challenge flow configures retain objective");
        write(challenge,"objectiveSeconds",.2f); tick(.1f); require((float)read(challenge,"objectiveProgress") > 0,"active update advances objective");
        tap("pause"); byte[] paused = bytes(challenge); tick(.1f); same(paused,bytes(challenge),"pause freezes objective");
        callActivityOnPause(activity); same(paused,Base64.decode(storage.getString("battle",""),Base64.DEFAULT),"lifecycle save callback persists exact objective");
        callActivityOnResume(activity); write(view,"running",false); tick(.1f); same(paused,bytes(challenge),"background/resume remains paused");
        tap("resume"); tick(.1f); require((int)read(challenge,"outcome") == 1,"objective result reached by active updates"); capture("result");
        require((int)call(read(profile,"progress"),"objectiveProgress") == 1,"real result records mastery progress");
        tap("menu"); tap("mastery"); capture("mastery"); tap("home"); tap("daily"); capture("daily"); tap("today_mission"); tap("begin_attempt");
        Object daily = read(scene,"model"); require(!((String)read(daily,"dailyDate")).isEmpty() && (int)read(daily,"difficulty") == 1,"daily keeps date and fixed Normal difficulty");
        tick(.1f); tap("pause"); tap("menu"); tap("daily"); tap("resume"); tap("pause"); tap("settings");
    }
    private void preview(String name,boolean zoom) throws Exception {
        if (zoom) tap("zoom_in"); else tap("fit_board"); render().recycle(); Object model = read(scene,"model");
        int home = (Integer)call(model,"originalKing",0);
        float[] from = (float[])call(scene,"position",home), to = (float[])call(scene,"position",home+1);
        if (zoom && (int)read(model,"levelIndex") == 59) {
            float centerY = ((Float)call(scene,"boardTop")+(Float)call(scene,"boardBottom"))/2;
            call(scene,"cameraGesture",210f,centerY,1f,210-(from[0]+to[0])/2,centerY-(from[1]+to[1])/2);
            render().recycle(); from = (float[])call(scene,"position",home); to = (float[])call(scene,"position",home+1);
        }
        touch(MotionEvent.ACTION_DOWN,from); touch(MotionEvent.ACTION_MOVE,to); capture(name);
        require((int)read(scene,"selected") >= 0 && (int)read(model,"unitsSent") == 0,"native selection preview does not dispatch"); touch(MotionEvent.ACTION_CANCEL,to);
    }
    private void export() throws Exception {
        String csv = (String)call(read(profile,"log"),"exportCsv");
        require(csv.contains("attempt_start") && csv.contains("attempt_result") && csv.contains("rules_version") && csv.contains((String)read(read(scene,"model"),"dailyDate")),"CSV contains real campaign/challenge/daily events");
        Uri destination = null;
        if (Build.VERSION.SDK_INT >= 29) try {
            ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME,shot+"-fixture.csv"); values.put(MediaStore.MediaColumns.MIME_TYPE,"text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/FrontlineV10Native");
            destination = getTargetContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        } catch (SecurityException unavailable) { exportMode = "external destination unavailable"; }
        final Uri uri = destination; final Intent[] captured = new Intent[1];
        ActivityMonitor capture = new ActivityMonitor() { @Override public ActivityResult onStartActivity(Intent intent) {
            if (!Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction())) return null;
            captured[0] = new Intent(intent); return new ActivityResult(uri == null ? Activity.RESULT_CANCELED : Activity.RESULT_OK,uri == null ? null : new Intent().setData(uri));
        }};
        IntentFilter filter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT); filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addDataType("text/csv");
        ActivityMonitor fallback = new ActivityMonitor(filter,new ActivityResult(Activity.RESULT_CANCELED,null),true); addMonitor(capture); addMonitor(fallback);
        try {
            ui(() -> tap("export_log")); waitForIdleSync();
            require(captured[0] != null || fallback.getHits() > 0,"SAF export intent intercepted");
            if (captured[0] != null) require("text/csv".equals(captured[0].getType()) && captured[0].hasCategory(Intent.CATEGORY_OPENABLE)
                && "Frontline-playtest.csv".equals(captured[0].getStringExtra(Intent.EXTRA_TITLE)),"SAF intent has MIME, category and filename");
            if (uri != null && captured[0] != null) {
                try (InputStream input = getTargetContext().getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) bytes.write(buffer,0,count);
                    require(csv.equals(new String(bytes.toByteArray(),StandardCharsets.UTF_8)),"onActivityResult writes exact external CSV bytes");
                }
                exportMode = "SAF intent + external CSV write verified";
            } else exportMode = "SAF intent + real CSV verified (external-write fallback)";
            try (FileOutputStream stream = new FileOutputStream(new File(output,"playtest.csv"))) { stream.write(csv.getBytes(StandardCharsets.UTF_8)); }
        } finally { removeMonitor(capture); removeMonitor(fallback); if (uri != null) getTargetContext().getContentResolver().delete(uri,null,null); }
    }
    private void roundtripAndCorruption() throws Exception {
        final byte[][] expected = new byte[3][]; ui(() -> { call(view,"save"); expected[0] = bytes(read(scene,"model")); expected[1] = bytes(read(profile,"progress")); expected[2] = bytes(read(profile,"log")); });
        copy(fixture,storage.getAll()); close(); launch();
        ui(() -> { same(expected[0],bytes(read(scene,"model")),"Activity recreation restores battle"); same(expected[1],bytes(read(profile,"progress")),"SharedPreferences restores Progress"); same(expected[2],bytes(read(profile,"log")),"SharedPreferences restores log"); });
        String[] keys = {"battle","progress-v10","playtest-v10"};
        for (int corrupt = 0; corrupt < keys.length; corrupt++) {
            close(); copy(storage,fixture.getAll()); require(storage.edit().putString(keys[corrupt],"AAAA").commit(),"corrupt fixture committed"); launch(); final int bad = corrupt;
            ui(() -> {
                if (bad != 0) same(expected[0],bytes(read(scene,"model")),"isolated corruption keeps battle"); else require(!(boolean)read(scene,"hasBattle"),"bad battle is discarded alone");
                if (bad != 1) same(expected[1],bytes(read(profile,"progress")),"isolated corruption keeps Progress"); else require((int)call(read(profile,"progress"),"earnedCount") == 0,"bad Progress resets alone");
                if (bad != 2) same(expected[2],bytes(read(profile,"log")),"isolated corruption keeps log"); else require((int)call(read(profile,"log"),"size") == 0,"bad log resets alone");
            });
        }
    }
    private void tap(String id) throws Exception { render().recycle(); float[] point = (float[])call(scene,"buttonPosition",id); require(point != null,"visible button "+id); touch(MotionEvent.ACTION_DOWN,point); touch(MotionEvent.ACTION_UP,point); }
    private void touch(int action,float[] point) throws Exception {
        float scale = (float)read(view,"scale"), x = point[0]*scale+(float)read(view,"offsetX"), y = point[1]*scale+(int)read(view,"safeTop");
        require(x >= 0 && y >= 0 && x < view.getWidth() && y < view.getHeight(),"touch point within native viewport");
        long now = SystemClock.uptimeMillis(); MotionEvent event = MotionEvent.obtain(now,now,action,x,y,0); try { require(view.dispatchTouchEvent(event),"View consumes native touch"); } finally { event.recycle(); }
    }
    private Bitmap render() { Bitmap bitmap = Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap)); return bitmap; }
    private void capture(String name) throws Exception {
        Bitmap bitmap = render(); int pixels = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 12) for (int x = 0; x < bitmap.getWidth(); x += 12) if (bitmap.getPixel(x,y) != 0xFF17191B) pixels++;
        require(pixels > 80,"nonblank native "+name); try (FileOutputStream stream = new FileOutputStream(new File(output,name+".png"))) { require(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream),"PNG capture"); } finally { bitmap.recycle(); }
    }
    private void tick(float seconds) throws Exception { call(scene,"update",seconds); }
    private void close() throws Exception {
        if (activity == null) return;
        ui(() -> { callActivityOnPause(activity); activity.finish(); });
        long deadline = SystemClock.uptimeMillis()+5000;
        while (!activity.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
        waitForIdleSync(); require(activity.isDestroyed(),"Activity teardown completed before fixture replacement"); activity = null;
    }
    private void restoreOriginal() throws Exception { if (backup.getBoolean(MARKER,false)) { Map<String,?> original = new HashMap<>(backup.getAll()); original.remove(MARKER); copy(storage,original); require(backup.edit().clear().commit(),"backup cleared"); } require(fixture.edit().clear().commit(),"isolated fixture cleared"); }
    @SuppressWarnings("unchecked") private void copy(SharedPreferences destination,Map<String,?> values) {
        SharedPreferences.Editor edit = destination.edit().clear();
        for (Map.Entry<String,?> entry : values.entrySet()) { String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String) edit.putString(key,(String)value); else if (value instanceof Integer) edit.putInt(key,(Integer)value); else if (value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
            else if (value instanceof Long) edit.putLong(key,(Long)value); else if (value instanceof Float) edit.putFloat(key,(Float)value); else if (value instanceof Set) edit.putStringSet(key,(Set<String>)value); else throw new IllegalArgumentException("Preference type");
        } require(edit.commit(),"preferences committed");
    }
    private interface Step { void run() throws Exception; }
    private void ui(Step step) throws Exception { final Throwable[] error = new Throwable[1]; runOnMainSync(() -> { try { step.run(); } catch (Throwable failed) { error[0] = failed; } }); if (error[0] != null) throw new Exception("Native UI check",error[0]); }
    private void require(boolean value,String message) { if (!value) throw new AssertionError(message); checks++; }
    private void same(byte[] a,byte[] b,String message) { require(Arrays.equals(a,b),message); }
    private static byte[] bytes(Object object) throws Exception { return (byte[])call(object,"save"); }
    private static Object read(Object object,String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void write(Object object,String name,Object value) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object,value); }
    private static Object call(Object object,String name,Object... args) throws Exception { for (Method method : object.getClass().getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterTypes().length == args.length) { method.setAccessible(true); return method.invoke(object,args); } throw new NoSuchMethodException(name); }
}
