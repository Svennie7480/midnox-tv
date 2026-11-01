package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.io.File;
import java.util.*;

/** Ausweich-Logos: freie Sammlung tv-logos (DE/AT/CH), Verzeichnis liegt im midnox-Repo; dazu Logos aus dem EPG. */
final class Logos {
    static final String INDEX = "https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/live/logos.json";
    static volatile Map<String, String> idx = new HashMap<>();
    static volatile String base = "";

    static void load(final Context c) {
        final File f = new File(c.getFilesDir(), "logos.json");
        try { parse(U.readFile(f)); } catch (Exception ignored) {}
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7 * 86400000L) new Thread(new Runnable() { public void run() {
            try { String s = U.get(INDEX); parse(s); U.writeFile(f, s); } catch (Exception ignored) {}
        }}).start();
    }
    static void parse(String s) throws JSONException {
        JSONObject o = new JSONObject(s);
        JSONObject l = o.getJSONObject("logos");
        Map<String, String> m = new HashMap<>();
        for (Iterator<String> it = l.keys(); it.hasNext(); ) { String k = it.next(); m.put(k, l.getString(k)); }
        base = o.optString("base"); idx = m;
    }
    /** Logo nach Sendername bzw. tvg-id ("ZDF.de" -> zdf). */
    static String byName(Model.Channel ch) {
        String u = idx.get(U.norm(ch.name));
        if (u == null && !ch.tvgId.isEmpty() && !ch.tvgId.startsWith("stalker:")) u = idx.get(U.norm(ch.tvgId.replaceAll("(?i)\\.(de|at|ch)(@.*)?$", "").replace('.', ' ')));
        if (u == null) { String k = U.norm(ch.name).replaceAll("\\d+$", ""); if (k.length() >= 4) u = idx.get(k); }   // "Sky Sport Bundesliga 7" -> Sky Sport Bundesliga
        return u == null ? null : base + u;
    }
    /** Reihenfolge: Anbieter-Logo, EPG-Logo, freie Sammlung. */
    static List<String> candidates(Model.Channel ch) {
        List<String> l = new ArrayList<>();
        for (String s : ch.logo.split("\\|")) if (!s.trim().isEmpty()) l.add(s.trim());
        String e = Epg.icon(ch); if (e != null) l.add(e);
        String n = byName(ch); if (n != null) l.add(n);
        return l;
    }
}
