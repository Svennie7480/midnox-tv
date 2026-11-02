package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * Suche ueber Filme und Serien aller Quellen. Gleiche Titel verschiedener Anbieter werden zu einem Treffer
 * zusammengefasst; die beste Quelle steht vorne (Qualitaet im Namen, dann Reihenfolge der Quellen), die anderen
 * lassen sich auf der Detailseite waehlen.
 */
final class VodSearch {
    static final class Hit { String key, title, year = "", poster = ""; boolean series; final List<Vod.Item> sources = new ArrayList<>(); Vod.Item best() { return sources.get(0); } }

    // ------------------------------------------------------------ Xtream: Gesamtliste je Quelle (24 h im Dateicache)
    static File idxFile(Context c, String src) { return new File(c.getFilesDir(), "vidx_" + src + ".json"); }
    static List<Vod.Item> xtreamIndex(Context c, Model.Source s) {
        File f = idxFile(c, s.id);
        try {
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * 3600000L) {
                JSONArray a = new JSONArray(U.readFile(f)); List<Vod.Item> l = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) l.add(Vod.Item.from(a.getJSONObject(i)));
                return l;
            }
        } catch (Exception ignored) {}
        List<Vod.Item> l = new ArrayList<>();
        for (int kind = 0; kind < 2; kind++) {
            try {
                Vod.Cat all = Vod.cat(s, "", "", kind, false);   // ohne Kategorie = alles
                Vod.xtreamCache.remove(s.id + "|" + kind + "|");
                Vod.Page pg = Vod.items(c, s, all, 1);
                l.addAll(Vod.xtreamCache.containsKey(s.id + "|" + kind + "|") ? Vod.xtreamCache.remove(s.id + "|" + kind + "|") : pg.items);
            } catch (Exception ignored) {}
        }
        JSONArray a = new JSONArray();
        try { for (Vod.Item it : l) a.put(it.json()); U.writeFile(f, a.toString()); } catch (Exception ignored) {}
        return l;
    }

    // ------------------------------------------------------------ Suche
    static List<Hit> search(final Context c, final String q) {
        final String nq = norm(q);
        if (nq.length() < 2) return new ArrayList<>();
        final List<Vod.Item> found = Collections.synchronizedList(new ArrayList<Vod.Item>());
        final List<Model.Source> srcs = new ArrayList<>();
        for (Model.Source s : Lib.sources(c)) if (s.on) srcs.add(s);
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, Math.min(4, srcs.size())));
        List<Future<?>> fs = new ArrayList<>();
        for (final Model.Source s : srcs) fs.add(ex.submit(new Runnable() { public void run() {
            try {
                if (s.type.equals("xtream")) { for (Vod.Item it : xtreamIndex(c, s)) if (norm(it.name).contains(nq)) found.add(it); }
                else if (s.type.equals("stalker")) found.addAll(stalker(s, q));
                else for (String g : Vod.m3uGroups(c, s)) for (Vod.Item it : Vod.m3uItems(c, s, g)) if (norm(it.name).contains(nq)) found.add(it);
            } catch (Exception ignored) {}
        }}));
        for (Future<?> f : fs) try { f.get(40, TimeUnit.SECONDS); } catch (Exception ignored) {}
        ex.shutdownNow();
        List<Hit> hits = group(c, found, nq);
        save(c, hits);
        return hits;
    }
    static List<Vod.Item> stalker(Model.Source s, String q) throws Exception {
        List<Vod.Item> out = new ArrayList<>();
        Stalker.Session ss = Stalker.session(s, false);
        for (String type : new String[]{"vod", "series"}) {
            for (int p = 1; p <= 2; p++) {
                try {
                    JSONObject js = Stalker.call(ss, "type=" + type + "&action=get_ordered_list&search=" + Stalker.enc(q) + "&sortby=added&p=" + p).optJSONObject("js");
                    JSONArray a = js == null ? null : js.optJSONArray("data");
                    if (a == null || a.length() == 0) break;
                    Vod.Cat cat = Vod.cat(s, "*", "", type.equals("series") ? Vod.SERIES : Vod.MOVIE, type.equals("series"));
                    for (int i = 0; i < a.length(); i++) out.add(Vod.stalkerItem(ss, s, cat, a.getJSONObject(i)));
                    int total = js.optInt("total_items"), per = Math.max(1, js.optInt("max_page_items", 14));
                    if (p * per >= total) break;
                } catch (Exception e) { break; }
            }
        }
        return out;
    }

    /** Titel vereinheitlichen: "DE - Avatar (2009) 4K" -> "avatar". */
    static String clean(String n) {
        return n.replaceAll("^\\s*\\|?[A-Z]{2,3}\\|?\\s*[:|•-]\\s*", "").replaceAll("\\(\\s*\\d{4}\\s*\\)|\\[[^]]*\\]", " ")
            .replaceAll("(?i)\\b(4k|uhd|fhd|hd|sd|hevc|h265|x265|1080p|720p|2160p|multi|dual|german|deutsch|ger|dl|web(-?dl)?|bluray)\\b", " ").replaceAll("\\s+", " ").trim();
    }
    static String norm(String n) { return U.norm(clean(n)); }
    static String year(Vod.Item it) {
        if (!it.year.isEmpty()) return it.year;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{4})\\)").matcher(it.name);
        return m.find() ? m.group(1) : "";
    }
    static int quality(String n) {
        String u = n.toUpperCase(Locale.ROOT);
        return u.matches(".*\\b(4K|UHD|2160P)\\b.*") ? 3 : u.matches(".*\\b(FHD|1080P)\\b.*") ? 2 : u.matches(".*\\b(HD|720P)\\b.*") ? 1 : 0;
    }
    static List<Hit> group(Context c, List<Vod.Item> found, String nq) { return group(prioOrder(c), found, nq); }
    static List<Hit> group(List<String> order, List<Vod.Item> found, String nq) {
        final Map<String, Integer> prio = new HashMap<>();
        int i = 0;
        for (String id : order) prio.put(id, i++);
        Map<String, Hit> by = new LinkedHashMap<>();
        for (Vod.Item it : found) {
            String y = year(it);
            String k = (it.series ? "S:" : "M:") + norm(it.name);
            Hit h = by.get(k + "|" + y);
            if (h == null && !y.isEmpty()) h = by.get(k + "|");               // ohne Jahr beim anderen Anbieter
            if (h == null) {
                if (y.isEmpty()) for (Map.Entry<String, Hit> e : by.entrySet()) if (e.getKey().startsWith(k + "|")) { h = e.getValue(); break; }
            }
            if (h == null) { h = new Hit(); h.key = k + "|" + y; h.title = clean(it.name); h.year = y; h.series = it.series; by.put(h.key, h); }
            boolean dup = false;
            for (Vod.Item o : h.sources) if (o.key().equals(it.key())) dup = true;
            if (!dup) h.sources.add(it);
            if (h.poster.isEmpty()) h.poster = it.poster;
            if (h.year.isEmpty()) h.year = y;
        }
        List<Hit> out = new ArrayList<>(by.values());
        for (Hit h : out) Collections.sort(h.sources, new Comparator<Vod.Item>() { public int compare(Vod.Item a, Vod.Item b) {
            int q = quality(b.name) - quality(a.name);
            if (q != 0) return q;
            Integer pa = prio.get(a.src), pb = prio.get(b.src);
            return (pa == null ? 99 : pa) - (pb == null ? 99 : pb);
        }});
        // Treffer, deren Titel mit dem Suchbegriff beginnt, nach vorn
        final String fq = nq;
        Collections.sort(out, new Comparator<Hit>() { public int compare(Hit a, Hit b) {
            int sa = U.norm(a.title).startsWith(fq) ? 0 : 1, sb = U.norm(b.title).startsWith(fq) ? 0 : 1;
            if (sa != sb) return sa - sb;
            return b.sources.size() - a.sources.size();
        }});
        return out.size() > 120 ? new ArrayList<>(out.subList(0, 120)) : out;
    }
    /** Bevorzugte Reihenfolge der Quellen (einstellbar), sonst Reihenfolge der Einrichtung. */
    static List<String> prioOrder(Context c) {
        List<String> l = new ArrayList<>();
        try { JSONArray a = new JSONArray(U.prefs(c).getString("vod_prio", "[]")); for (int i = 0; i < a.length(); i++) l.add(a.getString(i)); } catch (Exception ignored) {}
        for (Model.Source s : Lib.sources(c)) if (!l.contains(s.id)) l.add(s.id);
        return l;
    }
    /** Wahl auf der Detailseite merken: diese Quelle kuenftig zuerst. */
    static void prefer(Context c, String src) {
        List<String> l = prioOrder(c); l.remove(src); l.add(0, src);
        U.prefs(c).edit().putString("vod_prio", new JSONArray(l).toString()).apply();
    }

    // ------------------------------------------------------------ letzte Treffer (fuer den Sprung aus midnox TV)
    static void save(Context c, List<Hit> hits) {
        try {
            JSONArray a = new JSONArray();
            for (Hit h : hits) {
                JSONArray s = new JSONArray(); for (Vod.Item it : h.sources) s.put(it.json());
                a.put(new JSONObject().put("key", h.key).put("title", h.title).put("year", h.year).put("poster", h.poster).put("series", h.series).put("sources", s));
            }
            U.writeFile(new File(c.getFilesDir(), "vod_last.json"), a.toString());
        } catch (Exception ignored) {}
    }
    static Hit load(Context c, String key) {
        try {
            JSONArray a = new JSONArray(U.readFile(new File(c.getFilesDir(), "vod_last.json")));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("key").equals(key)) continue;
                Hit h = new Hit(); h.key = key; h.title = o.optString("title"); h.year = o.optString("year"); h.poster = o.optString("poster"); h.series = o.optBoolean("series");
                JSONArray s = o.getJSONArray("sources"); for (int k = 0; k < s.length(); k++) h.sources.add(Vod.Item.from(s.getJSONObject(k)));
                return h;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
