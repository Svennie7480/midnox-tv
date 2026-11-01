package de.midnox.tv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.io.*;

/** Eigene Sounds fuer Tor-Alarm und Tor-Jubel: per PC-Verwaltung hochgeladen, ueber jeder App abgespielt. */
class GoalSound {
    static final String[] SLOTS = {"mygoal", "against", "goal", "card"};
    static final String[] NAMES = {"Tor meines Vereins", "Gegentor f\u00fcr meinen Verein", "Tor in anderen Spielen", "Rote / Gelbe Karte"};
    static final String EXT = "mp3|ogg|wav|m4a|aac|opus|flac";
    static MediaPlayer mp;

    static File dir(Context c) { File d = new File(c.getFilesDir(), "sounds"); d.mkdirs(); return d; }
    static File file(Context c, String slot) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) if (f.getName().startsWith(slot + ".")) return f;
        return null;
    }
    static String orig(Context c, String slot) { return Style.prefs(c).getString("gs_name_" + slot, ""); }

    static String receive(Context c, InputStream in, long len, String slot, String name) throws IOException {
        if (java.util.Arrays.asList(SLOTS).indexOf(slot) < 0) return "{\"ok\":false,\"msg\":\"Unbekannter Sound\"}";
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ext.matches(EXT)) return "{\"ok\":false,\"msg\":\"Bitte eine Audiodatei (MP3, OGG, WAV, M4A \u2026)\"}";
        if (len > 10L * 1024 * 1024) return "{\"ok\":false,\"msg\":\"Datei zu gro\u00df (max. 10 MB)\"}";
        delete(c, slot);
        File out = new File(dir(c), slot + "." + ext);
        OutputStream o = new FileOutputStream(out);
        byte[] b = new byte[65536]; long n = 0; int r;
        while ((len < 0 || n < len) && (r = in.read(b, 0, (int) (len < 0 ? b.length : Math.min(b.length, len - n)))) > 0) { o.write(b, 0, r); n += r; }
        o.close();
        Style.prefs(c).edit().putString("gs_name_" + slot, name).apply();
        return "{\"ok\":true,\"msg\":\"Sound gespeichert \u2713\"}";
    }
    static void delete(Context c, String slot) {
        File f; while ((f = file(c, slot)) != null) if (!f.delete()) break;
        Style.prefs(c).edit().remove("gs_name_" + slot).apply();
    }
    static JSONArray list(Context c) throws JSONException {
        JSONArray a = new JSONArray();
        for (int i = 0; i < SLOTS.length; i++) {
            File f = file(c, SLOTS[i]);
            a.put(new JSONObject().put("slot", SLOTS[i]).put("name", NAMES[i]).put("file", f == null ? "" : orig(c, SLOTS[i])).put("kb", f == null ? 0 : f.length() / 1024));
        }
        return a;
    }

    /** Spielt den Sound eines Ereignisses (Tor meines Vereins faellt auf "Tor" zurueck, ein Gegentor nie). */
    static void play(Context c, String slot) {
        if (!Style.prefs(c).getBoolean("gs_on", true)) return;
        File f = file(c, slot);
        if (f == null && slot.equals("mygoal")) f = file(c, "goal");
        if (f != null) playFile(c, f);
    }
    static void test(Context c, String slot) { File f = file(c, slot); if (f != null) playFile(c, f); }

    static void playFile(final Context c, final File f) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() {
            try {
                stop();
                final MediaPlayer p = new MediaPlayer();
                p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                p.setDataSource(f.getAbsolutePath());
                float v = Math.max(0, Math.min(100, Style.prefs(c).getInt("gs_vol", 80))) / 100f;
                p.setVolume(v, v);
                p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer m) { if (mp == m) mp = null; m.release(); } });
                p.prepare();
                p.start();
                mp = p;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { public void run() { if (mp == p) stop(); } }, 20000);   // max. 20 s
            } catch (Exception ignored) {}
        }});
    }
    static void stop() { if (mp != null) { try { mp.stop(); mp.release(); } catch (Exception ignored) {} mp = null; } }
}
