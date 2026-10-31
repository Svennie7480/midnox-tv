package de.midnox.tv;

import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.util.Xml;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * "Jetzt im TV": Programmdaten aus einer oeffentlichen XMLTV-Quelle (alle 6 Stunden),
 * zeigt die laufende Sendung je Sender. OK oeffnet MagentaTV.
 */
public class TvRows implements MediaCard.Listener {
    static final String DEFAULT_EPG = "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz";
    static final String TV_APP = "de.telekom.magentatv.firetv";
    static final String[][] CHANNELS = {
        {"Das.Erste.de", "Das Erste"}, {"ZDF.de", "ZDF"}, {"RTL.de", "RTL"}, {"SAT.1.de", "SAT.1"}, {"ProSieben.de", "ProSieben"},
        {"VOX.de", "VOX"}, {"kabel.eins.de", "kabel eins"}, {"RTLZWEI.de", "RTLZWEI"}, {"NDR.de", "NDR"}, {"ZDFneo.de", "ZDFneo"},
        {"ONE.de", "ONE"}, {"3sat.de", "3sat"}, {"ARTE.de", "ARTE"}, {"DMAX.de", "DMAX"}, {"Tele.5.de", "Tele 5"},
        {"sixx.de", "sixx"}, {"NITRO.de", "NITRO"}, {"ProSieben.MAXX.de", "ProSieben MAXX"}, {"SUPER.RTL.de", "Super RTL"},
        {"Comedy.Central.de", "Comedy Central"}, {"RTLup.de", "RTLup"}, {"kabel.eins.Doku.de", "kabel eins Doku"},
        {"ZDFinfo.de", "ZDFinfo"}, {"ntv.de", "n-tv"}, {"WELT.de", "WELT"}, {"tagesschau24.de", "tagesschau24"},
        {"SPORT1.de", "SPORT1"}, {"Eurosport.1.de", "Eurosport 1"}, {"KIKA.de", "KiKA"}, {"Hamburg.1.de", "Hamburg 1"},
    };

    static class Prog { long start, stop; String title, sub = "", cat = "", icon, desc = ""; }

    final HomeActivity act;
    final LinearLayout box, row;
    final TextView cap;
    final HorizontalScrollView sv;
    final Handler ui = new Handler(Looper.getMainLooper());
    final Map<String, Bitmap> imgs = Collections.synchronizedMap(new HashMap<String, Bitmap>());
    volatile Map<String, List<Prog>> data;
    volatile int gen;
    String sig = "";

