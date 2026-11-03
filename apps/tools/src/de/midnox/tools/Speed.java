package de.midnox.tools;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Speedtest ueber die Cloudflare-Messserver (speed.cloudflare.com): Ping, Download, Upload. */
final class Speed {
    static final String BASE = "https://speed.cloudflare.com";
    static final int IDLE = 0, PING = 1, DOWN = 2, UP = 3, DONE = 4, FAIL = 5;
    static volatile int phase = IDLE;
    static volatile double live;        // aktuelle Mbit/s fuer die Tachonadel
    static volatile double ping = -1, jitter = -1, down = -1, up = -1;
    static volatile String server = "", error = "";
    static volatile long phaseAt;
    static volatile boolean stop;

    static void run(final Context c) {
        if (phase == PING || phase == DOWN || phase == UP) return;
        stop = false; ping = jitter = down = up = -1; live = 0; error = ""; server = "";
        new Thread(new Runnable() { public void run() {
            try {
                set(PING); measurePing();
                set(DOWN); down = transfer(true, 10000);
                set(UP); up = transfer(false, 8000);
                live = 0; set(DONE);
                save(c);
            } catch (Exception e) { error = e.getMessage() == null ? "Keine Verbindung" : e.getMessage(); live = 0; set(FAIL); }
        }}, "speedtest").start();
        new Thread(new Runnable() { public void run() {   // Standort des Messservers
            try { for (String l : U.get("https://speed.cloudflare.com/cdn-cgi/trace").split("\n")) if (l.startsWith("colo=")) server = "Cloudflare " + l.substring(5); } catch (Exception ignored) {}
        }}).start();
    }
    static void set(int p) { phase = p; phaseAt = System.currentTimeMillis(); }

    static void measurePing() throws Exception {
        List<Long> l = new ArrayList<>();
        for (int i = 0; i < 12 && !stop; i++) {
            long t0 = System.nanoTime();
            HttpURLConnection h = (HttpURLConnection) new URL(BASE + "/__down?bytes=0&r=" + i).openConnection();
            h.setConnectTimeout(5000); h.setReadTimeout(5000); h.setUseCaches(false);
            h.getResponseCode(); try { U.read(h.getInputStream()); } catch (Exception ignored) {}
            long ms = (System.nanoTime() - t0) / 1000000;
            if (i > 1) l.add(ms);   // die ersten beiden enthalten den Verbindungsaufbau
        }
        if (l.isEmpty()) throw new IOException("Messserver nicht erreichbar");
        List<Long> s = new ArrayList<>(l); Collections.sort(s);
        ping = s.get(s.size() / 2);
        double j = 0; for (int i = 1; i < l.size(); i++) j += Math.abs(l.get(i) - l.get(i - 1));
        jitter = l.size() > 1 ? j / (l.size() - 1) : 0;
    }

    /** Mehrere Verbindungen parallel; gemessen wird nach einer kurzen Anlaufphase. */
    static double transfer(final boolean download, final long ms) throws Exception {
        final AtomicLong bytes = new AtomicLong();
        final long start = System.currentTimeMillis(), end = start + ms;
        final byte[] chunk = new byte[256 * 1024]; new Random().nextBytes(chunk);
        int n = download ? 4 : 3;
        List<Thread> ts = new ArrayList<>();
        final String[] err = {null};
        for (int k = 0; k < n; k++) {
            Thread t = new Thread(new Runnable() { public void run() {
                byte[] buf = new byte[64 * 1024];
                while (!stop && System.currentTimeMillis() < end) {
                    HttpURLConnection h = null;
                    try {
                        if (download) {
                            h = (HttpURLConnection) new URL(BASE + "/__down?bytes=50000000").openConnection();
                            h.setConnectTimeout(6000); h.setReadTimeout(6000); h.setUseCaches(false);
                            InputStream in = h.getInputStream(); int r;
                            while (!stop && System.currentTimeMillis() < end && (r = in.read(buf)) > 0) bytes.addAndGet(r);
                            in.close();
                        } else {
                            int total = 8 * chunk.length;
                            h = (HttpURLConnection) new URL(BASE + "/__up").openConnection();
                            h.setConnectTimeout(6000); h.setReadTimeout(10000); h.setDoOutput(true); h.setRequestMethod("POST");
                            h.setFixedLengthStreamingMode(total); h.setRequestProperty("Content-Type", "application/octet-stream");
                            OutputStream o = h.getOutputStream();
                            for (int i = 0; i < 8 && !stop; i++) { o.write(chunk); bytes.addAndGet(chunk.length); if (System.currentTimeMillis() > end) break; }
                            o.close(); h.getResponseCode();
                        }
                    } catch (Exception e) { if (bytes.get() == 0) err[0] = e.getMessage(); try { Thread.sleep(200); } catch (InterruptedException ignored) {} }
                    finally { if (h != null) h.disconnect(); }
                }
            }});
            t.start(); ts.add(t);
        }
        // Live-Anzeige und Messung (erste 1,5 s nicht mitzaehlen)
        long warm = start + 1500, warmBytes = -1, lastT = start, lastB = 0;
        while (System.currentTimeMillis() < end && !stop) {
            Thread.sleep(250);
            long now = System.currentTimeMillis(), b = bytes.get();
            double inst = (b - lastB) * 8 / 1e6 / ((now - lastT) / 1000.0);
            live = live * 0.6 + inst * 0.4; lastT = now; lastB = b;
            if (warmBytes < 0 && now >= warm) warmBytes = b;
        }
        stop = stop;   // laufende Verbindungen enden von selbst am Zeitlimit
        for (Thread t : ts) t.join(3000);
        long b = bytes.get();
        if (b == 0) throw new IOException(err[0] != null ? err[0] : "Keine Daten übertragen");
        double secs = (System.currentTimeMillis() - (warmBytes >= 0 ? warm : start)) / 1000.0;
        long counted = warmBytes >= 0 ? b - warmBytes : b;
        return counted * 8 / 1e6 / Math.max(0.5, secs);
    }

    // ------------------------------------------------------------ Verlauf
    static JSONArray history(Context c) { try { return new JSONArray(U.prefs(c).getString("speed_hist", "[]")); } catch (Exception e) { return new JSONArray(); } }
    static void save(Context c) {
        try {
            JSONArray a = history(c), b = new JSONArray();
            b.put(new JSONObject().put("at", System.currentTimeMillis()).put("ping", ping).put("down", down).put("up", up).put("net", Sys.net(c).type));
            for (int i = 0; i < a.length() && b.length() < 10; i++) b.put(a.get(i));
            U.prefs(c).edit().putString("speed_hist", b.toString()).apply();
        } catch (Exception ignored) {}
    }
}
