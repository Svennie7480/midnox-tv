package de.midnox.live;

import org.json.*;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;

/** Stalker-/Ministra-Portale (MAG-Box-Emulation mit MAC-Adresse). */
final class Stalker {
    static final String MAG_UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3";

    static final class Session { String api, portal, mac, token = ""; long since; }
    static final Map<String, Session> sessions = new HashMap<>();
    /** Zuletzt funktionierende MAC je Quelle (Index in macs()). */
    static final Map<String, Integer> active = Collections.synchronizedMap(new HashMap<String, Integer>());

    /** Mehrere MACs je Quelle: eine pro Zeile oder mit Komma/Leerzeichen getrennt. */
    static List<String> macs(Model.Source src) {
        List<String> l = new ArrayList<>();
        for (String m : src.mac.split("[\\s,;]+")) { m = mac(m); if (m.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}") && !l.contains(m)) l.add(m); }
        return l;
    }
    static int activeIndex(Model.Source src) { Integer i = active.get(src.id); int n = macs(src).size(); return i == null || i >= n ? 0 : i; }

    /** Portal-Adresse -> moegliche API-Adressen. */
    static List<String> apis(String url) {
        String u = url.trim();
        if (!u.matches("(?i)^https?://.*")) u = "http://" + u;
        u = u.replaceAll("/+$", "").replaceAll("(?i)/c$", "").replaceAll("(?i)/(portal\\.php|server/load\\.php)$", "");
        List<String> l = new ArrayList<>();
        if (u.toLowerCase(Locale.ROOT).endsWith("/stalker_portal")) { l.add(u + "/server/load.php"); l.add(u.substring(0, u.length() - 15) + "/portal.php"); }
        else { l.add(u + "/portal.php"); l.add(u + "/stalker_portal/server/load.php"); l.add(u + "/server/load.php"); }
        return l;
    }
    static String mac(String m) {
        m = m.trim().toUpperCase(Locale.ROOT).replace("-", ":");
        if (m.matches("[0-9A-F]{12}")) m = m.replaceAll("(..)(?!$)", "$1:");
        return m;
    }
    static Map<String, String> headers(Session s) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", MAG_UA);
        h.put("X-User-Agent", "Model: MAG250; Link: WiFi");
        h.put("Cookie", "mac=" + enc(s.mac) + "; stb_lang=de; timezone=" + enc(TimeZone.getDefault().getID()));
        h.put("Referer", s.portal + "/c/");
        h.put("Accept", "*/*");
        if (!s.token.isEmpty()) h.put("Authorization", "Bearer " + s.token);
        return h;
    }
    static JSONObject call(Session s, String query) throws Exception {
        String body = U.get(s.api + "?" + query + (query.contains("JsHttpRequest") ? "" : "&JsHttpRequest=1-xml"), headers(s));
        int i = body.indexOf('{');
        if (i < 0) throw new Exception(body.length() > 80 ? body.substring(0, 80) : body);
        return new JSONObject(body.substring(i));
    }

    static Session session(Model.Source src, boolean fresh) throws Exception { return session(src, activeIndex(src), fresh); }
    static synchronized Session session(Model.Source src, int macIdx, boolean fresh) throws Exception {
        List<String> ms = macs(src);
        if (ms.isEmpty()) throw new Exception("keine gültige MAC-Adresse");
        String theMac = ms.get(Math.max(0, Math.min(macIdx, ms.size() - 1)));
        String sk = src.id + "#" + theMac;
        Session s = sessions.get(sk);
        if (s != null && !fresh && System.currentTimeMillis() - s.since < 3 * 3600000L) return s;
        Exception last = null;
        for (String api : apis(src.url)) {
            try {
                Session n = new Session();
                n.api = api; n.mac = theMac;
                n.portal = api.replaceAll("/(portal\\.php|server/load\\.php)$", "");
                JSONObject h = call(n, "type=stb&action=handshake&token=&prehash=0");
                String tok = h.optJSONObject("js") == null ? "" : h.getJSONObject("js").optString("token");
                if (tok.isEmpty()) throw new Exception("kein Token");
                n.token = tok;
                String sn = md5(n.mac).substring(0, 13).toUpperCase(Locale.ROOT), dev = sha256(sn).toUpperCase(Locale.ROOT);
                JSONObject p = call(n, "type=stb&action=get_profile&hd=1&ver=ImageDescription%3A%200.2.18-r23-250%3B%20ImageDate%3A%20Thu%20Sep%2013%2011%3A31%3A16%20EEST%202018%3B%20PORTAL%20version%3A%205.6.1%3B%20API%20Version%3A%20JS%20API%20version%3A%20343&num_banks=2&sn=" + sn
                    + "&stb_type=MAG250&client_type=STB&image_version=218&video_out=hdmi&device_id=" + dev + "&device_id2=" + dev + "&signature=&auth_second_step=1&hw_version=1.7-BD-00&not_valid_token=0&api_signature=262&prehash=0");
                JSONObject js = p.optJSONObject("js");
                if (js != null && js.optInt("status", 0) == 2 && js.optString("msg").length() > 0) throw new Exception(js.optString("msg"));
                if (js != null && js.has("block_msg") && js.optString("block_msg").length() > 0) throw new Exception(js.optString("block_msg"));
                n.since = System.currentTimeMillis();
                sessions.put(sk, n);
                return n;
            } catch (Exception e) { last = e; }
        }
        throw new Exception("Portal nicht erreichbar oder MAC nicht freigeschaltet" + (last == null ? "" : " (" + last.getMessage() + ")"));
    }

