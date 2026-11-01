package de.midnox.live;

import org.json.*;
import java.util.*;

/** Xtream-Codes-Server (player_api.php). */
final class Xtream {
    static String base(Model.Source s) {
        String u = s.url.trim().replaceAll("/+$", "");
        if (!u.matches("(?i)^https?://.*")) u = "http://" + u;
        return u.replaceAll("(?i)/(player_api\\.php|get\\.php|c)$", "");
    }
    static List<Model.Channel> channels(Model.Source s) throws Exception {
        String b = base(s), q = "?username=" + enc(s.user) + "&password=" + enc(s.pass);
        JSONObject info = new JSONObject(U.get(b + "/player_api.php" + q));
        JSONObject ui = info.optJSONObject("user_info");
        if (ui != null && ui.optInt("auth", 1) == 0) throw new Exception("Benutzername oder Passwort falsch");
        Map<String, String> cats = new HashMap<>();
        try {
            JSONArray c = new JSONArray(U.get(b + "/player_api.php" + q + "&action=get_live_categories"));
            for (int i = 0; i < c.length(); i++) cats.put(c.getJSONObject(i).optString("category_id"), c.getJSONObject(i).optString("category_name"));
        } catch (Exception ignored) {}
        JSONArray a = new JSONArray(U.get(b + "/player_api.php" + q + "&action=get_live_streams"));
        List<Model.Channel> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Model.Channel c = new Model.Channel();
            c.src = s.id;
            c.id = s.id + ":" + o.optString("stream_id");
            c.name = o.optString("name").trim();
            c.logo = o.optString("stream_icon");
            c.tvgId = o.optString("epg_channel_id");
            c.group = cats.containsKey(o.optString("category_id")) ? cats.get(o.optString("category_id")) : "";
            c.num = o.optInt("num");
            c.url = b + "/live/" + s.user + "/" + s.pass + "/" + o.optString("stream_id") + ".ts";
            out.add(c);
        }
        return out;
    }
    static String epgUrl(Model.Source s) { return base(s) + "/xmltv.php?username=" + enc(s.user) + "&password=" + enc(s.pass); }
    static String enc(String s) { try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }
}
