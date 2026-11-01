package de.midnox.live;

import android.content.Context;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Programmfuehrer: XMLTV (aus der M3U-Kopfzeile, Xtream oder eigener Adresse, auch .gz) und Portal-EPG (Stalker).
 * Zuordnung ueber tvg-id, sonst ueber den Sendernamen. Gespeichert wird nur das Fenster -3 h bis +30 h.
 */
final class Epg {
    static final String DEFAULT_EXTRA = "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz";
    static volatile Map<String, List<Model.Prog>> progs = new HashMap<>();   // EPG-Kanal-ID -> Sendungen
    static volatile Map<String, String> names = new HashMap<>();             // normierter Name -> EPG-Kanal-ID
    static volatile Map<String, String> icons = new HashMap<>();             // EPG-Kanal-ID -> Logo
    static final Map<String, String> resolved = Collections.synchronizedMap(new HashMap<String, String>());   // Sender-ID -> EPG-ID
    static final Map<String, List<Model.Prog>> shortCache = Collections.synchronizedMap(new HashMap<String, List<Model.Prog>>());
    static volatile long loadedAt;
    static Map<String, String> curIcons;
    static volatile boolean loading;
    static volatile String status = "";

    // ------------------------------------------------------------ Abfrage
    static String key(Model.Channel c) {
        String k = resolved.get(c.id);
        if (k != null) return k.isEmpty() ? null : k;
        Map<String, List<Model.Prog>> p = progs;
        String found = null;
        if (!c.tvgId.isEmpty()) {
            if (p.containsKey(c.tvgId)) found = c.tvgId;
            else { String lc = c.tvgId.toLowerCase(Locale.ROOT); for (String x : p.keySet()) if (x.toLowerCase(Locale.ROOT).equals(lc)) { found = x; break; } }
        }
        if (found == null) { String n = U.norm(c.name); if (!n.isEmpty()) found = names.get(n); }
        if (found == null && !c.tvgId.isEmpty()) found = names.get(U.norm(c.tvgId));
        resolved.put(c.id, found == null ? "" : found);
        return found;
    }
    static List<Model.Prog> list(Model.Channel c) {
        String k = key(c);
        List<Model.Prog> l = k == null ? null : progs.get(k);
        if (l == null) l = shortCache.get(c.id);
        return l == null ? Collections.<Model.Prog>emptyList() : l;
    }
    static String icon(Model.Channel c) { String k = key(c); return k == null ? null : icons.get(k); }
    static Model.Prog at(Model.Channel c, long t) { for (Model.Prog p : list(c)) if (p.now(t)) return p; return null; }
    static Model.Prog now(Model.Channel c) { return at(c, System.currentTimeMillis()); }
    static Model.Prog next(Model.Channel c) {
        long t = System.currentTimeMillis();
        for (Model.Prog p : list(c)) if (p.start >= t) return p;
        return null;
    }

