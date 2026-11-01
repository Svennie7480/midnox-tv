package de.midnox.radio;

import org.json.*;
import java.util.*;

/** Ein Radiosender (aus dem Katalog oder der radio-browser-Suche). */
final class Station {
    String id, name, sub = "", url, logo, nowplaying, group = "";
    int color;
    List<String> tags = new ArrayList<>();
    boolean midnox() { return "midnox".equals(id); }

    static Station from(JSONObject o, String logoBase, String group) {
        Station s = new Station();
        s.id = o.optString("id"); s.name = o.optString("name"); s.sub = o.optString("sub", "");
        s.url = o.optString("url");
        String l = o.optString("logo", "");
        s.logo = l.isEmpty() || l.equals("null") ? null : l.startsWith("http") ? l : logoBase + l;
        s.nowplaying = o.optString("nowplaying", null);
        s.color = U.color(o.optString("color", ""), U.hashColor(s.name));
        JSONArray t = o.optJSONArray("tags");
        for (int i = 0; t != null && i < t.length(); i++) s.tags.add(t.optString(i));
        s.group = o.optString("group", group);
        return s;
    }

    JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("name", name).put("sub", sub).put("url", url).put("logo", logo == null ? "" : logo)
             .put("color", String.format("#%06X", color & 0xFFFFFF)).put("group", group);
            if (nowplaying != null) o.put("nowplaying", nowplaying);
            o.put("tags", new JSONArray(tags));
        } catch (JSONException ignored) {}
        return o;
    }

    /** Untertitel fuer die Karte: Kanalname oder Genres. */
    String caption() {
        if (!sub.isEmpty()) return sub;
        StringBuilder sb = new StringBuilder();
        for (String t : tags) { if (t.isEmpty() || t.length() > 16) continue; if (sb.length() > 0) sb.append(" · "); sb.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1)); if (sb.length() > 22) break; }
        return sb.toString();
    }
}
