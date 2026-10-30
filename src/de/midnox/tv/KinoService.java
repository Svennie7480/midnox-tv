package de.midnox.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Kino-Modus: beobachtet die Emby-Wiedergabe auf diesem Fire TV und steuert das Wohnzimmerlicht ueber Home Assistant.
 *  Start  -> Zustand sichern (scene.create), Lichter aus, TV LED 20 %
 *  Pause  -> Lichter 25 %
 *  Ende   -> gesicherten Zustand wiederherstellen
 * Einstellungen (ha.json "kino"): area, ambient, ambient_pct, pause_pct
 */
public class KinoService extends Service {
    static final int IDLE = 0, PLAYING = 1, PAUSED = 2;
    volatile boolean running;
    Thread worker;
    int state = IDLE, idleCount = 0;
    boolean active;            // Kino-Szene wurde angewendet
    List<String> lights;       // Lichter im Bereich, ohne Ambient
    String ambient;            // TV LED

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        foreground();
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(new Runnable() { public void run() { loop(); } }, "kino");
            worker.start();
        }
        return START_STICKY;
    }

    void foreground() {
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("kino", "midnox Kino-Modus", NotificationManager.IMPORTANCE_MIN));
                b = new Notification.Builder(this, "kino");
            } else b = new Notification.Builder(this);
            b.setContentTitle("midnox TV").setContentText("Kino-Modus aktiv").setSmallIcon(R.drawable.icon);
            startForeground(42, b.build());
        } catch (Exception ignored) {}
    }

    @Override public void onDestroy() { running = false; super.onDestroy(); }

    void status(String s) {
        Style.prefs(this).edit().putString("kino_status", new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date()) + "  " + s).apply();
    }

    void loop() {
        String ip = SettingsActivity.ownIp();
        while (running) {
            try {
                SharedPreferences pr = Style.prefs(this);
                JSONObject cfg = Style.loadConfig(this);
                Emby emby = Emby.from(this);
                if (pr.getBoolean("kino_on", true) && cfg != null && emby != null && cfg.has("url")) {
                    int now = playState(emby, ip);
                    if (now == IDLE) idleCount++; else idleCount = 0;
                    if (now == IDLE && idleCount < 3) now = state;       // kurze Luecken (Folgenwechsel) ueberbruecken
                    if (now != state) transition(cfg, pr, state, now);
                    state = now;
                }
            } catch (Exception e) { status("Fehler: " + e.getClass().getSimpleName()); }
            try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
        }
    }

    /** Wiedergabe-Zustand der Emby-Sitzung, die von dieser IP kommt. */
    int playState(Emby emby, String ip) throws Exception {
        JSONArray sessions = new JSONArray(Style.httpGet(emby.base + "/emby/Sessions?ActiveWithinSeconds=120" + emby.q, null));
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.getJSONObject(i);
            String rep = s.optString("RemoteEndPoint");
            if (!(rep.equals(ip) || rep.startsWith(ip + ":"))) continue;
            JSONObject item = s.optJSONObject("NowPlayingItem");
            if (item == null || !"Video".equals(item.optString("MediaType"))) continue;
            JSONObject ps = s.optJSONObject("PlayState");
            return ps != null && ps.optBoolean("IsPaused") ? PAUSED : PLAYING;
        }
        return IDLE;
    }

    void transition(JSONObject cfg, SharedPreferences pr, int from, int to) throws Exception {
        String base = cfg.getString("url").replaceAll("/+$", ""), token = cfg.getString("token");
        JSONObject k = cfg.optJSONObject("kino");
        if (k == null) k = new JSONObject();
        if (to == PLAYING && !active) {
            if (pr.getBoolean("kino_dark", true) && !dark(base, token)) { status("Wiedergabe – hell draußen, Licht bleibt"); return; }
            loadLights(base, token, k);
            JSONArray all = new JSONArray(lights);
            if (ambient != null) all.put(ambient);
            call(base, token, "scene", "create", new JSONObject().put("scene_id", "midnox_kino_vorher").put("snapshot_entities", all));
            playScene(base, token, k);
            active = true;
            status("Film läuft – Kino-Licht an");
        } else if (to == PLAYING && active) {
            playScene(base, token, k);
            status("Film läuft weiter");
        } else if (to == PAUSED && active) {
            if (!lights.isEmpty()) call(base, token, "light", "turn_on", new JSONObject().put("entity_id", new JSONArray(lights)).put("brightness_pct", k.optInt("pause_pct", 25)));
            status("Pause – Licht 25 %");
        } else if (to == IDLE && active) {
            call(base, token, "scene", "turn_on", new JSONObject().put("entity_id", "scene.midnox_kino_vorher"));
            active = false;
            status("Wiedergabe beendet – Licht wiederhergestellt");
        }
    }

    void playScene(String base, String token, JSONObject k) throws Exception {
        if (!lights.isEmpty()) call(base, token, "light", "turn_off", new JSONObject().put("entity_id", new JSONArray(lights)));
        if (ambient != null) call(base, token, "light", "turn_on", new JSONObject().put("entity_id", ambient).put("brightness_pct", k.optInt("ambient_pct", 20)));
    }

    /** Lichter im Bereich (Standard Wohnzimmer), die erreichbar sind; Ambient-Licht separat. */
    void loadLights(String base, String token, JSONObject k) throws Exception {
        String area = k.optString("area", "Wohnzimmer"), amb = k.optString("ambient", "TV LED");
        String tpl = "{{ area_entities('" + area + "') | select('match','light\\\\.') | list | tojson }}";
        JSONArray ids = new JSONArray(Style.httpPost(base + "/api/template", token, new JSONObject().put("template", tpl).toString()).trim());
        lights = new ArrayList<>(); ambient = null;
        for (int i = 0; i < ids.length(); i++) {
            String id = ids.getString(i);
            JSONObject st = new JSONObject(Style.httpGet(base + "/api/states/" + id, token));
            if ("unavailable".equals(st.optString("state"))) continue;
            String name = st.optJSONObject("attributes") == null ? "" : st.getJSONObject("attributes").optString("friendly_name");
            if (name.equalsIgnoreCase(amb)) ambient = id; else lights.add(id);
        }
    }

    boolean dark(String base, String token) {
        try { return "below_horizon".equals(new JSONObject(Style.httpGet(base + "/api/states/sun.sun", token)).optString("state")); }
        catch (Exception e) { return true; }
    }

    void call(String base, String token, String domain, String service, JSONObject data) throws Exception {
        Style.httpPost(base + "/api/services/" + domain + "/" + service, token, data.toString());
    }
}
