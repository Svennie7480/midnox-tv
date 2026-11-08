package de.midnox.snes;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Winziger lokaler HTTP-Server. Liefert dem WebView (127.0.0.1) die Emulator-Seite + WASM-Core
 * und dem Handy (WLAN-IP) die Gamepad-Seite. localhost gilt als sicherer Kontext, daher läuft
 * der AudioWorklet des Cores ohne HTTPS. Kein SharedArrayBuffer nötig.
 */
final class Web {
    static ServerSocket server;
    static volatile int port;
    static SnesActivity act;

    static void start(SnesActivity a) {
        act = a;
        for (port = 8099; port < 8130; port++) { try { server = new ServerSocket(); server.setReuseAddress(true); server.bind(new InetSocketAddress(port)); break; } catch (Exception e) { server = null; } }
        if (server == null) return;
        new Thread(new Runnable() { public void run() {
            while (true) {
                try { final Socket s = server.accept(); new Thread(new Runnable() { public void run() { handle(s); } }).start(); }
                catch (Exception e) { if (server == null) return; }
            }
        } }, "snes-web").start();
    }

    static void handle(Socket s) {
        try {
            s.setSoTimeout(15000);
            PushbackInputStream in = new PushbackInputStream(new BufferedInputStream(s.getInputStream()), 4);
            String req = line(in);
            if (req == null) { s.close(); return; }
            String[] p = req.split(" ");
            String method = p.length > 0 ? p[0] : "GET", path = p.length > 1 ? p[1] : "/";
            int clen = 0;
            String h;
            while ((h = line(in)) != null && !h.isEmpty()) { String lh = h.toLowerCase(Locale.ROOT); if (lh.startsWith("content-length:")) try { clen = Integer.parseInt(h.substring(15).trim()); } catch (Exception ignored) {} }

            if (path.equals("/") || path.startsWith("/index")) { asset(s, "index.html"); return; }
            if (path.equals("/pad") || path.startsWith("/pad?")) { asset(s, "pad.html"); return; }
            if (path.startsWith("/core/")) { asset(s, "core/" + path.substring(6).replaceAll("[?#].*$", "")); return; }
            if (path.startsWith("/rom")) { byte[] r = act.romBytes(); if (r == null) send(s, 404, "text/plain", "no rom".getBytes()); else send(s, 200, "application/octet-stream", r); return; }
            if (path.startsWith("/in")) {   // Handy-Tasten: p=Spieler, b=Bitmaske
                String pp = U.q(path, "p"), bb = U.q(path, "b");
                try { act.onPad(pp == null ? 0 : Integer.parseInt(pp), bb == null ? 0 : Integer.parseInt(bb)); } catch (Exception ignored) {}
                send(s, 200, "text/plain", "ok".getBytes()); return;
            }
            if (path.startsWith("/upload") && method.equals("POST")) {   // ROM vom Handy
                byte[] body = readN(in, clen);
                String name = U.q(path, "name");
                act.onUpload(name == null ? "upload.sfc" : name, body);
                send(s, 200, "application/json", "{\"ok\":true}".getBytes()); return;
            }
            if (path.startsWith("/lib/")) { asset(s, "lib/" + path.substring(5).replaceAll("[?#].*$", "")); return; }
            if (path.startsWith("/states")) { send(s, 200, "application/json", act.listStates(U.q(path, "key")).getBytes("UTF-8")); return; }
            if (path.startsWith("/state")) {   // Savestate speichern (POST) / laden (GET)
                String key = U.q(path, "key"), slot = U.q(path, "slot");
                if (key == null) key = "default"; if (slot == null) slot = "auto";
                if (method.equals("POST")) { act.saveState(key, slot, readN(in, clen)); send(s, 200, "text/plain", "ok".getBytes()); }
                else { byte[] d = act.loadStateBytes(key, slot); if (d == null) send(s, 404, "text/plain", "no".getBytes()); else send(s, 200, "application/octet-stream", d); }
                return;
            }
            if (path.startsWith("/cmd")) { act.cmd(U.q(path, "c")); send(s, 200, "text/plain", "ok".getBytes()); return; }
            if (path.startsWith("/cover")) { byte[] c = act.coverBytes(U.q(path, "name")); if (c == null || c.length == 0) send(s, 404, "text/plain", "no".getBytes()); else send(s, 200, "image/png", c); return; }
            if (path.startsWith("/info")) {
                String j = "{\"ip\":\"" + U.ownIp() + "\",\"port\":" + port + ",\"rom\":" + (act.romBytes() != null) + "}";
                send(s, 200, "application/json", j.getBytes("UTF-8")); return;
            }
            send(s, 404, "text/plain", "not found".getBytes());
        } catch (Exception ignored) {}
        finally { try { s.close(); } catch (Exception ignored) {} }
    }

    static void asset(Socket s, String path) throws IOException {
        try { send(s, 200, U.mime(path), U.asset(act, path)); }
        catch (Exception e) { send(s, 404, "text/plain", ("asset not found: " + path).getBytes()); }
    }

    static void send(Socket s, int code, String ctype, byte[] body) throws IOException {
        OutputStream o = new BufferedOutputStream(s.getOutputStream());
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 404 ? " Not Found" : " Err") + "\r\n"
                + "Content-Type: " + ctype + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        o.write(head.getBytes("UTF-8")); o.write(body); o.flush();
    }

    static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(); int c; boolean any = false;
        while ((c = in.read()) != -1) { any = true; if (c == '\n') break; if (c != '\r') bo.write(c); }
        if (!any && bo.size() == 0) return null;
        return new String(bo.toByteArray(), "UTF-8");
    }
    static byte[] readN(InputStream in, int n) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[65536]; int got = 0, r;
        while (got < n && (r = in.read(b, 0, Math.min(b.length, n - got))) > 0) { bo.write(b, 0, r); got += r; }
        return bo.toByteArray();
    }
}
