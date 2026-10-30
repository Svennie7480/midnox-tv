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
    List<String> lights;       // werden ausgeschaltet
    List<String> ambients = new ArrayList<>();   // werden gedimmt eingeschaltet

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
            loadLights(base, token, k, pr);
            JSONArray all = new JSONArray(lights);
            for (String a : ambients) all.put(a);
            call(base, token, "scene", "create", new JSONObject().put("scene_id", "midnox_kino_vorher").put("snapshot_entities", all));
            playScene(base, token, k, pr);
            active = true;
            status("Film läuft – Kino-Licht an");
        } else if (to == PLAYING && active) {
            playScene(base, token, k, pr);
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

    void playScene(String base, String token, JSONObject k, SharedPreferences pr) throws Exception {
        if (!lights.isEmpty()) call(base, token, "light", "turn_off", new JSONObject().put("entity_id", new JSONArray(lights)));
        if (!ambients.isEmpty()) call(base, token, "light", "turn_on", new JSONObject().put("entity_id", new JSONArray(ambients))
                .put("brightness_pct", pr.getInt("kino_amb", k.optInt("ambient_pct", 20))));
    }

    /** Lampen-Auswahl aus dem Einstellungsmenue; ohne Auswahl: Bereich Wohnzimmer aus, "TV LED" als Ambient. */
    void loadLights(String base, String token, JSONObject k, SharedPreferences pr) throws Exception {
        lights = new ArrayList<>(); ambients = new ArrayList<>();
        String saved = pr.getString("kino_lights", null);
        if (saved != null) {
            JSONObject m = new JSONObject(saved);
            for (Iterator<String> it = m.keys(); it.hasNext(); ) {
                String id = it.next(), mode = m.optString(id);
                if ("off".equals(mode)) lights.add(id); else if ("ambient".equals(mode)) ambients.add(id);
            }
            return;
        }
        for (JSONArray l : allLights(base, token)) {
            if (!l.optString(2).equalsIgnoreCase(k.optString("area", "Wohnzimmer"))) continue;
            if (l.optString(1).equalsIgnoreCase(k.optString("ambient", "TV LED"))) ambients.add(l.optString(0)); else lights.add(l.optString(0));
        }
    }

    /** Alle erreichbaren Lampen als [entity_id, Name, Bereich], ohne LED-Segmente. */
    static List<JSONArray> allLights(String base, String token) throws Exception {
        String tpl = "{% set ns = namespace(l=[]) %}{% for s in states.light %}{% if s.state != 'unavailable' and 'Segment' not in s.name %}"
                + "{% set ns.l = ns.l + [[s.entity_id, s.name, area_name(s.entity_id) or '']] %}{% endif %}{% endfor %}{{ ns.l | tojson }}";
        JSONArray arr = new JSONArray(Style.httpPost(base + "/api/template", token, new JSONObject().put("template", tpl).toString()).trim());
        List<JSONArray> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONArray(i));
        return out;
    }

    boolean dark(String base, String token) {
        try { return "below_horizon".equals(new JSONObject(Style.httpGet(base + "/api/states/sun.sun", token)).optString("state")); }
        catch (Exception e) { return true; }
    }

    void call(String base, String token, String domain, String service, JSONObject data) throws Exception {
        Style.httpPost(base + "/api/services/" + domain + "/" + service, token, data.toString());
    }
}
