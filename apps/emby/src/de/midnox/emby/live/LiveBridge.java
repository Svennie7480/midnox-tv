package de.midnox.emby.live;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Live-TV in midnox Media: Quellen (Stalker-Portale, Xtream, M3U), Senderliste, TV-Guide und Abspiel-Adressen.
 * Die Daten-Schicht stammt aus midnox Live; die Oberflaeche baut die Web-UI (js/live.js).
 * In JS erreichbar als window.Live; Ergebnisse langsamer Aufrufe kommen als Ereignis LIVE.ev({...}).
 */
public class LiveBridge {
    public interface Out { void js(String code); }
    final Context c; final Out out;
    volatile boolean ready;

    public LiveBridge(Context ctx, Out o) {
        c = ctx.getApplicationContext(); out = o;
        U.init(c);
        new Thread(new Runnable() { public void run() {
            Lib.loadCache(c);
            for (Model.Source s : Lib.sources(c)) if (s.type.equals("stalker")) Stalker.active.put(s.id, U.prefs(c).getInt("mac_" + s.id, 0));
            Epg.loadOverrides(c); Epg.loadCache(c); Logos.load(c);
            ready = true;
            ev("{\"e\":\"cache\"}");
            if (Lib.sources(c).isEmpty()) importLocal(true);                    // erster Start: still aus midnox Live uebernehmen
            else if (Lib.channels.isEmpty() || Lib.stale()) reloadAll();
            else if (Epg.stale()) reloadEpg();
        }}, "live-init").start();
    }

    void ev(String json) { out.js("window.LIVE&&LIVE.ev(" + json + ")"); }
    static String q(String s) { return JSONObject.quote(s == null ? "" : s); }

    // ------------------------------------------------------------ Status und Daten
    @JavascriptInterface public String status() {
        try {
            JSONArray src = new JSONArray();
            for (Model.Source s : Lib.sources(c)) {
                JSONObject o = new JSONObject().put("id", s.id).put("name", s.name).put("type", s.type).put("label", s.label()).put("on", s.on);
                String err = Lib.errors.get(s.id); if (err != null) o.put("err", err);
                if (s.type.equals("stalker")) o.put("macs", Stalker.macs(s).size());
                src.put(o);
            }
            return new JSONObject().put("ready", ready).put("sources", src).put("count", Lib.channels.size()).put("loading", Lib.loading)
                .put("status", Lib.status).put("epgLoading", Epg.loading).put("epgStatus", Epg.status).put("epgAt", Epg.loadedAt)
                .put("liveInstalled", liveInstalled()).toString();
        } catch (Exception e) { return "{}"; }
    }

    /** Alle sichtbaren Sender: [id, name, gruppe, nummer, logos (mit | getrennt), quelle]. */
    @JavascriptInterface public String channels() {
        JSONArray a = new JSONArray();
        for (Model.Channel ch : Lib.channels) {
            JSONArray r = new JSONArray();
            r.put(ch.id).put(ch.name).put(ch.group).put(ch.num).put(android.text.TextUtils.join("|", Logos.candidates(ch))).put(ch.src);
            a.put(r);
        }
        return a.toString();
    }
    @JavascriptInterface public String groups() { return new JSONArray(Lib.groups).toString(); }
    @JavascriptInterface public String sourceNames() { return new JSONObject(Lib.srcNames).toString(); }
    @JavascriptInterface public String favs() { return new JSONArray(Lib.favs(c)).toString(); }
    @JavascriptInterface public boolean fav(String id) { return Lib.toggleFav(c, id); }

    /** Programm fuer Sender im Zeitfenster: {id: [[start, stop, titel, text], ...]}. */
    @JavascriptInterface public String epg(String idsJson, double from, double to) {
        JSONObject o = new JSONObject();
        try {
            JSONArray ids = new JSONArray(idsJson);
            for (int i = 0; i < ids.length(); i++) {
                Model.Channel ch = Lib.byId(ids.getString(i)); if (ch == null) continue;
                JSONArray l = new JSONArray();
                for (Model.Prog p : Epg.list(ch)) if (p.stop > from && p.start < to) l.put(new JSONArray().put(p.start).put(p.stop).put(p.title).put(p.desc));
                o.put(ch.id, l);
            }
        } catch (Exception ignored) {}
        return o.toString();
    }
    /** Kurz-EPG vom Portal nachladen (Stalker, wenn kein XMLTV passt) – meldet sich mit {e:"epg"}. */
    @JavascriptInterface public void shortEpg(String id) {
        final Model.Channel ch = Lib.byId(id); if (ch == null) return;
        if (!Epg.list(ch).isEmpty()) return;
        Epg.ensureShort(c, ch, new Runnable() { public void run() { ev("{\"e\":\"epg\",\"id\":" + q(ch.id) + "}"); } });
    }

