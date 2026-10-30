package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.io.InputStream;
import java.net.*;
import java.util.*;

/** Zugriff auf den Emby-Server + Datenmodell fuer Vorschaukarten. */
public class Emby {
    public static final String FIELDS = "&Fields=Overview,Genres,CommunityRating,OfficialRating,RunTimeTicks,ProductionYear,PrimaryImageAspectRatio,BackdropImageTags,ParentBackdropItemId,ParentBackdropImageTags,ParentThumbItemId,SeriesId,SeasonId,ParentLogoItemId,ParentLogoImageTag";

    /** Ein Film / eine Folge / eine Serie. */
    public static class Entry {
        public String id, linkId, type, title, sub, img, backdrop, kind, meta, overview, seriesId, seasonId;
        public double progress;
        public boolean logo, imgHasTitle, forceText;
        public String logoUrl, heroLogo;
        public Bitmap bmp, logoBmp;
    }

    public final String base, key, user, q;
    public String server;

    Emby(String base, String key, String user, String server) throws Exception {
        this.base = base.replaceAll("/+$", ""); this.key = key; this.user = user; this.server = server;
        this.q = "&api_key=" + URLEncoder.encode(key, "UTF-8");
    }

    /** Aus ha.json; null wenn nicht konfiguriert. */
    public static Emby from(Context c) {
        JSONObject cfg = Style.loadConfig(c);
        JSONObject e = cfg == null ? null : cfg.optJSONObject("emby");
        if (e == null) return null;
        try { return new Emby(e.getString("url"), e.getString("key"), e.getString("user"), e.optString("server", "")); }
        catch (Exception ex) { return null; }
    }

    public JSONObject getObj(String path) throws Exception { return new JSONObject(Style.httpGet(base + path + (path.contains("?") ? "" : "?") + q, null)); }
    public JSONArray getArr(String path) throws Exception { return new JSONArray(Style.httpGet(base + path + (path.contains("?") ? "" : "?") + q, null)); }

    public String serverId() {
        if (server == null || server.isEmpty()) {
            try { server = new JSONObject(Style.httpGet(base + "/emby/System/Info/Public", null)).optString("Id", ""); } catch (Exception ignored) {}
        }
        return server;
    }

    public String img(String itemId, String type, int maxWidth) {
        return base + "/emby/Items/" + itemId + "/Images/" + type + "?maxWidth=" + maxWidth + "&quality=80" + q;
    }

    public static Bitmap fetch(String url) {
        if (url == null) return null;
        try {
            InputStream in = new URL(url).openStream();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) { return null; }
    }

    /** Bild mit Transparenz (fuer Logos). */
    public static Bitmap fetchAlpha(String url) {
        if (url == null) return null;
        try {
            InputStream in = new URL(url).openStream();
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Exception e) { return null; }
    }

    /** Dateinamen wie "Lady.Gaga.My.Songs" in lesbaren Text verwandeln. */
    public static String clean(String t) {
        if (t == null) return null;
        int dots = t.length() - t.replace(".", "").length() + t.length() - t.replace("_", "").length();
        if (dots >= 2 && t.trim().indexOf(' ') < 0) {
            t = t.replaceAll("[._]+", " ").replaceAll("\\s*-\\s*", " \u2013 ").replaceAll("\\s+", " ").trim();
        }
        return t;
    }

