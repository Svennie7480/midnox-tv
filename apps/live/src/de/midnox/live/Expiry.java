package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * Ablaufdatum der Zugaenge: Xtream (user_info.exp_date), Stalker je MAC (get_profile / get_main_info),
 * M3U-Adressen im Xtream-Format. Ergebnis wird 12 Stunden zwischengespeichert.
 */
final class Expiry {
    static final long UNLIMITED = -1, UNKNOWN = 0;
    static final class Entry { String src, name, label, status = ""; long exp = UNKNOWN; int maxCons; boolean ok = true;
        JSONObject json() throws JSONException { return new JSONObject().put("src", src).put("name", name).put("label", label).put("exp", exp).put("status", status).put("max", maxCons).put("ok", ok); }
        static Entry from(JSONObject o) { Entry e = new Entry(); e.src = o.optString("src"); e.name = o.optString("name"); e.label = o.optString("label"); e.exp = o.optLong("exp"); e.status = o.optString("status"); e.maxCons = o.optInt("max"); e.ok = o.optBoolean("ok", true); return e; }
        long days() { return exp <= 0 ? Long.MAX_VALUE : (exp - System.currentTimeMillis()) / 86400000L; }
        String text() {
            if (!ok) return status.isEmpty() ? "nicht erreichbar" : status;
            if (exp == UNLIMITED) return "unbegrenzt";
            if (exp == UNKNOWN) return "Ablaufdatum unbekannt";
            long d = days();
            String date = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(new Date(exp));
            return d < 0 ? "abgelaufen am " + date : "bis " + date + " (noch " + d + (d == 1 ? " Tag)" : " Tage)");
        }
        boolean warn() { return !ok || (exp > 0 && days() <= 14); }
    }

