package de.midnox.rezepte;

import org.json.*;
import java.io.*;
import java.util.*;

/** Rezepte auf midnox.de (server/index.php). Die App spricht als einzige mit dem Server – Browser im Heimnetz gehen über die App. */
final class Cloud {
    static final LinkedHashMap<String, JSONObject> cache = new LinkedHashMap<>();
    static long at;               // letzter erfolgreicher Abgleich
    static volatile String err = "";
    static final Object lock = new Object();

    static boolean on() { return !Store.setting("cloudKey", "").isEmpty() && !Store.setting("cloudUrl", "").isEmpty(); }
    static String base() { String u = Store.setting("cloudUrl", "").trim(); if (!u.endsWith("/") && !u.endsWith(".php")) u += "/"; return u; }
    static Map<String, String> head() { HashMap<String, String> h = new HashMap<>(); h.put("X-Key", Store.setting("cloudKey", "")); return h; }
    static String url(String a, String id) { return base() + (base().endsWith(".php") ? "" : "index.php") + "?a=" + a + (id == null ? "" : "&id=" + U.enc(id)); }

    static void loadCache() {
        try {
            JSONObject o = new JSONObject(U.readText(new File(Store.dir, "cloud.json"), "{}"));
            JSONArray a = o.optJSONArray("list");
            synchronized (lock) { cache.clear(); if (a != null) for (int i = 0; i < a.length(); i++) { JSONObject r = a.getJSONObject(i); r.put("store", "cloud"); cache.put(r.getString("id"), r); } }
        } catch (Exception ignored) {}
    }
    static void saveCache() {
        try {
            JSONArray a = new JSONArray(); synchronized (lock) { for (JSONObject r : cache.values()) a.put(r); }
            U.writeText(new File(Store.dir, "cloud.json"), new JSONObject().put("list", a).toString());
        } catch (Exception ignored) {}
        Store.ver.incrementAndGet();
    }
    static void reset() { synchronized (lock) { at = 0; err = ""; } }

    /** Liste abgleichen – höchstens alle 2 Minuten, außer force */
    static void refresh(boolean force) {
        if (!on()) { err = ""; return; }
        synchronized (lock) { if (!force && System.currentTimeMillis() - at < 120000) return; }
        try {
            JSONObject o = new JSONObject(new String(U.http(url("list", null), "GET", null, head(), 6000, 20 << 20), "UTF-8"));
            if (!o.optBoolean("ok")) throw new IOException(o.optString("err", "Server-Fehler"));
            JSONArray a = o.getJSONArray("list");
            String before; synchronized (lock) { before = cache.keySet().toString() + cache.size(); }
            synchronized (lock) { cache.clear(); for (int i = 0; i < a.length(); i++) { JSONObject r = a.getJSONObject(i); r.put("store", "cloud"); cache.put(r.getString("id"), r); } at = System.currentTimeMillis(); }
            err = "";
            saveCache();
        } catch (Exception e) {
            err = msg(e);
            synchronized (lock) { at = System.currentTimeMillis() - 90000; }   // in 30 s nochmal
        }
    }
    static String msg(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("403")) return "Schlüssel passt nicht";
        if (m.contains("404")) return "index.php nicht gefunden – Adresse prüfen";
        if (e instanceof java.net.UnknownHostException) return "Server nicht erreichbar (kein Internet?)";
        if (e instanceof java.net.SocketTimeoutException) return "Server antwortet nicht";
        return m;
    }
    static JSONObject get(String id) { synchronized (lock) { return cache.get(id); } }
    static List<JSONObject> all() { synchronized (lock) { return new ArrayList<>(cache.values()); } }

    static JSONObject call(String a, String id, byte[] body) throws Exception {
        JSONObject o;
        try { o = new JSONObject(new String(U.http(url(a, id), body == null ? "GET" : "POST", body, head(), 8000, 1 << 20), "UTF-8")); }
        catch (Exception e) { throw new IOException("midnox.de: " + msg(e)); }
        if (!o.optBoolean("ok")) throw new IOException(o.optString("err", "Server-Fehler"));
        return o;
    }
    static void save(JSONObject r) throws Exception {
        if (!on()) throw new IOException("midnox.de ist nicht eingerichtet (Einstellungen → Schlüssel)");
        JSONObject send = new JSONObject(r.toString()); send.remove("store");
        call("save", r.getString("id"), send.toString().getBytes("UTF-8"));
        r.put("store", "cloud");
        synchronized (lock) { cache.put(r.getString("id"), r); }
        saveCache();
    }
    static void del(String id) throws Exception {
        call("del", id, new byte[0]);
        synchronized (lock) { cache.remove(id); }
        Store.dropCloudImg(id);
        saveCache();
    }
    static void putImg(String id, byte[] img) throws Exception {
        if (img == null) { call("imgdel", id, new byte[0]); return; }
        call("img", id, img);
    }
    /** Bild eines Cloud-Rezepts – wird je Stand (upd) auf dem Gerät zwischengespeichert */
    static byte[] img(String id, long upd) throws Exception {
        File f = Store.cloudImg(id, upd);
        if (f.exists()) return U.readFile(f);
        byte[] b; try { b = U.http(url("img", id), "GET", null, head(), 8000, 8 << 20); } catch (Exception e) { throw new IOException("midnox.de: " + msg(e)); }
        Store.dropCloudImg(id);
        try { U.write(f, b); } catch (Exception ignored) {}
        return b;
    }
}
