package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.io.File;
import java.util.*;

/** Filme und Serien (Video auf Abruf) aus Stalker-Portalen, Xtream-Codes und M3U-Listen. */
final class Vod {
    static final int MOVIE = 0, SERIES = 1;

    static final class Cat { String src, id, name; int kind; boolean stalkerSeries; }
    static final class Item {
        String src, id, name = "", poster = "", year = "", rating = "", plot = "", genre = "", cast = "", director = "", duration = "", cmd = "", url = "", ext = "mp4";
        int kind; boolean series;
        String key() { return src + "|" + id; }
        JSONObject json() throws JSONException {
            return new JSONObject().put("src", src).put("id", id).put("name", name).put("poster", poster).put("year", year).put("rating", rating).put("plot", plot)
                .put("genre", genre).put("cast", cast).put("director", director).put("duration", duration).put("cmd", cmd).put("url", url).put("ext", ext).put("kind", kind).put("series", series);
        }
        static Item from(JSONObject o) {
            Item i = new Item();
            i.src = o.optString("src"); i.id = o.optString("id"); i.name = o.optString("name"); i.poster = o.optString("poster"); i.year = o.optString("year");
            i.rating = o.optString("rating"); i.plot = o.optString("plot"); i.genre = o.optString("genre"); i.cast = o.optString("cast"); i.director = o.optString("director");
            i.duration = o.optString("duration"); i.cmd = o.optString("cmd"); i.url = o.optString("url"); i.ext = o.optString("ext", "mp4"); i.kind = o.optInt("kind"); i.series = o.optBoolean("series");
            return i;
        }
    }
    static final class Episode { int season, num; String title = "", plot = "", image = "", cmd = "", url = "", id = "", duration = ""; String key(Item it) { return it.key() + "|" + season + "|" + num; } }
    static final class Page { final List<Item> items = new ArrayList<>(); boolean more; }

    static String host(String portal) { return portal.replaceAll("^(https?://[^/]+).*$", "$1"); }
    static String abs(Stalker.Session s, String u) {
        if (u == null || u.isEmpty() || u.equals("null")) return "";
        if (u.startsWith("http")) return u;
        if (u.startsWith("/")) return host(s.portal) + u;
        return s.portal + "/" + u;
    }
    static String clean(String s) { return s == null || s.equals("null") || s.equals("0") || s.equals("N/A") ? "" : s.trim(); }

