package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Einrichtung per Handy/PC: Quellen (M3U, Xtream, Stalker), EPG-Adressen, Gruppen ein-/ausblenden. */
final class Web {
    static ServerSocket server;
    static int port;
    static Runnable onChange;

    static String key(Context c) {
        String k = U.prefs(c).getString("webkey", "");
        if (k.isEmpty()) { k = Long.toHexString(new Random().nextLong() & 0xFFFFFFFFFFFFL); U.prefs(c).edit().putString("webkey", k).apply(); }
        return k;
    }
    static String url(Context c) { return "http://" + U.ownIp() + ":" + port + "/?k=" + key(c); }

    static synchronized void start(final Context ctx) {
        if (server != null) return;
        final Context c = ctx.getApplicationContext();
        for (port = 8150; port < 8170; port++) { try { server = new ServerSocket(port); break; } catch (IOException e) { server = null; } }
        if (server == null) return;
        new Thread(new Runnable() { public void run() {
            while (true) {
                try { final Socket s = server.accept(); new Thread(new Runnable() { public void run() { serve(c, s); } }).start(); }
                catch (IOException e) { return; }
            }
        }}, "web").start();
    }

    static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int ch;
        while ((ch = in.read()) >= 0) { if (ch == '\n') break; if (ch != '\r') sb.append((char) ch); }
        return ch < 0 && sb.length() == 0 ? null : sb.toString();
    }

    static void serve(Context c, Socket s) {
        try {
            s.setSoTimeout(20000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            String req = line(in);
            if (req == null) return;
            String[] parts = req.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            long len = 0; String l;
            while ((l = line(in)) != null && !l.isEmpty()) if (l.toLowerCase(Locale.ROOT).startsWith("content-length:")) len = Long.parseLong(l.substring(15).trim());
            boolean ok = path.contains("k=" + key(c));
            String type = "application/json", body;
            if (path.startsWith("/upload")) {
                s.setSoTimeout(180000);
                body = ok ? upload(c, path, in, len) : "{\"ok\":false,\"msg\":\"QR-Code neu scannen\"}";
            } else {
                byte[] b = new byte[(int) Math.min(len, 2L * 1024 * 1024)]; int r = 0;
                while (r < b.length) { int n = in.read(b, r, b.length - r); if (n < 0) break; r += n; }
                String data = new String(b, 0, r, "UTF-8");
                if (path.startsWith("/api/")) body = ok ? api(c, path.substring(5).replaceAll("\\?.*", ""), data.isEmpty() ? new JSONObject() : new JSONObject(data)) : "{\"ok\":false,\"msg\":\"QR-Code neu scannen\"}";
                else { type = "text/html"; body = ok ? page(key(c)) : "<meta charset=utf-8><body style='background:#0b0a14;color:#ddd;font-family:sans-serif;padding:30px'>Bitte den QR-Code am Fernseher scannen (midnox Live → MENU → Quellen einrichten).</body>"; }
            }
            byte[] out = body.getBytes("UTF-8");
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + out.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            o.write(out); o.flush();
        } catch (Exception ignored) {
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }

    static void changed() { if (onChange != null) Img.ui.post(onChange); }

    static String api(final Context c, String cmd, JSONObject in) throws Exception {
        switch (cmd) {
            case "state": {
                JSONArray a = new JSONArray();
                Map<String, Integer> cnt = new HashMap<>();
                for (Model.Channel ch : Lib.channels) cnt.put(ch.src, cnt.containsKey(ch.src) ? cnt.get(ch.src) + 1 : 1);
                for (Model.Source s : Lib.sources(c)) {
                    JSONObject o = s.json();
                    o.put("pass", s.pass.isEmpty() ? "" : "••••").put("label", s.label()).put("error", Lib.errors.containsKey(s.id) ? Lib.errors.get(s.id) : "").put("count", cnt.containsKey(s.id) ? cnt.get(s.id) : 0);
                    a.put(o);
                }
                return new JSONObject().put("sources", a).put("status", Lib.loading ? Lib.status : Lib.channels.size() + " Sender").put("epg", Epg.loading ? Epg.status : Epg.status)
                    .put("epg_extra", U.prefs(c).getString("epg_extra", Epg.DEFAULT_EXTRA)).put("busy", Lib.loading || Epg.loading).toString();
            }
            case "add": {
                Model.Source s = new Model.Source();
                s.id = Long.toHexString(System.currentTimeMillis() & 0xFFFFFFFFL);
                s.type = in.optString("type", "m3u");
                s.url = in.optString("url").trim(); s.user = in.optString("user").trim(); s.pass = in.optString("pass").trim();
                s.mac = in.optString("mac").trim(); s.epg = in.optString("epg").trim();
                s.name = in.optString("name").trim();
                if (s.name.isEmpty()) s.name = s.type.equals("m3u") ? "M3U-Liste" : s.url.replaceAll("^https?://", "").replaceAll("[:/].*", "");
                if (s.url.isEmpty()) return "{\"ok\":false,\"msg\":\"Bitte eine Adresse eingeben\"}";
                if (s.type.equals("stalker") && !Stalker.mac(s.mac).matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) return "{\"ok\":false,\"msg\":\"MAC-Adresse im Format 00:1A:79:XX:XX:XX\"}";
                List<Model.Source> l = Lib.sources(c); l.add(s); Lib.saveSources(c, l);
                reloadAll(c);
                return "{\"ok\":true,\"msg\":\"Quelle hinzugefügt – Sender werden geladen …\"}";
            }
            case "del": {
                List<Model.Source> l = Lib.sources(c), n = new ArrayList<>();
                for (Model.Source s : l) if (!s.id.equals(in.optString("id"))) n.add(s); else Lib.m3uFile(c, s.id).delete();
                Lib.saveSources(c, n); reloadAll(c);
                return "{\"ok\":true}";
            }
            case "toggle": {
                List<Model.Source> l = Lib.sources(c);
                for (Model.Source s : l) if (s.id.equals(in.optString("id"))) s.on = !s.on;
                Lib.saveSources(c, l); reloadAll(c);
                return "{\"ok\":true}";
            }
            case "epg": {
                U.prefs(c).edit().putString("epg_extra", in.optString("extra").trim()).apply();
                new Thread(new Runnable() { public void run() { Epg.reload(c); changed(); } }).start();
                return "{\"ok\":true,\"msg\":\"Programm wird neu geladen …\"}";
            }
            case "reload": reloadAll(c); return "{\"ok\":true,\"msg\":\"Wird neu geladen …\"}";
            case "groups": {
                Set<String> hid = Lib.hiddenGroups(c);
                JSONArray a = new JSONArray();
                for (String[] g : Lib.allGroups(c)) a.put(new JSONObject().put("name", g[0]).put("count", Integer.parseInt(g[1])).put("hidden", hid.contains(g[0])));
                return new JSONObject().put("groups", a).toString();
            }
            case "hide": {
                Set<String> s = new HashSet<>();
                JSONArray a = in.optJSONArray("hidden");
                for (int i = 0; a != null && i < a.length(); i++) s.add(a.getString(i));
                Lib.setHidden(c, s); changed();
                return "{\"ok\":true}";
            }
        }
        return "{\"ok\":false}";
    }

    static void reloadAll(final Context c) {
        new Thread(new Runnable() { public void run() { Lib.reload(c); changed(); Epg.reload(c); changed(); } }).start();
    }

    /** M3U-Datei hochladen -> neue Quelle. */
    static String upload(Context c, String path, InputStream in, long len) throws Exception {
        String name = "Playlist";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]name=([^&]*)").matcher(path);
        if (m.find()) name = URLDecoder.decode(m.group(1), "UTF-8");
        Model.Source s = new Model.Source();
        s.id = Long.toHexString(System.currentTimeMillis() & 0xFFFFFFFFL);
        s.type = "m3u"; s.url = "datei:" + name; s.name = name.replaceAll("\\.m3u8?$", "");
        File f = Lib.m3uFile(c, s.id);
        try (OutputStream o = new FileOutputStream(f)) {
            byte[] b = new byte[65536]; long n = 0; int r;
            while ((len < 0 || n < len) && (r = in.read(b, 0, (int) (len < 0 ? b.length : Math.min(b.length, len - n)))) > 0) { o.write(b, 0, r); n += r; }
        }
        List<Model.Source> l = Lib.sources(c); l.add(s); Lib.saveSources(c, l);
        reloadAll(c);
        return "{\"ok\":true,\"msg\":\"Playlist gespeichert – Sender werden geladen …\"}";
    }

    static String page(String k) {
        return "<!doctype html><html lang=de><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\"><title>midnox Live – Quellen</title><style>"
            + ":root{--g:linear-gradient(90deg,#7C3AED,#3B5BF0,#06B6D4)}*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0b0a14;color:#e6e8f2;padding:22px 16px 60px}"
            + ".w{max-width:720px;margin:0 auto}h1{font-weight:300;font-size:32px;margin:0 0 4px}h1 b{background:var(--g);-webkit-background-clip:text;background-clip:text;color:transparent;font-weight:300}h1 i{font-style:normal;font-size:14px;background:#ef4444;color:#fff;padding:3px 9px;border-radius:7px;vertical-align:middle;margin-left:6px;font-weight:700}"
            + ".sub{color:#8a8fa8;margin:0 0 18px;font-size:14px}h3{font-size:12px;letter-spacing:.25em;color:#8a8fa8;font-weight:500;margin:26px 0 10px}"
            + ".card{background:#15141f;border:1px solid #26243a;border-radius:16px;padding:16px;margin-bottom:12px}input,select{width:100%;padding:12px;border-radius:10px;border:1px solid #2e2c45;background:#0e0d18;color:#fff;font-size:16px;margin-bottom:8px}"
            + "button{padding:10px 18px;border:0;border-radius:999px;background:var(--g);color:#fff;font-size:14px;cursor:pointer}button.g{background:#26243a}.tabs{display:flex;gap:6px;margin-bottom:12px;flex-wrap:wrap}.tabs button{background:#26243a}.tabs button.on{background:var(--g)}"
            + ".src{display:flex;gap:12px;align-items:center}.src b{display:block}.src span{color:#8a8fa8;font-size:13px}.err{color:#fca5a5!important}.src .bt{margin-left:auto;display:flex;gap:6px}#msg{position:fixed;left:50%;bottom:20px;transform:translateX(-50%);background:#26243a;padding:12px 18px;border-radius:12px;display:none}"
            + ".grp{display:flex;flex-wrap:wrap;gap:6px}.grp label{padding:6px 12px;border-radius:999px;border:1px solid #2e2c45;font-size:13px;cursor:pointer}.grp label.on{background:#3B5BF0;border-color:#3B5BF0}.grp input{display:none}.hint{color:#8a8fa8;font-size:13px}"
            + "</style></head><body><div class=w><h1><b>midnox</b> Live<i>LIVE</i></h1><p class=sub id=st>Lade …</p>"
            + "<h3>DEINE QUELLEN</h3><div id=src></div>"
            + "<h3>QUELLE HINZUFÜGEN</h3><div class=card><div class=tabs><button class=on data-t=m3u>M3U-Adresse</button><button data-t=file>M3U-Datei</button><button data-t=xtream>Xtream Codes</button><button data-t=stalker>Stalker-Portal</button></div>"
            + "<div id=f_m3u><input id=m_url placeholder='http://…/playlist.m3u'><input id=m_name placeholder='Name (optional)'><input id=m_epg placeholder='EPG-Adresse (optional, sonst aus der Playlist)'><button onclick=add('m3u')>Hinzufügen</button></div>"
            + "<div id=f_file style=display:none><input type=file id=file accept='.m3u,.m3u8,audio/x-mpegurl,application/x-mpegurl,text/plain'><button onclick=up()>Hochladen</button></div>"
            + "<div id=f_xtream style=display:none><input id=x_url placeholder='Server, z. B. http://anbieter.tv:8080'><input id=x_user placeholder='Benutzername'><input id=x_pass placeholder='Passwort' type=password><input id=x_name placeholder='Name (optional)'><button onclick=add('xtream')>Hinzufügen</button></div>"
            + "<div id=f_stalker style=display:none><input id=s_url placeholder='Portal-Adresse, z. B. http://portal.tv:8080/c/'><input id=s_mac placeholder='MAC-Adresse 00:1A:79:XX:XX:XX'><input id=s_name placeholder='Name (optional)'><input id=s_epg placeholder='Zusätzliche EPG-Adresse (optional)'><button onclick=add('stalker')>Hinzufügen</button><p class=hint>midnox Live meldet sich beim Portal wie eine MAG-Box an. Die MAC-Adresse muss bei deinem Anbieter freigeschaltet sein.</p></div></div>"
            + "<h3>PROGRAMMFÜHRER (EPG)</h3><div class=card><p class=hint style=margin-top:0>Zusätzliche XMLTV-Adressen (auch .gz), mehrere mit Komma. Die Sender werden über tvg-id oder den Namen zugeordnet. Voreingestellt ist ein freies deutsches EPG.</p><input id=epg><button onclick=epg()>Speichern und neu laden</button> <span class=hint id=epgst></span></div>"
            + "<h3>GRUPPEN</h3><div class=card><p class=hint style=margin-top:0>Angetippte Gruppen werden am Fernseher ausgeblendet (praktisch bei großen Listen mit vielen Ländern).</p><div class=grp id=grp></div></div>"
            + "<p><button class=g onclick=\"api('reload').then(msg)\">Alles neu laden</button></p><div id=msg></div></div><script>"
            + "var K='" + k + "';function $(i){return document.getElementById(i)}function esc(s){return String(s==null?'':s).replace(/[&<>\"']/g,function(c){return'&#'+c.charCodeAt(0)+';'})}"
            + "function api(c,o){return fetch('/api/'+c+'?k='+K,{method:'POST',body:JSON.stringify(o||{})}).then(function(r){return r.json()})}"
            + "function msg(j){var m=$('msg');m.textContent=j&&j.msg?j.msg:'OK';m.style.display='block';clearTimeout(m.t);m.t=setTimeout(function(){m.style.display='none'},3500);setTimeout(load,600)}"
            + "document.querySelectorAll('.tabs button').forEach(function(b){b.onclick=function(){document.querySelectorAll('.tabs button').forEach(function(x){x.classList.toggle('on',x==b)});['m3u','file','xtream','stalker'].forEach(function(t){$('f_'+t).style.display=t==b.dataset.t?'block':'none'})}});"
            + "function v(i){return $(i).value.trim()}"
            + "function add(t){var o={type:t};if(t=='m3u')o={type:t,url:v('m_url'),name:v('m_name'),epg:v('m_epg')};if(t=='xtream')o={type:t,url:v('x_url'),user:v('x_user'),pass:v('x_pass'),name:v('x_name')};if(t=='stalker')o={type:t,url:v('s_url'),mac:v('s_mac'),name:v('s_name'),epg:v('s_epg')};api('add',o).then(msg)}"
            + "function up(){var f=$('file').files[0];if(!f)return;msg({msg:'Lade hoch …'});var x=new XMLHttpRequest();x.open('POST','/upload?k='+K+'&name='+encodeURIComponent(f.name));x.onload=function(){try{msg(JSON.parse(x.responseText))}catch(e){msg({msg:'Fehler beim Hochladen'})}};x.send(f)}"
            + "function epg(){api('epg',{extra:v('epg')}).then(msg)}"
            + "var first=true;function load(){api('state').then(function(s){$('st').textContent=s.status+(s.busy?' · lädt …':'')+(s.epg?' · '+s.epg:'');if(first){$('epg').value=s.epg_extra;first=false}"
            + "$('src').innerHTML=s.sources.length?s.sources.map(function(x){return '<div class=card><div class=src><div><b>'+esc(x.name)+'</b><span>'+esc(x.label)+' · '+(x.on?x.count+' Sender':'ausgeschaltet')+'</span>'+(x.error?'<br><span class=err>'+esc(x.error)+'</span>':'')+'</div><div class=bt><button class=g onclick=\"api(\\'toggle\\',{id:\\''+x.id+'\\'}).then(msg)\">'+(x.on?'Aus':'An')+'</button><button class=g onclick=\"if(confirm(\\'Quelle löschen?\\'))api(\\'del\\',{id:\\''+x.id+'\\'}).then(msg)\">Löschen</button></div></div></div>'}).join(''):'<div class=card><span class=hint>Noch keine Quelle – füge unten deine M3U-Liste, Xtream-Zugangsdaten oder dein Stalker-Portal hinzu.</span></div>';"
            + "if(s.busy)setTimeout(load,2500)});"
            + "api('groups').then(function(g){$('grp').innerHTML=g.groups.map(function(x,i){return '<label class=\"'+(x.hidden?'':'on')+'\"><input type=checkbox '+(x.hidden?'':'checked')+' data-n=\"'+esc(x.name)+'\">'+esc(x.name||'(ohne Gruppe)')+' · '+x.count+'</label>'}).join('')||'<span class=hint>Gruppen erscheinen, sobald Sender geladen sind.</span>';"
            + "document.querySelectorAll('#grp input').forEach(function(c){c.onchange=function(){c.parentNode.classList.toggle('on',c.checked);var h=[];document.querySelectorAll('#grp input').forEach(function(x){if(!x.checked)h.push(x.dataset.n)});api('hide',{hidden:h}).then(function(){msg({msg:'Gespeichert'})})}})})}"
            + "load();</script></body></html>";
    }
}
