package de.midnox.live;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Pakete vom midnox-Server: Einrichtungscode einloesen, Aenderungen automatisch abholen. */
final class Cloud {
    static final String SERVER = "https://midnox.de/midnoxtv/live/index.php";

    static String server(Context c) { return U.prefs(c).getString("pk_server", SERVER); }
    static boolean linked(Context c) { return !U.prefs(c).getString("pk_token", "").isEmpty(); }
    static String packageName(Context c) { return U.prefs(c).getString("pk_name", ""); }

    static String device(Context c) {
        String n = null;
        try { n = android.provider.Settings.Global.getString(c.getContentResolver(), "device_name"); } catch (Exception ignored) {}
        if (n == null || n.trim().isEmpty()) n = Build.MANUFACTURER + " " + Build.MODEL;
        return n.trim();
    }

    static JSONObject post(Context c, String api, JSONObject body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(server(c) + "?api=" + api).openConnection();
        h.setConnectTimeout(10000); h.setReadTimeout(20000);
        h.setRequestMethod("POST"); h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json");
        h.setRequestProperty("User-Agent", U.UA);
        try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
        int code = h.getResponseCode();
        InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
        String s = in == null ? "" : U.read(in);
        try { JSONObject j = new JSONObject(s); j.put("_http", code); return j; }
        catch (Exception e) { throw new Exception("Server antwortet nicht richtig (HTTP " + code + ")"); }
    }

    /** Code einloesen; liefert Meldung fuer den Nutzer. */
    static String redeem(Context c, String code) throws Exception {
        JSONObject r = post(c, "redeem", new JSONObject().put("code", code.replaceAll("\\D", "")).put("device", device(c)));
        if (!r.optBoolean("ok")) throw new Exception(r.optString("msg", "Code nicht angenommen"));
        U.prefs(c).edit().putString("pk_token", r.getString("token")).putInt("pk_ver", r.optInt("ver")).putString("pk_name", r.optString("name"))
            .putLong("pk_pulled", System.currentTimeMillis()).apply();
        apply(c, r.getJSONObject("package"), true);
        return "Eingerichtet: " + r.optString("name");
    }

    /** Aenderungen holen (alle 12 Stunden und beim Start). true = es hat sich etwas geaendert. */
    static boolean pull(Context c) throws Exception {
        String tok = U.prefs(c).getString("pk_token", "");
        if (tok.isEmpty()) return false;
        JSONObject r = post(c, "pull", new JSONObject().put("token", tok).put("ver", U.prefs(c).getInt("pk_ver", -1)).put("device", device(c)));
        U.prefs(c).edit().putLong("pk_pulled", System.currentTimeMillis()).apply();
        if (r.optBoolean("revoked") || r.optInt("_http") == 410) { unlink(c); return true; }
        if (!r.optBoolean("ok") || !r.optBoolean("changed")) return false;
        U.prefs(c).edit().putInt("pk_ver", r.optInt("ver")).putString("pk_name", r.optString("name")).apply();
        apply(c, r.getJSONObject("package"), false);
        return true;
    }
    static boolean due(Context c) { return linked(c) && System.currentTimeMillis() - U.prefs(c).getLong("pk_pulled", 0) > 12 * 3600000L; }

    /** Paket-Quellen ersetzen die bisherigen Paket-Quellen; eigene Quellen bleiben. */
    static void apply(Context c, JSONObject p, boolean first) throws Exception {
        Set<String> old = new HashSet<>(U.prefs(c).getStringSet("pk_ids", new HashSet<String>()));
        List<Model.Source> l = new ArrayList<>();
        for (Model.Source s : Lib.sources(c)) if (!old.contains(s.id)) l.add(s);
        Set<String> ids = new HashSet<>();
        JSONArray a = p.optJSONArray("sources");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject so = a.getJSONObject(i);
            Model.Source s = Model.Source.from(so);
            if (s.id.isEmpty()) s.id = "pk" + i;
            if (s.type.isEmpty()) s.type = "m3u";
            if (so.has("file")) { U.writeFile(Lib.m3uFile(c, s.id), so.getString("file")); if (s.url.isEmpty()) s.url = "datei:" + s.name; }
            if (s.type.equals("stalker")) s.mac = android.text.TextUtils.join("\n", Stalker.macs(s));
            l.add(s); ids.add(s.id);
        }
        Lib.saveSources(c, l);
        android.content.SharedPreferences.Editor ed = U.prefs(c).edit().putStringSet("pk_ids", ids);
        String epg = p.optString("epg_extra", "");
        if (!epg.isEmpty()) ed.putString("epg_extra", epg);
        if (first) {   // Favoriten und ausgeblendete Gruppen nur beim Einrichten uebernehmen (danach entscheidet der Nutzer)
            List<String> favs = Lib.favs(c);
            JSONArray f = p.optJSONArray("favs");
            for (int i = 0; f != null && i < f.length(); i++) if (!favs.contains(f.getString(i))) favs.add(f.getString(i));
            ed.putString("favs", new JSONArray(favs).toString());
            Set<String> hid = Lib.hiddenGroups(c);
            JSONArray hg = p.optJSONArray("hidden");
            for (int i = 0; hg != null && i < hg.length(); i++) hid.add(hg.getString(i));
            ed.putStringSet("hidden", hid);
        }
        ed.apply();
    }
    static void unlink(Context c) {
        Set<String> old = new HashSet<>(U.prefs(c).getStringSet("pk_ids", new HashSet<String>()));
        List<Model.Source> l = new ArrayList<>();
        for (Model.Source s : Lib.sources(c)) if (!old.contains(s.id)) l.add(s);
        Lib.saveSources(c, l);
        U.prefs(c).edit().remove("pk_token").remove("pk_ver").remove("pk_name").remove("pk_ids").apply();
    }
}
