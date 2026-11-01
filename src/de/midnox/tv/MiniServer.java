package de.midnox.tv;

import java.io.*;
import java.net.*;
import java.util.*;

/** Kleiner HTTP-Server fuers Handy (Suche tippen, Musikwuensche). */
public class MiniServer {
    public interface Handler { String[] handle(String method, String path, String body); }   // {contentType, body}
    public interface Upload { String handle(String path, InputStream in, long len) throws Exception; }   // -> JSON
    Upload up;

    ServerSocket server; volatile boolean running; int port;
    final Handler h;
    MiniServer(Handler h) { this.h = h; }

    int start(int from) throws IOException {
        for (port = from; port < from + 20; port++) { try { server = new ServerSocket(port); break; } catch (IOException e) { server = null; } }
        if (server == null) throw new IOException("kein freier Port");
        running = true;
        new Thread(new Runnable() { public void run() {
            while (running) {
                try { final Socket s = server.accept(); new Thread(new Runnable() { public void run() { serve(s); } }).start(); }
                catch (IOException e) { if (!running) return; }
            }
        }}).start();
        return port;
    }
    void stop() { running = false; try { server.close(); } catch (Exception ignored) {} }

    void serve(Socket s) {
        try {
            s.setSoTimeout(15000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            String req = SetupServer.line(in);
            if (req == null) return;
            String[] parts = req.split(" ");
            long len = 0; String l;
            while ((l = SetupServer.line(in)) != null && !l.isEmpty()) if (l.toLowerCase(Locale.ROOT).startsWith("content-length:")) len = Long.parseLong(l.substring(15).trim());
            if (up != null && parts.length > 1 && parts[1].startsWith("/upload")) {
                s.setSoTimeout(120000);
                String r;
                try { r = up.handle(parts[1], in, len); } catch (Exception e) { r = "{\"ok\":false,\"msg\":\"Fehler: " + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}"; }
                byte[] out = r.getBytes("UTF-8");
                OutputStream o = s.getOutputStream();
                o.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + out.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                o.write(out); o.flush();
                return;
            }
            byte[] b = new byte[(int) Math.min(len, 16L * 1024 * 1024)]; int r = 0;
            while (r < b.length) { int n = in.read(b, r, b.length - r); if (n < 0) break; r += n; }
            String[] res = h.handle(parts[0], parts.length > 1 ? parts[1] : "/", new String(b, 0, r, "UTF-8"));
            byte[] out = res[1].getBytes("UTF-8");
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 200 OK\r\nContent-Type: " + res[0] + "; charset=utf-8\r\nContent-Length: " + out.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            o.write(out); o.flush();
        } catch (Exception ignored) {
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }

    /** Gemeinsames Seiten-Design (midnox) fuer Handy-Seiten. */
    static String page(String title, String body, String script) {
        return "<!doctype html><html lang=de><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\"><title>" + title + "</title><style>"
            + ":root{--g:linear-gradient(90deg,#7C3AED,#3B5BF0,#06B6D4)}*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0b0a14;color:#e6e8f2;padding:22px 16px 60px}"
            + ".w{max-width:520px;margin:0 auto}h1{font-weight:300;font-size:30px;margin:0 0 4px;background:var(--g);-webkit-background-clip:text;background-clip:text;color:transparent}"
            + ".sub{color:#8a8fa8;margin:0 0 18px;font-size:14px}input{width:100%;padding:14px;border-radius:12px;border:1px solid #2e2c45;background:#0e0d18;color:#fff;font-size:17px;margin-bottom:10px}"
            + "input:focus{outline:none;border-color:#3B5BF0}.it{display:flex;gap:12px;align-items:center;padding:10px;border-radius:12px;background:#15141f;margin-bottom:8px;border:1px solid #26243a}"
            + ".it img{width:52px;height:52px;border-radius:8px;object-fit:cover;background:#222}.it b{display:block;font-weight:500}.it span{color:#8a8fa8;font-size:13px}.it button{margin-left:auto}"
            + "button{padding:10px 16px;border:0;border-radius:999px;background:var(--g);color:#fff;font-size:14px}.msg{padding:12px;border-radius:12px;background:#15141f;margin:10px 0;display:none}"
            + "</style></head><body><div class=w>" + body + "</div><script>" + script + "</script></body></html>";
    }
}
