package de.midnox.quiz;

import android.content.Context;
import org.json.*;
import java.io.File;
import java.util.*;

/** Fragenkatalog: mitgeliefert in assets, aktualisiert sich von GitHub (gleiche Fragen fuer alle beim Tages-Duell). */
final class Bank {
    static final String UPDATE = "https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/quiz/questions.json";

    static final class Q {
        String id, cat, text; int level;
        String[] answers = new String[4];   // bereits gemischt
        int correct;                         // Index der richtigen Antwort in answers
    }
    static final class Raw { String id, cat, text; int level; String[] a; }

    static List<Raw> all = new ArrayList<>();
    static LinkedHashMap<String, String> cats = new LinkedHashMap<>();
    static int version;

    static synchronized void load(Context c) {
        if (!all.isEmpty()) return;
        JSONObject best = null;
        try { best = new JSONObject(U.read(c.getAssets().open("questions.json"))); } catch (Exception ignored) {}
        try {
            JSONObject up = new JSONObject(U.readFile(new File(c.getFilesDir(), "questions.json")));
            if (best == null || up.optInt("v") > best.optInt("v")) best = up;
        } catch (Exception ignored) {}
        if (best != null) parse(best);
    }
    static void parse(JSONObject o) {
        List<Raw> l = new ArrayList<>();
        LinkedHashMap<String, String> cs = new LinkedHashMap<>();
        JSONObject co = o.optJSONObject("cats");
        if (co != null) for (Iterator<String> it = co.keys(); it.hasNext(); ) { String k = it.next(); cs.put(k, co.optString(k)); }
        JSONArray a = o.optJSONArray("q");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject q = a.optJSONObject(i); if (q == null) continue;
            JSONArray an = q.optJSONArray("a"); if (an == null || an.length() != 4) continue;
            Raw r = new Raw(); r.id = q.optString("id"); r.cat = q.optString("c"); r.text = q.optString("q"); r.level = q.optInt("l", 2);
            r.a = new String[]{an.optString(0), an.optString(1), an.optString(2), an.optString(3)};
            l.add(r);
        }
        Collections.sort(l, new Comparator<Raw>() { public int compare(Raw x, Raw y) { return x.id.compareTo(y.id); } });
        all = l; cats = cs; version = o.optInt("v");
    }
    /** Im Hintergrund: neuere Fragen holen. */
    static void update(Context c) {
        try {
            JSONObject o = new JSONObject(U.http(UPDATE + "?t=" + System.currentTimeMillis() / 3600000L, null));
            if (o.optInt("v") > version && o.optJSONArray("q") != null && o.optJSONArray("q").length() > 50) {
                U.writeFile(new File(c.getFilesDir(), "questions.json"), o.toString());
                synchronized (Bank.class) { parse(o); }
            }
        } catch (Exception ignored) {}
    }

    static Q make(Raw r, Random rnd) {
        Q q = new Q(); q.id = r.id; q.cat = r.cat; q.text = r.text; q.level = r.level;
        Integer[] idx = {0, 1, 2, 3};
        List<Integer> l = Arrays.asList(idx);
        Collections.shuffle(l, rnd);
        for (int i = 0; i < 4; i++) { q.answers[i] = r.a[l.get(i)]; if (l.get(i) == 0) q.correct = i; }
        return q;
    }

    // ------------------------------------------------------------ Auswahl
    static Set<String> seen(Context c) { return new HashSet<>(Arrays.asList(U.prefs(c).getString("seen", "").split(","))); }
    static void markSeen(Context c, String id) {
        LinkedList<String> l = new LinkedList<>(Arrays.asList(U.prefs(c).getString("seen", "").split(",")));
        l.remove(""); l.remove(id); l.add(id);
        while (l.size() > Math.max(40, all.size() * 2 / 3)) l.removeFirst();
        U.prefs(c).edit().putString("seen", android.text.TextUtils.join(",", l)).apply();
    }
    static List<Raw> byLevel(int level, String cat) {
        List<Raw> l = new ArrayList<>();
        for (Raw r : all) if (r.level == level && (cat == null || r.cat.equals(cat))) l.add(r);
        return l;
    }
    /** Zufaellig, bisher ungesehene bevorzugt. */
    static List<Raw> pick(Context c, List<Raw> from, int n, Random rnd, Set<String> exclude) {
        Set<String> seen = seen(c);
        List<Raw> fresh = new ArrayList<>(), old = new ArrayList<>();
        for (Raw r : from) if (!exclude.contains(r.id)) (seen.contains(r.id) ? old : fresh).add(r);
        Collections.shuffle(fresh, rnd); Collections.shuffle(old, rnd);
        fresh.addAll(old);
        return new ArrayList<>(fresh.subList(0, Math.min(n, fresh.size())));
    }

    /** Die Million: 5 leichte, 5 mittlere, 5 schwere Fragen. */
    static List<Q> million(Context c) {
        Random rnd = new Random();
        List<Q> out = new ArrayList<>(); Set<String> ex = new HashSet<>();
        for (int lv = 1; lv <= 3; lv++) for (Raw r : pick(c, byLevel(lv, null), 5, rnd, ex)) { ex.add(r.id); out.add(make(r, rnd)); }
        // Ersatz, falls eine Stufe zu wenige Fragen hat
        if (out.size() < 15) for (Raw r : pick(c, all, 15 - out.size(), rnd, ex)) out.add(make(r, rnd));
        return out;
    }

    /** Tages-Duell: fuer alle gleich (Datum als Startwert), 3 leicht, 4 mittel, 3 schwer. */
    static List<Q> daily(String day) {
        long seed = Long.parseLong(day.replace("-", ""));
        Random rnd = new Random(seed * 7919L);
        List<Q> out = new ArrayList<>();
        int[] per = {0, 3, 4, 3};
        for (int lv = 1; lv <= 3; lv++) {
            List<Raw> l = byLevel(lv, null);
            Collections.shuffle(l, rnd);
            for (int i = 0; i < per[lv] && i < l.size(); i++) out.add(make(l.get(i), new Random(seed ^ l.get(i).id.hashCode())));
        }
        return out;
    }

    /** Party: gemischt nach Kategorie, Schwierigkeit steigt leicht an. */
    static List<Q> party(Context c, String cat, int n) {
        Random rnd = new Random();
        List<Raw> from = new ArrayList<>();
        for (Raw r : all) if (cat == null || r.cat.equals(cat)) from.add(r);
        List<Raw> l = pick(c, from, n, rnd, new HashSet<String>());
        Collections.sort(l, new Comparator<Raw>() { public int compare(Raw a, Raw b) { return a.level - b.level; } });
        List<Q> out = new ArrayList<>();
        for (Raw r : l) out.add(make(r, rnd));
        return out;
    }
    static int count(String cat) { int n = 0; for (Raw r : all) if (cat == null || r.cat.equals(cat)) n++; return n; }
}
