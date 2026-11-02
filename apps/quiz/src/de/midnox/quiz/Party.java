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
    static final int LOBBY = 0, QUESTION = 1, REVEAL = 2, SCORES = 3, END = 4, ANSWERING = 5;
    static final long Q_MS = 20000, BUZZ_ANSWER_MS = 8000;
    static int mode;   // 0 = klassisch, 1 = Buzzer, 2 = Teams
    static long qMs = Q_MS;   // Zeit fuer die aktuelle Frage (Schaetzen/Sortieren dauert laenger)
    static final String[] TEAM = {"Team Lila", "Team Türkis"};
    static String buzzer; static long buzzAt, remaining; static final Set<String> locked = new HashSet<>();
    static int lastWrongQn = -1; static long lastWrongAt;   // fuer Ansage „falsch“ am Fernseher

    static final class Player {
        String id, name; int color, score, gained, answer = -1, streak, team; long answerMs; int answeredQn = -1; long seen;
        double est; int[] ord; int estRank;
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
            else if (p.equals("/api/buzz")) body = buzz(new JSONObject(data.isEmpty() ? "{}" : data)).toString();
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
        JSONObject o = new JSONObject().put("phase", phase).put("qn", qn).put("total", qs.size()).put("joined", me != null).put("mode", mode);
        if (me == null) return o;
        me.seen = System.currentTimeMillis();
        o.put("name", me.name).put("color", me.color).put("score", me.score).put("rank", rank(me)).put("players", players.size());
        if (mode == 2) { int[] ts = teamScores(); o.put("team", me.team).put("teams", new JSONArray().put(ts[0]).put(ts[1])); }
        if (phase == QUESTION || phase == REVEAL || phase == ANSWERING) {
            Bank.Q q = qs.get(qn);
            o.put("q", q.text).put("type", q.type).put("left", left()).put("dur", qMs)
             .put("my", me.answeredQn == qn ? me.answer : -1).put("done", me.answeredQn == qn);
            if (!q.type.equals("est")) o.put("answers", new JSONArray(Arrays.asList(q.answers)));
            else o.put("unit", q.unit);
            if (phase == REVEAL && q.type.equals("est")) o.put("num", q.num).put("myest", me.answeredQn == qn ? me.est : JSONObject.NULL).put("estrank", me.estRank);
            if (phase == REVEAL && q.type.equals("ord")) { JSONArray a = new JSONArray(); for (int k : q.order) a.put(k); o.put("order", a); if (me.ord != null && me.answeredQn == qn) { JSONArray b = new JSONArray(); for (int k : me.ord) b.put(k); o.put("myord", b); } }
            if (mode == 1) {
                o.put("locked", locked.contains(me.id)).put("answering", me.id.equals(buzzer));
                Player b = buzzer == null ? null : players.get(buzzer);
                o.put("buzzer", b == null ? "" : b.name).put("aleft", Math.max(0, BUZZ_ANSWER_MS - (System.currentTimeMillis() - buzzAt)));
            }
            if (phase == REVEAL) o.put("correct", q.correct).put("gained", me.gained);
        }
        return o;
    }
    static synchronized JSONObject answer(JSONObject in) throws JSONException {
        if (mode == 1) return buzzAnswer(in);
        Player p = players.get(in.optString("id"));
        if (p == null || phase != QUESTION || in.optInt("qn", -2) != qn || p.answeredQn == qn) return new JSONObject().put("ok", false);
        long t = System.currentTimeMillis() - qStart;
        if (t > qMs + 500) return new JSONObject().put("ok", false).put("msg", "Zu spät");
        Bank.Q q = qs.get(qn);
        if (q.type.equals("est")) {
            double v; try { v = Double.parseDouble(in.optString("v").replace(".", "").replace(",", ".").trim()); } catch (Exception e) { return new JSONObject().put("ok", false).put("msg", "Bitte eine Zahl"); }
            p.est = v; p.answer = 0; p.answeredQn = qn; p.answerMs = t;
            return new JSONObject().put("ok", true);
        }
        if (q.type.equals("ord")) {
            JSONArray o = in.optJSONArray("o"); if (o == null || o.length() != 4) return new JSONObject().put("ok", false);
            int[] ord = new int[4]; Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 4; i++) { ord[i] = o.optInt(i, -1); if (ord[i] < 0 || ord[i] > 3 || !seen.add(ord[i])) return new JSONObject().put("ok", false); }
            p.ord = ord; p.answer = 0; p.answeredQn = qn; p.answerMs = t;
            return new JSONObject().put("ok", true);
        }
        int a = in.optInt("a", -1); if (a < 0 || a > 3) return new JSONObject().put("ok", false);
        p.answer = a; p.answeredQn = qn; p.answerMs = t;
        return new JSONObject().put("ok", true);
    }

    // ------------------------------------------------------------ Buzzer
    static synchronized JSONObject buzz(JSONObject in) throws JSONException {
        Player p = players.get(in.optString("id"));
        if (mode != 1 || p == null || phase != QUESTION || in.optInt("qn", -2) != qn || locked.contains(p.id) || left() <= 0) return new JSONObject().put("ok", false);
        remaining = left(); buzzer = p.id; buzzAt = System.currentTimeMillis(); phase = ANSWERING;
        return new JSONObject().put("ok", true);
    }
    static synchronized JSONObject buzzAnswer(JSONObject in) throws JSONException {
        Player p = players.get(in.optString("id"));
        if (p == null || phase != ANSWERING || !p.id.equals(buzzer) || in.optInt("qn", -2) != qn) return new JSONObject().put("ok", false);
        int a = in.optInt("a", -1); if (a < 0 || a > 3) return new JSONObject().put("ok", false);
        p.answer = a; p.answeredQn = qn;
        resolveBuzz(a == qs.get(qn).correct);
        return new JSONObject().put("ok", true);
    }
    /** Antwort des Buzzernden auswerten: richtig = +1000 und aufloesen, falsch = -500, gesperrt, weiter fuer die anderen. */
    static synchronized void resolveBuzz(boolean right) {
        Player p = players.get(buzzer);
        if (p != null) {
            if (right) { p.gained = 1000; p.score += 1000; p.streak++; }
            else { p.gained = -500; p.score -= 500; p.streak = 0; locked.add(p.id); lastWrongQn = qn; lastWrongAt = System.currentTimeMillis(); }
        }
        buzzer = null;
        if (right || locked.size() >= players.size()) { phase = REVEAL; return; }
        phase = QUESTION; qStart = System.currentTimeMillis() - (qMs - remaining);   // Uhr laeuft dort weiter, wo sie stand
    }
    static synchronized void buzzTick() { if (phase == ANSWERING && System.currentTimeMillis() - buzzAt > BUZZ_ANSWER_MS) { Player p = players.get(buzzer); if (p != null) { p.answer = -1; p.answeredQn = qn; } resolveBuzz(false); } }
    static synchronized String buzzerName() { Player p = buzzer == null ? null : players.get(buzzer); return p == null ? "" : p.name; }

    // ------------------------------------------------------------ Ablauf (vom Fernseher)
    static synchronized void reset() { phase = LOBBY; qn = -1; qs.clear(); buzzer = null; locked.clear(); for (Player p : players.values()) { p.score = 0; p.gained = 0; p.answer = -1; p.answeredQn = -1; p.streak = 0; } }
    static synchronized void kick(String id) { players.remove(id); }
    static synchronized void begin(List<Bank.Q> l) {
        qs = l; qn = -1; int i = 0;
        for (Player p : players.values()) { p.score = 0; p.streak = 0; p.answeredQn = -1; p.team = i++ % 2; }
        next();
    }
    /** Teamwertung: Durchschnitt je Mitglied (fair bei ungleich grossen Teams). */
    static synchronized int[] teamScores() {
        int[] sum = new int[2], n = new int[2];
        for (Player p : players.values()) { sum[p.team] += p.score; n[p.team]++; }
        return new int[]{n[0] == 0 ? 0 : Math.round(sum[0] / (float) n[0]), n[1] == 0 ? 0 : Math.round(sum[1] / (float) n[1])};
    }
    static synchronized int teamSize(int t) { int n = 0; for (Player p : players.values()) if (p.team == t) n++; return n; }
    static synchronized void next() {
        if (qn + 1 >= qs.size()) { phase = END; return; }
        qn++; phase = QUESTION; qStart = System.currentTimeMillis();
        qMs = qs.get(qn).type.equals("mc") ? Q_MS : 30000;
        buzzer = null; locked.clear();
        for (Player p : players.values()) { p.gained = 0; p.answer = -1; p.ord = null; p.estRank = 0; }
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
        if (mode == 1) { if (phase == QUESTION || phase == ANSWERING) { buzzer = null; phase = REVEAL; } return; }
        if (phase != QUESTION) return;
        Bank.Q q = qs.get(qn);
        if (q.type.equals("est")) { revealEstimate(q); phase = REVEAL; return; }
        if (q.type.equals("ord")) { revealOrder(q); phase = REVEAL; return; }
        for (Player p : players.values()) {
            boolean ok = p.answeredQn == qn && p.answer == q.correct;
            if (ok) {
                p.streak++;
                p.gained = 500 + (int) Math.round(500.0 * Math.max(0, qMs - p.answerMs) / qMs) + (p.streak >= 3 ? 100 : 0);
                p.score += p.gained;
            } else { p.streak = 0; p.gained = 0; }
        }
        phase = REVEAL;
    }
    static final int[] EST_POINTS = {1000, 750, 500, 350, 250, 150};
    /** Schaetzen: wer am naechsten dran ist, bekommt am meisten; ein Volltreffer gibt 500 extra. */
    static void revealEstimate(Bank.Q q) {
        final double target = q.num;
        List<Player> l = new ArrayList<>();
        for (Player p : players.values()) { p.gained = 0; p.estRank = 0; if (p.answeredQn == qn) l.add(p); else p.streak = 0; }
        Collections.sort(l, new Comparator<Player>() { public int compare(Player a, Player b) { return Double.compare(Math.abs(a.est - target), Math.abs(b.est - target)); } });
        int rank = 0; double last = -1;
        for (int i = 0; i < l.size(); i++) {
            Player p = l.get(i); double d = Math.abs(p.est - target);
            if (i == 0 || d != last) rank = i; last = d;
            p.estRank = rank + 1;
            p.gained = (rank < EST_POINTS.length ? EST_POINTS[rank] : 100) + (d == 0 ? 500 : 0);
            p.score += p.gained;
            if (rank == 0) p.streak++; else p.streak = 0;
        }
    }
    /** Sortieren: 250 Punkte je richtiger Position, alles richtig = 1250. */
    static void revealOrder(Bank.Q q) {
        for (Player p : players.values()) {
            p.gained = 0;
            if (p.answeredQn != qn || p.ord == null) { p.streak = 0; continue; }
            int ok = 0; for (int k = 0; k < 4; k++) if (p.ord[k] == q.order[k]) ok++;
            p.gained = ok * 250 + (ok == 4 ? 250 : 0); p.score += p.gained;
            if (ok == 4) p.streak++; else p.streak = 0;
        }
    }
    static synchronized List<Player> estimates() {
        List<Player> l = new ArrayList<>(); for (Player p : players.values()) if (p.answeredQn == qn) l.add(p);
        Collections.sort(l, new Comparator<Player>() { public int compare(Player a, Player b) { return a.estRank - b.estRank; } });
        return l;
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
    static synchronized long left() { return phase == ANSWERING ? remaining : Math.max(0, qMs - (System.currentTimeMillis() - qStart)); }
}
