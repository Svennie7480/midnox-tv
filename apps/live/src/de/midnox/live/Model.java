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
        List<String> backups = new ArrayList<>();   // Ausweich-URLs (Playlist bzw. Server), in Reihenfolge
        transient String active;                     // gerade verwendete Basis-URL (url oder ein Backup)
        boolean on = true;
        JSONObject json() throws JSONException {
            JSONArray bk = new JSONArray(); for (String b : backups) bk.put(b);
            return new JSONObject().put("id", id).put("type", type).put("name", name).put("url", url).put("user", user).put("pass", pass)
                .put("mac", mac).put("epg", epg).put("on", on).put("bk", bk);
        }
        static Source from(JSONObject o) {
            Source s = new Source();
            s.id = o.optString("id"); s.type = o.optString("type"); s.name = o.optString("name"); s.url = o.optString("url");
            s.user = o.optString("user"); s.pass = o.optString("pass"); s.mac = o.optString("mac"); s.epg = o.optString("epg"); s.on = o.optBoolean("on", true);
            JSONArray bk = o.optJSONArray("bk"); if (bk != null) for (int i = 0; i < bk.length(); i++) { String b = bk.optString(i).trim(); if (!b.isEmpty()) s.backups.add(b); }
            return s;
        }
        /** Basis-URL + Ausweich-URLs, die zuletzt funktionierende zuerst. */
        List<String> candidates() {
            List<String> l = new ArrayList<>();
            if (active != null && !active.isEmpty()) l.add(active);
            if (url != null && !url.isEmpty() && !l.contains(url)) l.add(url);
            for (String b : backups) { String t = b == null ? "" : b.trim(); if (!t.isEmpty() && !l.contains(t)) l.add(t); }
            return l;
        }
        /** Aktuell zu verwendende Basis-URL (Ausweich, falls aktiv). */
        String activeUrl() { return active != null && !active.isEmpty() ? active : url; }
        String label() {
            return type.equals("stalker") ? "Stalker-Portal" : type.equals("xtream") ? "Xtream Codes" : "M3U-Liste";
        }
    }
}
