package com.frontline.offline;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

final class BackgroundMusic implements AudioManager.OnAudioFocusChangeListener {
    private final Context context;
    private final AudioManager manager;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private AudioFocusRequest request;
    private MediaPlayer player;
    private boolean enabled, foreground, focused, blocked, failed;

    BackgroundMusic(Context context,boolean enabled) {
        this.context = context; this.enabled = enabled;
        manager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    }

    void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value; blocked = false;
        if (enabled) play(); else stop();
    }

    void resume() { foreground = true; blocked = false; play(); }
    void pause() { foreground = false; stop(); }
    void dispose() { pause(); if (player != null) { player.release(); player = null; } }

    private void play() {
        if (!enabled || !foreground || blocked || failed || manager == null) return;
        try {
            if (!focused) {
                int result;
                if (Build.VERSION.SDK_INT >= 26) {
                    if (request == null) request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes).setOnAudioFocusChangeListener(this).build();
                    result = manager.requestAudioFocus(request);
                } else result = manager.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
                focused = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            if (!focused) return;
            if (player == null) {
                player = MediaPlayer.create(context,R.raw.frontier_theme,attributes,0);
                if (player == null) { failed = true; android.util.Log.w("FrontlineMusic","FAILED"); stop(); return; }
                player.setLooping(true);
                player.setOnErrorListener((media,what,extra) -> {
                    failed = true; android.util.Log.w("FrontlineMusic","FAILED"); stop(); media.release(); player = null; return true;
                });
            }
            player.setVolume(.28f,.28f);
            if (!player.isPlaying()) { player.start(); android.util.Log.d("FrontlineMusic","PLAYING"); }
        } catch (RuntimeException unavailable) { failed = true; android.util.Log.w("FrontlineMusic","FAILED",unavailable); stop(); }
    }

    private void stop() {
        if (player != null) {
            try { if (player.isPlaying()) { player.pause(); android.util.Log.d("FrontlineMusic","PAUSED"); } } catch (IllegalStateException unavailable) { }
        }
        if (focused && manager != null) {
            if (Build.VERSION.SDK_INT >= 26 && request != null) manager.abandonAudioFocusRequest(request);
            else manager.abandonAudioFocus(this);
        }
        focused = false;
    }

    @Override public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (!enabled || !foreground) { focused = true; stop(); return; }
            focused = true; blocked = false; play();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (player != null) player.setVolume(.07f,.07f);
        } else {
            blocked = true;
            if (player != null) {
                try { if (player.isPlaying()) { player.pause(); android.util.Log.d("FrontlineMusic","PAUSED"); } } catch (IllegalStateException unavailable) { }
            }
            if (change == AudioManager.AUDIOFOCUS_LOSS) focused = false;
        }
    }
}
