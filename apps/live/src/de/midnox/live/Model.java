package de.midnox.live;

import org.json.*;
import java.util.*;

/** Sender, Sendung und Quelle. */
final class Model {
    static final class Channel {
        String id, name, group = "", logo = "", url = "", tvgId = "", src = "", cmd = "", ua = "", referer = "";
        int num;
        JSONObject json() throws JSONException {
            return new JSONObject().put("i", id).put("n", name).put("g", group).put("l", logo).put("u", url).put("t", tvgId)
                .put("s", src).put("c", cmd).put("a", ua).put("r", referer).put("#", num);
        }
        static Channel from(JSONObject o) {
            Channel c = new Channel();
            c.id = o.optString("i"); c.name = o.optString("n"); c.group = o.optString("g"); c.logo = o.optString("l"); c.url = o.optString("u");
            c.tvgId = o.optString("t"); c.src = o.optString("s"); c.cmd = o.optString("c"); c.ua = o.optString("a"); c.referer = o.optString("r"); c.num = o.optInt("#");
            return c;
        }
    }
    static final class Prog {
        long start, stop; String title, desc = "";
        Prog(long a, long b, String t) { start = a; stop = b; title = t; }
        boolean now(long t) { return t >= start && t < stop; }
        float progress(long t) { return stop <= start ? 0 : Math.max(0, Math.min(1, (t - start) / (float) (stop - start))); }
    }
    /** type: m3u | xtream | stalker */
    static final class Source {
        String id, type, name, url, user = "", pass = "", mac = "", epg = "";
        boolean on = true;
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("type", type).put("name", name).put("url", url).put("user", user).put("pass", pass)
                .put("mac", mac).put("epg", epg).put("on", on);
        }
        static Source from(JSONObject o) {
            Source s = new Source();
            s.id = o.optString("id"); s.type = o.optString("type"); s.name = o.optString("name"); s.url = o.optString("url");
            s.user = o.optString("user"); s.pass = o.optString("pass"); s.mac = o.optString("mac"); s.epg = o.optString("epg"); s.on = o.optBoolean("on", true);
            return s;
        }
        String label() {
            return type.equals("stalker") ? "Stalker-Portal" : type.equals("xtream") ? "Xtream Codes" : "M3U-Liste";
        }
    }
}
