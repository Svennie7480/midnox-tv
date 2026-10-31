package de.midnox.tv;

import android.content.Context;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Kleiner Webserver fuer die Einrichtung per Handy. Laeuft nur, solange der Einrichtungs-Bildschirm offen ist,
 * und nimmt Aenderungen nur mit der am Fernseher angezeigten PIN an.
 */
public class SetupServer {
    public interface Listener { void onSaved(String summary); }

    final Context ctx; final String pin; final Listener listener;
    volatile List<String> haFound = new ArrayList<>();
    volatile List<JSONObject> embyFound = new ArrayList<>();
    ServerSocket server;
    volatile boolean running;
    int port;

    SetupServer(Context c, String pin, Listener l) { ctx = c.getApplicationContext(); this.pin = pin; listener = l; }

    int start() throws IOException {
        for (port = 8099; port < 8110; port++) {
            try { server = new ServerSocket(port); break; } catch (IOException e) { server = null; }
        }
        if (server == null) throw new IOException("kein freier Port");
        running = true;
        new Thread(new Runnable() { public void run() {
            while (running) {
                try { final Socket s = server.accept(); new Thread(new Runnable() { public void run() { handle(s); } }).start(); }
                catch (IOException e) { if (!running) return; }
            }
        }}, "setup-server").start();
        return port;
    }

    void stop() { running = false; try { server.close(); } catch (Exception ignored) {} }