    // ------------------------------------------------------------ Laden
    @JavascriptInterface public void reload() { new Thread(new Runnable() { public void run() { reloadAll(); } }).start(); }
    void reloadAll() {
        ev("{\"e\":\"loading\"}");
        Lib.reload(c);
        ev("{\"e\":\"channels\"}");
        reloadEpg();
    }
    void reloadEpg() { Epg.reload(c); ev("{\"e\":\"epg\"}"); }

    // ------------------------------------------------------------ Abspielen
    static final class Try { Model.Channel ch; int mac; String label; }
    /** Reihenfolge der Versuche: alle MACs des Senders, dann gleichnamige/zugeordnete Sender anderer Quellen. */
    List<Try> plan(Model.Channel ch) {
        List<Try> l = new ArrayList<>();
        addTries(l, ch);
        List<Model.Channel> others = new ArrayList<>();
        try { for (Mapping.Group g : Mapping.load(c)) if (g.ids.contains(ch.id)) for (String id : g.ids) { Model.Channel o = Lib.byId(id); if (o != null && o != ch) others.add(o); } } catch (Exception ignored) {}
        for (Model.Channel o : Lib.sameName(ch)) if (o != ch && !others.contains(o)) others.add(o);
        for (Model.Channel o : others) { if (l.size() >= 24) break; if (!o.src.equals(ch.src) || o.cmd.isEmpty()) addTries(l, o); }
        return l;
    }
    void addTries(List<Try> l, Model.Channel ch) {
        Model.Source s = Lib.source(c, ch.src);
        String sn = s == null ? "" : s.name;
        if (s != null && s.type.equals("stalker")) {
            int n = Math.max(1, Stalker.macs(s).size()), st = Stalker.activeIndex(s);
            for (int k = 0; k < n; k++) { Try t = new Try(); t.ch = ch; t.mac = (st + k) % n; t.label = sn + (n > 1 ? " · MAC " + (t.mac + 1) + "/" + n : ""); l.add(t); }
        } else { Try t = new Try(); t.ch = ch; t.mac = -1; t.label = sn; l.add(t); }
    }
    /** Abspiel-Adresse fuer Versuch k ermitteln; Antwort: {e:"tune", req, k, n, url, ua, ref, label} oder mit err. */
    @JavascriptInterface public void tune(final String req, final String id, final int k) {
        new Thread(new Runnable() { public void run() {
            try {
                Model.Channel ch = Lib.byId(id);
                if (ch == null) throw new Exception("Sender nicht gefunden");
                List<Try> pl = plan(ch);
                if (k >= pl.size()) { ev("{\"e\":\"tune\",\"req\":" + q(req) + ",\"k\":" + k + ",\"n\":" + pl.size() + ",\"end\":true}"); return; }
                Try t = pl.get(k);
                Model.Source s = Lib.source(c, t.ch.src);
                String url, ua;
                if (s != null && s.type.equals("stalker")) { url = Stalker.link(s, t.ch, Math.max(0, t.mac)); ua = Stalker.MAG_UA; }
                else { url = t.ch.url; ua = t.ch.ua.isEmpty() ? U.UA : t.ch.ua; }
                JSONObject o = new JSONObject().put("e", "tune").put("req", req).put("k", k).put("n", pl.size()).put("url", url).put("ua", ua)
                    .put("ref", t.ch.referer).put("label", t.label).put("cid", t.ch.id).put("mac", t.mac);
                ev(o.toString());
            } catch (Exception e) {
                ev("{\"e\":\"tune\",\"req\":" + q(req) + ",\"k\":" + k + ",\"err\":" + q(e.getMessage() == null ? e.toString() : e.getMessage()) + "}");
            }
        }}, "live-tune").start();
    }
    /** Versuch hat geklappt: funktionierende MAC merken. */
    @JavascriptInterface public void ok(String cid, int mac) {
        Model.Channel ch = Lib.byId(cid); if (ch == null) return;
        U.prefs(c).edit().putString("last", cid).apply();
        if (mac >= 0) { Stalker.active.put(ch.src, mac); U.prefs(c).edit().putInt("mac_" + ch.src, mac).apply(); }
    }
    @JavascriptInterface public String last() { return U.prefs(c).getString("last", ""); }