    /** Senderliste ueber die erste MAC, die funktioniert. */
    static List<Model.Channel> channels(Model.Source src) throws Exception {
        List<String> ms = macs(src);
        Exception last = null;
        int start = activeIndex(src);
        for (int k = 0; k < Math.max(1, ms.size()); k++) {
            int i = (start + k) % Math.max(1, ms.size());
            try { List<Model.Channel> l = channels(src, i); active.put(src.id, i); return l; } catch (Exception e) { last = e; }
        }
        throw last != null ? last : new Exception("keine gültige MAC-Adresse");
    }
    static List<Model.Channel> channels(Model.Source src, int macIdx) throws Exception {
        Session s = session(src, macIdx, true);
        Map<String, String> genres = new HashMap<>();
        try {
            JSONArray g = call(s, "type=itv&action=get_genres").optJSONArray("js");
            for (int i = 0; g != null && i < g.length(); i++) genres.put(g.getJSONObject(i).optString("id"), g.getJSONObject(i).optString("title"));
        } catch (Exception ignored) {}
        JSONObject r = call(s, "type=itv&action=get_all_channels");
        JSONObject js = r.optJSONObject("js");
        JSONArray a = js == null ? r.optJSONArray("js") : js.optJSONArray("data");
        if (a == null) throw new Exception("Portal liefert keine Senderliste");
        List<Model.Channel> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Model.Channel c = new Model.Channel();
            c.src = src.id;
            c.id = src.id + ":" + o.optString("id");
            c.tvgId = "stalker:" + o.optString("id");
            c.name = o.optString("name").trim();
            c.num = o.optInt("number");
            c.cmd = o.optString("cmd");
            String logo = o.optString("logo");
            c.logo = logo.isEmpty() ? "" : logo.startsWith("http") ? logo : s.portal + "/misc/logos/320/" + logo;
            String gn = genres.get(o.optString("tv_genre_id"));
            c.group = gn == null ? "" : gn;
            out.add(c);
        }
        return out;
    }

    /** Abspiel-Adresse fuer einen Sender (Portale erzeugen sie erst beim Abspielen). */
    static String link(Model.Source src, Model.Channel c) throws Exception { return link(src, c, activeIndex(src)); }
    /** Abspiel-Adresse ueber eine bestimmte MAC. */
    static String link(Model.Source src, Model.Channel c, int macIdx) throws Exception {
        String cmd = c.cmd;
        String direct = strip(cmd);
        if (direct.startsWith("http") && !direct.contains("localhost") && !direct.contains("127.0.0.1")) {
            // manche Portale liefern schon die fertige Adresse – trotzdem create_link versuchen (Token)
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Session s = session(src, macIdx, attempt > 0);
                JSONObject r = call(s, "type=itv&action=create_link&cmd=" + enc(cmd) + "&series=&forced_storage=undefined&disable_ad=0&download=0");
                JSONObject js = r.optJSONObject("js");
                String u = js == null ? "" : strip(js.optString("cmd"));
                if (u.startsWith("http") || u.startsWith("rtmp") || u.startsWith("udp")) return u;
            } catch (Exception e) { if (attempt > 0) throw e; }
        }
        if (direct.startsWith("http")) return direct;
        throw new Exception("Portal liefert keine Adresse für diesen Sender");
    }
    static String strip(String cmd) {
        cmd = cmd == null ? "" : cmd.trim();
        int sp = cmd.indexOf(' ');
        if (sp > 0 && !cmd.startsWith("http")) cmd = cmd.substring(sp + 1).trim();
        return cmd;
    }

    /** Programm fuer alle Sender (Portal-EPG): ch_id -> Sendungen. */
    static Map<String, List<Model.Prog>> epg(Model.Source src) throws Exception {
        Session s = session(src, false);
        JSONObject r = call(s, "type=itv&action=get_epg_info&period=24");
        JSONObject js = r.optJSONObject("js");
        JSONObject data = js == null ? null : js.optJSONObject("data");
        Map<String, List<Model.Prog>> out = new HashMap<>();
        if (data == null) return out;
        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONArray l = data.optJSONArray(id);
            List<Model.Prog> ps = new ArrayList<>();
            for (int i = 0; l != null && i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                Model.Prog p = new Model.Prog(o.optLong("start_timestamp") * 1000, o.optLong("stop_timestamp") * 1000, o.optString("name"));
                p.desc = o.optString("descr");
                if (p.stop > p.start) ps.add(p);
            }
            out.put("stalker:" + id, ps);
        }
        return out;
    }
    /** Kurz-EPG fuer einen Sender (wenn das Portal kein Gesamt-EPG liefert). */
    static List<Model.Prog> shortEpg(Model.Source src, Model.Channel c) throws Exception {
        Session s = session(src, false);
        String id = c.id.substring(c.id.indexOf(':') + 1);
        JSONArray a = call(s, "type=itv&action=get_short_epg&ch_id=" + enc(id) + "&size=10").optJSONArray("js");
        List<Model.Prog> ps = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Model.Prog p = new Model.Prog(o.optLong("start_timestamp") * 1000, o.optLong("stop_timestamp") * 1000, o.optString("name"));
            p.desc = o.optString("descr");
            if (p.stop > p.start) ps.add(p);
        }
        return ps;
    }

    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }
    static String md5(String s) { return hash("MD5", s); }
    static String sha256(String s) { return hash("SHA-256", s); }
    static String hash(String alg, String s) {
        try { byte[] h = MessageDigest.getInstance(alg).digest(s.getBytes("UTF-8")); StringBuilder sb = new StringBuilder(); for (byte x : h) sb.append(String.format("%02x", x)); return sb.toString(); }
        catch (Exception e) { return "0000000000000000"; }
    }
}