    TvRows(HomeActivity a) {
        act = a;
        box = EmbyRows.column(a);
        box.setPadding(0, Style.px(6), 0, 0);
        cap = Style.text(a, "JETZT IM TV", 11, Style.MUTED);
        cap.setLetterSpacing(0.3f);
        cap.setPadding(Style.px(64), 0, 0, 0);
        box.addView(cap);
        sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false); sv.setClipToPadding(false);
        sv.setPadding(Style.px(54), Style.px(8), Style.px(54), Style.px(10));
        row = new LinearLayout(a);
        row.setClipChildren(false);
        sv.addView(row);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        box.setVisibility(View.GONE);
    }

    // ---------------- Karten
    @Override public void onFocus(MediaCard card) { act.reveal(card); act.showHero(card.e); }
    @Override public void onClick(MediaCard card) { openTv(); }
    @Override public void onLongClick(MediaCard card) { openTv(); }
    void openTv() {
        Intent i = Style.launchIntent(act.getPackageManager(), TV_APP);
        if (i == null) { Toast.makeText(act, "MagentaTV ist nicht installiert", Toast.LENGTH_SHORT).show(); return; }
        Style.start(act, i);
    }

    void start() {
        final int g = ++gen;
        if (Style.prefs(act).getBoolean("hide_tv", false)) { box.setVisibility(View.GONE); return; }
        new Thread(new Runnable() { public void run() {
            while (g == gen) {
                try { ensureData(); show(); } catch (Exception e) { status("Fehler: " + e); }
                try { Thread.sleep(60000); } catch (InterruptedException e) { return; }
            }
        }}, "tv-now").start();
    }
    void stop() { gen++; }

    File cacheFile() { return new File(act.getFilesDir(), "tvnow.json"); }

    /** Programmdaten laden (Datei-Cache; Quelle hoechstens alle 6 Stunden). */
    void ensureData() throws Exception {
        File f = cacheFile();
        if (data == null && f.exists()) data = readCache(f);
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600 * 1000L && data != null) return;
        JSONObject cfg = Style.loadConfig(act);
        String url = cfg == null ? "" : cfg.optString("epg_url");
        if (url.isEmpty()) url = DEFAULT_EPG;
        Map<String, List<Prog>> d = download(url);
        if (d.isEmpty()) throw new IOException("keine Sendungen");
        writeCache(f, d);
        data = d;
        status("ok: " + d.size() + " Sender");
    }

    Map<String, List<Prog>> download(String url) throws Exception {
        Set<String> want = new HashSet<>();
        for (String[] c : CHANNELS) want.add(c[0]);
        long now = System.currentTimeMillis(), until = now + 30 * 3600 * 1000L;
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT);
        Map<String, List<Prog>> out = new HashMap<>();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "midnox TV");
        InputStream in = new GZIPInputStream(new BufferedInputStream(c.getInputStream(), 65536));
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(in, "UTF-8");
            Prog cur = null; String ch = null, tag = null;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if (tag.equals("programme")) {
                        ch = p.getAttributeValue(null, "channel");
                        cur = null;
                        if (want.contains(ch)) {
                            long st = df.parse(p.getAttributeValue(null, "start")).getTime(), sp = df.parse(p.getAttributeValue(null, "stop")).getTime();
                            if (sp > now && st < until) { cur = new Prog(); cur.start = st; cur.stop = sp; }
                        }
                    } else if (cur != null && tag.equals("icon") && cur.icon == null) { cur.icon = p.getAttributeValue(null, "src"); if (cur.icon != null && cur.icon.startsWith("http://")) cur.icon = "https://" + cur.icon.substring(7); }
                } else if (ev == XmlPullParser.TEXT && cur != null && tag != null) {
                    String t = p.getText();
                    if (t == null || t.trim().isEmpty()) continue;
                    t = t.replace("\\n", "\n").trim();
                    switch (tag) {
                        case "title": if (cur.title == null) cur.title = t; break;
                        case "sub-title": cur.sub = t; break;
                        case "category": if (cur.cat.isEmpty()) cur.cat = t; break;
                        case "desc": cur.desc = t.length() > 600 ? t.substring(0, 600) : t; break;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (p.getName().equals("programme") && cur != null && cur.title != null) {
                        List<Prog> l = out.get(ch);
                        if (l == null) { l = new ArrayList<>(); out.put(ch, l); }
                        l.add(cur);
                        cur = null;
                    }
                    tag = null;
                }
            }
        } finally { in.close(); }
        for (List<Prog> l : out.values()) Collections.sort(l, new Comparator<Prog>() { public int compare(Prog a, Prog b) { return Long.compare(a.start, b.start); } });
        return out;
    }

    void writeCache(File f, Map<String, List<Prog>> d) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, List<Prog>> e : d.entrySet()) {
            JSONArray a = new JSONArray();
            for (Prog p : e.getValue()) a.put(new JSONArray().put(p.start).put(p.stop).put(p.title).put(p.sub).put(p.cat).put(p.icon == null ? "" : p.icon).put(p.desc));
            o.put(e.getKey(), a);
        }
        FileOutputStream w = new FileOutputStream(f);
        w.write(o.toString().getBytes("UTF-8"));
        w.close();
    }
    Map<String, List<Prog>> readCache(File f) {
        try {
            JSONObject o = new JSONObject(Style.read(new FileInputStream(f)));
            Map<String, List<Prog>> d = new HashMap<>();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next(); JSONArray a = o.getJSONArray(k);
                List<Prog> l = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) {
                    JSONArray x = a.getJSONArray(i); Prog p = new Prog();
                    p.start = x.getLong(0); p.stop = x.getLong(1); p.title = x.getString(2); p.sub = x.optString(3); p.cat = x.optString(4);
                    p.icon = x.optString(5).isEmpty() ? null : x.optString(5); p.desc = x.optString(6);
                    l.add(p);
                }
                d.put(k, l);
            }
            return d;
        } catch (Exception e) { return null; }
    }

    /** Laufende Sendungen je Sender als Karten. */
    void show() {
        Map<String, List<Prog>> d = data;
        if (d == null) return;
        long now = System.currentTimeMillis();
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        final List<Emby.Entry> list = new ArrayList<>();
        for (String[] c : CHANNELS) {
            List<Prog> l = d.get(c[0]);
            if (l == null) continue;
            Prog cur = null, next = null;
            for (int i = 0; i < l.size(); i++) if (l.get(i).start <= now && l.get(i).stop > now) { cur = l.get(i); if (i + 1 < l.size()) next = l.get(i + 1); break; }
            if (cur == null) continue;
            Emby.Entry e = new Emby.Entry();
            e.id = c[0] + "@" + cur.start; e.type = "TV";
            e.title = cur.title;
            e.sub = c[1] + "  ·  bis " + hm.format(new Date(cur.stop));
            e.kind = "JETZT IM TV  ·  " + c[1].toUpperCase(Locale.GERMANY);
            List<String> meta = new ArrayList<>();
            meta.add(hm.format(new Date(cur.start)) + "–" + hm.format(new Date(cur.stop)));
            if (!cur.cat.isEmpty()) meta.add(cur.cat);
            if (!cur.sub.isEmpty()) meta.add(cur.sub);
            if (next != null) meta.add("danach " + hm.format(new Date(next.start)) + " " + next.title);
            e.meta = android.text.TextUtils.join("   ·   ", meta);
            e.overview = cur.desc;
            e.progress = 100.0 * (now - cur.start) / Math.max(1, cur.stop - cur.start);
            e.img = cur.icon; e.backdrop = cur.icon;
            e.forceText = true;
            if (cur.icon != null) {
                Bitmap b = imgs.get(cur.icon);
                if (b == null) { b = Emby.fetch(cur.icon); if (b != null) imgs.put(cur.icon, b); }
                e.bmp = b;
            }
            list.add(e);
        }
        // nicht mehr laufende Bilder vergessen
        Set<String> keep = new HashSet<>();
        for (Emby.Entry e : list) if (e.img != null) keep.add(e.img);
        imgs.keySet().retainAll(keep);
        ui.post(new Runnable() { public void run() { set(list); } });
    }

    void set(List<Emby.Entry> list) {
        if (list.isEmpty()) { box.setVisibility(View.GONE); return; }
        box.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        for (Emby.Entry e : list) sb.append(e.id).append(':').append((int) e.progress).append(e.bmp != null ? '+' : '-').append(',');
        if (sb.toString().equals(sig)) return;
        sig = sb.toString();
        View f = row.findFocus();
        int focusIdx = f == null ? -1 : row.indexOfChild(f);
        row.removeAllViews();
        for (Emby.Entry e : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Style.px(168), Style.px(95));
            lp.setMargins(Style.px(8), 0, Style.px(8), 0);
            row.addView(new MediaCard(act, e, sv, this), lp);
        }
        if (focusIdx >= 0 && focusIdx < row.getChildCount()) row.getChildAt(focusIdx).requestFocus();
    }

    void status(String msg) {
        try {
            FileOutputStream o = new FileOutputStream(new File(act.getExternalFilesDir(null), "tv_status.txt"), true);
            o.write((new Date() + "  " + msg + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Exception ignored) {}
    }
}