    static List<Entry> cached(Context c) {
        List<Entry> l = new ArrayList<>();
        try { JSONArray a = new JSONObject(U.prefs(c).getString("expiry", "{}")).optJSONArray("list"); for (int i = 0; a != null && i < a.length(); i++) l.add(Entry.from(a.getJSONObject(i))); } catch (Exception ignored) {}
        return l;
    }
    static boolean due(Context c) {
        try { return System.currentTimeMillis() - new JSONObject(U.prefs(c).getString("expiry", "{}")).optLong("at") > 12 * 3600000L; } catch (Exception e) { return true; }
    }
    /** Alle Quellen pruefen (im Hintergrund aufrufen). */
    static List<Entry> refresh(Context c) {
        List<Entry> out = new ArrayList<>();
        for (Model.Source s : Lib.sources(c)) {
            if (!s.on) continue;
            try {
                if (s.type.equals("xtream")) out.add(xtream(s, s.url, s.user, s.pass, s.name));
                else if (s.type.equals("stalker")) out.addAll(stalker(s));
                else {   // M3U im Xtream-Format: .../get.php?username=..&password=..
                    Matcher m = Pattern.compile("^(https?://[^/]+(?:/[^?]*)?)/get\\.php\\?.*username=([^&]+).*password=([^&]+)").matcher(s.url);
                    if (m.find()) out.add(xtream(s, m.group(1), java.net.URLDecoder.decode(m.group(2), "UTF-8"), java.net.URLDecoder.decode(m.group(3), "UTF-8"), s.name));
                }
            } catch (Exception e) { Entry x = new Entry(); x.src = s.id; x.name = s.name; x.label = ""; x.ok = false; x.status = "nicht erreichbar"; out.add(x); }
        }
        try {
            JSONArray a = new JSONArray(); for (Entry e : out) a.put(e.json());
            U.prefs(c).edit().putString("expiry", new JSONObject().put("at", System.currentTimeMillis()).put("list", a).toString()).apply();
        } catch (Exception ignored) {}
        return out;
    }
    static Entry xtream(Model.Source s, String url, String user, String pass, String name) throws Exception {
        Model.Source t = new Model.Source(); t.url = url; t.user = user; t.pass = pass;
        String b = Xtream.base(t);
        JSONObject ui = new JSONObject(U.get(b + "/player_api.php?username=" + Xtream.enc(user) + "&password=" + Xtream.enc(pass))).optJSONObject("user_info");
        Entry e = new Entry(); e.src = s.id; e.name = name; e.label = user;
        if (ui == null) { e.ok = false; e.status = "keine Kontodaten"; return e; }
        if (ui.optInt("auth", 1) == 0) { e.ok = false; e.status = "Zugangsdaten falsch"; return e; }
        String st = ui.optString("status");
        if (!st.isEmpty() && !st.equalsIgnoreCase("Active")) { e.ok = false; e.status = st.equalsIgnoreCase("Expired") ? "abgelaufen" : st.equalsIgnoreCase("Banned") ? "gesperrt" : st; }
        String ex = ui.optString("exp_date", "");
        e.exp = ex.isEmpty() || ex.equals("null") || ex.equals("0") ? UNLIMITED : parse(ex);
        e.maxCons = ui.optInt("max_connections");
        if (e.exp > 0 && e.exp < System.currentTimeMillis()) { e.ok = false; e.status = "abgelaufen"; }
        return e;
    }
    static List<Entry> stalker(Model.Source s) {
        List<Entry> out = new ArrayList<>();
        List<String> macs = Stalker.macs(s);
        for (int i = 0; i < macs.size(); i++) {
            Entry e = new Entry(); e.src = s.id; e.name = s.name; e.label = macs.get(i);
            try {
                Stalker.Session ss = Stalker.session(s, i, false);
                long exp = UNKNOWN;
                try { exp = fromJs(Stalker.call(ss, "type=account_info&action=get_main_info").optJSONObject("js")); } catch (Exception ignored) {}
                if (exp == UNKNOWN && ss.profile != null) exp = fromJs(ss.profile);
                e.exp = exp;
                if (exp > 0 && exp < System.currentTimeMillis()) { e.ok = false; e.status = "abgelaufen"; }
            } catch (Exception ex) { e.ok = false; e.status = ex.getMessage() == null ? "nicht freigeschaltet" : ex.getMessage(); }
            out.add(e);
        }
        return out;
    }
    /** Ablaufdatum aus den ueblichen Feldern der Ministra/Stalker-Portale lesen. */
    static long fromJs(JSONObject js) {
        if (js == null) return UNKNOWN;
        for (String k : new String[]{"end_date", "expire_billing_date", "tariff_expired_date", "phone", "account_balance_date"}) {
            String v = js.optString(k, "").trim();
            if (v.isEmpty() || v.startsWith("0000")) continue;
            if (v.toLowerCase(Locale.ROOT).matches(".*(unlimit|unbegrenzt|forever).*")) return UNLIMITED;
            long t = parse(v);
            if (t > 0) return t;
        }
        return UNKNOWN;
    }
    static long parse(String v) {
        v = v.trim();
        if (v.matches("\\d{9,11}")) return Long.parseLong(v) * 1000L;
        if (v.matches("\\d{12,14}")) return Long.parseLong(v);
        String[] f = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm", "dd.MM.yyyy", "MMMM d, yyyy, h:mm a", "MMMM d, yyyy", "d MMMM yyyy", "dd/MM/yyyy", "MM/dd/yyyy"};
        Matcher m = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}:\\d{2})?|\\d{2}\\.\\d{2}\\.\\d{4}|[A-Za-z]+ \\d{1,2}, \\d{4}(, \\d{1,2}:\\d{2} [ap]m)?|\\d{1,2} [A-Za-z]+ \\d{4}|\\d{2}/\\d{2}/\\d{4})").matcher(v);
        String s = m.find() ? m.group(1) : v;
        for (String p : f) {
            for (Locale loc : new Locale[]{Locale.ENGLISH, Locale.GERMAN}) {
                try { SimpleDateFormat d = new SimpleDateFormat(p, loc); d.setLenient(false); Date x = d.parse(s); if (x != null && x.getTime() > 946684800000L) return x.getTime(); } catch (Exception ignored) {}
            }
        }
        return UNKNOWN;
    }
    /** Fuer den Server-Bericht (Pakete): kurz und ohne Passwoerter, MAC gekuerzt. */
    static JSONArray report(Context c) {
        JSONArray a = new JSONArray();
        for (Entry e : cached(c)) try {
            String l = e.label.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}") ? "…" + e.label.substring(e.label.length() - 5) : "";
            a.put(new JSONObject().put("n", e.name).put("l", l).put("e", e.exp > 0 ? e.exp / 1000 : e.exp).put("ok", e.ok).put("s", e.status));
        } catch (Exception ignored) {}
        return a;
    }
    /** Warnungen (abgelaufen, gesperrt, laeuft in 14 Tagen ab). */
    static List<String> warnings(Context c) {
        List<String> w = new ArrayList<>();
        for (Entry e : cached(c)) if (e.warn()) w.add("„" + e.name + "“" + (e.label.isEmpty() ? "" : " (" + (e.label.contains(":") ? "MAC …" + e.label.substring(Math.max(0, e.label.length() - 5)) : e.label) + ")") + ": " + e.text());
        return w;
    }
}
