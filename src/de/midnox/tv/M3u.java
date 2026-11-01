package de.midnox.tv;

import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Eigene Sender aus einer M3U-Liste: speichern, mit bekannten Sendern verknuepfen, in VLC abspielen. */
public final class M3u {
    static final class Ch { String id, name, logo, url, group, tvg; }
    static List<Ch> cache;

    static File file(Context c) { return new File(c.getFilesDir(), "m3u.json"); }

    static synchronized List<Ch> all(Context c) {
        if (cache != null) return cache;
        List<Ch> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Style.read(new FileInputStream(file(c))));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i); Ch x = new Ch();
                x.id = o.getString("id"); x.name = o.optString("name"); x.logo = o.optString("logo", null); x.url = o.optString("url");
                x.group = o.optString("group"); x.tvg = o.optString("tvg");
                if (x.logo != null && x.logo.isEmpty()) x.logo = null;
                l.add(x);
            }
        } catch (Exception ignored) {}
        return cache = l;
    }
    static Ch get(Context c, String id) { for (Ch x : all(c)) if (x.id.equals(id)) return x; return null; }

    static synchronized void save(Context c, List<Ch> l) throws IOException {
        JSONArray a = new JSONArray();
        try { for (Ch x : l) a.put(new JSONObject().put("id", x.id).put("name", x.name).put("logo", x.logo == null ? "" : x.logo).put("url", x.url).put("group", x.group).put("tvg", x.tvg)); }
        catch (JSONException ignored) {}
        try (FileOutputStream o = new FileOutputStream(file(c))) { o.write(a.toString().getBytes("UTF-8")); }
        cache = l;
    }

    static String attr(String line, String key) {
        Matcher m = Pattern.compile(key + "=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(line);
        return m.find() ? m.group(1).trim() : "";
    }
    static String norm(String n) {
        return n.toLowerCase(Locale.GERMANY).replaceAll("\\b(hd|uhd|fhd|sd|de|ger|germany|deutschland|tv|\\(.*?\\)|\\[.*?\\])\\b", " ").replaceAll("[^a-z0-9]", "");
    }

    /** M3U einlesen. replace = vorhandene eigene Sender ersetzen. Liefert Ergebnis fuer die Webseite. */
    static JSONObject importText(Context c, String text, boolean replace) throws Exception {
        List<Ch> list = replace ? new ArrayList<Ch>() : new ArrayList<>(all(c));
        Set<String> urls = new HashSet<>();
        for (Ch x : list) urls.add(x.url);
        String pending = null; int added = 0, linked = 0;
        SharedPreferences pr = Style.prefs(c);
        SharedPreferences.Editor ed = pr.edit();
        Map<String, String> builtin = new HashMap<>();
        for (String[] ch : TvRows.CHANNELS) { builtin.put(norm(ch[1]), ch[0]); builtin.put(ch[0].toLowerCase(Locale.ROOT), ch[0]); }
        boolean many;
        List<Ch> fresh = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) { pending = line; continue; }
            if (line.startsWith("#")) continue;
            if (pending == null || urls.contains(line) || !line.matches("(?i)(https?|rtmp|rtsp|udp|rtp)://.+")) { pending = null; continue; }
            Ch x = new Ch();
            x.url = line;
            int comma = pending.lastIndexOf(',');
            x.name = comma >= 0 ? pending.substring(comma + 1).trim() : "";
            String tn = attr(pending, "tvg-name");
            if (x.name.isEmpty()) x.name = tn.isEmpty() ? "Sender" : tn;
            x.logo = attr(pending, "tvg-logo"); if (x.logo.isEmpty()) x.logo = null;
            x.group = attr(pending, "group-title");
            x.tvg = attr(pending, "tvg-id");
            x.id = "m3u:" + Integer.toHexString((x.url + x.name).hashCode());
            urls.add(line); pending = null;
            fresh.add(x);
        }
        many = fresh.size() + list.size() > 40;
        for (Ch x : fresh) {
            // bekannter Sender? -> Stream als zusaetzliche Quelle hinterlegen
            String b = builtin.get(x.tvg.toLowerCase(Locale.ROOT));
            if (b == null) b = builtin.get(norm(x.name));
            if (b != null) {
                if (pr.getString("tvurl_" + b, "").isEmpty()) { ed.putString("tvurl_" + b, x.url); linked++; }
                ed.putString("tvsrc_" + x.id, "hide");   // Doppel vermeiden
            } else if (many) ed.putString("tvsrc_" + x.id, "hide");   // grosse Listen: erst in der Senderliste einschalten
            list.add(x); added++;
        }
        ed.apply();
        save(c, list);
        return new JSONObject().put("ok", true).put("added", added).put("linked", linked).put("total", list.size()).put("hiddenByDefault", many);
    }

    static void clear(Context c) throws IOException {
        SharedPreferences.Editor ed = Style.prefs(c).edit();
        for (Ch x : all(c)) ed.remove("tvsrc_" + x.id);
        for (String[] ch : TvRows.CHANNELS) ed.remove("tvurl_" + ch[0]);
        ed.apply();
        save(c, new ArrayList<Ch>());
    }

    /** Stream in VLC (oder einem anderen Videoplayer) abspielen. */
    static void play(Context c, String url, String title) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(url), "video/*");
        i.putExtra("title", title);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Style.launchIntent(c.getPackageManager(), "org.videolan.vlc") != null) {
            i.setPackage("org.videolan.vlc");
            i.setComponent(new ComponentName("org.videolan.vlc", "org.videolan.vlc.gui.video.VideoPlayerActivity"));
        }
        try { c.startActivity(i); }
        catch (Exception e) {
            i.setComponent(null);
            try { c.startActivity(i); }
            catch (Exception e2) {
                i.setPackage(null);
                try { c.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception e3) { Style.toast(c, "Kein Videoplayer gefunden – VLC aus dem midnox Store installieren"); }
            }
        }
    }
}
