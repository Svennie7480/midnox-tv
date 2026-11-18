package de.midnox.emby;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;

/**
 * Handy-Helfer: Kleiner Webserver im WLAN. Der Fernseher zeigt einen QR-Code, das Handy öffnet die Seite
 * und kann bequem tippen (Server-Adresse, Login, Suche, Chat). Nur mit dem Schlüssel aus dem QR-Code.
 */
final class Phone {
    interface Cb { void got(String json); }
    static ServerSocket srv; static Thread th; static Context ctx; static volatile Cb cb;
    static volatile String state; static String key; static int port;

    static synchronized String start(Context c, Cb callback) {
        ctx = c.getApplicationContext(); cb = callback;
        if (srv == null) {
            byte[] r = new byte[6]; new SecureRandom().nextBytes(r); StringBuilder k = new StringBuilder();
            for (byte b : r) k.append(String.format("%02x", b)); key = k.toString();
            for (port = 8420; port < 8440; port++) { try { srv = new ServerSocket(port); break; } catch (IOException ignored) {} }
            if (srv == null) return "";
            th = new Thread(new Runnable() { public void run() { loop(); } }, "phone"); th.setDaemon(true); th.start();
        }
        return "http://" + U.ownIp() + ":" + port + "/?k=" + key;
    }
    static void setState(String s) { state = s; }
    static synchronized void stop() { try { if (srv != null) srv.close(); } catch (IOException ignored) {} srv = null; }

    static void loop() {
        while (srv != null && !srv.isClosed()) {
            try {
                final Socket s = srv.accept();
                new Thread(new Runnable() { public void run() { handle(s); } }).start();
            } catch (IOException e) { break; }
        }
    }
    static void handle(Socket s) {
        try {
            s.setSoTimeout(10000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            String line = readLine(in); if (line == null) { s.close(); return; }
            String[] parts = line.split(" "); String method = parts[0], path = parts.length > 1 ? parts[1] : "/";
            int len = 0; String l;
            while ((l = readLine(in)) != null && !l.isEmpty()) if (l.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(l.substring(15).trim());
            boolean ok = path.contains("k=" + key);
            if (method.equals("GET") && (path.equals("/") || path.startsWith("/?"))) {
                InputStream a = ctx.getAssets().open("ui/phone.html"); send(s, 200, "text/html; charset=utf-8", U.read(a).getBytes("UTF-8"));
            } else if (method.equals("GET") && path.startsWith("/state")) {
                send(s, ok ? 200 : 403, "application/json", (ok ? (state == null ? "{\"mode\":\"idle\"}" : state) : "{}").getBytes("UTF-8"));
            } else if (method.equals("POST") && path.startsWith("/send")) {
                byte[] b = new byte[Math.min(len, 65536)]; int got = 0; while (got < b.length) { int n = in.read(b, got, b.length - got); if (n < 0) break; got += n; }
                if (!ok) { send(s, 403, "application/json", "{}".getBytes()); return; }
                String body = new String(b, 0, got, "UTF-8");
                new JSONObject(body);   // nur gültiges JSON weiterreichen
                if (cb != null) cb.got(body);
                send(s, 200, "application/json", "{\"ok\":true}".getBytes());
            } else send(s, 404, "text/plain", "404".getBytes());
        } catch (Exception e) { try { s.close(); } catch (IOException ignored) {} }
    }
    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) >= 0) { if (c == '\n') break; if (c != '\r') sb.append((char) c); if (sb.length() > 8192) break; }
        return c < 0 && sb.length() == 0 ? null : sb.toString();
    }
    static void send(Socket s, int code, String type, byte[] b) throws IOException {
        OutputStream o = s.getOutputStream();
        o.write(("HTTP/1.1 " + code + " OK\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        o.write(b); o.flush(); s.close();
    }
}