    /** JSON-Objekt aus der Emby-API in eine Vorschaukarte umwandeln. */
    public Entry entry(JSONObject o) throws JSONException {
        Entry en = new Entry();
        String id = o.getString("Id");
        en.id = id; en.linkId = id;
        en.type = o.optString("Type");
        en.seriesId = o.optString("SeriesId", null);
        en.seasonId = o.optString("SeasonId", null);
        if ("Episode".equals(en.type)) {
            en.title = o.optString("SeriesName", o.optString("Name"));
            String se = (o.has("ParentIndexNumber") ? "S" + o.optInt("ParentIndexNumber") : "") + (o.has("IndexNumber") ? " E" + o.optInt("IndexNumber") : "");
            en.sub = (se.trim().isEmpty() ? "" : se.trim() + " · ") + o.optString("Name");
            en.kind = "SERIE" + (se.trim().isEmpty() ? "" : "  ·  " + se.trim());
        } else if ("Series".equals(en.type)) {
            en.title = o.optString("Name");
            int cnt = o.optInt("ChildCount", 0);
            en.sub = cnt > 1 ? cnt + " neue Folgen" : "Neue Folge";
            en.kind = "SERIE";
            en.seriesId = id;
        } else {
            en.title = o.optString("Name");
            en.sub = o.has("ProductionYear") ? String.valueOf(o.optInt("ProductionYear")) : "";
            en.kind = "Movie".equals(en.type) ? "FILM" : en.type.toUpperCase(Locale.GERMANY);
        }
        // Metazeile: Jahr · Laufzeit · Bewertung · FSK · Genres
        List<String> meta = new ArrayList<>();
        if (o.has("ProductionYear")) meta.add(String.valueOf(o.optInt("ProductionYear")));
        long ticks = o.optLong("RunTimeTicks", 0);
        if (ticks > 0 && !"Series".equals(en.type)) {
            long min = ticks / 600000000L;
            meta.add(min >= 60 ? (min / 60) + " Std. " + (min % 60) + " Min." : min + " Min.");
        }
        if (o.has("CommunityRating")) meta.add("★ " + String.format(Locale.GERMANY, "%.1f", o.optDouble("CommunityRating")));
        if (o.has("OfficialRating")) meta.add(o.optString("OfficialRating"));
        JSONArray g = o.optJSONArray("Genres");
        if (g != null && g.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, g.length()); i++) { if (i > 0) sb.append(", "); sb.append(g.optString(i)); }
            meta.add(sb.toString());
        }
        en.meta = android.text.TextUtils.join("   ·   ", meta);
        en.title = clean(en.title);
        en.sub = clean(en.sub);
        en.overview = o.optString("Overview", "");
        JSONObject ud = o.optJSONObject("UserData");
        en.progress = ud == null ? 0 : ud.optDouble("PlayedPercentage", 0);

        JSONObject tags = o.optJSONObject("ImageTags");
        JSONArray bd = o.optJSONArray("BackdropImageTags");
        // Vorschaubild (16:9)
        // Thumb-Bilder enthalten meist schon den Titel; dann kein Text/Logo darueber
        if (tags != null && tags.has("Thumb")) { en.img = img(id, "Thumb", 360); en.imgHasTitle = true; }
        else if (o.has("ParentThumbItemId") && !"Episode".equals(en.type)) { en.img = img(o.getString("ParentThumbItemId"), "Thumb", 360); en.imgHasTitle = true; }
        else if (bd != null && bd.length() > 0) en.img = img(id, "Backdrop/0", 360);
        else if (o.has("ParentBackdropItemId")) en.img = img(o.getString("ParentBackdropItemId"), "Backdrop/0", 360);
        else if (tags != null && tags.has("Primary")) en.img = img(id, "Primary", 360);
        // grosses Hintergrundbild
        if (bd != null && bd.length() > 0) en.backdrop = img(id, "Backdrop/0", 1280);
        else if (o.has("ParentBackdropItemId")) en.backdrop = img(o.getString("ParentBackdropItemId"), "Backdrop/0", 1280);
        else if (en.seriesId != null) en.backdrop = img(en.seriesId, "Backdrop/0", 1280);
        else en.backdrop = en.img;
        // Titel-Logo (Schriftzug) fuer Karte und Kopfbereich
        String logoId = null;
        if (tags != null && tags.has("Logo")) logoId = id;
        else if (o.has("ParentLogoItemId")) logoId = o.optString("ParentLogoItemId");
        if (logoId != null) {
            en.logoUrl = base + "/emby/Items/" + logoId + "/Images/Logo?maxWidth=240&maxHeight=90" + q;
            en.heroLogo = base + "/emby/Items/" + logoId + "/Images/Logo?maxWidth=520&maxHeight=160" + q;
        }
        return en;
    }

    public List<Entry> entries(JSONArray arr, int max) throws JSONException {
        List<Entry> list = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; arr != null && i < arr.length() && list.size() < max; i++) {
            JSONObject o = arr.getJSONObject(i);
            if (!ids.add(o.getString("Id"))) continue;
            list.add(entry(o));
        }
        return list;
    }

    /** Direkt-Link in die Emby-App: "items" = Detailseite in Emby, "play" = sofort abspielen. */
    public static boolean open(Context c, String what, String server, String itemId) {
        if (server != null && !server.isEmpty()) {
            try {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("emby://" + what + "/" + server + "/" + itemId));
                i.setPackage("com.mb.android");
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Exception ignored) {}
        }
        android.content.Intent i = Style.launchIntent(c.getPackageManager(), "com.mb.android");
        if (i != null) Style.start(c, i);
        return false;
    }
}
