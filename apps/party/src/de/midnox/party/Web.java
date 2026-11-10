package de.midnox.party;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LAN-Party-Relay. Der Fernseher (tv.html, 127.0.0.1) hält die Spiellogik und schickt jedem
 * Handy eine Ansicht; die Handys (p.html, WLAN-IP) rendern sie und senden Eingaben zurück.
 * Push ohne WebSocket: Long-Polling. TV->Handy per Ansichts-Version, Handy->TV per Event-Liste.
 */
final class Web {
    static ServerSocket server;
    static volatile int port;
    static PartyActivity act;

    // Handy -> TV: fortlaufende Ereignisliste (join, ev)
    static final Object lock = new Object();
    static final ArrayList<String> evs = new ArrayList<>();
    // TV -> Handy: aktuelle Ansicht je Spieler + Version
    static final HashMap<Integer, String> view = new HashMap<>();
    static final HashMap<Integer, Long> viewVer = new HashMap<>();
    static final AtomicInteger nextPid = new AtomicInteger(1);
    static long verCounter = 1;

    static void start(PartyActivity a) {
        act = a;
        for (port = 8300; port < 8340; port++) {
            try { server = new ServerSocket(); server.setReuseAddress(true); server.bind(new InetSocketAddress(port)); break; }
            catch (Exception e) { server = null; }
        }
        if (server == null) return;
        new Thread(new Runnable() { public void run() {
            while (true) {
                try { final Socket s = server.accept(); new Thread(new Runnable() { public void run() { handle(s); } }).start(); }
                catch (Exception e) { if (server == null) return; }
            }
        } }, "party-web").start();
    }

    static void handle(Socket s) {
        try {
            s.setSoTimeout(40000);
            PushbackInputStream in = new PushbackInputStream(new BufferedInputStream(s.getInputStream()), 4);
            String req = line(in);
            if (req == null) { s.close(); return; }
            String[] p = req.split(" ");
            String method = p.length > 0 ? p[0] : "GET", path = p.length > 1 ? p[1] : "/";
            int clen = 0; String h;
            while ((h = line(in)) != null && !h.isEmpty()) {
                String lh = h.toLowerCase(Locale.ROOT);
                if (lh.startsWith("content-length:")) try { clen = Integer.parseInt(h.substring(15).trim()); } catch (Exception ignored) {}
            }

            if (path.equals("/") || path.equals("/tv") || path.startsWith("/tv?")) { asset(s, "tv.html"); return; }
            if (path.equals("/p") || path.startsWith("/p?") || path.startsWith("/p/")) { asset(s, "p.html"); return; }
            if (path.startsWith("/lib/")) { asset(s, "lib/" + path.substring(5).replaceAll("[?#].*$", "")); return; }

            if (path.startsWith("/join") && method.equals("POST")) {
                byte[] body = readN(in, clen);
                int pid = nextPid.getAndIncrement();
                synchronized (lock) {
                    evs.add("{\"t\":\"join\",\"pid\":" + pid + ",\"d\":" + bodyOrNull(body) + "}");
                    lock.notifyAll();
                }
                send(s, 200, "application/json", ("{\"pid\":" + pid + "}").getBytes("UTF-8"));
                return;
            }
            if (path.startsWith("/ev") && method.equals("POST")) {
                byte[] body = readN(in, clen);
                String pid = U.q(path, "pid");
                synchronized (lock) {
                    evs.add("{\"t\":\"ev\",\"pid\":" + (pid == null ? "0" : pid) + ",\"d\":" + bodyOrNull(body) + "}");
                    lock.notifyAll();
                }
                send(s, 200, "application/json", "{\"ok\":true}".getBytes());
                return;
            }
            if (path.startsWith("/push") && method.equals("POST")) {   // TV -> Handy Ansicht setzen
                byte[] body = readN(in, clen);
                String to = U.q(path, "to");
                synchronized (lock) {
                    long v = ++verCounter;
                    String vjson = new String(body, "UTF-8");
                    if (to == null || to.equals("all")) {
                        for (Integer k : new ArrayList<>(view.keySet())) { view.put(k, vjson); viewVer.put(k, v); }
                    } else {
                        try { int pid = Integer.parseInt(to); view.put(pid, vjson); viewVer.put(pid, v); } catch (Exception ignored) {}
                    }
                    lock.notifyAll();
                }
                send(s, 200, "application/json", "{\"ok\":true}".getBytes());
                return;
            }
            if (path.startsWith("/tvpoll")) {   // TV holt Handy-Ereignisse (Long-Poll)
                int since = intq(path, "since", 0);
                String out;
                synchronized (lock) {
                    long until = System.currentTimeMillis() + 25000;
                    while (evs.size() <= since) {
                        long wait = until - System.currentTimeMillis();
                        if (wait <= 0) break;
                        try { lock.wait(wait); } catch (InterruptedException ie) { break; }
                    }
                    StringBuilder sb = new StringBuilder("{\"seq\":").append(evs.size()).append(",\"evs\":[");
                    for (int i = since; i < evs.size(); i++) { if (i > since) sb.append(','); sb.append(evs.get(i)); }
                    sb.append("]}");
                    out = sb.toString();
                }
                send(s, 200, "application/json", out.getBytes("UTF-8"));
                return;
            }
            if (path.startsWith("/ppoll")) {   // Handy holt seine Ansicht (Long-Poll)
                int pid = intq(path, "pid", 0);
                long since = longq(path, "since", 0);
                String out;
                synchronized (lock) {
                    if (!viewVer.containsKey(pid)) { viewVer.put(pid, 0L); view.put(pid, "null"); }
                    long until = System.currentTimeMillis() + 25000;
                    while (viewVer.get(pid) <= since) {
                        long wait = until - System.currentTimeMillis();
                        if (wait <= 0) break;
                        try { lock.wait(wait); } catch (InterruptedException ie) { break; }
                    }
                    out = "{\"ver\":" + viewVer.get(pid) + ",\"view\":" + view.get(pid) + "}";
                }
                send(s, 200, "application/json", out.getBytes("UTF-8"));
                return;
            }
            if (path.startsWith("/reset")) {
                synchronized (lock) { evs.clear(); view.clear(); viewVer.clear(); lock.notifyAll(); }
                send(s, 200, "application/json", "{\"ok\":true}".getBytes());
                return;
            }
            if (path.startsWith("/info")) {
                send(s, 200, "application/json", ("{\"ip\":\"" + U.ownIp() + "\",\"port\":" + port + "}").getBytes("UTF-8"));
                return;
            }
            send(s, 404, "text/plain", "not found".getBytes());
        } catch (Exception ignored) {}
        finally { try { s.close(); } catch (Exception ignored) {} }
    }

    static String bodyOrNull(byte[] b) { try { String x = new String(b, "UTF-8").trim(); return x.isEmpty() ? "null" : x; } catch (Exception e) { return "null"; } }
    static int intq(String path, String k, int def) { String v = U.q(path, k); if (v == null) return def; try { return Integer.parseInt(v); } catch (Exception e) { return def; } }
    static long longq(String path, String k, long def) { String v = U.q(path, k); if (v == null) return def; try { return Long.parseLong(v); } catch (Exception e) { return def; } }

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
