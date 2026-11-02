package de.midnox.quiz;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import java.util.*;

/** Musik (Endlosschleifen) und Effekte. Alles aus assets/snd, selbst erzeugt. */
final class Audio {
    static Context ctx;
    static MediaPlayer music, fx;
    static String musicName;
    static SoundPool pool;
    static final Map<String, Integer> ids = new HashMap<>();
    static float vol = 0.5f;      // Musik leiser als Effekte
    static boolean paused;

    static void init(Context c) {
        ctx = c.getApplicationContext();
        try {
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            for (String n : new String[]{"click", "tick"}) { AssetFileDescriptor fd = ctx.getAssets().openFd("snd/" + n + ".ogg"); ids.put(n, pool.load(fd, 1)); }
        } catch (Exception ignored) {}
    }
    static boolean on() { return ctx != null && U.prefs(ctx).getBoolean("sound", true); }
    static boolean musicOn() { return ctx != null && U.prefs(ctx).getBoolean("music", true); }

    static MediaPlayer make(String name, boolean loop) throws Exception {
        MediaPlayer m = new MediaPlayer();
        AssetFileDescriptor fd = ctx.getAssets().openFd("snd/" + name + ".ogg");
        m.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength()); fd.close();
        m.setAudioStreamType(AudioManager.STREAM_MUSIC);
        m.setLooping(loop);
        m.prepare();
        return m;
    }
    /** Hintergrundmusik wechseln (null = Stille). Gleiche Musik laeuft einfach weiter. */
    static void music(String name) {
        if (!on() || !musicOn()) name = null;
        if (name == null ? musicName == null : name.equals(musicName)) { if (music != null && !paused && !music.isPlaying()) try { music.start(); } catch (Exception ignored) {} return; }
        stopMusic();
        musicName = name;
        if (name == null || paused) return;
        try { music = make(name, true); float v = ducked ? vol * 0.3f : vol; music.setVolume(v, v); music.start(); } catch (Exception e) { music = null; }
    }
    static void stopMusic() { if (music != null) { try { music.release(); } catch (Exception ignored) {} music = null; } musicName = null; }
    /** Einmaliger Effekt (lock, right, wrong, win). Musik wird kurz gedimmt. */
    static void fx(String name) {
        if (!on()) return;
        try {
            if (fx != null) fx.release();
            fx = make(name, false); fx.setVolume(0.9f, 0.9f);
            if (music != null) music.setVolume(vol * 0.25f, vol * 0.25f);
            fx.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer mp) { duck(ducked); } });
            fx.start();
        } catch (Exception ignored) {}
    }
    static volatile boolean ducked;
    /** Musik leiser, waehrend die Moderatoren-Stimme spricht. */
    static void duck(boolean d) { ducked = d; try { if (music != null) { float v = d ? vol * 0.3f : vol; music.setVolume(v, v); } } catch (Exception ignored) {} }
    static void stopFx() { if (fx != null) { try { fx.release(); } catch (Exception ignored) {} fx = null; } if (music != null) music.setVolume(vol, vol); }
    static void blip(String name) { if (!on() || pool == null || !ids.containsKey(name)) return; pool.play(ids.get(name), 0.6f, 0.6f, 1, 0, 1f); }

    static void pause() { paused = true; if (music != null) try { music.pause(); } catch (Exception ignored) {} stopFx(); }
    static void resume() { paused = false; String n = musicName; if (music != null) try { music.start(); } catch (Exception ignored) {} else if (n != null) { musicName = null; music(n); } }
    static void release() { stopMusic(); stopFx(); if (pool != null) pool.release(); pool = null; }
}
