package de.midnox.rezepte;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Kleiner Webserver: liefert die Oberfläche (assets/www) und die API – für die App selbst (127.0.0.1)
 * und für jeden Browser im Heimnetz (http://<ip>:8095).
 */
final class Srv {
    static ServerSocket server;
    static volatile int port;
    static Context ctx;

    static synchronized void start(Context c) {
        ctx = c.getApplicationContext();
        Store.init(ctx);
        if (server != null) return;
        for (port = 8095; port < 8120; port++) {
            try { ServerSocket ss = new ServerSocket(); ss.setReuseAddress(true); ss.bind(new InetSocketAddress(port)); server = ss; break; }
            catch (Exception e) { server = null; }
        }
        if (server == null) return;
        final ServerSocket ss = server;
        new Thread(new Runnable() { public void run() {
            while (true) {
                try { final Socket s = ss.accept(); new Thread(new Runnable() { public void run() { handle(s); } }).start(); }
                catch (Exception e) { if (ss.isClosed()) return; }
            }
        } }, "rezepte-web").start();
        new Thread(new Runnable() { public void run() { Cloud.refresh(true); } }).start();
    }
    static String url() { return "http://" + U.ownIp() + ":" + port; }

    static void handle(Socket s) {
        try {
            s.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            String req = line(in); if (req == null) return;
            String[] p = req.split(" ");
            String method = p.length > 0 ? p[0] : "GET", path = p.length > 1 ? p[1] : "/";
            int clen = 0; String h;
            while ((h = line(in)) != null && !h.isEmpty()) {
                String lh = h.toLowerCase(Locale.ROOT);
                if (lh.startsWith("content-length:")) try { clen = Integer.parseInt(h.substring(15).trim()); } catch (Exception ignored) {}
            }
            if (clen > 12 << 20) { send(s, 413, "text/plain", "zu groß".getBytes()); return; }
            byte[] body = method.equals("POST") ? readN(in, clen) : new byte[0];
            String rel = path.replaceAll("[?#].*$", "");
            if (rel.startsWith("/api/")) { api(s, method, rel.substring(5), path, body); return; }
            if (rel.startsWith("/img/")) { img(s, rel.substring(5), path); return; }
            if (rel.equals("/") || rel.isEmpty()) rel = "/index.html";
            rel = rel.substring(1);
            if (rel.contains("..")) { send(s, 404, "text/plain", "nicht gefunden".getBytes()); return; }
            try { send(s, 200, U.mime(rel), U.asset(ctx, "www/" + rel)); }
            catch (Exception e) { send(s, 404, "text/plain", "nicht gefunden".getBytes()); }
        } catch (Exception ignored) {
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }

    static void api(Socket s, String m, String ep, String path, byte[] body) throws Exception {
        JSONObject out = new JSONObject();
        try {
            switch (ep) {
                case "ver": out.put("ver", Store.ver.get()); break;
                case "state":
                    out.put("ver", Store.ver.get()).put("url", url()).put("port", port).put("ip", U.ownIp());
                    out.put("cloud", cloudState()).put("settings", Store.publicSettings());
                    break;
                case "recipes": {
                    Cloud.refresh("1".equals(U.q(path, "fresh")));
                    JSONArray a = new JSONArray();
                    synchronized (Store.lock) { for (JSONObject r : Store.local.values()) a.put(r); }
                    for (JSONObject r : Cloud.all()) a.put(r);
                    out.put("list", a).put("cloud", cloudState()).put("ver", Store.ver.get());
                    break;
                }
                case "recipe": {
                    if (!m.equals("POST")) { JSONObject r = find(U.q(path, "id")); if (r == null) throw new IOException("Rezept nicht gefunden"); out.put("recipe", r); break; }
                    out.put("recipe", save(new JSONObject(new String(body, "UTF-8"))));
                    break;
                }
                case "del": {
                    String id = U.q(path, "id"); JSONObject r = find(id); if (r == null) break;
                    if ("cloud".equals(r.optString("store"))) Cloud.del(id); else Store.delLocal(id);
                    break;
                }
                case "img": {   // Bild hochladen (Rohdaten) oder entfernen (leerer Body)
                    String id = U.q(path, "id"); JSONObject r = find(id); if (r == null) throw new IOException("Rezept nicht gefunden");
                    r = new JSONObject(r.toString());
                    out.put("recipe", save2(r, body.length == 0 ? null : body, body.length == 0, r.optString("store")));
                    break;
                }
                case "import": {
                    JSONObject q = new JSONObject(new String(body, "UTF-8"));
                    String u = q.optString("url").trim();
                    java.util.regex.Matcher mm = java.util.regex.Pattern.compile("https?://\\S+").matcher(u);
                    if (!mm.find()) throw new IOException("Das ist kein Link");
                    out.put("recipe", Importer.fromUrl(mm.group()));
                    break;
                }
                case "plan": obj(m, "plan.json", body, out, "plan"); break;
                case "shop": obj(m, "shop.json", body, out, "shop"); break;
                case "settings":
                    if (m.equals("POST")) { Store.putSettings(new JSONObject(new String(body, "UTF-8"))); NetService.sync(ctx); new Thread(new Runnable() { public void run() { Cloud.refresh(true); } }).start(); }
                    out.put("settings", Store.publicSettings());
                    break;
                case "export": {
                    JSONObject o = new JSONObject().put("app", "midnox-rezepte").put("v", 1).put("at", System.currentTimeMillis());
                    JSONArray rs = new JSONArray();
                    List<JSONObject> l; synchronized (Store.lock) { l = new ArrayList<>(Store.local.values()); }
                    for (JSONObject r : l) {
                        JSONObject c = new JSONObject(r.toString());
                        File f = Store.localImg(r.getString("id"));
                        if (f.exists()) c.put("imgData", android.util.Base64.encodeToString(U.readFile(f), android.util.Base64.NO_WRAP));
                        rs.put(c);
                    }
                    synchronized (Store.lock) { o.put("recipes", rs).put("plan", Store.plan).put("shop", Store.shop); }
                    byte[] b = o.toString().getBytes("UTF-8");
                    String d = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date());
                    OutputStream os = new BufferedOutputStream(s.getOutputStream());
                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Disposition: attachment; filename=\"midnox-rezepte-" + d + ".json\"\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    os.write(b); os.flush();
                    return;
                }
                case "restore": {
                    JSONObject o = new JSONObject(new String(body, "UTF-8"));
                    if (!"midnox-rezepte".equals(o.optString("app"))) throw new IOException("Das ist keine Sicherung von midnox Rezepte");
                    JSONArray rs = o.optJSONArray("recipes"); int n = 0;
                    if (rs != null) for (int i = 0; i < rs.length(); i++) {
                        JSONObject r = rs.getJSONObject(i); if (r.optString("id").isEmpty()) continue;
                        String im = r.optString("imgData", ""); r.remove("imgData");
                        if (!im.isEmpty()) { U.write(Store.localImg(r.getString("id")), android.util.Base64.decode(im, android.util.Base64.DEFAULT)); r.put("img", true); }
                        Store.putLocal(r); n++;
                    }
                    synchronized (Store.lock) {
                        if (o.optJSONObject("plan") != null) { Store.plan = o.getJSONObject("plan"); Store.saveObj("plan.json", Store.plan); }
                        if (o.optJSONObject("shop") != null) { Store.shop = o.getJSONObject("shop"); Store.saveObj("shop.json", Store.shop); }
                    }
                    out.put("n", n);
                    break;
                }
                case "cloudtest": Cloud.reset(); Cloud.refresh(true); out.put("cloud", cloudState()); break;
                default: send(s, 404, "application/json", "{\"ok\":false,\"err\":\"unbekannt\"}".getBytes()); return;
            }
            out.put("ok", true);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (e instanceof JSONException) msg = "Daten unlesbar";
            out = new JSONObject().put("ok", false).put("err", msg);
        }
        send(s, 200, "application/json; charset=utf-8", out.toString().getBytes("UTF-8"));
    }

    static JSONObject cloudState() throws JSONException { return new JSONObject().put("on", Cloud.on()).put("err", Cloud.err).put("n", Cloud.all().size()); }

    static void obj(String m, String file, byte[] body, JSONObject out, String key) throws Exception {
        synchronized (Store.lock) {
            JSONObject o = key.equals("plan") ? Store.plan : Store.shop;
            if (m.equals("POST")) {
                JSONObject n = new JSONObject(new String(body, "UTF-8"));
                if (key.equals("plan")) Store.plan = o = n; else Store.shop = o = n;
                Store.saveObj(file, o);
            }
            out.put(key, o).put("ver", Store.ver.get());
        }
    }

    static JSONObject find(String id) { if (id == null) return null; JSONObject r = Store.getLocal(id); return r != null ? r : Cloud.get(id); }

    /** Rezept speichern; imgUrl = Bild aus dem Netz übernehmen; store = Ziel (local/cloud), Umzug inklusive Bild */
    static JSONObject save(JSONObject r) throws Exception {
        if (r.optString("id").isEmpty()) r.put("id", U.id());
        String target = "cloud".equals(r.optString("store")) ? "cloud" : "local";
        byte[] img = null; boolean drop = r.optBoolean("imgDel");
        String iu = r.optString("imgUrl", ""); r.remove("imgUrl"); r.remove("imgDel");
        if (iu.startsWith("http")) { try { img = U.http(iu, "GET", null, null, 10000, 10 << 20); } catch (Exception ignored) {} }
        return save2(r, img, drop, target);
    }
    static JSONObject save2(JSONObject r, byte[] img, boolean drop, String target) throws Exception {
        String id = r.getString("id");
        JSONObject old = find(id); String from = old == null ? null : old.optString("store", "local");
        if (img == null && !drop && old != null && old.optBoolean("img") && from != null && !from.equals(target)) img = oldImg(old);   // Umzug: Bild mitnehmen
        if (img != null) r.put("img", true); else if (drop) r.put("img", false); else r.put("img", old != null && old.optBoolean("img"));
        r.put("upd", System.currentTimeMillis());
        if (!r.has("created")) r.put("created", old != null ? old.optLong("created", System.currentTimeMillis()) : System.currentTimeMillis());
        if (target.equals("cloud")) {
            r.put("store", "cloud");
            Cloud.save(r);
            if (img != null || drop) { Cloud.putImg(id, img); Store.dropCloudImg(id); }
            if (img != null) try { U.write(Store.cloudImg(id, r.getLong("upd")), img); } catch (Exception ignored) {}
        } else {
            if (img != null) U.write(Store.localImg(id), img); else if (drop) Store.localImg(id).delete();
            Store.putLocal(r);
        }
        if (from != null && !from.equals(target)) {   // vom alten Ort entfernen
            if (from.equals("cloud")) try { Cloud.del(id); } catch (Exception ignored) {}
            else { synchronized (Store.lock) { Store.local.remove(id); Store.saveLocal(); } if (!target.equals("local")) Store.localImg(id).delete(); }
        }
        return r;
    }
    static byte[] oldImg(JSONObject r) {
        try {
            String id = r.getString("id");
            if ("cloud".equals(r.optString("store"))) return Cloud.img(id, r.optLong("upd"));
            File f = Store.localImg(id); return f.exists() ? U.readFile(f) : null;
        } catch (Exception e) { return null; }
    }

    static void img(Socket s, String id, String path) throws IOException {
        JSONObject r = find(id);
        byte[] b = null;
        if (r != null && r.optBoolean("img")) b = oldImg(r);
        if (b == null) { send(s, 404, "text/plain", "kein Bild".getBytes()); return; }
        send(s, 200, U.imgMime(b), b, "public, max-age=31536000");   // URL enthält ?v=<upd>
    }

    static void send(Socket s, int code, String ctype, byte[] body) throws IOException { send(s, code, ctype, body, "no-store"); }
    static void send(Socket s, int code, String ctype, byte[] body, String cache) throws IOException {
        OutputStream o = new BufferedOutputStream(s.getOutputStream());
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 404 ? " Not Found" : " Err") + "\r\n"
                + "Content-Type: " + ctype + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + cache + "\r\n"
                + "Connection: close\r\n\r\n";
        o.write(head.getBytes("UTF-8")); o.write(body); o.flush();
    }
    static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(); int c; boolean any = false;
        while ((c = in.read()) != -1) { any = true; if (c == '\n') break; if (c != '\r') bo.write(c); }
        if (!any) return null;
        return new String(bo.toByteArray(), "UTF-8");
    }
    static byte[] readN(InputStream in, int n) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[65536]; int got = 0, r;
        while (got < n && (r = in.read(b, 0, Math.min(b.length, n - got))) > 0) { bo.write(b, 0, r); got += r; }
        return bo.toByteArray();
    }
}