    // ------------------------------------------------------------ Kategorien
    static List<Cat> categories(Context c, int kind) {
        List<Cat> out = new ArrayList<>();
        for (Model.Source s : Lib.sources(c)) {
            if (!s.on) continue;
            out.addAll(catsOf(c, s, kind));
        }
        return out;
    }
    static List<Cat> catsOf(Context c, Model.Source s, int kind) {
        List<Cat> out = new ArrayList<>();
        {
            try {
                if (s.type.equals("xtream")) {
                    String b = Xtream.base(s), q = "?username=" + Xtream.enc(s.user) + "&password=" + Xtream.enc(s.pass);
                    JSONArray a = new JSONArray(U.get(b + "/player_api.php" + q + "&action=" + (kind == MOVIE ? "get_vod_categories" : "get_series_categories")));
                    for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); out.add(cat(s, o.optString("category_id"), o.optString("category_name"), kind, false)); }
                } else if (s.type.equals("stalker")) {
                    Stalker.Session ss = Stalker.session(s, false);
                    if (kind == MOVIE) {
                        JSONArray a = Stalker.call(ss, "type=vod&action=get_categories").optJSONArray("js");
                        for (int i = 0; a != null && i < a.length(); i++) { JSONObject o = a.getJSONObject(i); if (!o.optString("id").equals("*")) out.add(cat(s, o.optString("id"), o.optString("title"), kind, false)); }
                    } else {
                        try {   // neuere Portale haben Serien getrennt
                            JSONArray a = Stalker.call(ss, "type=series&action=get_categories").optJSONArray("js");
                            for (int i = 0; a != null && i < a.length(); i++) { JSONObject o = a.getJSONObject(i); if (!o.optString("id").equals("*")) out.add(cat(s, o.optString("id"), o.optString("title"), kind, true)); }
                        } catch (Exception ignored) {}
                    }
                } else if (kind == MOVIE) {
                    for (String g : m3uGroups(c, s)) out.add(cat(s, g, g, kind, false));
                }
            } catch (Exception ignored) {}
        }
        return out;
    }
    static Cat cat(Model.Source s, String id, String name, int kind, boolean ss) {
        Cat c = new Cat(); c.src = s.id; c.id = id; c.name = name.trim(); c.kind = kind; c.stalkerSeries = ss;
        return c;
    }

    // ------------------------------------------------------------ Inhalte einer Kategorie
    static final Map<String, List<Item>> xtreamCache = Collections.synchronizedMap(new HashMap<String, List<Item>>());

    /** Seite (ab 1) einer Kategorie. */
    static Page items(Context c, Cat cat, int page) throws Exception { return items(c, Lib.source(c, cat.src), cat, page); }
    static Page items(Context c, Model.Source s, Cat cat, int page) throws Exception {
        Page p = new Page();
        if (s == null) return p;
        if (s.type.equals("xtream")) {
            String ck = cat.src + "|" + cat.kind + "|" + cat.id;
            List<Item> all = xtreamCache.get(ck);
            if (all == null) {
                all = new ArrayList<>();
                String b = Xtream.base(s), q = "?username=" + Xtream.enc(s.user) + "&password=" + Xtream.enc(s.pass);
                JSONArray a = new JSONArray(U.get(b + "/player_api.php" + q + "&action=" + (cat.kind == MOVIE ? "get_vod_streams" : "get_series") + "&category_id=" + Xtream.enc(cat.id)));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Item it = new Item(); it.src = s.id; it.kind = cat.kind;
                    if (cat.kind == MOVIE) {
                        it.id = o.optString("stream_id"); it.poster = clean(o.optString("stream_icon")); it.ext = o.optString("container_extension", "mp4");
                        it.url = b + "/movie/" + s.user + "/" + s.pass + "/" + it.id + "." + it.ext;
                    } else {
                        it.id = o.optString("series_id"); it.poster = clean(o.optString("cover")); it.series = true;
                        it.plot = clean(o.optString("plot")); it.genre = clean(o.optString("genre")); it.cast = clean(o.optString("cast"));
                        it.year = clean(o.optString("releaseDate", o.optString("year"))); if (it.year.length() > 4) it.year = it.year.substring(0, 4);
                    }
                    it.name = o.optString("name").trim();
                    it.rating = clean(o.optString("rating"));
                    all.add(it);
                }
                xtreamCache.put(ck, all);
            }
            int from = (page - 1) * 60;
            for (int i = from; i < all.size() && i < from + 60; i++) p.items.add(all.get(i));
            p.more = from + 60 < all.size();
        } else if (s.type.equals("stalker")) {
            Stalker.Session ss = Stalker.session(s, false);
            String type = cat.stalkerSeries ? "series" : "vod";
            JSONObject js = Stalker.call(ss, "type=" + type + "&action=get_ordered_list&category=" + Stalker.enc(cat.id) + "&genre=" + Stalker.enc(cat.id) + "&sortby=added&p=" + page).optJSONObject("js");
            JSONArray a = js == null ? null : js.optJSONArray("data");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item(); it.src = s.id; it.kind = cat.kind;
                it.id = o.optString("id"); it.name = o.optString("name").trim();
                it.poster = abs(ss, clean(o.optString("screenshot_uri", o.optString("cover_big"))));
                it.year = clean(o.optString("year")); if (it.year.length() > 4) it.year = it.year.replaceAll(".*?(\\d{4}).*", "$1");
                it.rating = clean(o.optString("rating_imdb", o.optString("rating_kinopoisk")));
                it.plot = clean(o.optString("description")); it.genre = clean(o.optString("genres_str"));
                it.cast = clean(o.optString("actors")); it.director = clean(o.optString("director"));
                String t = clean(o.optString("time")); it.duration = t.isEmpty() ? "" : t + " Min.";
                it.cmd = o.optString("cmd");
                it.series = cat.stalkerSeries || o.optInt("is_series") == 1 || (o.optJSONArray("series") != null && o.optJSONArray("series").length() > 0);
                if (it.series) it.kind = SERIES;
                p.items.add(it);
            }
            int total = js == null ? 0 : js.optInt("total_items"), per = js == null ? 14 : Math.max(1, js.optInt("max_page_items", 14));
            p.more = page * per < total;
        } else {   // M3U
            List<Item> all = m3uItems(c, s, cat.id);
            int from = (page - 1) * 60;
            for (int i = from; i < all.size() && i < from + 60; i++) p.items.add(all.get(i));
            p.more = from + 60 < all.size();
        }
        return p;
    }

    // ------------------------------------------------------------ Details und Folgen
    /** Zusatzinfos (Xtream-Filme haben in der Liste nur Name/Poster). */
    static void details(Context c, Item it) { details(Lib.source(c, it.src), it); }
    static void details(Model.Source s, Item it) {
        if (s == null || !s.type.equals("xtream") || it.series) return;
        try {
            String b = Xtream.base(s), q = "?username=" + Xtream.enc(s.user) + "&password=" + Xtream.enc(s.pass);
            JSONObject o = new JSONObject(U.get(b + "/player_api.php" + q + "&action=get_vod_info&vod_id=" + it.id));
            JSONObject info = o.optJSONObject("info");
            if (info == null) return;
            if (it.plot.isEmpty()) it.plot = clean(info.optString("plot", info.optString("description")));
            it.genre = clean(info.optString("genre")); it.cast = clean(info.optString("cast", info.optString("actors"))); it.director = clean(info.optString("director"));
            String rd = clean(info.optString("releasedate", info.optString("release_date"))); if (rd.length() >= 4) it.year = rd.substring(0, 4);
            String d = clean(info.optString("duration")); if (!d.isEmpty()) it.duration = d.replaceAll("^0?(\\d+):(\\d\\d):\\d\\d$", "$1 Std. $2 Min.");
            if (it.rating.isEmpty()) it.rating = clean(info.optString("rating"));
            if (it.poster.isEmpty()) it.poster = clean(info.optString("movie_image", info.optString("cover_big")));
        } catch (Exception ignored) {}
    }

    /** Staffeln -> Folgen. */
    static TreeMap<Integer, List<Episode>> episodes(Context c, Item it) throws Exception { return episodes(Lib.source(c, it.src), it); }
    static TreeMap<Integer, List<Episode>> episodes(Model.Source s, Item it) throws Exception {
        TreeMap<Integer, List<Episode>> out = new TreeMap<>();
        if (s == null) return out;
        if (s.type.equals("xtream")) {
            String b = Xtream.base(s), q = "?username=" + Xtream.enc(s.user) + "&password=" + Xtream.enc(s.pass);
            JSONObject o = new JSONObject(U.get(b + "/player_api.php" + q + "&action=get_series_info&series_id=" + it.id));
            JSONObject info = o.optJSONObject("info");
            if (info != null) {
                if (it.plot.isEmpty()) it.plot = clean(info.optString("plot"));
                if (it.genre.isEmpty()) it.genre = clean(info.optString("genre"));
                if (it.cast.isEmpty()) it.cast = clean(info.optString("cast"));
            }
            Object eps = o.opt("episodes");
            List<JSONArray> lists = new ArrayList<>();
            if (eps instanceof JSONObject) { JSONObject e = (JSONObject) eps; for (Iterator<String> k = e.keys(); k.hasNext(); ) lists.add(e.optJSONArray(k.next())); }
            else if (eps instanceof JSONArray) { JSONArray e = (JSONArray) eps; for (int i = 0; i < e.length(); i++) if (e.optJSONArray(i) != null) lists.add(e.optJSONArray(i)); }
            for (JSONArray l : lists) for (int i = 0; l != null && i < l.length(); i++) {
                JSONObject x = l.getJSONObject(i);
                Episode ep = new Episode();
                ep.season = x.optInt("season", 1); ep.num = x.optInt("episode_num", i + 1); ep.id = x.optString("id");
                ep.title = x.optString("title").trim();
                JSONObject ei = x.optJSONObject("info");
                if (ei != null) { ep.plot = clean(ei.optString("plot")); ep.image = clean(ei.optString("movie_image")); ep.duration = clean(ei.optString("duration")); }
                ep.url = b + "/series/" + s.user + "/" + s.pass + "/" + ep.id + "." + x.optString("container_extension", "mp4");
                add(out, ep);
            }
        } else if (s.type.equals("stalker")) {
            Stalker.Session ss = Stalker.session(s, false);
            String type = "vod";
            JSONArray seasons = null;
            for (String t : new String[]{"series", "vod"}) {
                try {
                    JSONObject js = Stalker.call(ss, "type=" + t + "&action=get_ordered_list&movie_id=" + Stalker.enc(it.id) + "&season_id=0&episode_id=0&p=1").optJSONObject("js");
                    seasons = js == null ? null : js.optJSONArray("data");
                    if (seasons != null && seasons.length() > 0) { type = t; break; }
                } catch (Exception ignored) {}
            }
            if (seasons != null) for (int i = 0; i < seasons.length(); i++) {
                JSONObject se = seasons.getJSONObject(i);
                int sn = i + 1;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(se.optString("name"));
                if (m.find()) sn = Integer.parseInt(m.group(1));
                JSONArray nums = se.optJSONArray("series");
                if (nums != null && nums.length() > 0) {
                    for (int k = 0; k < nums.length(); k++) {
                        Episode ep = new Episode(); ep.season = sn; ep.num = nums.optInt(k, k + 1); ep.cmd = se.optString("cmd"); ep.title = "Folge " + ep.num;
                        add(out, ep);
                    }
                } else {   // Folgen einzeln abfragen
                    try {
                        JSONObject js = Stalker.call(ss, "type=" + type + "&action=get_ordered_list&movie_id=" + Stalker.enc(it.id) + "&season_id=" + Stalker.enc(se.optString("id")) + "&episode_id=0&p=1").optJSONObject("js");
                        JSONArray el = js == null ? null : js.optJSONArray("data");
                        for (int k = 0; el != null && k < el.length(); k++) {
                            JSONObject x = el.getJSONObject(k);
                            Episode ep = new Episode(); ep.season = sn; ep.num = x.optInt("series_number", k + 1); ep.cmd = x.optString("cmd"); ep.title = x.optString("name").trim();
                            add(out, ep);
                        }
                    } catch (Exception ignored) {}
                }
            }
            // aeltere Portale: Folgen-Nummern direkt am Film
            if (out.isEmpty()) {
                JSONObject js = Stalker.call(ss, "type=vod&action=get_ordered_list&movie_id=" + Stalker.enc(it.id) + "&p=1").optJSONObject("js");
                JSONArray d = js == null ? null : js.optJSONArray("data");
                JSONArray nums = d != null && d.length() > 0 ? d.getJSONObject(0).optJSONArray("series") : null;
                for (int k = 0; nums != null && k < nums.length(); k++) { Episode ep = new Episode(); ep.season = 1; ep.num = nums.optInt(k); ep.cmd = it.cmd; ep.title = "Folge " + ep.num; add(out, ep); }
            }
        }
        for (List<Episode> l : out.values()) Collections.sort(l, new Comparator<Episode>() { public int compare(Episode a, Episode b) { return a.num - b.num; } });
        return out;
    }
    static void add(TreeMap<Integer, List<Episode>> m, Episode e) { List<Episode> l = m.get(e.season); if (l == null) { l = new ArrayList<>(); m.put(e.season, l); } l.add(e); }

    /** Abspieladresse (Stalker erzeugt sie erst auf Anfrage). */
    static String link(Context c, Item it, Episode ep) throws Exception { return link(Lib.source(c, it.src), it, ep); }
    static String link(Model.Source s, Item it, Episode ep) throws Exception {
        if (ep != null && !ep.url.isEmpty()) return ep.url;
        if (ep == null && !it.url.isEmpty()) return it.url;
        if (s == null) throw new Exception("Quelle fehlt");
        String cmd = ep != null && !ep.cmd.isEmpty() ? ep.cmd : it.cmd;
        Exception last = null;
        int n = Math.max(1, Stalker.macs(s).size()), st = Stalker.activeIndex(s);
        for (int k = 0; k < n; k++) {   // auch hier: naechste MAC, wenn eine nicht will
            try {
                Stalker.Session ss = Stalker.session(s, (st + k) % n, k > 0);
                JSONObject js = Stalker.call(ss, "type=vod&action=create_link&cmd=" + Stalker.enc(cmd) + "&series=" + (ep == null ? "" : String.valueOf(ep.num)) + "&forced_storage=&disable_ad=0&download=0").optJSONObject("js");
                String u = js == null ? "" : Stalker.strip(js.optString("cmd"));
                if (u.startsWith("http")) return u;
                last = new Exception(js == null ? "Portal liefert keine Adresse" : js.optString("error", "Portal liefert keine Adresse"));
            } catch (Exception e) { last = e; }
        }
        throw last;
    }

    // ------------------------------------------------------------ M3U-Filme (beim Laden der Liste gemerkt)
    static File m3uFile(Context c, String src) { return new File(c.getFilesDir(), "vod_" + src + ".json"); }
    static void saveM3u(Context c, String src, List<Model.Channel> vod) {
        JSONArray a = new JSONArray();
        try { for (Model.Channel ch : vod) a.put(new JSONObject().put("n", ch.name).put("g", ch.group).put("l", ch.logo).put("u", ch.url)); U.writeFile(m3uFile(c, src), a.toString()); } catch (Exception ignored) {}
    }
    static List<String> m3uGroups(Context c, Model.Source s) {
        LinkedHashSet<String> g = new LinkedHashSet<>();
        try { JSONArray a = new JSONArray(U.readFile(m3uFile(c, s.id))); for (int i = 0; i < a.length(); i++) g.add(a.getJSONObject(i).optString("g", "Filme")); } catch (Exception ignored) {}
        return new ArrayList<>(g);
    }
    static List<Item> m3uItems(Context c, Model.Source s, String group) {
        List<Item> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(U.readFile(m3uFile(c, s.id)));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("g", "Filme").equals(group)) continue;
                Item it = new Item(); it.src = s.id; it.id = Integer.toHexString(o.optString("u").hashCode()); it.name = o.optString("n"); it.poster = o.optString("l"); it.url = o.optString("u");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{4})\\)").matcher(it.name);
                if (m.find()) it.year = m.group(1);
                l.add(it);
            }
        } catch (Exception ignored) {}
        return l;
    }

    // ------------------------------------------------------------ Weiterschauen
    /** Position je Film/Folge merken (letzte 60). */
    static void savePos(Context c, Item it, Episode ep, long pos, long dur) {
        try {
            JSONArray a = new JSONArray(U.prefs(c).getString("vod_resume", "[]")), n = new JSONArray();
            String k = ep == null ? it.key() : ep.key(it);
            boolean done = dur > 0 && pos > dur - Math.max(120000, dur / 20);   // fast zu Ende = gesehen
            JSONObject e = new JSONObject().put("k", k).put("item", it.json()).put("pos", done ? 0 : pos).put("dur", dur).put("t", System.currentTimeMillis()).put("done", done);
            if (ep != null) e.put("s", ep.season).put("e", ep.num).put("et", ep.title);
            n.put(e);
            for (int i = 0; i < a.length() && n.length() < 60; i++) if (!a.getJSONObject(i).optString("k").equals(k)) n.put(a.get(i));
            U.prefs(c).edit().putString("vod_resume", n.toString()).apply();
        } catch (Exception ignored) {}
    }
    static JSONObject resume(Context c, String key) {
        try { JSONArray a = new JSONArray(U.prefs(c).getString("vod_resume", "[]")); for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).optString("k").equals(key)) return a.getJSONObject(i); } catch (Exception ignored) {}
        return null;
    }
    /** Fuer die Kategorie "Weiterschauen": angefangene Filme/Serien (je Serie nur der letzte Eintrag). */
    static List<Item> continueList(Context c) {
        List<Item> l = new ArrayList<>(); Set<String> seen = new HashSet<>();
        try {
            JSONArray a = new JSONArray(U.prefs(c).getString("vod_resume", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = Item.from(o.getJSONObject("item"));
                if (!seen.add(it.key())) continue;
                if (o.optBoolean("done") && !it.series) continue;
                l.add(it);
            }
        } catch (Exception ignored) {}
        return l;
    }
}
