package de.midnox.tv;

import android.content.*;
import android.media.AudioManager;
import org.json.*;
import java.util.*;

/** Einschlaf-Timer: Radio/Filme stoppen, Licht aus, Fernseher aus (ueber Home Assistant). */
public class Sleep {
    static final int[] STEPS = {0, 15, 30, 45, 60, 90, 120};

    static long at(Context c) { return Style.prefs(c).getLong("sleep_at", 0); }
    static int remainingMin(Context c) {
        long a = at(c);
        if (a <= 0) return 0;
        return (int) Math.max(1, Math.ceil((a - System.currentTimeMillis()) / 60000.0));
    }
    static void set(Context c, int min) {
        restoreVolume(c);
        SharedPreferences.Editor e = Style.prefs(c).edit();
        e.putLong("sleep_at", min <= 0 ? 0 : System.currentTimeMillis() + min * 60000L).remove("sleep_fading");
        e.apply();
        if (min > 0) c.startService(new Intent(c, KinoService.class));   // ueberwacht den Timer
    }
    /** Abgesenkte Lautstaerke zuruecksetzen (beim Verlaengern/Abbrechen). */
    static void restoreVolume(Context c) {
        SharedPreferences pr = Style.prefs(c);
        int vol = pr.getInt("sleep_vol", -1);
        if (vol >= 0) {
            try { ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0); } catch (Exception ignored) {}
            pr.edit().remove("sleep_vol").apply();
        }
        if (RadioService.inst != null) RadioService.inst.volume(1f);
    }

    /** Naechste Stufe (ab aktueller Restzeit). */
    static void cycle(Context c, int dir) {
        int rem = remainingMin(c), i = 0;
        if (dir > 0) { while (i < STEPS.length && STEPS[i] <= rem) i++; if (i >= STEPS.length) i = 0; }
        else { i = STEPS.length - 1; while (i > 0 && STEPS[i] >= rem && rem > 0) i--; if (rem == 0) i = STEPS.length - 1; }
        set(c, STEPS[i]);
    }
    static String label(Context c) { int m = remainingMin(c); return m <= 0 ? "Aus" : m + " Min."; }

    /** Alles schlafen legen (im Hintergrund-Thread aufrufen). */
    static void execute(Context c) {
        SharedPreferences pr = Style.prefs(c);
        pr.edit().putLong("sleep_at", 0).putBoolean("sleep_mode", true).apply();
        // 1. Radio aus
        if (RadioService.inst != null) RadioService.start(c, "stop");
        // 2. andere Apps pausieren: Tonfokus dauerhaft anfordern
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try { am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN); } catch (Exception ignored) {}
        // Lautstaerke wiederherstellen (wurde in der letzten Minute abgesenkt)
        int vol = pr.getInt("sleep_vol", -1);
        if (vol >= 0) { try { am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0); } catch (Exception ignored) {} pr.edit().remove("sleep_vol").apply(); }
        // 3. Emby-Wiedergabe dieses Geraets stoppen
        try {
            Emby e = Emby.from(c);
            if (e != null) {
                String ip = SettingsActivity.ownIp();
                JSONArray ss = new JSONArray(Style.httpGet(e.base + "/emby/Sessions?ActiveWithinSeconds=600" + e.q, null));
                for (int i = 0; i < ss.length(); i++) {
                    JSONObject s = ss.getJSONObject(i);
                    String rep = s.optString("RemoteEndPoint");
                    if ((rep.equals(ip) || rep.startsWith(ip + ":")) && s.has("NowPlayingItem"))
                        Style.httpPost(e.base + "/emby/Sessions/" + s.getString("Id") + "/Playing/Stop?" + e.q.substring(1), null, "{}");
                }
            }
        } catch (Exception ignored) {}
        // 4./5. Home Assistant: Licht aus, Fernseher aus
        try {
            JSONObject cfg = Style.loadConfig(c);
            if (cfg != null) {
                String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
                if (pr.getBoolean("sleep_lights", true)) {
                    JSONArray ids = new JSONArray();
                    try {
                        JSONObject m = new JSONObject(pr.getString("kino_lights", "{}"));
                        for (Iterator<String> it = m.keys(); it.hasNext(); ) ids.put(it.next());
                    } catch (Exception ignored) {}
                    if (ids.length() > 0) Style.httpPost(base + "/api/services/light/turn_off", tok, new JSONObject().put("entity_id", ids).put("transition", 20).toString());
                }
                String tv = pr.getString("sleep_tv", "");
                if (!tv.isEmpty()) {
                    String domain = tv.substring(0, tv.indexOf('.'));
                    // Steckdose erst nach kurzer Pause abschalten (Licht-Uebergang, Emby-Stopp laufen noch)
                    if (domain.equals("switch")) { try { Thread.sleep(15000); } catch (InterruptedException ignored) {} }
                    Style.httpPost(base + "/api/services/" + domain + "/turn_off", tok, new JSONObject().put("entity_id", tv).toString());
                }
            }
        } catch (Exception ignored) {}
    }
}
