package de.midnox.quiz;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Party-Modus: kleiner Webserver im Fernseher. Die Handys oeffnen die Seite per QR-Code,
 * melden sich mit Namen an und tippen ihre Antworten. Der Fernseher steuert den Ablauf.
 */
final class Party {
    static final int LOBBY = 0, QUESTION = 1, REVEAL = 2, SCORES = 3, END = 4;
    static final long Q_MS = 20000;

    static final class Player {
        String id, name; int color, score, gained, answer = -1, streak; long answerMs; int answeredQn = -1; long seen;
    }

    static ServerSocket server; static int port;
    static Context ctx;
    static int phase = LOBBY, qn = -1;
    static List<Bank.Q> qs = new ArrayList<>();
    static long qStart;
    static final LinkedHashMap<String, Player> players = new LinkedHashMap<>();
    static String code = "";

    // ------------------------------------------------------------ Server
    static synchronized void start(Context c) {
        ctx = c.getApplicationContext();
        if (server != null) return;
        for (port = 8180; port < 8200; port++) { try { server = new ServerSocket(port); break; } catch (IOException e) { server = null; } }
        if (server == null) return;
        new Thread(new Runnable() { public void run() {
            while (server != null) {
                try { final Socket s = server.accept(); new Thread(new Runnable() { public void run() { serve(s); } }).start(); }
                catch (IOException e) { return; }
            }
        }}, "party").start();
    }
    static synchronized void stop() { try { if (server != null) server.close(); } catch (Exception ignored) {} server = null; }
    static String url() { return "http://" + U.ownIp() + ":" + port + "/"; }

