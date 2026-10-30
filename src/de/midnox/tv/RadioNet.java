package de.midnox.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;

/**
 * Netzwerk fuers Radio, robust gegen DNS-Filter im Heimnetz: Liefert der lokale DNS fuer den
 * Radio-Server eine Sperradresse (0.0.0.0 / ::), wird die Adresse per DNS-over-HTTPS (1.1.1.1)
 * ermittelt und direkt verbunden. Der Stream laeuft ueber einen kleinen lokalen Proxy.
 */
public class RadioNet {
    static final String UA = "midnox TV (Fire TV)";
    static final Map<String, InetAddress> cache = new HashMap<>();
    static volatile String lastInfo = "";

    static boolean usable(InetAddress a) {
        return !(a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress());
    }

    static synchronized InetAddress resolve(String host) throws IOException {
        InetAddress c = cache.get(host);
        if (c != null) return c;
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) if (usable(a) && a instanceof Inet4Address) { cache.put(host, a); return a; }
            for (InetAddress a : InetAddress.getAllByName(host)) if (usable(a)) { cache.put(host, a); return a; }
        } catch (IOException ignored) {}
        // lokaler DNS blockiert -> DNS-over-HTTPS
        HttpsURLConnection d = (HttpsURLConnection) new URL("https://1.1.1.1/dns-query?name=" + host + "&type=A").openConnection();
        d.setConnectTimeout(5000); d.setReadTimeout(5000);
        d.setRequestProperty("Accept", "application/dns-json");
        JSONObject o;
        try { o = new JSONObject(Style.read(d.getInputStream())); } catch (JSONException e) { throw new IOException("DoH: " + e.getMessage()); }
        JSONArray ans = o.optJSONArray("Answer");
        for (int i = 0; ans != null && i < ans.length(); i++) {
            JSONObject a = ans.optJSONObject(i);
            if (a != null && a.optInt("type") == 1) {
                InetAddress ip = InetAddress.getByName(a.optString("data"));
                cache.put(host, ip);
                lastInfo = "lokaler DNS blockiert " + host + ", nutze " + ip.getHostAddress();
                return ip;
            }
        }
        throw new IOException(host + " nicht auflösbar (DNS)");
    }

    /** Antwort mit offenem Datenstrom (HTTP/1.0, folgt Weiterleitungen). */
    static class Resp { int code; String type = ""; InputStream in; Socket s; void close() { try { s.close(); } catch (Exception ignored) {} } }

    static Resp open(String url) throws IOException {
        for (int hop = 0; hop < 4; hop++) {
            URL u = new URL(url);
            boolean tls = u.getProtocol().equals("https");
            int port = u.getPort() > 0 ? u.getPort() : (tls ? 443 : 80);
            InetAddress ip = resolve(u.getHost());
            Socket s = new Socket();
            s.connect(new InetSocketAddress(ip, port), 8000);
            s.setSoTimeout(15000);
            if (tls) {
                SSLSocket ss = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(s, u.getHost(), port, true);
                ss.startHandshake();
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(u.getHost(), ss.getSession())) { ss.close(); throw new IOException("Zertifikat passt nicht"); }
                s = ss;
            }
            String path = u.getFile().isEmpty() ? "/" : u.getFile();
            OutputStream o = s.getOutputStream();
            o.write(("GET " + path + " HTTP/1.0\r\nHost: " + u.getHost() + "\r\nUser-Agent: " + UA + "\r\nAccept: */*\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            o.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            String status = SetupServer.line(in);
            if (status == null) { s.close(); throw new IOException("keine Antwort"); }
            Resp r = new Resp(); r.s = s; r.in = in;
            String[] parts = status.split(" ");
            r.code = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            String loc = null, l;
            while ((l = SetupServer.line(in)) != null && !l.isEmpty()) {
                String lo = l.toLowerCase(Locale.ROOT);
                if (lo.startsWith("location:")) loc = l.substring(9).trim();
                if (lo.startsWith("content-type:")) r.type = l.substring(13).trim();
            }
            if ((r.code == 301 || r.code == 302 || r.code == 307 || r.code == 308) && loc != null) {
                r.close(); url = new URL(u, loc).toString(); continue;
            }
            return r;
        }
        throw new IOException("zu viele Weiterleitungen");
    }

    static String get(String url) throws IOException {
        Resp r = open(url);
        try {
            if (r.code != 200) throw new IOException("HTTP " + r.code);
            return Style.read(r.in);
        } finally { r.close(); }
    }

    static Bitmap bitmap(String url) {
        if (url == null) return null;
        try {
            Resp r = open(url);
            try {
                if (r.code != 200) return null;
                BitmapFactory.Options op = new BitmapFactory.Options();
                op.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(r.in, null, op);
            } finally { r.close(); }
        } catch (Exception e) { return null; }
    }

    /** Lokaler Proxy: MediaPlayer spielt http://127.0.0.1:PORT/, wir holen den echten Stream. */
    static class Proxy {
        ServerSocket server; volatile boolean running; final String url; volatile String error;
        Proxy(String url) { this.url = url; }
        String start() throws IOException {
            server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
            running = true;
            new Thread(new Runnable() { public void run() {
                while (running) {
                    try { final Socket c = server.accept(); new Thread(new Runnable() { public void run() { serve(c); } }).start(); }
                    catch (IOException e) { return; }
                }
            }}).start();
            return "http://127.0.0.1:" + server.getLocalPort() + "/stream.mp3";
        }
        void serve(Socket c) {
            Resp r = null;
            try {
                InputStream ci = c.getInputStream();
                String l; while ((l = SetupServer.line(ci)) != null && !l.isEmpty()) {}
                r = open(url);
                OutputStream o = c.getOutputStream();
                if (r.code != 200) { error = "HTTP " + r.code; o.write(("HTTP/1.0 " + r.code + " Fehler\r\n\r\n").getBytes()); return; }
                o.write(("HTTP/1.0 200 OK\r\nContent-Type: " + (r.type.isEmpty() ? "audio/mpeg" : r.type) + "\r\nConnection: close\r\n\r\n").getBytes());
                byte[] buf = new byte[16384]; int n;
                while (running && (n = r.in.read(buf)) > 0) o.write(buf, 0, n);
            } catch (Exception e) { if (running) error = e.getClass().getSimpleName() + ": " + e.getMessage(); }
            finally { if (r != null) r.close(); try { c.close(); } catch (Exception ignored) {} }
        }
        void stop() { running = false; try { server.close(); } catch (Exception ignored) {} }
    }
}
