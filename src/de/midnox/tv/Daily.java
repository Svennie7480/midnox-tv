package de.midnox.tv;

import android.content.*;
import android.media.AudioManager;
import android.os.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Hintergrund-Pruefungen (aus dem KinoService, alle paar Sekunden):
 * Dienstplan-Schlafenszeit, TV-Erinnerungen und Anruf auf dem Handy.
 */
public class Daily {
    static long lastShift, lastCall, lastWish;
    static long wishSeen = -1;
    static String callState = "idle";
    static boolean pausedEmby, duckedRadio;
    static AudioManager.OnAudioFocusChangeListener callFocus = new AudioManager.OnAudioFocusChangeListener() { public void onAudioFocusChange(int f) {} };

    static void tick(Context c) {
        try { reminders(c); } catch (Exception ignored) {}
        long now = System.currentTimeMillis();
        if (now - lastShift > 60000) { lastShift = now; try { shift(c); } catch (Exception ignored) {} }
        if (now - lastCall > 3000) { lastCall = now; try { call(c); } catch (Exception ignored) {} }
        if (now - lastWish > 15000) { lastWish = now; try { wishes(c); } catch (Exception e) { lastWish = now + 10 * 60000L; } }
    }

    static boolean screenOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    // ------------------------------------------------------------ Dienstplan
    /** Beginn des naechsten Dienstes aus Home Assistant (Kachel mit format "dienst"). */
    static Object[] nextShift(Context c) throws Exception {
        JSONObject cfg = Style.loadConfig(c);
        if (cfg == null) return null;
        String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
        JSONArray keys = null;
        JSONArray tiles = cfg.optJSONArray("tiles");
        for (int i = 0; tiles != null && i < tiles.length(); i++) if ("dienst".equals(tiles.getJSONObject(i).optString("format"))) keys = tiles.getJSONObject(i).optJSONArray("main");
        if (keys == null) keys = new JSONArray().put("sensor.nachster_dienst");
        JSONArray states = new JSONArray(Style.httpGet(base + "/api/states", tok));
        Map<String, JSONObject> byId = new HashMap<>(), byName = new HashMap<>();
        for (int i = 0; i < states.length(); i++) {
            JSONObject s = states.getJSONObject(i);
            byId.put(s.getString("entity_id"), s);
            JSONObject at = s.optJSONObject("attributes");
            if (at != null && at.has("friendly_name")) byName.put(at.getString("friendly_name").toLowerCase(Locale.ROOT), s);
        }
        JSONObject s = HaPanel.find(keys, byId, byName);
        if (s == null) return null;
        String start = s.optJSONObject("attributes") == null ? "" : s.optJSONObject("attributes").optString("start");
        Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).parse(start);
        return new Object[]{d, s.optString("state")};
    }

    /** {Erinnerung, Schlafenszeit, Aufstehen} in ms fuer einen Dienstbeginn. */
    static long[] plan(Context c, long start) {
        SharedPreferences pr = Style.prefs(c);
        long wake = start - pr.getInt("shift_lead", 75) * 60000L;
        long bed = wake - (long) (pr.getFloat("shift_sleep", 7.5f) * 3600000L);
        return new long[]{bed - 30 * 60000L, bed, wake};
    }

    static void shift(Context c) throws Exception {
        SharedPreferences pr = Style.prefs(c);
        if (!pr.getBoolean("shift_on", true) || !screenOn(c)) return;
        if (Sleep.at(c) > 0 || pr.getBoolean("sleep_mode", false)) return;
        Object[] n = nextShift(c);
        if (n == null) return;
        long start = ((Date) n[0]).getTime(), now = System.currentTimeMillis();
        long[] p = plan(c, start);
        // Fenster: ab Erinnerung bis 3 Std. nach Schlafenszeit (aber vor dem Aufstehen)
        if (now < p[0] || now > Math.min(p[1] + 3 * 3600000L, p[2])) return;
        if (pr.getLong("shift_done", 0) == start) return;
        if (now < pr.getLong("shift_snooze", 0)) return;
        pr.edit().putLong("shift_done", start).apply();
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        Calendar sc = Calendar.getInstance(); sc.setTimeInMillis(start);
        Calendar tm = Calendar.getInstance(); tm.add(Calendar.DAY_OF_YEAR, 1);
        String day = HaPanel.same(sc, tm) ? "Morgen" : HaPanel.same(sc, Calendar.getInstance()) ? "Heute" : new SimpleDateFormat("EEEE", Locale.GERMANY).format(new Date(start));
        float hrs = pr.getFloat("shift_sleep", 7.5f);
        String text = day + " um " + hm.format(new Date(start)) + " Dienst" + (String.valueOf(n[1]).isEmpty() ? "" : " (" + n[1] + ")") + ".\n"
                + "Aufstehen um " + hm.format(new Date(p[2])) + " – für " + fmtH(hrs) + " Schlaf solltest du um " + hm.format(new Date(p[1])) + " schlafen.";
        PromptActivity.show(c, "shift", "Zeit fürs Bett", text, null);
    }
    static String fmtH(float h) { return (h == Math.floor(h) ? String.valueOf((int) h) : String.format(Locale.GERMANY, "%.1f", h)) + " Std."; }

    // ------------------------------------------------------------ TV-Erinnerungen
    static JSONArray reminderList(Context c) { try { return new JSONArray(Style.prefs(c).getString("tv_reminders", "[]")); } catch (Exception e) { return new JSONArray(); } }
    static boolean hasReminder(Context c, String ch, long start) {
        JSONArray a = reminderList(c);
        for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null && o.optString("ch").equals(ch) && o.optLong("start") == start) return true; }
        return false;
    }
    /** Erinnerung an/aus; true = jetzt gesetzt. */
    static boolean toggleReminder(Context c, String ch, String chName, long start, String title) {
        JSONArray a = reminderList(c), out = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o.optString("ch").equals(ch) && o.optLong("start") == start) { removed = true; continue; }
            out.put(o);
        }
        if (!removed) try { out.put(new JSONObject().put("ch", ch).put("name", chName).put("start", start).put("title", title)); } catch (Exception ignored) {}
        Style.prefs(c).edit().putString("tv_reminders", out.toString()).apply();
        c.startService(new Intent(c, KinoService.class));
        return !removed;
    }

    static void reminders(Context c) {
        JSONArray a = reminderList(c), keep = new JSONArray();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            long start = o.optLong("start");
            if (now >= start - 5 * 60000L) {
                changed = true;
                if (now < start + 30 * 60000L) {
                    Bundle x = new Bundle(); x.putString("channel", o.optString("ch"));
                    long min = Math.max(0, (start - now) / 60000);
                    PromptActivity.show(c, "reminder", o.optString("title"),
                            (min > 0 ? "Beginnt in " + min + " Min." : "Läuft jetzt") + " auf " + o.optString("name") + " (" + new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(start)) + " Uhr).", x);
                }
            } else keep.put(o);
        }
        if (changed) Style.prefs(c).edit().putString("tv_reminders", keep.toString()).apply();
    }

    // ------------------------------------------------------------ Musikwuensche
    /** Neue Wuensche von midnox.de holen und einblenden (nur solange das Radio laeuft oder der Fernseher an ist). */
    static void wishes(final Context c) throws Exception {
        if (!Style.prefs(c).getBoolean("wish_show", true) || !screenOn(c)) return;
        JSONObject o = new JSONObject(RadioNet.get(RadioService.wishBase(c) + "?list=1&since=" + Math.max(0, wishSeen)));
        JSONArray a = o.optJSONArray("wishes");
        long max = wishSeen;
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject w = a.getJSONObject(i);
            long id = w.optLong("id");
            if (id > max) max = id;
            if (wishSeen < 0 || id <= wishSeen) continue;   // beim ersten Abruf nichts Altes zeigen
            final String text = "\u266A  " + (w.optString("name").isEmpty() ? "Jemand" : w.optString("name")) + " w\u00fcnscht sich: " + w.optString("title")
                    + (w.optString("artist").isEmpty() ? "" : " \u2013 " + w.optString("artist"));
            new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() { Style.toast(c, text); } });
        }
        wishSeen = Math.max(max, 0);
    }

    // ------------------------------------------------------------ Anruf
    static void call(Context c) throws Exception {
        SharedPreferences pr = Style.prefs(c);
        if (!pr.getBoolean("call_on", true)) return;
        JSONObject cfg = Style.loadConfig(c);
        if (cfg == null) return;
        String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
        String ent = pr.getString("call_entity", "");
        if (ent.isEmpty()) {   // automatisch: erster Telefonstatus-Sensor der Home-Assistant-App
            JSONArray st = new JSONArray(Style.httpGet(base + "/api/states", tok));
            for (int i = 0; i < st.length(); i++) { String id = st.getJSONObject(i).getString("entity_id"); if (id.startsWith("sensor.") && id.endsWith("_phone_state")) { ent = id; break; } }
            if (ent.isEmpty()) { lastCall = System.currentTimeMillis() + 10 * 60000L; return; }   // keiner da: seltener pruefen
            pr.edit().putString("call_entity", ent).apply();
        }
        String now = new JSONObject(Style.httpGet(base + "/api/states/" + ent, tok)).optString("state");
        if (now.equals(callState)) return;
        String before = callState;
        callState = now;
        if (now.equals("ringing") && before.equals("idle")) startCall(c);
        else if (now.equals("idle") && !before.equals("idle")) endCall(c);
    }

    static void startCall(final Context c) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() {
            Style.toast(c, "☎  Anruf auf dem Handy – alles pausiert");
            RadioService r = RadioService.inst;
            if (r != null && r.playing) { r.volume(0f); duckedRadio = true; }
            try { ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(callFocus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT); } catch (Exception ignored) {}
        }});
        pausedEmby = embyCommand(c, "Pause");
    }

    static void endCall(final Context c) {
        if (pausedEmby) { embyCommand(c, "Unpause"); pausedEmby = false; }
        new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() {
            try { ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(callFocus); } catch (Exception ignored) {}
            if (duckedRadio && RadioService.inst != null) RadioService.inst.volume(1f);
            duckedRadio = false;
        }});
    }

    /** Pause/Unpause an die Emby-Wiedergabe dieses Geraets; true = es lief etwas. */
    static boolean embyCommand(Context c, String cmd) {
        try {
            Emby e = Emby.from(c);
            if (e == null) return false;
            String ip = SettingsActivity.ownIp();
            JSONArray ss = new JSONArray(Style.httpGet(e.base + "/emby/Sessions?ActiveWithinSeconds=600" + e.q, null));
            boolean any = false;
            for (int i = 0; i < ss.length(); i++) {
                JSONObject s = ss.getJSONObject(i);
                String rep = s.optString("RemoteEndPoint");
                if ((rep.equals(ip) || rep.startsWith(ip + ":")) && s.has("NowPlayingItem")) {
                    JSONObject ps = s.optJSONObject("PlayState");
                    if (cmd.equals("Pause") && ps != null && ps.optBoolean("IsPaused")) continue;
                    Style.httpPost(e.base + "/emby/Sessions/" + s.getString("Id") + "/Playing/" + cmd + "?" + e.q.substring(1), null, "{}");
                    any = true;
                }
            }
            return any;
        } catch (Exception ex) { return false; }
    }
}
