package de.midnox.quiz;

import android.content.Context;
import android.media.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Moderatoren-Stimme: Sprachdateien vom Server (ElevenLabs, dort einmal erzeugt), hier zwischengespeichert. */
final class Voice {
    static Context ctx;
    static volatile boolean available;   // Server hat eine Stimme eingerichtet
    static volatile int gen;
    static MediaPlayer mp;
    static final Object lock = new Object();

    static void init(Context c) {
        ctx = c.getApplicationContext();
        new Thread(new Runnable() { public void run() {
            try { available = new org.json.JSONObject(U.http(Scores.SERVER + "?a=tts&info=1", null)).optBoolean("on"); } catch (Exception e) { available = false; }
        }}).start();
    }
    static boolean on() { return available && ctx != null && U.prefs(ctx).getBoolean("voice", true) && Audio.on(); }
    static File file(String key) { File d = new File(ctx.getCacheDir(), "tts"); d.mkdirs(); return new File(d, key.replaceAll("[^A-Za-z0-9_]", "_") + ".mp3"); }
    static String url(String key) {
        try { return Scores.SERVER + "?a=tts&" + (key.startsWith("p:") ? "p=" : "q=") + URLEncoder.encode(key.substring(2), "UTF-8"); } catch (Exception e) { return ""; }
    }
    static File fetch(String key) {
        File f = file(key);
        if (f.exists() && f.length() > 500) return f;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url(key)).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(45000);
            if (c.getResponseCode() != 200) return null;
            File t = new File(f.getPath() + ".part");
            try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(t)) { byte[] b = new byte[16384]; int n; while ((n = in.read(b)) > 0) o.write(b, 0, n); }
            if (t.length() > 500 && t.renameTo(f)) return f;
        } catch (Exception ignored) {}
        return null;
    }
    /** Im Hintergrund vorladen (z. B. die naechste Frage). */
    static void prefetch(final String... keys) {
        if (!on()) return;
        new Thread(new Runnable() { public void run() { for (String k : keys) fetch(k); } }).start();
    }
    /** Nacheinander sprechen; bricht laufende Ansagen ab. Schluessel: "q:<id>" oder "p:<satz>". */
    static void say(final String... keys) {
        stop();
        if (!on() || keys.length == 0) return;
        final int g = ++gen;
        new Thread(new Runnable() { public void run() {
            for (String k : keys) {
                if (g != gen) return;
                File f = fetch(k);
                if (f == null || g != gen) continue;
                playBlocking(f, g);
            }
            if (g == gen) Audio.duck(false);
        }}).start();
    }
    static void playBlocking(File f, int g) {
        final Object done = new Object();
        final boolean[] fin = {false};
        try {
            MediaPlayer m = new MediaPlayer();
            m.setAudioStreamType(AudioManager.STREAM_MUSIC);
            m.setDataSource(f.getPath()); m.prepare(); m.setVolume(1f, 1f);
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer x) { synchronized (done) { fin[0] = true; done.notifyAll(); } } });
            synchronized (lock) { if (g != gen) { m.release(); return; } if (mp != null) mp.release(); mp = m; }
            Audio.duck(true);
            m.start();
            long until = System.currentTimeMillis() + m.getDuration() + 3000;
            synchronized (done) { while (!fin[0] && g == gen && System.currentTimeMillis() < until) done.wait(200); }
        } catch (Exception ignored) {}
    }
    static void stop() {
        gen++;
        synchronized (lock) { if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; } }
        Audio.duck(false);
    }
    static String pick(String... options) { return options[new Random().nextInt(options.length)]; }
}
