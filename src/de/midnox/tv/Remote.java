package de.midnox.tv;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.*;
import java.util.*;

/**
 * Handy-Fernbedienung: Webseite im midnox-Design, erreichbar ueber QR-Code (mit geheimem Schluessel).
 * Steuert Emby-Wiedergabe, Apps, TV-Sender, Radio, Einschlaf-Timer und die Suche.
 */
public class Remote {
    static MiniServer server;
    static int port;

    static String key(Context c) {
        SharedPreferences pr = Style.prefs(c);
        String k = pr.getString("remote_key", "");
        if (k.isEmpty()) {
            String chars = "abcdefghjkmnpqrstuvwxyz23456789";
            StringBuilder sb = new StringBuilder();
            Random r = new java.security.SecureRandom();
            for (int i = 0; i < 10; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
            k = sb.toString();
            pr.edit().putString("remote_key", k).apply();
        }
        return k;
    }

    static String url(Context c) { return port == 0 ? null : "http://" + SettingsActivity.ownIp() + ":" + port + "/?k=" + key(c); }

    static synchronized void start(final Context ctx) {
        if (server != null) return;
        final Context c = ctx.getApplicationContext();
        server = new MiniServer(new MiniServer.Handler() { public String[] handle(String m, String path, String body) { return Remote.handle(c, path, body); } });
        try { port = server.start(8130); } catch (Exception e) { server = null; port = 0; }
    }

    static String[] handle(final Context c, String path, String body) {
        String k = key(c);
        if (!path.contains("k=" + k)) return new String[]{"text/html", MiniServer.page("midnox TV", "<h1>midnox TV</h1><p class=sub>Bitte den QR-Code am Fernseher neu scannen.</p>", "")};
        if (path.startsWith("/api/")) {
            try { return new String[]{"application/json", api(c, path.substring(5, path.indexOf('?') < 0 ? path.length() : path.indexOf('?')), body)}; }
            catch (Exception e) { return new String[]{"application/json", "{\"error\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}"}; }
        }
        return new String[]{"text/html", page(k)};
    }

    static void ui(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
    static void startAct(Context c, Intent i) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { c.startActivity(i); } catch (Exception ignored) {} }

    static String api(final Context c, String cmd, String body) throws Exception {
        final JSONObject in = new JSONObject(body.isEmpty() ? "{}" : body);
        switch (cmd) {
            case "state": {
                JSONObject o = new JSONObject();
                JSONArray apps = new JSONArray();
                PackageManager pm = c.getPackageManager();
                for (String p : HomeActivity.favs(c)) {
                    if (Style.launchIntent(pm, p) == null) continue;
                    try { apps.put(new JSONObject().put("pkg", p).put("name", pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString())); } catch (Exception ignored) {}
                }
                o.put("apps", apps);
                JSONArray tv = new JSONArray();
                for (String[] ch : TvRows.CHANNELS) tv.put(new JSONObject().put("id", ch[0]).put("name", ch[1]));
                o.put("tv", tv);
                JSONArray st = new JSONArray();
                for (Stations.Station s : Stations.ALL) st.put(new JSONObject().put("id", s.id).put("name", s.name));
                o.put("stations", st);
                RadioService r = RadioService.inst;
                o.put("radio", r != null && (r.playing || r.preparing) ? r.station.name + (r.current == null ? "" : " · " + r.current.artist + " – " + r.current.title) : "");
                o.put("sleep", Sleep.remainingMin(c));
                return o.toString();
            }
            case "emby": {   // PlayPause, Rewind, FastForward, Stop
                boolean ok = Daily.embyCommand(c, in.optString("c", "PlayPause"));
                return "{\"ok\":" + ok + "}";
            }
            case "app":
                ui(new Runnable() { public void run() { Intent i = Style.launchIntent(c.getPackageManager(), in.optString("pkg")); if (i != null) startAct(c, i); } });
                return "{\"ok\":true}";
            case "home":
                ui(new Runnable() { public void run() { startAct(c, new Intent(c, HomeActivity.class)); } });
                return "{\"ok\":true}";
            case "tv":
                ui(new Runnable() { public void run() { TvRows.openTv(c, in.optString("id")); } });
                return "{\"ok\":true}";
            case "radio":
                ui(new Runnable() { public void run() {
                    String id = in.optString("id");
                    if (id.isEmpty()) { RadioService.start(c, "toggle"); return; }
                    Style.prefs(c).edit().putString("radio_station", id).apply();
                    if (RadioService.inst != null) RadioService.inst.switchTo(Stations.byId(id)); else RadioService.start(c, "play");
                }});
                return "{\"ok\":true}";
            case "sleep":
                Sleep.set(c, in.optInt("min"));
                return "{\"ok\":true}";
            case "search":
                ui(new Runnable() { public void run() { Intent i = new Intent(c, SearchActivity.class); i.putExtra("q", in.optString("q")); startAct(c, i); } });
                return "{\"ok\":true}";
        }
        return "{\"error\":\"unbekannt\"}";
    }

    static String page(String k) {
        return MiniServer.page("midnox TV Fernbedienung",
            "<h1>midnox TV</h1><p class=sub id=st>Fernbedienung</p>"
            + "<style>.g{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:18px}.g button{padding:14px 4px;font-size:15px;border-radius:14px}"
            + ".l{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin-bottom:18px}.l button{background:#1a1928;border:1px solid #2e2c45;border-radius:12px;padding:12px;text-align:left}"
            + "h3{font-size:12px;letter-spacing:.25em;color:#8a8fa8;font-weight:500;margin:18px 0 10px}</style>"
            + "<h3>WIEDERGABE (EMBY)</h3><div class=g><button onclick=\"e('Rewind')\">⏪</button><button onclick=\"e('PlayPause')\">⏯</button><button onclick=\"e('FastForward')\">⏩</button><button onclick=\"e('Stop')\">⏹</button></div>"
            + "<h3>SUCHE</h3><input id=q placeholder='Film, Serie, Sendung …' onkeydown=\"if(event.key=='Enter')a('search',{q:this.value})\"><button onclick=\"a('search',{q:document.getElementById('q').value})\">Am Fernseher suchen</button>"
            + "<h3>APPS</h3><div class=l id=apps></div>"
            + "<h3>RADIO</h3><div class=g><button onclick=\"a('radio',{})\">⏯</button></div><div class=l id=radio></div>"
            + "<h3>TV</h3><div class=l id=tv></div>"
            + "<h3>EINSCHLAF-TIMER</h3><div class=g><button onclick=\"a('sleep',{min:15})\">15</button><button onclick=\"a('sleep',{min:30})\">30</button><button onclick=\"a('sleep',{min:60})\">60</button><button onclick=\"a('sleep',{min:0})\">Aus</button></div>"
            + "<div class=g><button onclick=\"a('home',{})\" style='grid-column:span 4'>Startseite</button></div>",
            "var k='" + k + "';function a(c,o){return fetch('/api/'+c+'?k='+k,{method:'POST',body:JSON.stringify(o||{})}).then(function(r){return r.json()}).then(function(j){setTimeout(load,800);return j})}"
            + "function e(c){a('emby',{c:c}).then(function(j){if(!j.ok)st('Gerade läuft nichts in Emby')})}"
            + "function st(s){document.getElementById('st').textContent=s}"
            + "function esc(s){return String(s).replace(/[&<>\"']/g,function(c){return'&#'+c.charCodeAt(0)+';'})}"
            + "function list(id,items,fn){var d=document.getElementById(id);d.innerHTML='';items.forEach(function(it){var b=document.createElement('button');b.innerHTML=esc(it.name);b.onclick=function(){fn(it)};d.appendChild(b)})}"
            + "var first=true;function load(){fetch('/api/state?k='+k,{method:'POST',body:'{}'}).then(function(r){return r.json()}).then(function(s){"
            + "st((s.radio?'♪ '+s.radio:'Fernbedienung')+(s.sleep>0?'  ·  Timer '+s.sleep+' Min.':''));"
            + "if(first){first=false;list('apps',s.apps,function(it){a('app',{pkg:it.pkg})});list('radio',s.stations,function(it){a('radio',{id:it.id})});list('tv',s.tv,function(it){a('tv',{id:it.id})})}})}"
            + "load();setInterval(load,10000);");
    }
}