    // ------------------------------------------------------------ Quellen verwalten
    @JavascriptInterface public String addSource(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Model.Source s = new Model.Source();
            s.id = Long.toHexString(System.currentTimeMillis() & 0xFFFFFFFFL);
            s.type = o.optString("type", "m3u"); s.name = o.optString("name").trim(); s.url = o.optString("url").trim();
            s.user = o.optString("user").trim(); s.pass = o.optString("pass").trim();
            if (s.type.equals("stalker")) {
                String txt = o.optString("mac") + "\n" + s.url;
                List<String> ms = Stalker.findMacs(txt);
                if (ms.isEmpty()) return "{\"ok\":false,\"msg\":\"Keine gültige MAC-Adresse gefunden\"}";
                s.mac = android.text.TextUtils.join("\n", ms);
                String p = Stalker.findPortal(s.url); if (!p.isEmpty()) s.url = p;
            }
            if (s.url.isEmpty()) return "{\"ok\":false,\"msg\":\"Bitte eine Adresse eingeben\"}";
            if (s.name.isEmpty()) s.name = s.label();
            List<Model.Source> l = Lib.sources(c); l.add(s); Lib.saveSources(c, l);
            reload();
            return "{\"ok\":true}";
        } catch (Exception e) { return "{\"ok\":false,\"msg\":" + q(e.getMessage()) + "}"; }
    }
    @JavascriptInterface public void removeSource(String id) {
        List<Model.Source> l = Lib.sources(c), keep = new ArrayList<>();
        for (Model.Source s : l) if (!s.id.equals(id)) keep.add(s);
        Lib.saveSources(c, keep);
        List<Model.Channel> rest = new ArrayList<>(); for (Model.Channel ch : Lib.channels) if (!ch.src.equals(id)) rest.add(ch);
        Lib.set(c, rest);
        reload();
    }
    @JavascriptInterface public void toggleSource(String id) {
        List<Model.Source> l = Lib.sources(c);
        for (Model.Source s : l) if (s.id.equals(id)) s.on = !s.on;
        Lib.saveSources(c, l); reload();
    }

    // ------------------------------------------------------------ Uebernehmen aus midnox Live
    static final Uri LIVE_EXPORT = Uri.parse("content://de.midnox.live.export/all");
    boolean liveInstalled() { try { c.getPackageManager().getPackageInfo("de.midnox.live", 0); return true; } catch (Exception e) { return false; } }
    /** Liest die Einstellungen der midnox-Live-App auf diesem Geraet (gleiche Signatur noetig). */
    @JavascriptInterface public void importLocal() { new Thread(new Runnable() { public void run() { importLocal(false); } }).start(); }
    void importLocal(boolean quiet) {
        String msg;
        try {
            Cursor cu = c.getContentResolver().query(LIVE_EXPORT, null, null, null, null);
            if (cu == null) throw new Exception("midnox Live ist nicht installiert oder zu alt – bitte midnox Live im Store aktualisieren");
            String json = null;
            try { if (cu.moveToFirst()) json = cu.getString(0); } finally { cu.close(); }
            if (json == null) throw new Exception("keine Daten");
            int n = LiveSync.merge(c, new JSONObject(json));
            msg = n == 0 ? "Schon aktuell – alles aus midnox Live ist da" : n + (n == 1 ? " Quelle" : " Quellen") + " aus midnox Live übernommen";
            ev("{\"e\":\"import\",\"ok\":true,\"n\":" + n + ",\"msg\":" + q(msg) + "}");
            reloadAll();
        } catch (Exception e) {
            if (!quiet) ev("{\"e\":\"import\",\"ok\":false,\"msg\":" + q(e.getMessage()) + "}");
        }
    }
    /** midnox-Live-Geraete im Netzwerk suchen (Quickübertragen). */
    @JavascriptInterface public void peers() {
        new Thread(new Runnable() { public void run() {
            JSONArray a = new JSONArray();
            for (LiveSync.Peer p : LiveSync.discover()) try { a.put(new JSONObject().put("name", p.name).put("ip", p.ip).put("port", p.port).put("count", p.count)); } catch (Exception ignored) {}
            ev("{\"e\":\"peers\",\"list\":" + a + "}");
        }}).start();
    }
    @JavascriptInterface public void pull(final String ip, final int port, final String pin) {
        new Thread(new Runnable() { public void run() {
            String msg; boolean ok = false;
            try { int n = LiveSync.pull(c, ip, port, pin); ok = true; msg = n == 0 ? "Schon aktuell" : n + (n == 1 ? " Quelle" : " Quellen") + " übernommen"; if (n > 0) reloadAll(); }
            catch (Exception e) { msg = e.getMessage(); }
            ev("{\"e\":\"import\",\"ok\":" + ok + ",\"msg\":" + q(msg) + "}");
        }}).start();
    }
}