    static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int ch;
        while ((ch = in.read()) >= 0) { if (ch == '\n') break; if (ch != '\r') sb.append((char) ch); }
        return ch < 0 && sb.length() == 0 ? null : sb.toString();
    }
    static void serve(Socket s) {
        try {
            s.setSoTimeout(15000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            String req = line(in); if (req == null) return;
            String[] parts = req.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            long len = 0; String l;
            while ((l = line(in)) != null && !l.isEmpty()) if (l.toLowerCase(Locale.ROOT).startsWith("content-length:")) len = Long.parseLong(l.substring(15).trim());
            byte[] b = new byte[(int) Math.min(len, 8192)]; int r = 0;
            while (r < b.length) { int n = in.read(b, r, b.length - r); if (n < 0) break; r += n; }
            String data = new String(b, 0, r, "UTF-8");
            String type = "application/json", body;
            String p = path.replaceAll("\\?.*", "");
            if (p.equals("/api/join")) body = join(new JSONObject(data.isEmpty() ? "{}" : data)).toString();
            else if (p.equals("/api/state")) body = state(query(path, "id")).toString();
            else if (p.equals("/api/answer")) body = answer(new JSONObject(data.isEmpty() ? "{}" : data)).toString();
            else { type = "text/html"; body = U.read(ctx.getAssets().open("phone.html")); }
            byte[] out = body.getBytes("UTF-8");
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + out.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            o.write(out); o.flush();
        } catch (Exception ignored) {
        } finally { try { s.close(); } catch (Exception ignored) {} }
    }
    static String query(String path, String k) {
        int i = path.indexOf('?'); if (i < 0) return "";
        for (String kv : path.substring(i + 1).split("&")) { String[] p = kv.split("=", 2); if (p[0].equals(k) && p.length > 1) try { return URLDecoder.decode(p[1], "UTF-8"); } catch (Exception e) { return p[1]; } }
        return "";
    }

    // ------------------------------------------------------------ Spieler-Seite
    static synchronized JSONObject join(JSONObject in) throws JSONException {
        String id = in.optString("id");
        String name = in.optString("name").replaceAll("[\\x00-\\x1F<>]", "").trim();
        if (name.length() > 14) name = name.substring(0, 14);
        Player p = players.get(id);
        if (p == null) {
            if (name.isEmpty()) return new JSONObject().put("ok", false).put("msg", "Bitte einen Namen eingeben");
            if (players.size() >= 12) return new JSONObject().put("ok", false).put("msg", "Das Spiel ist voll (12 Spieler)");
            for (Player o : players.values()) if (o.name.equalsIgnoreCase(name)) return new JSONObject().put("ok", false).put("msg", "Den Namen gibt es schon");
            p = new Player(); p.id = Long.toHexString(new Random().nextLong() & 0xFFFFFFFFFFFFL); p.name = name; p.color = players.size() % 8;
            players.put(p.id, p);
        } else if (!name.isEmpty()) p.name = name;
        p.seen = System.currentTimeMillis();
        return new JSONObject().put("ok", true).put("id", p.id).put("name", p.name).put("color", p.color);
    }
    static synchronized JSONObject state(String id) throws JSONException {
        Player me = players.get(id);
        JSONObject o = new JSONObject().put("phase", phase).put("qn", qn).put("total", qs.size()).put("joined", me != null);
        if (me == null) return o;
        me.seen = System.currentTimeMillis();
        o.put("name", me.name).put("color", me.color).put("score", me.score).put("rank", rank(me)).put("players", players.size());
        if (phase == QUESTION || phase == REVEAL) {
            Bank.Q q = qs.get(qn);
            o.put("q", q.text).put("answers", new JSONArray(Arrays.asList(q.answers))).put("left", Math.max(0, Q_MS - (System.currentTimeMillis() - qStart)))
             .put("my", me.answeredQn == qn ? me.answer : -1);
            if (phase == REVEAL) o.put("correct", q.correct).put("gained", me.answeredQn == qn ? me.gained : 0);
        }
        return o;
    }
    static synchronized JSONObject answer(JSONObject in) throws JSONException {
        Player p = players.get(in.optString("id"));
        if (p == null || phase != QUESTION || in.optInt("qn", -2) != qn || p.answeredQn == qn) return new JSONObject().put("ok", false);
        int a = in.optInt("a", -1); if (a < 0 || a > 3) return new JSONObject().put("ok", false);
        long t = System.currentTimeMillis() - qStart;
        if (t > Q_MS + 500) return new JSONObject().put("ok", false).put("msg", "Zu spät");
        p.answer = a; p.answeredQn = qn; p.answerMs = t;
        return new JSONObject().put("ok", true);
    }

    // ------------------------------------------------------------ Ablauf (vom Fernseher)
    static synchronized void reset() { phase = LOBBY; qn = -1; qs.clear(); for (Player p : players.values()) { p.score = 0; p.gained = 0; p.answer = -1; p.answeredQn = -1; p.streak = 0; } }
    static synchronized void kick(String id) { players.remove(id); }
    static synchronized void begin(List<Bank.Q> l) { qs = l; qn = -1; for (Player p : players.values()) { p.score = 0; p.streak = 0; p.answeredQn = -1; } next(); }
    static synchronized void next() {
        if (qn + 1 >= qs.size()) { phase = END; return; }
        qn++; phase = QUESTION; qStart = System.currentTimeMillis();
        for (Player p : players.values()) { p.gained = 0; p.answer = -1; }
        Bank.markSeen(ctx, qs.get(qn).id);
    }
    static synchronized boolean allAnswered() {
        if (players.isEmpty()) return false;
        for (Player p : players.values()) if (p.answeredQn != qn) return false;
        return true;
    }
    static synchronized int answeredCount() { int n = 0; for (Player p : players.values()) if (p.answeredQn == qn) n++; return n; }
    /** Aufloesen: Punkte verteilen (schneller = mehr), Serien-Bonus ab 3 richtigen in Folge. */
    static synchronized void reveal() {
        if (phase != QUESTION) return;
        Bank.Q q = qs.get(qn);
        for (Player p : players.values()) {
            boolean ok = p.answeredQn == qn && p.answer == q.correct;
            if (ok) {
                p.streak++;
                p.gained = 500 + (int) Math.round(500.0 * Math.max(0, Q_MS - p.answerMs) / Q_MS) + (p.streak >= 3 ? 100 : 0);
                p.score += p.gained;
            } else { p.streak = 0; p.gained = 0; }
        }
        phase = REVEAL;
    }
    static synchronized void scores() { phase = qn + 1 >= qs.size() ? END : SCORES; }
    static synchronized int[] votes() {
        int[] v = new int[4];
        for (Player p : players.values()) if (p.answeredQn == qn && p.answer >= 0) v[p.answer]++;
        return v;
    }
    static synchronized List<Player> ranking() {
        List<Player> l = new ArrayList<>(players.values());
        Collections.sort(l, new Comparator<Player>() { public int compare(Player a, Player b) { return b.score - a.score; } });
        return l;
    }
    static synchronized int rank(Player me) { int r = 1; for (Player p : players.values()) if (p.score > me.score) r++; return r; }
    static synchronized long left() { return Math.max(0, Q_MS - (System.currentTimeMillis() - qStart)); }
}
