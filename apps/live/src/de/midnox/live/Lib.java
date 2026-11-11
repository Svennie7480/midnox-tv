package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** Quellen, Senderliste (mit Cache), Gruppen, Favoriten, ausgeblendete Gruppen. */
final class Lib {
    static final String FAV = "★ Favoriten", ALL = "Alle Sender", SETTINGS = "\u2699 Einstellungen", VOD = "\uD83C\uDFAC Filme & Serien", MANAGE = "\uD83D\uDC41 Gruppen ein-/ausblenden";
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
    static Model.Source source(Context c, String id) { for (Model.Source s : sources(c)) if (s.id.equals(id)) { String w = U.prefs(c).getString("work_" + s.id, ""); if (!w.isEmpty()) s.active = w; return s; } return null; }
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
                String work = U.prefs(c).getString("work_" + s.id, "");   // zuletzt funktionierender Ausweich-Server zuerst
                if (!work.isEmpty()) s.active = work;
                try {
                    // Bei Stalker probiert die Sitzung die Ausweich-Server selbst; sonst hier der Reihe nach.
                    boolean local = s.url.isEmpty() || s.url.startsWith("datei:");
                    List<String> cands = (s.type.equals("stalker") || local) ? java.util.Arrays.asList(s.activeUrl()) : s.candidates();
                    List<Model.Channel> l = null; Exception last = null;
                    for (int ci = 0; ci < cands.size(); ci++) {
                        s.active = cands.get(ci);
                        if (cands.size() > 1) status = "Lade " + s.name + (ci > 0 ? " · Ausweich-Server " + (ci + 1) : "") + " …";
                        try {
                            List<Model.Channel> r;
                            if (s.type.equals("xtream")) r = Xtream.channels(s);
                            else if (s.type.equals("stalker")) r = Stalker.channels(s);
                            else {
                                InputStream in = local ? new FileInputStream(m3uFile(c, s.id)) : U.stream(s.active, null, 60000);
                                M3u.Result res = M3u.parse(new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536), s.id);
                                in.close();
                                r = res.channels;
                                Vod.saveM3u(c, s.id, res.vod);
                                U.prefs(c).edit().putString("epg_auto_" + s.id, res.epg).apply();
                            }
                            if (r.isEmpty() && !(s.type.equals("m3u") && Vod.m3uFile(c, s.id).length() > 5)) throw new Exception("keine Sender gefunden");
                            l = r; last = null; break;
                        } catch (Exception e) { last = e; }
                    }
                    if (l == null) throw last != null ? last : new Exception("nicht erreichbar");
                    all.addAll(l);
                    errors.remove(s.id);
                    // Ausweich-Server aktiv? merken, damit der nächste Lauf dort beginnt.
                    if (s.active != null && !s.active.equals(s.url) && !s.active.isEmpty() && !s.active.startsWith("datei:"))
                        U.prefs(c).edit().putString("work_" + s.id, s.active).apply();
                    else U.prefs(c).edit().remove("work_" + s.id).apply();
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
        clearDedup();   // Cache für zusammengefasste Sender verwerfen, Liste hat sich geändert
        List<String> g = new ArrayList<>();
        g.add(FAV); g.add(ALL); g.addAll(ordered(gs, grpOrder(c))); g.add(MANAGE); g.add(SETTINGS);   // Filme & Serien sind jetzt eine eigene Rubrik oben
        groups = g;
    }

    // ------------------------------------------------------------ Reihenfolge der Gruppen (Server-Vorgabe von der Website)
    static List<String> jsonList(Context c, String pref) {
        List<String> l = new ArrayList<>();
        try { JSONArray a = new JSONArray(U.prefs(c).getString(pref, "[]")); for (int i = 0; i < a.length(); i++) l.add(a.getString(i)); } catch (Exception ignored) {}
        return l;
    }
    static List<String> grpOrder(Context c) { return jsonList(c, "grp_order"); }
    static List<String> vodOrder(Context c, int kind) { return jsonList(c, "vod_order_" + kind); }
    /** Elemente nach Vorgabe sortieren: genannte zuerst in dieser Reihenfolge, der Rest hinterher in natürlicher Folge. */
    static List<String> ordered(Collection<String> items, List<String> order) {
        List<String> rest = new ArrayList<>(items), out = new ArrayList<>();
        for (String o : order) if (rest.remove(o)) out.add(o);
        out.addAll(rest);
        return out;
    }
    /** Server-Vorgabe als Delta anwenden: nur Änderungen seit dem letzten Abgleich auf die lokale Auswahl legen (lokale Entscheidung bleibt sonst). */
    static void applyServerHidden(Context c, String effPref, String prevPref, Collection<String> server) {
        Set<String> prev = new HashSet<>(U.prefs(c).getStringSet(prevPref, new HashSet<String>()));
        Set<String> now = new HashSet<>(server);
        Set<String> eff = new HashSet<>(U.prefs(c).getStringSet(effPref, new HashSet<String>()));
        for (String s : now) if (!prev.contains(s)) eff.add(s);      // Server neu ausgeblendet -> ausblenden
        for (String s : prev) if (!now.contains(s)) eff.remove(s);   // Server neu eingeblendet -> einblenden
        U.prefs(c).edit().putStringSet(effPref, eff).putStringSet(prevPref, now).apply();
    }

    /** Gruppen/Kategorien aus den Paket-Quellen für die Website melden (damit Sven sie dort sortieren/ausblenden kann). */
    static JSONObject reportGroups(Context c, Set<String> pkIds) {
        JSONObject out = new JSONObject();
        try {
            LinkedHashSet<String> tv = new LinkedHashSet<>();
            JSONArray a = new JSONObject(U.readFile(new File(c.getFilesDir(), "channels.json"))).optJSONArray("list");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject ch = a.getJSONObject(i);
                String grp = ch.optString("g"); if (grp.isEmpty()) continue;
                if (pkIds.isEmpty() || pkIds.contains(ch.optString("src"))) tv.add(grp);
            }
            out.put("tv", new JSONArray(ordered(tv, grpOrder(c))));
            out.put("mov", catReport(c, Vod.MOVIE, pkIds));
            out.put("ser", catReport(c, Vod.SERIES, pkIds));
        } catch (Exception ignored) {}
        return out;
    }
    static JSONArray catReport(Context c, int kind, Set<String> pkIds) {
        JSONArray arr = new JSONArray();
        try { for (Vod.Cat k : Vod.categories(c, kind)) if (pkIds.isEmpty() || pkIds.contains(k.src)) arr.put(new JSONObject().put("k", vodKey(k)).put("n", k.name)); } catch (Exception ignored) {}
        return arr;
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
    /** Alle Gruppen (inkl. ausgeblendeter) nach Anbieter/Quelle gebündelt – aus channels.json. */
    static LinkedHashMap<String, List<String>> groupsBySource(Context c) {
        LinkedHashMap<String, String> firstSrc = new LinkedHashMap<>();   // Gruppe -> erste gesehene Quelle
        try {
            JSONArray a = new JSONObject(U.readFile(new File(c.getFilesDir(), "channels.json"))).getJSONArray("list");
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); String g = o.optString("g"); if (!firstSrc.containsKey(g)) firstSrc.put(g, o.optString("src")); }
        } catch (Exception ignored) {}
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : firstSrc.entrySet()) {
            if (!out.containsKey(e.getValue())) out.put(e.getValue(), new ArrayList<String>());
            out.get(e.getValue()).add(e.getKey());
        }
        return out;
    }
    static boolean special(String g) { return g.equals(FAV) || g.equals(ALL) || g.equals(VOD) || g.equals(SETTINGS) || g.equals(MANAGE); }
    static void hideGroup(Context c, String g, boolean hide) {
        Set<String> s = hiddenGroups(c);
        if (hide) s.add(g); else s.remove(g);
        setHidden(c, s);
    }
    /** Filme/Serien-Kategorien: Schluessel Quelle|Art|Name (Name statt ID, damit es Portal-Neuladen uebersteht). */
    static String vodKey(Vod.Cat k) { return k.src + "|" + k.kind + "|" + k.name; }
    static Set<String> hiddenVod(Context c) { return new HashSet<>(U.prefs(c).getStringSet("vod_hidden", new HashSet<String>())); }
    static void setHiddenVod(Context c, Set<String> s) { U.prefs(c).edit().putStringSet("vod_hidden", new HashSet<>(s)).apply(); }
    static Set<String> hiddenGroups(Context c) { return new HashSet<>(U.prefs(c).getStringSet("hidden", new HashSet<String>())); }
    static void setHidden(Context c, Set<String> s) {
        U.prefs(c).edit().putStringSet("hidden", new HashSet<>(s)).apply();
        loadCache(c);
    }

    // ------------------------------------------------------------ Auswahl
    /** Treffer der Sendersuche (z. B. aus „Wo läuft das?“ im Launcher) als eigene Gruppe oben in der Liste. */
    static String findGroup; static List<Model.Channel> found = new ArrayList<>();
    static String searchGroup; static List<Model.Channel> searchResult = new ArrayList<>();
    /** Live-Sender nach Name durchsuchen (enthaelt, Treffer am Wortanfang zuerst). */
    static List<Model.Channel> searchChannels(Context c, String q) {
        final String nq = U.norm(q);
        List<Model.Channel> starts = new ArrayList<>(), contains = new ArrayList<>();
        if (nq.isEmpty()) return starts;
        for (Model.Channel ch : channels) { String n = U.norm(ch.name); if (n.startsWith(nq)) starts.add(ch); else if (n.contains(nq)) contains.add(ch); }
        starts.addAll(contains);
        return dedupe(c, starts);
    }
    static List<Model.Channel> find(String[] keys) {
        LinkedHashMap<String, Model.Channel> out = new LinkedHashMap<>();
        for (String k : keys) {
            String nk = U.norm(k); if (nk.isEmpty()) continue;
            List<Model.Channel> exact = new ArrayList<>(), part = new ArrayList<>();
            for (Model.Channel ch : channels) { String n = U.norm(ch.name.replaceAll("(?i)\\b(hd|fhd|uhd|4k|sd|hevc|raw|de|ger|german)\\b|^[a-z]{2,3}\\s*[:|]\\s*", " ")); if (n.equals(nk)) exact.add(ch); else if (n.startsWith(nk) || n.contains(nk)) part.add(ch); }
            Collections.sort(part, new Comparator<Model.Channel>() { public int compare(Model.Channel a, Model.Channel b) { String x = U.norm(a.name), y = U.norm(b.name); return x.length() != y.length() ? x.length() - y.length() : x.compareTo(y); } });
            for (Model.Channel ch : exact) if (!out.containsKey(U.norm(ch.name))) out.put(U.norm(ch.name), ch);
            for (Model.Channel ch : part) if (!out.containsKey(U.norm(ch.name))) out.put(U.norm(ch.name), ch);
        }
        return new ArrayList<>(out.values());
    }
    static List<Model.Channel> inGroup(Context c, String g) {
        if (g != null && g.equals(findGroup)) return found;
        if (g != null && g.equals(searchGroup)) return searchResult;
        if (g == null || g.equals(ALL)) return dedupeCached(c, "\u0000ALL", channels);
        List<Model.Channel> l = new ArrayList<>();
        if (g.equals(FAV)) {   // Favoriten bewusst nicht zusammenfassen – der Nutzer hat sie selbst gewählt
            List<String> f = favs(c);
            Map<String, Model.Channel> by = new HashMap<>();
            for (Model.Channel ch : channels) by.put(ch.id, ch);
            for (String id : f) if (by.containsKey(id)) l.add(by.get(id));
            return l;
        }
        for (Model.Channel ch : channels) if (g.equals(ch.group)) l.add(ch);
        return dedupeCached(c, g, l);
    }
    /** Gleichnamige Sender (aus mehreren Servern/MACs) in der Anzeige auf einen Eintrag zusammenfassen.
     *  Die Senderliste selbst bleibt vollständig – der Failover (MAC→MAC→anderer Server) nutzt weiter alle. */
    static boolean mergeDupes(Context c) { return U.prefs(c).getBoolean("merge_dupes", true); }
    static final Map<String, List<Model.Channel>> dedupCache = new HashMap<>();
    static void clearDedup() { dedupCache.clear(); }
    /** Zusammengefasste Gruppenliste gecached – wird pro Gruppe nur einmal berechnet, nicht bei jedem Zappen. */
    static List<Model.Channel> dedupeCached(Context c, String key, List<Model.Channel> l) {
        if (!mergeDupes(c)) return l;
        List<Model.Channel> hit = dedupCache.get(key);
        if (hit != null) return hit;
        List<Model.Channel> out = dedupe(c, l);
        dedupCache.put(key, out);
        return out;
    }
    static List<Model.Channel> dedupe(Context c, List<Model.Channel> l) {
        if (!mergeDupes(c)) return l;
        List<Model.Channel> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Model.Channel ch : l) { String k = U.norm(ch.name); if (k.isEmpty() || seen.add(k)) out.add(ch); }
        return out;
    }
    static Model.Channel byId(String id) { for (Model.Channel ch : channels) if (ch.id.equals(id)) return ch; return null; }
    static Model.Channel byNum(int n) { for (Model.Channel ch : channels) if (ch.num == n) return ch; return null; }
    /** Fuer den Launcher: Sender per EPG-Kennung ("RTL.de"), direkt oder ueber die EPG-Zuordnung; HD bevorzugt. */
    static Model.Channel byTvg(String tvg) {
        if (tvg == null || tvg.isEmpty()) return null;
        Model.Channel best = null; int score = 0;
        for (Model.Channel ch : channels) {
            int s = 0;
            if (ch.tvgId.equalsIgnoreCase(tvg)) s = 100;
            else { String k = Epg.key(ch); if (k != null && k.equalsIgnoreCase(tvg)) s = 90; }
            if (s > 0 && ch.name.toUpperCase(Locale.ROOT).contains("HD")) s += 5;
            if (s > score) { score = s; best = ch; }
        }
        return best;
    }
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