    // ------------------------------------------------------------ Laden
    static void loadCache(Context c) {
        try {
            JSONObject o = new JSONObject(U.readFile(new File(c.getFilesDir(), "epg.json")));
            Map<String, List<Model.Prog>> m = new HashMap<>();
            JSONObject pm = o.getJSONObject("p");
            for (Iterator<String> it = pm.keys(); it.hasNext(); ) {
                String k = it.next(); JSONArray a = pm.getJSONArray(k);
                List<Model.Prog> l = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) { JSONArray x = a.getJSONArray(i); Model.Prog p = new Model.Prog(x.getLong(0), x.getLong(1), x.getString(2)); p.desc = x.optString(3); l.add(p); }
                m.put(k, l);
            }
            Map<String, String> n = new HashMap<>();
            JSONObject nm = o.getJSONObject("n");
            for (Iterator<String> it = nm.keys(); it.hasNext(); ) { String k = it.next(); n.put(k, nm.getString(k)); }
            Map<String, String> ic = new HashMap<>();
            JSONObject im = o.optJSONObject("i");
            if (im != null) for (Iterator<String> it = im.keys(); it.hasNext(); ) { String k = it.next(); ic.put(k, im.getString(k)); }
            progs = m; names = n; icons = ic; loadedAt = o.optLong("at"); resolved.clear();
        } catch (Exception ignored) {}
    }
    static boolean stale() { return System.currentTimeMillis() - loadedAt > 12 * 3600000L; }

    static List<String> urls(Context c) {
        LinkedHashSet<String> u = new LinkedHashSet<>();
        for (Model.Source s : Lib.sources(c)) {
            if (!s.on) continue;
            if (!s.epg.trim().isEmpty()) { u.add(s.epg.trim()); continue; }
            if (s.type.equals("xtream")) u.add(Xtream.epgUrl(s));
            else if (s.type.equals("m3u")) { String a = U.prefs(c).getString("epg_auto_" + s.id, ""); if (!a.isEmpty()) u.add(a); }
        }
        String extra = U.prefs(c).getString("epg_extra", DEFAULT_EXTRA).trim();
        if (!extra.isEmpty()) for (String x : extra.split("[\\s,]+")) if (!x.isEmpty()) u.add(x);
        return new ArrayList<>(u);
    }

    static synchronized void reload(Context c) {
        loading = true;
        try {
            List<Model.Channel> chans = Lib.channels;
            // Was wird gebraucht? tvg-ids und Namen der Sender
            Set<String> wantIds = new HashSet<>(), wantNames = new HashSet<>();
            for (Model.Channel ch : chans) { if (!ch.tvgId.isEmpty()) wantIds.add(ch.tvgId.toLowerCase(Locale.ROOT)); wantNames.add(U.norm(ch.name)); if (!ch.tvgId.isEmpty()) wantNames.add(U.norm(ch.tvgId)); }
            Map<String, List<Model.Prog>> m = new HashMap<>();
            Map<String, String> n = new HashMap<>();
            curIcons = new HashMap<>();
            long from = System.currentTimeMillis() - 3 * 3600000L, to = System.currentTimeMillis() + 30 * 3600000L;
            for (String url : urls(c)) {
                status = "Lade Programm: " + shortUrl(url);
                try (InputStream in = U.stream(url, null, 120000)) { parse(in, wantIds, wantNames, from, to, m, n); }
                catch (Exception e) { status = "EPG-Fehler: " + e.getMessage(); }
            }
            for (Model.Source s : Lib.sources(c)) if (s.on && s.type.equals("stalker")) {
                try { m.putAll(Stalker.epg(s)); } catch (Exception ignored) {}
            }
            for (List<Model.Prog> l : m.values()) Collections.sort(l, new Comparator<Model.Prog>() { public int compare(Model.Prog a, Model.Prog b) { return Long.compare(a.start, b.start); } });
            progs = m; names = n; icons = curIcons; loadedAt = System.currentTimeMillis(); resolved.clear();
            save(c);
            status = m.size() + " Sender mit Programm";
        } finally { loading = false; }
    }
    static String shortUrl(String u) { return u.replaceAll("^https?://", "").replaceAll("\\?.*", "").replaceAll("(.{40}).+", "$1…"); }

    static void save(Context c) {
        try {
            JSONObject pm = new JSONObject();
            for (Map.Entry<String, List<Model.Prog>> e : progs.entrySet()) {
                JSONArray a = new JSONArray();
                for (Model.Prog p : e.getValue()) a.put(new JSONArray().put(p.start).put(p.stop).put(p.title).put(p.desc));
                pm.put(e.getKey(), a);
            }
            U.writeFile(new File(c.getFilesDir(), "epg.json"), new JSONObject().put("at", loadedAt).put("p", pm).put("n", new JSONObject(names)).put("i", new JSONObject(icons)).toString());
        } catch (Exception ignored) {}
    }

    /** XMLTV streamend lesen; nur benoetigte Sender und das Zeitfenster behalten. */
    static void parse(InputStream in, Set<String> wantIds, Set<String> wantNames, long from, long to, Map<String, List<Model.Prog>> out, Map<String, String> nameMap) throws Exception {
        XmlPullParser x = android.util.Xml.newPullParser();
        x.setInput(in, null);
        parse(x, wantIds, wantNames, from, to, out, nameMap);
    }
    static void parse(XmlPullParser x, Set<String> wantIds, Set<String> wantNames, long from, long to, Map<String, List<Model.Prog>> out, Map<String, String> nameMap) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT);
        Set<String> keep = new HashSet<>();
        String chId = null, pCh = null, title = null, desc = null; long ps = 0, pe = 0;
        String tag = null;
        int ev;
        while ((ev = x.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                tag = x.getName();
                if (tag.equals("channel")) {
                    chId = x.getAttributeValue(null, "id");
                    if (chId != null && wantIds.contains(chId.toLowerCase(Locale.ROOT))) keep.add(chId);
                } else if (tag.equals("icon") && chId != null && pCh == null) {
                    String src = x.getAttributeValue(null, "src");
                    if (src != null && src.startsWith("http") && curIcons != null) curIcons.put(chId, src);
                } else if (tag.equals("display-name") && chId != null) {
                    String nm = U.norm(x.nextText());
                    if (wantNames.contains(nm)) { keep.add(chId); if (!nameMap.containsKey(nm)) nameMap.put(nm, chId); }
                    tag = null;
                } else if (tag.equals("programme")) {
                    pCh = x.getAttributeValue(null, "channel");
                    title = null; desc = null;
                    if (pCh == null || !keep.contains(pCh)) { pCh = null; skip(x); continue; }
                    ps = time(f, x.getAttributeValue(null, "start")); pe = time(f, x.getAttributeValue(null, "stop"));
                    if (pe < from || ps > to) { pCh = null; skip(x); continue; }
                } else if (pCh != null && tag.equals("title") && title == null) { title = x.nextText(); tag = null; }
                else if (pCh != null && tag.equals("desc") && desc == null) { desc = x.nextText(); tag = null; }
            } else if (ev == XmlPullParser.END_TAG) {
                String t = x.getName();
                if (t.equals("channel")) chId = null;
                else if (t.equals("programme") && pCh != null && title != null && pe > ps) {
                    List<Model.Prog> l = out.get(pCh);
                    if (l == null) { l = new ArrayList<>(); out.put(pCh, l); }
                    Model.Prog p = new Model.Prog(ps, pe, title.trim());
                    if (desc != null) p.desc = desc.length() > 500 ? desc.substring(0, 497) + "…" : desc.trim();
                    l.add(p);
                    pCh = null;
                }
            }
        }
    }
    static void skip(XmlPullParser x) throws Exception {
        int depth = 1;
        while (depth > 0) { int e = x.next(); if (e == XmlPullParser.START_TAG) depth++; else if (e == XmlPullParser.END_TAG) depth--; else if (e == XmlPullParser.END_DOCUMENT) return; }
    }
    static long time(SimpleDateFormat f, String s) {
        if (s == null) return 0;
        s = s.trim();
        try { if (s.length() >= 20) return f.parse(s.substring(0, 14) + " " + s.substring(s.length() - 5)).getTime(); } catch (Exception ignored) {}
        try { SimpleDateFormat g = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT); g.setTimeZone(TimeZone.getTimeZone("UTC")); return g.parse(s.substring(0, 14)).getTime(); } catch (Exception e) { return 0; }
    }

    /** Portal ohne Gesamt-EPG: Kurz-EPG fuer einen Sender nachladen. */
    static void ensureShort(Context c, final Model.Channel ch, final Runnable done) {
        if (!ch.tvgId.startsWith("stalker:") || !list(ch).isEmpty()) return;
        final Model.Source s = Lib.source(c, ch.src);
        if (s == null) return;
        new Thread(new Runnable() { public void run() {
            try { shortCache.put(ch.id, Stalker.shortEpg(s, ch)); if (done != null) Img.ui.post(done); } catch (Exception ignored) {}
        }}).start();
    }
}
