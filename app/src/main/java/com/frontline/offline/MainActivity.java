package com.frontline.offline;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private BattleView battleView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        battleView = new BattleView(this);
        setContentView(battleView);
    }

    @Override protected void onPause() {
        super.onPause();
        battleView.stop();
    }

    @Override protected void onResume() {
        super.onResume();
        if (battleView != null) battleView.resume();
    }

    @Override protected void onDestroy() {
        battleView.dispose();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (battleView.scene.overlay == GameScene.MENU) { finish(); return; }
        battleView.scene.back(); battleView.save(); battleView.invalidate();
    }

    private static final class BattleView extends View implements GameScene.Events, GameScene.Graphics {
        private final SharedPreferences storage;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Typeface regular = Typeface.create("sans-serif",Typeface.NORMAL);
        private final Typeface bold = Typeface.create("sans-serif",Typeface.BOLD);
        private final Handler handler = new Handler();
        private final GameScene scene;
        private final ToneGenerator audio;
        private final BackgroundMusic music;
        private Canvas canvas;
        private float scale = 1, offsetX;
        private int safeTop, safeBottom;
        private long previousFrame, lastSave, lastCaptureCue;
        private boolean running;
        private boolean cameraTouch;
        private float touchSpan, touchX, touchY;
        private final Runnable tooltip = new Runnable() {
            @Override public void run() {
                String label = scene.pressedLabel();
                if (label != null) Toast.makeText(getContext(),label,Toast.LENGTH_SHORT).show();
            }
        };

        BattleView(Context context) {
            super(context);
            setFocusable(true);
            setContentDescription("Frontline battlefield");
            setOnApplyWindowInsetsListener((view, insets) -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    safeTop = safe.top; safeBottom = safe.bottom;
                } else {
                    safeTop = insets.getSystemWindowInsetTop();
                    safeBottom = insets.getSystemWindowInsetBottom();
                }
                invalidate(); return insets;
            });
            storage = context.getSharedPreferences("frontline-v1",Context.MODE_PRIVATE);
            GameScene.Profile profile = new GameScene.Profile();
            profile.unlocked = Math.max(0,Math.min(GameModel.LEVELS.length-1,storage.getInt("unlocked",0)));
            profile.difficulty = Math.max(0,Math.min(2,storage.getInt("difficulty",1)));
            profile.wins = storage.getInt("wins",0);
            profile.sound = storage.getBoolean("sound",true);
            profile.music = storage.getBoolean("music",true);
            profile.haptics = storage.getBoolean("haptics",true);
            profile.tutorialSeen = storage.getBoolean("tutorial-seen",false);
            profile.selectedSector = storage.getInt("selected-sector",0);
            for (int i = 0; i < profile.best.length; i++) {
                profile.best[i] = Math.max(0,storage.getInt("best-"+i,0));
                profile.stars[i] = Math.max(0,Math.min(3,storage.getInt("stars-"+i,0)));
                profile.times[i] = Math.max(0,storage.getFloat("time-"+i,0));
            }
            GameModel restored = null;
            try {
                String saved = storage.getString("battle",null);
                if (saved != null) restored = GameModel.restore(Base64.decode(saved,Base64.DEFAULT));
            } catch (Exception invalidSave) { storage.edit().remove("battle").apply(); }
            scene = new GameScene(profile,restored,this);
            ToneGenerator generator;
            try { generator = new ToneGenerator(AudioManager.STREAM_MUSIC,35); }
            catch (RuntimeException unavailable) { generator = null; }
            audio = generator;
            music = new BackgroundMusic(context,profile.music);
        }

        void stop() {
            running = false; scene.pause(); handler.removeCallbacks(tooltip); save();
            if (audio != null) audio.stopTone();
            music.pause();
        }
        void resume() { running = true; previousFrame = 0; music.resume(); invalidate(); }
        void dispose() { handler.removeCallbacks(tooltip); music.dispose(); if (audio != null) audio.release(); }

        @Override protected void onDraw(Canvas frame) {
            super.onDraw(frame);
            canvas = frame;
            frame.drawColor(GameScene.BACKGROUND);
            int availableHeight = Math.max(1,getHeight()-safeTop-safeBottom);
            scale = Math.min(getWidth()/420f,availableHeight/620f);
            offsetX = (getWidth()-420*scale)/2;
            long now = System.nanoTime();
            if (running && previousFrame != 0) scene.update(Math.min(.06f,(now-previousFrame)/1_000_000_000f));
            previousFrame = now;
            frame.save(); frame.translate(offsetX,safeTop); frame.scale(scale,scale);
            scene.render(this,availableHeight/scale);
            frame.restore();
            if (now-lastSave > 15_000_000_000L) { save(); lastSave = now; }
            if (running && scene.needsAnimation()) postInvalidateOnAnimation();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = (event.getX()-offsetX)/scale, y = (event.getY()-safeTop)/scale;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cameraTouch = false;
                    scene.down(x,y); handler.postDelayed(tooltip,600); break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        scene.cancel(); cameraTouch = true; handler.removeCallbacks(tooltip);
                        touchX = ((event.getX(0)+event.getX(1))/2-offsetX)/scale;
                        touchY = ((event.getY(0)+event.getY(1))/2-safeTop)/scale;
                        touchSpan = (float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (cameraTouch && event.getPointerCount() >= 2) {
                        float nextX = ((event.getX(0)+event.getX(1))/2-offsetX)/scale;
                        float nextY = ((event.getY(0)+event.getY(1))/2-safeTop)/scale;
                        float span = (float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
                        scene.cameraGesture(touchX,touchY,touchSpan > 0 ? span/touchSpan : 1,nextX-touchX,nextY-touchY);
                        touchX = nextX; touchY = nextY; touchSpan = span;
                    } else if (!cameraTouch) scene.move(x,y);
                    handler.removeCallbacks(tooltip); break;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(tooltip);
                    if (cameraTouch) scene.cancel(); else scene.up(x,y);
                    cameraTouch = false; performClick(); break;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(tooltip); scene.cancel(); cameraTouch = false; break;
                default: return true;
            }
            invalidate(); return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public void changed() { music.setEnabled(scene.profile.music); save(); }
        @Override public void cue(int kind) {
            long now = System.nanoTime();
            if (kind == 1 && now-lastCaptureCue < 180_000_000L) return;
            lastCaptureCue = now;
            if (scene.profile.haptics) performHapticFeedback(kind == 0 ? HapticFeedbackConstants.KEYBOARD_TAP : HapticFeedbackConstants.LONG_PRESS);
            if (audio != null && scene.profile.sound) {
                int type = kind == 2 ? ToneGenerator.TONE_PROP_ACK : kind == 3 ? ToneGenerator.TONE_PROP_NACK : ToneGenerator.TONE_PROP_BEEP;
                audio.startTone(type,kind >= 2 ? 220 : 35);
            }
        }

        private void save() {
            SharedPreferences.Editor edit = storage.edit();
            GameScene.Profile profile = scene.profile;
            edit.putInt("unlocked",profile.unlocked).putInt("difficulty",profile.difficulty).putInt("wins",profile.wins)
                .putBoolean("sound",profile.sound).putBoolean("music",profile.music).putBoolean("haptics",profile.haptics)
                .putBoolean("tutorial-seen",profile.tutorialSeen).putInt("selected-sector",profile.selectedSector);
            for (int i = 0; i < profile.best.length; i++) {
                edit.putInt("best-"+i,profile.best[i]).putInt("stars-"+i,profile.stars[i]).putFloat("time-"+i,profile.times[i]);
            }
            try {
                if (scene.hasBattle) edit.putString("battle",Base64.encodeToString(scene.model.save(),Base64.NO_WRAP));
                else edit.remove("battle");
            }
            catch (java.io.IOException failedSave) { edit.remove("battle"); }
            edit.apply();
        }

        private void color(int color) { paint.setColor(color); paint.setStyle(Paint.Style.FILL); }
        @Override public void clip(float x,float y,float width,float height) { canvas.save(); canvas.clipRect(x,y,x+width,y+height); }
        @Override public void unclip() { canvas.restore(); }
        @Override public void rect(float x,float y,float w,float h,float radius,int color) {
            color(color); canvas.drawRoundRect(x,y,x+w,y+h,radius,radius,paint);
        }
        @Override public void circle(float x,float y,float radius,int color) { color(color); canvas.drawCircle(x,y,radius,paint); }
        @Override public void line(float x1,float y1,float x2,float y2,float width,int color) {
            color(color); paint.setStrokeWidth(width); paint.setStrokeCap(Paint.Cap.ROUND); canvas.drawLine(x1,y1,x2,y2,paint);
        }
        @Override public void polygon(float[] points,int fill,int stroke,float width) {
            path.reset(); path.moveTo(points[0],points[1]);
            for (int i = 2; i < points.length; i+=2) path.lineTo(points[i],points[i+1]);
            path.close();
            if (fill != 0) { color(fill); canvas.drawPath(path,paint); }
            if (stroke != 0 && width > 0) { color(stroke); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); canvas.drawPath(path,paint); }
        }
        @Override public void text(String text,float x,float baseline,float size,int color,boolean isBold,int align) {
            color(color); paint.setTextSize(size); paint.setTypeface(isBold ? bold : regular);
            paint.setTextAlign(align == 1 ? Paint.Align.CENTER : align == 2 ? Paint.Align.RIGHT : Paint.Align.LEFT);
            canvas.drawText(text,x,baseline,paint);
        }
    }
}
