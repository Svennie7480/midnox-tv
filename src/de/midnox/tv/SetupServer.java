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
            String html = request.startsWith("POST /save") ? save(form(body)) : page(null);
            byte[] out = html.getBytes("UTF-8");
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + out.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
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
            + "<form method=post action=/save>"
            + "<div class=card><h2>HOME ASSISTANT</h2><label>Adresse</label><input name=ha_url list=ha value=\"" + esc(haUrl) + "\" placeholder=\"http://192.168.x.x:8123\"><datalist id=ha>" + hints + "</datalist>"
            + "<label>Langlebiger Zugangstoken</label><textarea name=ha_token placeholder=\"" + (hasToken ? "bereits gespeichert – leer lassen, um ihn zu behalten" : "eyJ0eXAiOiJKV1Qi…") + "\"></textarea>"
            + "<div class=hint>In Home Assistant: Profil → Sicherheit → Langlebige Zugangstoken → Token erstellen."
            + (haUrl.isEmpty() ? "" : " <a href=\"" + esc(haUrl) + "/profile/security\" target=_blank>Direkt öffnen</a>") + "</div></div>"
            + "<div class=card><h2>EMBY</h2><label>Server</label><input name=emby_url list=em value=\"" + esc(eUrl) + "\" placeholder=\"http://192.168.x.x:8096\"><datalist id=em>" + eh + "</datalist>"
            + "<label>Benutzername</label><input name=emby_user autocomplete=username><label>Passwort</label><input name=emby_pw type=password autocomplete=current-password>"
            + "<div class=hint>Leer lassen, wenn Emby nicht geändert werden soll.</div></div>"
            + "<div class=card><h2>PIN VOM FERNSEHER</h2><input name=pin class=pin inputmode=numeric maxlength=4 required></div>"
            + "<button>Verbinden</button></form></div></body></html>";
    }
}
