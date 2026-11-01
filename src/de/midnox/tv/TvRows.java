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
    /** Bekannte TV-Apps: {Paket, Name}. Nur MagentaTV kann direkt auf einen Sender springen. */
    static final String[][] TV_APPS = {
        {"de.telekom.magentatv.firetv", "MagentaTV"}, {"de.exaring.waipu.firetv.live", "waipu.tv"}, {"de.exaring.waipu", "waipu.tv"},
        {"com.zattoo.player", "Zattoo"}, {"com.zattoo.android", "Zattoo"}, {"de.prosiebensat1digital.seventv", "Joyn"},
        {"ar.tvplayer.tv", "TiviMate"}, {"studio.scillarium.ottnavigator", "OTT Navigator"},
    };

    /** Gewaehlte bzw. erste installierte TV-App; null = keine. */
    static String tvApp(Context c) {
        android.content.pm.PackageManager pm = c.getPackageManager();
        String chosen = Style.prefs(c).getString("tv_app", "");
        if (chosen.equals("-")) return null;
        if (!chosen.isEmpty() && Style.launchIntent(pm, chosen) != null) return chosen;
        for (String[] a : TV_APPS) if (Style.launchIntent(pm, a[0]) != null) return a[0];
        return null;
    }
    static String tvAppName(String pkg) { for (String[] a : TV_APPS) if (a[0].equals(pkg)) return a[1]; return pkg; }
    // Senderplaetze laut MagentaTV-Senderliste (gueltig ab 07.10.2026); NDR = Schleswig-Holstein
    static final String[][] CHANNELS = {
        {"Das.Erste.de", "Das Erste", "1"}, {"ZDF.de", "ZDF", "2"}, {"RTL.de", "RTL", "3"}, {"SAT.1.de", "SAT.1", "4"}, {"ProSieben.de", "ProSieben", "5"},
        {"VOX.de", "VOX", "6"}, {"kabel.eins.de", "kabel eins", "7"}, {"RTLZWEI.de", "RTLZWEI", "8"}, {"NDR.de", "NDR", "467"}, {"ZDFneo.de", "ZDFneo", "17"},
        {"ONE.de", "ONE", "58"}, {"3sat.de", "3sat", "12"}, {"ARTE.de", "ARTE", "57"}, {"DMAX.de", "DMAX", "64"}, {"Tele.5.de", "Tele 5", "16"},
        {"sixx.de", "sixx", "75"}, {"NITRO.de", "NITRO", "70"}, {"ProSieben.MAXX.de", "ProSieben MAXX", "69"}, {"SUPER.RTL.de", "Super RTL", "9"},
        {"Comedy.Central.de", "Comedy Central", "61"}, {"RTLup.de", "RTLup", "71"}, {"kabel.eins.Doku.de", "kabel eins Doku", "89"},
        {"ZDFinfo.de", "ZDFinfo", "81"}, {"ntv.de", "n-tv", "14"}, {"WELT.de", "WELT", "13"}, {"tagesschau24.de", "tagesschau24", "82"}, {"PHOENIX.de", "phoenix", "15"},
        {"SPORT1.de", "SPORT1", "56"}, {"Eurosport.1.de", "Eurosport 1", "53"}, {"KIKA.de", "KiKA", "10"}, {"Hamburg.1.de", "Hamburg 1", "0"},
        // nur ueber RTL+ (Platz 0 = in MagentaTV unbekannt)
        {"VOXup.de", "VOXup", "0"}, {"TOGGO.plus.de", "TOGGO plus", "0"},
        {"RTL.Crime.de", "RTL Crime", "0"}, {"RTL.Passion.de", "RTL Passion", "0"}, {"RTL.Living.de", "RTL Living", "0"}, {"GEO.Television.de", "GEO Television", "0"},
    };

    // ------------------------------------------------------------ RTL+ (Livestreams per Direktlink)
    static final String RTLPLUS = "de.cbc.tvnow.firetv";
    static final String[][] RTL_LIVE = {
        {"RTL.de", "rtlde_rtl"}, {"VOX.de", "rtlde_vox"}, {"RTLZWEI.de", "rtlde_rtlzwei"}, {"NITRO.de", "rtlde_nitro"}, {"ntv.de", "rtlde_ntv"},
        {"RTLup.de", "rtlde_rtlup"}, {"VOXup.de", "rtlde_voxup"}, {"SUPER.RTL.de", "rtlde_super_rtl"}, {"TOGGO.plus.de", "rtlde_toggo_plus"},
        {"RTL.Crime.de", "rtlde_rtl_crime"}, {"RTL.Passion.de", "rtlde_rtl_passion"}, {"RTL.Living.de", "rtlde_rtl_living"}, {"GEO.Television.de", "rtlde_geo"},
    };
    static final Set<String> RTL_MAX = new HashSet<>(Arrays.asList("RTL.Crime.de", "RTL.Passion.de", "RTL.Living.de", "GEO.Television.de"));
    static String rtlSlug(String ch) { for (String[] r : RTL_LIVE) if (r[0].equals(ch)) return r[1]; return null; }
    static boolean hasRtl(Context c) { return Style.launchIntent(c.getPackageManager(), RTLPLUS) != null; }
    /** Quelle eines Senders: "magenta", "rtl" oder "hide" (eigene Wahl in der Senderliste, sonst automatisch). */
    static String source(Context c, String ch) {
        String s = Style.prefs(c).getString("tvsrc_" + ch, "");
        boolean rtlOk = rtlSlug(ch) != null && hasRtl(c);
        if (s.equals("rtl") && rtlOk) return "rtl";
        if (s.equals("magenta") || s.equals("hide")) return s;
        if (RTL_MAX.contains(ch) && !Style.prefs(c).getBoolean("tv_rtlmax", false)) return "hide";
        if (rtlOk && Style.prefs(c).getBoolean("tv_rtlplus", true)) return "rtl";
        return number(c, ch) > 0 || ch.equals("Hamburg.1.de") ? "magenta" : "hide";
    }
    static boolean viaRtl(Context c, String ch) { return "rtl".equals(source(c, ch)); }
    static boolean visible(Context c, String ch) { return !"hide".equals(source(c, ch)); }

    /** Sender in der eigenen Reihenfolge (Senderliste in den Einstellungen). */
    static List<String[]> ordered(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Style.prefs(c).getString("tv_order", "[]"));
            for (int i = 0; i < a.length(); i++) for (String[] ch : CHANNELS) if (ch[0].equals(a.optString(i))) { out.add(ch); break; }
        } catch (Exception ignored) {}
        for (String[] ch : CHANNELS) if (!out.contains(ch)) out.add(ch);
        return out;
    }
    static void saveOrder(Context c, List<String[]> l) {
        JSONArray a = new JSONArray();
        for (String[] ch : l) a.put(ch[0]);
        Style.prefs(c).edit().putString("tv_order", a.toString()).apply();
    }

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
    @Override public void onClick(MediaCard card) { openTv(act, card.e.id.substring(0, card.e.id.indexOf('@'))); }
    @Override public void onLongClick(MediaCard card) {   // Programm des Senders, Erinnerungen setzen
        Intent i = new Intent(act, ChannelActivity.class);
        i.putExtra("channel", card.e.id.substring(0, card.e.id.indexOf('@')));
        act.startActivity(i);
    }
    static String name(String channel) { for (String[] c : CHANNELS) if (c[0].equals(channel)) return c[1]; return channel; }

    /** Senderplatz in MagentaTV (eigene Einstellung oder Standard), 0 = unbekannt. */
    static int number(Context c, String channel) {
        for (String[] ch : CHANNELS) if (ch[0].equals(channel)) return Style.prefs(c).getInt("mtv_" + channel, Integer.parseInt(ch[2]));
        return 0;
    }

    /** MagentaTV direkt auf dem Sender starten (eigener Befehl der App), sonst nur die App oeffnen. */
    static void openTv(Context c, String channel) {
        if (viaRtl(c, channel)) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://plus.rtl.de/" + rtlSlug(channel) + "/live"));
                i.setPackage(RTLPLUS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            } catch (Exception e) { Style.start(c, Style.launchIntent(c.getPackageManager(), RTLPLUS)); return; }
        }
        String app = tvApp(c);
        if (app == null) { Style.toast(c, "Keine TV-App installiert (z. B. MagentaTV, waipu.tv, Zattoo)"); return; }
        int nr = number(c, channel);
        if (nr > 0 && app.equals(TV_APP)) {
            try {
                Intent i = new Intent("de.telekom.magentatv.androidtv.action.SWITCH_CHANNEL", android.net.Uri.parse("content://de.telekom.magentatv.androidtv/SWITCH_CHANNEL/"));
                i.setPackage(TV_APP);
                i.putExtra("channelNumber", String.valueOf(nr));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            } catch (Exception ignored) {}
        }
        Style.start(c, Style.launchIntent(c.getPackageManager(), app));
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
    static Map<String, List<Prog>> readCache(File f) {
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
        for (String[] c : ordered(act)) {
            if (!visible(act, c[0])) continue;
            List<Prog> l = d.get(c[0]);
            if (l == null) continue;
            Prog cur = null, next = null;
            for (int i = 0; i < l.size(); i++) if (l.get(i).start <= now && l.get(i).stop > now) { cur = l.get(i); if (i + 1 < l.size()) next = l.get(i + 1); break; }
            if (cur == null) continue;
            Emby.Entry e = new Emby.Entry();
            e.id = c[0] + "@" + cur.start; e.type = "TV";
            e.title = cur.title;
            e.sub = c[1] + "  ·  bis " + hm.format(new Date(cur.stop)) + (viaRtl(act, c[0]) ? "  ·  RTL+" : "");
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
