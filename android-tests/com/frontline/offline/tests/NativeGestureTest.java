package com.frontline.offline.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;

public final class NativeGestureTest extends Instrumentation {
    private String shot;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        shot = arguments.getString("shot","gesture-test");
        if (!shot.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Screenshot name");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Activity activity = startActivitySync(new Intent().setClassName("com.frontline.offline","com.frontline.offline.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(1000);
            final Throwable[] failure = new Throwable[1];
            runOnMainSync(() -> {
                try {
                    callActivityOnPause(activity);
                    View view = (View)read(activity,"battleView");
                    Object scene = read(view,"scene");
                    scene.getClass().getMethod("start",int.class).invoke(scene,59);
                    Bitmap bitmap = Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(bitmap));
                    Object model = read(scene,"model");
                    List<?> tiles = (List<?>)read(model,"territories");
                    int source = -1;
                    for (Object tile : tiles) if ((int)read(tile,"owner") == 0) { source = (int)read(tile,"id"); break; }
                    if (source < 0) throw new AssertionError("Missing player king");
                    float[] position = (float[])scene.getClass().getMethod("position",int.class).invoke(scene,source);
                    float scale = (float)read(view,"scale"), offset = (float)read(view,"offsetX");
                    int top = (int)read(view,"safeTop");
                    float x = position[0]*scale+offset, y = position[1]*scale+top;
                    long time = SystemClock.uptimeMillis();
                    touch(view,time,MotionEvent.ACTION_DOWN,new float[] {x,y});
                    touch(view,time,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[] {x,y,x+90*scale,y});
                    touch(view,time,MotionEvent.ACTION_MOVE,new float[] {x-20*scale,y,x+120*scale,y});
                    if ((float)read(scene,"zoom") < 1.3f) throw new AssertionError("Native two-finger pinch did not zoom");
                    float oldX = (float)read(scene,"panX"), oldY = (float)read(scene,"panY");
                    touch(view,time,MotionEvent.ACTION_MOVE,new float[] {x+10*scale,y+20*scale,x+150*scale,y+20*scale});
                    if (oldX == (float)read(scene,"panX") && oldY == (float)read(scene,"panY")) throw new AssertionError("Native two-finger drag did not pan");
                    touch(view,time,MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[] {x+10*scale,y+20*scale,x+150*scale,y+20*scale});
                    touch(view,time,MotionEvent.ACTION_UP,new float[] {x+10*scale,y+20*scale});
                    if ((int)read(model,"unitsSent") != 0 || (float)read(model,"elapsed") != 0) throw new AssertionError("Camera gesture changed frozen battle state");
                    scene.getClass().getMethod("start",int.class).invoke(scene,59);
                    view.draw(new Canvas(bitmap));
                    int populated = 0;
                    int bottom = (int)read(view,"safeBottom");
                    for (int py = top+(int)(204*scale); py < view.getHeight()-bottom-149*scale; py += 8)
                        for (int px = 0; px < view.getWidth(); px += 8) if (bitmap.getPixel(px,py) != 0xFF17191B) populated++;
                    if (populated < 100) throw new AssertionError("Native battlefield pixels are blank");
                    File output = new File(activity.getExternalFilesDir(null),shot+".png");
                    try (FileOutputStream stream = new FileOutputStream(output)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,stream); }
                    bitmap.recycle();
                    result.putString("stream","PASS: native pinch, two-finger pan, drag cancellation and nonblank battlefield at "+view.getWidth()+"x"+view.getHeight()+"; screenshot="+output.getAbsolutePath()+"\n");
                } catch (Throwable error) { failure[0] = error; }
            });
            if (failure[0] != null) throw new AssertionError("Native gesture test",failure[0]);
            finish(Activity.RESULT_OK,result);
        } catch (Throwable error) {
            result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private static Object read(Object object,String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void touch(View view,long downTime,int action,float[] xy) {
        int count = xy.length/2;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords(); coordinates[i].x = xy[i*2]; coordinates[i].y = xy[i*2+1]; coordinates[i].pressure = 1; coordinates[i].size = .1f;
        }
        MotionEvent event = MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,count,properties,coordinates,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
}
