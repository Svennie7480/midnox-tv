package de.midnox.radio;

import org.json.*;
import java.net.URLEncoder;
import java.util.*;

/** Live-Suche in radio-browser.info (zehntausende Sender weltweit). */
final class Browser {
    static final String[] SERVERS = {"https://de1.api.radio-browser.info", "https://at1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://de2.api.radio-browser.info"};

    static List<Station> query(String path) throws Exception {
        Exception last = null;
        for (String s : SERVERS) {
            try { return parse(U.get(s + "/json" + path + (path.contains("?") ? "&" : "?") + "hidebroken=true")); }
            catch (Exception e) { last = e; }
        }
        throw last;
    }

    static List<Station> search(String q) throws Exception {
        return query("/stations/search?name=" + URLEncoder.encode(q, "UTF-8") + "&order=votes&reverse=true&limit=120");
    }
    static List<Station> top(String country) throws Exception {
        return query("/stations/search?" + (country == null ? "" : "countrycode=" + country + "&") + "order=votes&reverse=true&limit=150");
    }

    static List<Station> parse(String json) throws JSONException {
        JSONArray a = new JSONArray(json);
        List<Station> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String u = o.optString("url_resolved", o.optString("url")).trim();
            String lu = u.toLowerCase(Locale.ROOT);
            if (u.isEmpty() || lu.contains(".m3u8") || lu.endsWith(".pls") || lu.endsWith(".m3u") || o.optInt("hls") == 1) continue;
            String name = o.optString("name").trim().replaceAll("\\s+", " ");
            String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (key.isEmpty() || !names.add(key)) continue;
            Station s = new Station();
            s.id = o.optString("stationuuid"); s.name = name; s.url = u;
            String f = o.optString("favicon", "");
            s.logo = f.startsWith("http") ? f : null;
            s.color = U.hashColor(name);
            for (String t : o.optString("tags").split(",")) if (!t.trim().isEmpty() && s.tags.size() < 3) s.tags.add(t.trim());
            String cc = o.optString("countrycode");
            if (!cc.isEmpty() && !cc.equals("DE")) s.sub = o.optString("country", cc);
            s.group = "Suche";
            out.add(s);
        }
        return out;
    }
}
