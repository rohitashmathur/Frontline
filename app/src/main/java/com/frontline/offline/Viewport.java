package com.frontline.offline;

/** Shared native/preview transform: fixed-size controls, wider tablet battlefields. */
final class Viewport {
    final float scale, offsetX, height, battlefieldWidth, minimumTouch;
    Viewport(int width,int availableHeight,float density,boolean tablet) {
        float fit = Math.min(width/420f,availableHeight/620f);
        scale = Math.max(.01f,tablet ? Math.min(fit,density*1.25f) : fit);
        offsetX = (width-420*scale)/2;
        height = availableHeight/scale;
        battlefieldWidth = tablet ? Math.max(420,width/scale) : 420;
        minimumTouch = 48*density/scale;
    }
}
