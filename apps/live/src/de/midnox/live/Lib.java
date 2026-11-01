package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** Quellen, Senderliste (mit Cache), Gruppen, Favoriten, ausgeblendete Gruppen. */
final class Lib {
    static final String FAV = "★ Favoriten", ALL = "Alle Sender", SETTINGS = "\u2699 Einstellungen";
    static volatile List<Model.Channel> channels = new ArrayList<>();
    static volatile List<String> groups = new ArrayList<>();
    static final Map<String, String> errors = Collections.synchronizedMap(new HashMap<String, String>());
    static volatile long loadedAt;
    static volatile boolean loading;
    static volatile String status = "";
    static volatile Map<String, String> srcNames = new HashMap<>();   // Quelle-ID -> Name (fuer die Anzeige)

    // ------------------------------------------------------------ Quellen
    static List<Model.Source> sources(Context c) {
        List<Model.Source> l = new ArrayList<>();
        try { JSONArray a = new JSONArray(U.prefs(c).getString("sources", "[]")); for (int i = 0; i < a.length(); i++) l.add(Model.Source.from(a.getJSONObject(i))); } catch (Exception ignored) {}
        return l;
    }
    static void saveSources(Context c, List<Model.Source> l) {
        JSONArray a = new JSONArray();
        try { for (Model.Source s : l) a.put(s.json()); } catch (Exception ignored) {}
        U.prefs(c).edit().putString("sources", a.toString()).apply();
    }
    static Model.Source source(Context c, String id) { for (Model.Source s : sources(c)) if (s.id.equals(id)) return s; return null; }
    static File m3uFile(Context c, String id) { return new File(c.getFilesDir(), "m3u_" + id + ".m3u"); }

