package de.midnox.maus;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

/** Webseite fuers Handy + WebSocket fuer die Zeigerbewegungen (schnell, ohne Verzoegerung). */
final class Server {
    static final int PORT = 8140;
    final MouseService svc;
    ServerSocket ss; volatile boolean running;
    Server(MouseService s) { svc = s; }

    static String key(Context c) {
        SharedPreferences pr = c.getSharedPreferences("maus", Context.MODE_PRIVATE);
        String k = pr.getString("key", "");
        if (k.isEmpty()) {
            String chars = "abcdefghjkmnpqrstuvwxyz23456789"; StringBuilder sb = new StringBuilder();
            Random r = new SecureRandom();
            for (int i = 0; i < 10; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
            k = sb.toString(); pr.edit().putString("key", k).apply();
        }
        return k;
    }

    static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) {}
        return "?";
    }
    static String url(Context c) { return "http://" + ip() + ":" + PORT + "/?k=" + key(c); }

    void start() {
        running = true;
        new Thread(new Runnable() { public void run() {
            for (int i = 0; i < 30 && running; i++) {
                try { ss = new ServerSocket(PORT); break; } catch (IOException e) { try { Thread.sleep(2000); } catch (InterruptedException ignored) {} }
            }
            while (running && ss != null) {
                try { final Socket s = ss.accept(); new Thread(new Runnable() { public void run() { serve(s); } }).start(); }
                catch (IOException e) { if (!running) return; }
            }
        }}).start();
    }
    void stop() { running = false; try { ss.close(); } catch (Exception ignored) {} }

    static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) >= 0) { if (c == '\n') break; if (c != '\r') sb.append((char) c); }
        return c < 0 && sb.length() == 0 ? null : sb.toString();
    }

    void serve(Socket s) {
        try {
            s.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String req = line(in);
            if (req == null) return;
            String path = req.split(" ").length > 1 ? req.split(" ")[1] : "/";
            String wsKey = null, l;
            while ((l = line(in)) != null && !l.isEmpty()) if (l.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) wsKey = l.substring(18).trim();
            boolean ok = path.contains("k=" + key(svc));
            if (path.startsWith("/ws") && wsKey != null) {
                if (!ok) { out.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes()); return; }
                String acc = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")), Base64.NO_WRAP);
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + acc + "\r\n\r\n").getBytes("UTF-8"));
                out.flush();
                s.setSoTimeout(0);
                ws(in, out);
                return;
            }
            byte[] body = (ok ? Page.html() : Page.denied()).getBytes("UTF-8");
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.write(body); out.flush();
        } catch (Exception ignored) {
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }

    void ws(InputStream in, OutputStream out) throws IOException {
        send(out, "hi " + svc.sw + " " + svc.sh);
        DataInputStream d = new DataInputStream(in);
        while (running) {
            int b0 = d.read(); if (b0 < 0) return;
            int b1 = d.read(); if (b1 < 0) return;
            int op = b0 & 0x0F; long len = b1 & 0x7F;
            if (len == 126) len = d.readUnsignedShort(); else if (len == 127) len = d.readLong();
            byte[] mask = new byte[4];
            if ((b1 & 0x80) != 0) d.readFully(mask);
            if (len > 65536) return;
            byte[] p = new byte[(int) len]; d.readFully(p);
            for (int i = 0; i < p.length; i++) p[i] ^= mask[i % 4];
            if (op == 8) return;
            if (op == 9) { frame(out, 0xA, p); continue; }
            if (op == 1) { String r = handle(new String(p, "UTF-8")); if (r != null) send(out, r); }
        }
    }

    synchronized void frame(OutputStream out, int op, byte[] p) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(0x80 | op);
        if (p.length < 126) b.write(p.length); else { b.write(126); b.write(p.length >> 8); b.write(p.length & 0xFF); }
        b.write(p);
        out.write(b.toByteArray()); out.flush();
    }
    void send(OutputStream out, String s) throws IOException { frame(out, 1, s.getBytes("UTF-8")); }

    String handle(String m) {
        String[] a = m.split(" ", 3);
        try {
            switch (a[0]) {
                case "m": svc.move(Float.parseFloat(a[1]), Float.parseFloat(a[2])); return null;
                case "c": svc.tap(40); return null;
                case "l": svc.tap(750); return null;
                case "s": svc.scroll(Float.parseFloat(a[1]), a.length > 2 ? Float.parseFloat(a[2]) : 0); return null;
                case "b": svc.global(AccessibilityService.GLOBAL_ACTION_BACK); return null;
                case "h": svc.global(AccessibilityService.GLOBAL_ACTION_HOME); return null;
                case "r": svc.global(AccessibilityService.GLOBAL_ACTION_RECENTS); return null;
                case "k": return adb("input keyevent " + Integer.parseInt(a[1]));
                case "t": {
                    StringBuilder sb = new StringBuilder();
                    for (char ch : m.substring(2).toCharArray()) {
                        if (ch == ' ') sb.append("%s");
                        else if ("()<>|;&*\\~\"'`$!?#{}[]".indexOf(ch) >= 0) sb.append('\\').append(ch);
                        else if (ch < 128) sb.append(ch);
                        else sb.append(ch == 'ä' ? "ae" : ch == 'ö' ? "oe" : ch == 'ü' ? "ue" : ch == 'Ä' ? "Ae" : ch == 'Ö' ? "Oe" : ch == 'Ü' ? "Ue" : ch == 'ß' ? "ss" : "");
                    }
                    return adb("input text '" + sb.toString().replace("'", "") + "'");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    String adb(String cmd) {
        try { Adb.shell(svc, cmd); return null; }
        catch (Exception e) { return "e Für Tasten und Text bitte Netzwerk-Debugging erlauben (einmal am Fernseher bestätigen)"; }
    }
}