    void handle(Socket s) {
        try {
            s.setSoTimeout(15000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            String request = line(in);
            if (request == null) return;
            int len = 0; String l;
            while ((l = line(in)) != null && !l.isEmpty()) if (l.toLowerCase(Locale.ROOT).startsWith("content-length:")) len = Integer.parseInt(l.substring(15).trim());
            String body = "";
            if (len > 0) { byte[] b = new byte[len]; int r = 0; while (r < len) { int n = in.read(b, r, len - r); if (n < 0) break; r += n; } body = new String(b, 0, r, "UTF-8"); }
            String type = "text/html", html;
            if (request.startsWith("POST /save")) html = save(form(body));
            else if (request.startsWith("POST /api/")) { type = "application/json"; html = api(request.split(" ")[1], body); }
            else html = page(null);
            byte[] out = html.getBytes("UTF-8");
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + out.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            o.write(out);
            o.flush();
        } catch (Exception ignored) {
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }

    static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) >= 0) { if (c == '\n') break; if (c != '\r') sb.append((char) c); }
        return c < 0 && sb.length() == 0 ? null : sb.toString();
    }

    static Map<String, String> form(String body) throws UnsupportedEncodingException {
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(URLDecoder.decode(kv.substring(0, i), "UTF-8"), URLDecoder.decode(kv.substring(i + 1), "UTF-8").trim());
        }
        return m;
    }

    static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    // ------------------------------------------------------------ Speichern
    String save(Map<String, String> f) {
        if (!pin.equals(f.get("pin"))) return page("<p class=err>Falsche PIN – bitte die PIN vom Fernseher eingeben.</p>");
        StringBuilder msg = new StringBuilder();
        try {
            JSONObject cfg = Style.loadConfig(ctx);
            if (cfg == null) cfg = new JSONObject();
            boolean haOk = false, embyOk = false;

            String haUrl = f.get("ha_url"), token = f.get("ha_token");
            if (haUrl != null && !haUrl.isEmpty()) {
                haUrl = haUrl.replaceAll("/+$", "");
                if (token == null || token.isEmpty()) token = cfg.optString("url").equals(haUrl) ? cfg.optString("token") : "";
                try {
                    Style.httpGet(haUrl + "/api/", token);
                    cfg.put("url", haUrl).put("token", token);
                    haOk = true;
                    msg.append("<p class=ok>✓ Home Assistant verbunden</p>");
                } catch (Exception e) { msg.append("<p class=err>✗ Home Assistant: ").append(esc(e.getMessage())).append(" – Adresse und Token prüfen</p>"); }
            }

            String eUrl = f.get("emby_url"), eUser = f.get("emby_user"), ePw = f.get("emby_pw");
            if (eUrl != null && !eUrl.isEmpty() && eUser != null && !eUser.isEmpty()) {
                eUrl = eUrl.replaceAll("/+$", "");
                try {
                    String dev = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
                    HttpURLConnection c = (HttpURLConnection) new URL(eUrl + "/emby/Users/AuthenticateByName").openConnection();
                    c.setConnectTimeout(5000); c.setReadTimeout(10000);
                    c.setRequestMethod("POST"); c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Emby-Authorization", "Emby UserId=\"\", Client=\"midnox TV\", Device=\"Fire TV\", DeviceId=\"midnox-" + dev + "\", Version=\"1.0\"");
                    OutputStream o = c.getOutputStream();
                    o.write(new JSONObject().put("Username", eUser).put("Pw", ePw == null ? "" : ePw).toString().getBytes("UTF-8"));
                    o.close();
                    if (c.getResponseCode() != 200) throw new IOException(c.getResponseCode() == 401 ? "Benutzername oder Passwort falsch" : "HTTP " + c.getResponseCode());
                    JSONObject r = new JSONObject(Style.read(c.getInputStream()));
                    JSONObject emby = cfg.optJSONObject("emby");
                    if (emby == null) emby = new JSONObject();
                    emby.put("url", eUrl).put("key", r.getString("AccessToken")).put("user", r.getJSONObject("User").getString("Id")).put("server", r.optString("ServerId", ""));
                    cfg.put("emby", emby);
                    embyOk = true;
                    msg.append("<p class=ok>✓ Emby verbunden als ").append(esc(r.getJSONObject("User").optString("Name"))).append("</p>");
                } catch (Exception e) { msg.append("<p class=err>✗ Emby: ").append(esc(e.getMessage())).append("</p>"); }
            }

            if (haOk || embyOk) {
                Style.saveConfig(ctx, cfg);
                listener.onSaved((haOk ? "Home Assistant ✓   " : "") + (embyOk ? "Emby ✓" : ""));
                msg.append("<p>Gespeichert. Der Fernseher lädt die Startseite neu.</p>");
            } else if (msg.length() == 0) msg.append("<p class=err>Bitte mindestens Home Assistant oder Emby ausfüllen.</p>");
        } catch (Exception e) { msg.append("<p class=err>Fehler: ").append(esc(e.toString())).append("</p>"); }
        return page(msg.toString());
    }

    // ------------------------------------------------------------ Zuhause-Kacheln
    /** JSON-Schnittstelle fuer den Kachel-Editor (immer mit PIN). */
    String api(String path, String body) {
        try {
            JSONObject in = new JSONObject(body.isEmpty() ? "{}" : body);
            if (!pin.equals(in.optString("pin"))) return "{\"error\":\"Falsche PIN\"}";
            JSONObject cfg = Style.loadConfig(ctx);
            if (cfg == null || !cfg.has("url")) return "{\"error\":\"Erst Home Assistant verbinden\"}";
            String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
            if (path.startsWith("/api/sensors")) {
                JSONArray st = new JSONArray(Style.httpGet(base + "/api/states", tok)), out = new JSONArray();
                for (int i = 0; i < st.length(); i++) {
                    JSONObject o = st.getJSONObject(i);
                    String id = o.getString("entity_id");
                    if (!(id.startsWith("sensor.") || id.startsWith("binary_sensor.") || id.startsWith("input_number.") || id.startsWith("weather."))) continue;
                    if ("unavailable".equals(o.optString("state"))) continue;
                    JSONObject a = o.optJSONObject("attributes");
                    out.put(new JSONObject().put("id", id).put("name", a == null ? id : a.optString("friendly_name", id))
                            .put("unit", a == null ? "" : a.optString("unit_of_measurement", "")).put("state", o.optString("state"))
                            .put("dienst", a != null && a.has("start_time") && a.has("end_time")));
                }
                return out.toString();
            }
            if (path.startsWith("/api/tiles/save")) {
                JSONArray t = in.getJSONArray("tiles");
                cfg.put("tiles", t);
                Style.saveConfig(ctx, cfg);
                listener.onSaved("Zuhause-Kacheln \u2713");
                return "{\"ok\":true}";
            }
            if (path.startsWith("/api/tiles")) {
                JSONArray t = cfg.optJSONArray("tiles");
                if (t == null || t.length() == 0) t = HaPanel.autoTiles(new JSONArray(Style.httpGet(base + "/api/states", tok)));
                return new JSONObject().put("tiles", t).toString();
            }
        } catch (Exception e) { return "{\"error\":\"" + esc(String.valueOf(e.getMessage())).replace("\"", "'") + "\"}"; }
        return "{}";
    }

    static String tilesEditor() {
        return "<div class=card><h2>ZUHAUSE-KACHELN</h2><p class=hint style='margin:0 0 10px'>Welche Werte auf der Startseite stehen \u2013 Reihenfolge, Namen, Zusatzwert.</p>"
            + "<button type=button onclick=loadTiles()>Kacheln bearbeiten</button><div id=tm class=hint></div><div id=tl></div>"
            + "<div id=ta style='display:none'><label>Neue Kachel: Sensor suchen</label><input id=ts placeholder='z. B. Temperatur, Akku, Strom \u2026' autocomplete=off><div id=tr></div>"
            + "<button type=button onclick=saveTiles() style='margin-top:14px'>Kacheln speichern</button></div></div>"
            + "<style>.tile{background:#0e0d18;border:1px solid #2e2c45;border-radius:12px;padding:10px;margin-top:8px}.tile .row{display:flex;gap:6px;align-items:center}"
            + ".tile input{margin:0;padding:9px;font-size:14px}.tile small{color:#6b6f86;display:block;margin:4px 0}.sb{width:auto;padding:8px 11px;margin:0;font-size:13px;background:#26243a}"
            + ".opt{padding:9px;border-bottom:1px solid #26243a;font-size:14px}.opt span{color:#6b6f86;float:right}</style>"
            + "<script>var tiles=[],sensors=[];function pin(){return document.getElementById('pin').value}"
            + "function post(u,o){o.pin=pin();return fetch(u,{method:'POST',body:JSON.stringify(o)}).then(function(r){return r.json()})}"
            + "function esc(s){return String(s==null?'':s).replace(/[&<>\"']/g,function(c){return'&#'+c.charCodeAt(0)+';'})}"
            + "function nm(id){for(var i=0;i<sensors.length;i++)if(sensors[i].id==id)return sensors[i];return{name:id,unit:'',state:''}}"
            + "function loadTiles(){var m=document.getElementById('tm');if(pin().length!=4){m.textContent='Bitte oben die PIN vom Fernseher eingeben.';return}m.textContent='l\u00e4dt \u2026';"
            + "post('/api/sensors',{}).then(function(s){if(s.error){m.textContent=s.error;return}sensors=s;return post('/api/tiles',{})}).then(function(t){if(!t)return;if(t.error){m.textContent=t.error;return}"
            + "tiles=t.tiles||[];m.textContent='';document.getElementById('ta').style.display='block';draw()})}"
            + "function unit(u){return u=='\u00b0C'?'\u00b0':u=='%'?' %':u?' '+u:''}"
            + "function draw(){var l=document.getElementById('tl');l.innerHTML='';tiles.forEach(function(t,i){var s=nm((t.main||[])[0]),d=document.createElement('div');d.className='tile';"
            + "d.innerHTML='<div class=row><input value=\"'+esc(t.label)+'\" oninput=\"tiles['+i+'].label=this.value\"><button type=button class=sb onclick=mv('+i+',-1)>\u25B2</button><button type=button class=sb onclick=mv('+i+',1)>\u25BC</button><button type=button class=sb onclick=rm('+i+')>\u2715</button></div>'"
            + "+'<small>'+esc(s.name)+(t.format=='dienst'?' \u00b7 Dienst':' \u00b7 jetzt: '+esc(s.state)+esc(unit(s.unit)))+'</small>'"
            + "+(t.format=='dienst'?'':'<div class=row><input placeholder=\"Zusatzwert (optional): Sensor-ID\" value=\"'+esc((t.sub||[])[0]||'')+'\" list=sl oninput=\"setSub('+i+',this.value)\"></div>');l.appendChild(d)});"
            + "if(!document.getElementById('sl')){var dl=document.createElement('datalist');dl.id='sl';sensors.forEach(function(s){var o=document.createElement('option');o.value=s.id;o.label=s.name;dl.appendChild(o)});document.body.appendChild(dl)}}"
            + "function setSub(i,v){var s=nm(v);tiles[i].sub=v?[v]:[];tiles[i].sub_unit=unit(s.unit);tiles[i].sub_decimals=0}"
            + "function mv(i,d){var j=i+d;if(j<0||j>=tiles.length)return;var x=tiles[i];tiles[i]=tiles[j];tiles[j]=x;draw()}"
            + "function rm(i){tiles.splice(i,1);draw()}"
            + "document.addEventListener('input',function(e){if(e.target.id!='ts')return;var q=e.target.value.toLowerCase(),r=document.getElementById('tr');r.innerHTML='';if(q.length<2)return;"
            + "sensors.filter(function(s){return(s.name+' '+s.id).toLowerCase().indexOf(q)>=0}).slice(0,12).forEach(function(s){var d=document.createElement('div');d.className='opt';"
            + "d.innerHTML=esc(s.name)+'<span>'+esc(s.state)+esc(unit(s.unit))+'</span>';d.onclick=function(){var t={label:s.name,main:[s.id]};"
            + "if(s.dienst)t.format='dienst';else{t.main_unit=unit(s.unit);t.decimals=isNaN(parseFloat(s.state))||s.state.indexOf('.')<0?0:1}"
            + "tiles.push(t);e.target.value='';r.innerHTML='';draw()};r.appendChild(d)})});"
            + "function saveTiles(){var m=document.getElementById('tm');post('/api/tiles/save',{tiles:tiles}).then(function(r){m.textContent=r.ok?'\u2713 Gespeichert \u2013 der Fernseher zeigt sie gleich an.':(r.error||'Fehler')})}"
            + "</script>";
    }

    // ------------------------------------------------------------ Seite
    String page(String result) {
        JSONObject cfg = Style.loadConfig(ctx);
        String haUrl = cfg == null ? "" : cfg.optString("url");
        if (haUrl.isEmpty() && !haFound.isEmpty()) haUrl = haFound.get(0);
        JSONObject e = cfg == null ? null : cfg.optJSONObject("emby");
        String eUrl = e == null ? "" : e.optString("url");
        if (eUrl.isEmpty() && !embyFound.isEmpty()) eUrl = embyFound.get(0).optString("Address");
        StringBuilder hints = new StringBuilder();
        for (String h : haFound) hints.append("<option value=\"").append(esc(h)).append("\">");
        StringBuilder eh = new StringBuilder();
        for (JSONObject o : embyFound) eh.append("<option value=\"").append(esc(o.optString("Address"))).append("\">").append(esc(o.optString("Name"))).append("</option>");
        boolean hasToken = cfg != null && !cfg.optString("token").isEmpty();
        return "<!doctype html><html lang=de><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
            + "<title>midnox TV einrichten</title><style>"
            + ":root{--g:linear-gradient(90deg,#7C3AED,#3B5BF0,#06B6D4)}*{box-sizing:border-box}"
            + "body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0b0a14;color:#e6e8f2;padding:24px 16px 48px}"
            + ".w{max-width:520px;margin:0 auto}h1{font-weight:300;font-size:34px;margin:0 0 4px;background:var(--g);-webkit-background-clip:text;background-clip:text;color:transparent}"
            + ".sub{color:#8a8fa8;margin:0 0 24px;font-size:14px}.card{background:#15141f;border:1px solid #26243a;border-radius:16px;padding:18px;margin-bottom:16px}"
            + "h2{font-size:12px;letter-spacing:.25em;color:#8a8fa8;font-weight:500;margin:0 0 12px}label{display:block;font-size:13px;color:#b4b8cc;margin:12px 0 6px}"
            + "input,textarea{width:100%;padding:12px 14px;border-radius:10px;border:1px solid #2e2c45;background:#0e0d18;color:#fff;font-size:15px}"
            + "textarea{min-height:90px;font-family:ui-monospace,monospace;font-size:12px}input:focus,textarea:focus{outline:none;border-color:#3B5BF0}"
            + "a{color:#06B6D4}.hint{font-size:12px;color:#6b6f86;margin-top:6px}button{width:100%;padding:14px;border:0;border-radius:999px;background:var(--g);color:#fff;font-size:16px;margin-top:8px}"
            + ".ok{color:#34d399}.err{color:#f87171}.pin{font-size:22px;letter-spacing:.4em;text-align:center}</style></head><body><div class=w>"
            + "<h1>midnox TV</h1><p class=sub>Fernseher mit Home Assistant und Emby verbinden</p>"
            + (result == null ? "" : "<div class=card>" + result + "</div>")
            + "<div class=card><h2>PIN VOM FERNSEHER</h2><input id=pin name=pin form=f class=pin inputmode=numeric maxlength=4 required></div>"
            + "<form id=f method=post action=/save>"
            + "<div class=card><h2>HOME ASSISTANT</h2><label>Adresse</label><input name=ha_url list=ha value=\"" + esc(haUrl) + "\" placeholder=\"http://192.168.x.x:8123\"><datalist id=ha>" + hints + "</datalist>"
            + "<label>Langlebiger Zugangstoken</label><textarea name=ha_token placeholder=\"" + (hasToken ? "bereits gespeichert – leer lassen, um ihn zu behalten" : "eyJ0eXAiOiJKV1Qi…") + "\"></textarea>"
            + "<div class=hint>In Home Assistant: Profil → Sicherheit → Langlebige Zugangstoken → Token erstellen."
            + (haUrl.isEmpty() ? "" : " <a href=\"" + esc(haUrl) + "/profile/security\" target=_blank>Direkt öffnen</a>") + "</div></div>"
            + "<div class=card><h2>EMBY</h2><label>Server</label><input name=emby_url list=em value=\"" + esc(eUrl) + "\" placeholder=\"http://192.168.x.x:8096\"><datalist id=em>" + eh + "</datalist>"
            + "<label>Benutzername</label><input name=emby_user autocomplete=username><label>Passwort</label><input name=emby_pw type=password autocomplete=current-password>"
            + "<div class=hint>Leer lassen, wenn Emby nicht geändert werden soll.</div></div>"
            + "<button>Verbinden</button></form>"
            + tilesEditor()
            + "</div></body></html>";
    }
}