    // ------------------------------------------------------------ Laden
    /** Cache lesen (schnell beim Start). */
    static void loadCache(Context c) {
        try {
            JSONObject o = new JSONObject(U.readFile(new File(c.getFilesDir(), "channels.json")));
            JSONArray a = o.getJSONArray("list");
            List<Model.Channel> l = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) l.add(Model.Channel.from(a.getJSONObject(i)));
            loadedAt = o.optLong("at");
            set(c, l);
        } catch (Exception ignored) {}
    }
    /** Alle Quellen neu laden (im Hintergrund aufrufen). */
    static synchronized void reload(Context c) {
        loading = true;
        try {
            List<Model.Channel> all = new ArrayList<>();
            for (Model.Source s : sources(c)) {
                if (!s.on) continue;
                status = "Lade " + s.name + " …";
                try {
                    List<Model.Channel> l;
                    if (s.type.equals("xtream")) l = Xtream.channels(s);
                    else if (s.type.equals("stalker")) l = Stalker.channels(s);
                    else {
                        InputStream in = s.url.isEmpty() || s.url.startsWith("datei:") ? new FileInputStream(m3uFile(c, s.id)) : U.stream(s.url, null, 60000);
                        M3u.Result r = M3u.parse(new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536), s.id);
                        in.close();
                        l = r.channels;
                        U.prefs(c).edit().putString("epg_auto_" + s.id, r.epg).apply();
                    }
                    if (l.isEmpty()) throw new Exception("keine Sender gefunden");
                    all.addAll(l);
                    errors.remove(s.id);
                } catch (Exception e) {
                    errors.put(s.id, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    // alte Sender dieser Quelle behalten
                    for (Model.Channel ch : channels) if (ch.src.equals(s.id)) all.add(ch);
                }
            }
            loadedAt = System.currentTimeMillis();
            set(c, all);
            JSONArray a = new JSONArray();
            for (Model.Channel ch : all) try { a.put(ch.json()); } catch (Exception ignored) {}
            try { U.writeFile(new File(c.getFilesDir(), "channels.json"), new JSONObject().put("at", loadedAt).put("list", a).toString()); } catch (Exception ignored) {}
            status = all.size() + " Sender";
        } finally { loading = false; }
    }

    static void set(Context c, List<Model.Channel> l) {
        Map<String, String> sn = new HashMap<>();
        for (Model.Source s : sources(c)) if (s.on) sn.put(s.id, s.name);
        srcNames = sn;
        Set<String> hidden = hiddenGroups(c);
        List<Model.Channel> vis = new ArrayList<>();
        LinkedHashSet<String> gs = new LinkedHashSet<>();
        int n = 0;
        for (Model.Channel ch : l) {
            if (hidden.contains(ch.group)) continue;
            vis.add(ch);
            if (!ch.group.isEmpty()) gs.add(ch.group);
        }
        // Senderplaetze: vorhandene Nummern behalten, sonst fortlaufend
        boolean numbered = false;
        for (Model.Channel ch : vis) if (ch.num > 0) { numbered = true; break; }
        if (!numbered) for (Model.Channel ch : vis) ch.num = ++n;
        channels = vis;
        List<String> g = new ArrayList<>();
        g.add(FAV); g.add(ALL); g.addAll(gs); g.add(SETTINGS);
        groups = g;
    }
    static boolean stale() { return System.currentTimeMillis() - loadedAt > 12 * 3600000L; }

    /** Alle Gruppen (auch ausgeblendete) aus dem Cache – fuer die Einrichtungsseite. */
    static List<String[]> allGroups(Context c) {
        Map<String, Integer> count = new LinkedHashMap<>();
        try {
            JSONArray a = new JSONObject(U.readFile(new File(c.getFilesDir(), "channels.json"))).getJSONArray("list");
            for (int i = 0; i < a.length(); i++) { String g = a.getJSONObject(i).optString("g"); count.put(g, count.containsKey(g) ? count.get(g) + 1 : 1); }
        } catch (Exception ignored) {}
        List<String[]> l = new ArrayList<>();
        for (Map.Entry<String, Integer> e : count.entrySet()) l.add(new String[]{e.getKey(), String.valueOf(e.getValue())});
        return l;
    }
    static Set<String> hiddenGroups(Context c) { return new HashSet<>(U.prefs(c).getStringSet("hidden", new HashSet<String>())); }
    static void setHidden(Context c, Set<String> s) {
        U.prefs(c).edit().putStringSet("hidden", new HashSet<>(s)).apply();
        loadCache(c);
    }

    // ------------------------------------------------------------ Auswahl
    static List<Model.Channel> inGroup(Context c, String g) {
        if (g == null || g.equals(ALL)) return channels;
        List<Model.Channel> l = new ArrayList<>();
        if (g.equals(FAV)) {
            List<String> f = favs(c);
            Map<String, Model.Channel> by = new HashMap<>();
            for (Model.Channel ch : channels) by.put(ch.id, ch);
            for (String id : f) if (by.containsKey(id)) l.add(by.get(id));
            return l;
        }
        for (Model.Channel ch : channels) if (g.equals(ch.group)) l.add(ch);
        return l;
    }
    static Model.Channel byId(String id) { for (Model.Channel ch : channels) if (ch.id.equals(id)) return ch; return null; }
    static Model.Channel byNum(int n) { for (Model.Channel ch : channels) if (ch.num == n) return ch; return null; }
    /** Fuer den Launcher: Sender per Name ("ZDF", "Das Erste") oder tvg-id finden; HD bevorzugt. */
    static Model.Channel byName(String name) {
        String n = U.norm(name);
        if (n.isEmpty()) return null;
        Model.Channel best = null; int score = 0;
        for (Model.Channel ch : channels) {
            String cn = U.norm(ch.name), ct = U.norm(ch.tvgId);
            int s = cn.equals(n) || ct.equals(n) ? 100 : ct.startsWith(n) ? 80 : cn.startsWith(n) ? 60 : cn.contains(n) ? 30 : 0;
            if (s > 0 && ch.name.toUpperCase(Locale.ROOT).contains("HD")) s += 5;
            if (s > score) { score = s; best = ch; }
        }
        return best;
    }

    static List<String> favs(Context c) {
        List<String> l = new ArrayList<>();
        try { JSONArray a = new JSONArray(U.prefs(c).getString("favs", "[]")); for (int i = 0; i < a.length(); i++) l.add(a.getString(i)); } catch (Exception ignored) {}
        return l;
    }
    static boolean isFav(Context c, String id) { return favs(c).contains(id); }
    static boolean toggleFav(Context c, String id) {
        List<String> l = favs(c);
        boolean now = !l.remove(id);
        if (now) l.add(id);
        U.prefs(c).edit().putString("favs", new JSONArray(l).toString()).apply();
        return now;
    }
}
