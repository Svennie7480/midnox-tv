package de.midnox.quiz;

import android.content.Context;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Weltweite Bestenliste auf midnox.de. */
final class Scores {
    static final String SERVER = "https://midnox.de/midnoxtv/quiz/index.php";
    static final String WEB = "midnox.de/midnoxtv/quiz";
    static final int[] LADDER = {0, 50, 100, 200, 300, 500, 1000, 2000, 4000, 8000, 16000, 32000, 64000, 125000, 500000, 1000000};

    static String today() { SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin")); return f.format(new Date()); }
    static String device(Context c) {
        String d = U.prefs(c).getString("dev", "");
        if (d.length() < 12) { d = Long.toHexString(new Random().nextLong() & 0x7FFFFFFFFFFFFFFFL) + Long.toHexString(System.nanoTime() & 0xFFFFFFL); U.prefs(c).edit().putString("dev", d).apply(); }
        return d;
    }
    static String hmac(String s) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(Secret.KEY.getBytes("UTF-8"), "HmacSHA256"));
        StringBuilder sb = new StringBuilder(); for (byte b : m.doFinal(s.getBytes("UTF-8"))) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }
    /** Ergebnis eintragen. Antwort: {ok, rank, msg}. Ohne Netz: wird spaeter nachgereicht. */
    static JSONObject submit(Context c, String mode, String name, int score, long sec, String day) {
        try {
            long ts = System.currentTimeMillis();
            String dev = device(c);
            name = name.replaceAll("[\\x00-\\x1F<>\"\\\\]", "").trim().replaceAll("\\s+", " ");
            if (name.length() > 16) name = name.substring(0, 16);
            String d = day == null ? "" : day;
            JSONObject o = new JSONObject().put("m", mode).put("n", name).put("s", score).put("t", sec).put("dev", dev).put("day", d).put("ts", ts)
                .put("sig", hmac(mode + "|" + name + "|" + score + "|" + sec + "|" + dev + "|" + d + "|" + ts));
            try {
                return new JSONObject(U.http(SERVER + "?a=submit", o.toString()));
            } catch (Exception net) {
                queue(c, o.put("queued", true));
                return new JSONObject().put("ok", false).put("msg", "Kein Netz – wird später eingetragen");
            }
        } catch (Exception e) { return new JSONObject(); }
    }
    /** Fehlgeschlagene Eintraege merken und spaeter neu signiert senden. */
    static void queue(Context c, JSONObject o) {
        try { JSONArray a = new JSONArray(U.prefs(c).getString("queue", "[]")); a.put(o); U.prefs(c).edit().putString("queue", a.toString()).apply(); } catch (Exception ignored) {}
    }
    static void flush(Context c) {
        try {
            JSONArray a = new JSONArray(U.prefs(c).getString("queue", "[]"));
            if (a.length() == 0) return;
            U.prefs(c).edit().putString("queue", "[]").apply();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String day = o.optString("day");
                if (o.optString("m").equals("daily") && !day.equals(today())) continue;   // Tag vorbei
                submit(c, o.optString("m"), o.optString("n"), o.optInt("s"), o.optLong("t"), day.isEmpty() ? null : day);
            }
        } catch (Exception ignored) {}
    }
    // ------------------------------------------------------------ Herausforderung
    static JSONObject chNew(Context c, String name, List<String> ids, long seed) throws Exception {
        return new JSONObject(U.http(SERVER + "?a=ch_new", new JSONObject().put("dev", device(c)).put("n", name).put("ids", new JSONArray(ids)).put("seed", seed).toString()));
    }
    static JSONObject chGet(Context c, String code) throws Exception { return new JSONObject(U.http(SERVER + "?a=ch&c=" + code + "&dev=" + device(c), null)); }
    static JSONObject chResult(Context c, String code, String name, int score, long sec, int correct) throws Exception {
        long ts = System.currentTimeMillis(); String dev = device(c);
        name = name.replaceAll("[\\x00-\\x1F<>\"\\\\]", "").trim();
        JSONObject o = new JSONObject().put("code", code).put("n", name).put("s", score).put("t", sec).put("c", correct).put("dev", dev).put("ts", ts)
            .put("sig", hmac("ch|" + code + "|" + name + "|" + score + "|" + sec + "|" + dev + "|" + ts));
        return new JSONObject(U.http(SERVER + "?a=ch_result", o.toString()));
    }
    static JSONObject top(String mode, String period) throws Exception {
        String q = mode.equals("daily") ? "?a=top&m=daily&d=" + today() : "?a=top&m=million&p=" + period;
        return new JSONObject(U.http(SERVER + q, null));
    }
}
