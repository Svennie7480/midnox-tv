package de.midnox.quiz;

import android.content.Context;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Persoenliche Statistik, Tagesserie und Abzeichen (alles auf dem Geraet). */
final class Stats {
    static final class Badge { final String id, icon, title, desc; Badge(String id, String icon, String title, String desc) { this.id = id; this.icon = icon; this.title = title; this.desc = desc; } }
    static final Badge[] ALL = {
        new Badge("first", "🎬", "Erste Schritte", "Dein erstes Spiel"),
        new Badge("m500", "🛟", "Sicherheitsnetz", "500 € in der Million"),
        new Badge("m16k", "💶", "Halbe Strecke", "16.000 € in der Million"),
        new Badge("m125k", "💰", "Großverdiener", "125.000 € in der Million"),
        new Badge("m1m", "🏆", "Millionär", "Die Million geknackt"),
        new Badge("nojoker", "🦸", "Ohne Netz", "16.000 € ganz ohne Joker"),
        new Badge("s3", "🔥", "Dreierserie", "3 Tage in Folge Tages-Duell"),
        new Badge("s7", "📅", "Wochenheld", "7 Tage in Folge Tages-Duell"),
        new Badge("s30", "👑", "Monatsmeister", "30 Tage in Folge Tages-Duell"),
        new Badge("perfect", "💯", "Perfekter Tag", "Tages-Duell mit 10 von 10"),
        new Badge("fast", "⚡", "Blitzmerker", "Tages-Duell mit 8.000 Punkten"),
        new Badge("party5", "🎉", "Partylöwe", "5 Party-Spiele"),
        new Badge("r500", "🧠", "Wissensdurst", "500 richtige Antworten"),
        new Badge("allround", "🌍", "Allrounder", "In jeder Kategorie 10 richtig"),
        new Badge("heimat", "🏠", "Heimatkunde", "50 richtig in „Deutschland & Regionen“"),
        new Badge("group", "🤝", "Gemeinsam", "In einer Freundesgruppe"),
    };

    static JSONObject get(Context c) { try { return new JSONObject(U.prefs(c).getString("stats", "{}")); } catch (Exception e) { return new JSONObject(); } }
    static void put(Context c, JSONObject o) { U.prefs(c).edit().putString("stats", o.toString()).apply(); }
    static void inc(JSONObject o, String k, int by) { try { o.put(k, o.optInt(k) + by); } catch (Exception ignored) {} }
    static void max(JSONObject o, String k, int v) { try { if (v > o.optInt(k)) o.put(k, v); } catch (Exception ignored) {} }

    static void answer(Context c, String cat, boolean right) {
        JSONObject o = get(c);
        inc(o, "total", 1); if (right) inc(o, "right", 1);
        try {
            JSONObject cs = o.optJSONObject("cat"); if (cs == null) { cs = new JSONObject(); o.put("cat", cs); }
            JSONArray a = cs.optJSONArray(cat); if (a == null) a = new JSONArray().put(0).put(0);
            a.put(0, a.optInt(0) + (right ? 1 : 0)); a.put(1, a.optInt(1) + 1); cs.put(cat, a);
        } catch (Exception ignored) {}
        put(c, o);
    }
    static void millionEnd(Context c, int won, boolean usedJoker) {
        JSONObject o = get(c); inc(o, "played_m", 1); max(o, "best_m", won); if (!usedJoker) max(o, "best_m_nj", won); if (won >= 1000000) inc(o, "millions", 1); put(c, o);
    }
    static void dailyEnd(Context c, int score, int correct) { JSONObject o = get(c); inc(o, "played_d", 1); max(o, "best_d", score); max(o, "best_dc", correct); put(c, o); }
    static void partyEnd(Context c) { JSONObject o = get(c); inc(o, "played_p", 1); put(c, o); }

    static String day(int back) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); f.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        return f.format(new Date(System.currentTimeMillis() - back * 86400000L));
    }
    /** Tage in Folge mit Tages-Duell (heute noch nicht gespielt -> Serie von gestern zaehlt weiter). */
    static int streak(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        int start = p.contains("daily_" + day(0)) ? 0 : 1, n = 0;
        while (p.contains("daily_" + day(start + n))) n++;
        return n;
    }

    static boolean has(Context c, String id) {
        JSONObject o = get(c); int s = streak(c);
        switch (id) {
            case "first": return o.optInt("played_m") + o.optInt("played_d") + o.optInt("played_p") > 0;
            case "m500": return o.optInt("best_m") >= 500;
            case "m16k": return o.optInt("best_m") >= 16000;
            case "m125k": return o.optInt("best_m") >= 125000;
            case "m1m": return o.optInt("best_m") >= 1000000;
            case "nojoker": return o.optInt("best_m_nj") >= 16000;
            case "s3": return s >= 3 || o.optInt("best_streak") >= 3;
            case "s7": return s >= 7 || o.optInt("best_streak") >= 7;
            case "s30": return s >= 30 || o.optInt("best_streak") >= 30;
            case "perfect": return o.optInt("best_dc") >= 10;
            case "fast": return o.optInt("best_d") >= 8000;
            case "party5": return o.optInt("played_p") >= 5;
            case "r500": return o.optInt("right") >= 500;
            case "heimat": { JSONArray a = o.optJSONObject("cat") == null ? null : o.optJSONObject("cat").optJSONArray("de"); return a != null && a.optInt(0) >= 50; }
            case "allround": {
                JSONObject cs = o.optJSONObject("cat"); if (cs == null) return false;
                for (String k : Bank.cats.keySet()) { JSONArray a = cs.optJSONArray(k); if (a == null || a.optInt(0) < 10) return false; }
                return !Bank.cats.isEmpty();
            }
            case "group": return Groups.list(c).length() > 0;
        }
        return false;
    }
    /** Neu freigeschaltete Abzeichen seit dem letzten Aufruf (und merken). */
    static List<Badge> fresh(Context c) {
        JSONObject o = get(c);
        max(o, "best_streak", streak(c));
        Set<String> got = new HashSet<>(Arrays.asList(o.optString("badges", "").split(",")));
        List<Badge> out = new ArrayList<>();
        put(c, o);
        for (Badge b : ALL) if (!got.contains(b.id) && has(c, b.id)) { out.add(b); got.add(b.id); }
        if (!out.isEmpty()) { got.remove(""); try { o = get(c); o.put("badges", android.text.TextUtils.join(",", got)); put(c, o); } catch (Exception ignored) {} }
        return out;
    }
    static int count(Context c) { int n = 0; for (Badge b : ALL) if (has(c, b.id)) n++; return n; }
}
