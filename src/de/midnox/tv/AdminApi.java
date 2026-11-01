package de.midnox.tv;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** PC-Verwaltung (http://<Fire-TV>:8130/admin): Anmeldung per Code am Fernseher, Senderliste, M3U, Apps, Einstellungen. */
final class AdminApi {
    static volatile String pin;
    static volatile long pinUntil, lastPin;
    static volatile int tries;

    static void ui(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }

    // ------------------------------------------------------------ Anmeldung
    static String requestPin(final Context c) throws JSONException {
        long now = System.currentTimeMillis();
        if (now - lastPin < 8000 && pin != null) return new JSONObject().put("ok", true).toString();
        lastPin = now;
        if (pin == null || now > pinUntil) { pin = String.format(Locale.ROOT, "%04d", new java.security.SecureRandom().nextInt(10000)); tries = 0; }
        pinUntil = now + 180000;
        final String p = pin;
        ui(new Runnable() { public void run() {
            PromptActivity.show(c, "info", "PC-Verwaltung", "Jemand möchte midnox TV am PC verwalten.\n\nCode:   " + p.charAt(0) + "  " + p.charAt(1) + "  " + p.charAt(2) + "  " + p.charAt(3) + "\n\nGültig für 3 Minuten. Nicht von dir? Einfach schließen.", null);
        }});
        return new JSONObject().put("ok", true).toString();
    }
    static String login(Context c, String body) throws JSONException {
        String p = new JSONObject(body.isEmpty() ? "{}" : body).optString("pin");
        if (pin == null || System.currentTimeMillis() > pinUntil) return new JSONObject().put("ok", false).put("msg", "Code abgelaufen – neuen anfordern").toString();
        if (++tries > 5) { pin = null; return new JSONObject().put("ok", false).put("msg", "Zu viele Versuche – neuen Code anfordern").toString(); }
        if (!p.equals(pin)) return new JSONObject().put("ok", false).put("msg", "Code stimmt nicht").toString();
        pin = null;
        return new JSONObject().put("ok", true).put("key", Remote.key(c)).toString();
    }

    // ------------------------------------------------------------ Befehle (mit Schluessel)
    static String handle(final Context c, String cmd, String body) throws Exception {
        final JSONObject in = new JSONObject(body.isEmpty() ? "{}" : body);
        SharedPreferences pr = Style.prefs(c);
        switch (cmd) {
            case "info": {
                JSONObject o = new JSONObject();
                o.put("version", c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName);
                o.put("device", Build.MODEL).put("ip", SettingsActivity.ownIp());
                o.put("vlc", Style.launchIntent(c.getPackageManager(), "org.videolan.vlc") != null);
                o.put("rtl", TvRows.hasRtl(c));
                return o.toString();
            }
            case "channels": {
                Map<String, List<TvRows.Prog>> epg = TvRows.readCache(new File(c.getFilesDir(), "tvnow.json"));
                long now = System.currentTimeMillis();
                JSONArray a = new JSONArray();
                for (String[] ch : TvRows.ordered(c)) {
                    String id = ch[0];
                    JSONObject o = new JSONObject().put("id", id).put("name", TvRows.name(c, id)).put("src", TvRows.source(c, id))
                        .put("number", TvRows.number(c, id)).put("builtin", !id.startsWith("m3u:")).put("rtl", TvRows.rtlSlug(id) != null);
                    String u = id.startsWith("m3u:") ? TvRows.streamUrl(c, id) : pr.getString("tvurl_" + id, "");
                    o.put("url", u == null ? "" : u);
                    M3u.Ch x = id.startsWith("m3u:") ? M3u.get(c, id) : null;
                    if (x != null) o.put("logo", x.logo == null ? "" : x.logo).put("group", x.group);
                    List<TvRows.Prog> l = epg == null ? null : epg.get(TvRows.epgId(c, id));
                    if (l != null) for (TvRows.Prog p : l) if (p.start <= now && p.stop > now) { o.put("now", p.title); break; }
                    a.put(o);
                }
                return new JSONObject().put("channels", a).put("rtl", TvRows.hasRtl(c)).put("vlc", Style.launchIntent(c.getPackageManager(), "org.videolan.vlc") != null).toString();
            }
            case "channels_save": {
                SharedPreferences.Editor ed = pr.edit();
                JSONArray order = in.optJSONArray("order");
                if (order != null) ed.putString("tv_order", order.toString());
                JSONArray items = in.optJSONArray("items");
                for (int i = 0; items != null && i < items.length(); i++) {
                    JSONObject o = items.getJSONObject(i);
                    String id = o.getString("id");
                    ed.putString("tvsrc_" + id, o.optString("src", ""));
                    if (!id.startsWith("m3u:")) {
                        ed.putInt("mtv_" + id, Math.max(0, Math.min(9999, o.optInt("number", 0))));
                        String u = o.optString("url", "").trim();
                        if (u.isEmpty()) ed.remove("tvurl_" + id); else ed.putString("tvurl_" + id, u);
                    }
                }
                ed.apply();
                SetupActivity.changed = true;
                return "{\"ok\":true}";
            }
            case "channels_reset": {
                SharedPreferences.Editor ed = pr.edit().remove("tv_order");
                for (String[] ch : TvRows.all(c)) ed.remove("tvsrc_" + ch[0]).remove("mtv_" + ch[0]);
                ed.apply(); SetupActivity.changed = true;
                return "{\"ok\":true}";
            }
            case "test": {
                final String id = in.optString("id");
                ui(new Runnable() { public void run() { TvRows.openTv(c, id); } });
                return "{\"ok\":true}";
            }
            case "m3u_import": {
                String text = in.optString("text", "");
                if (text.isEmpty() && !in.optString("url").isEmpty()) text = fetch(in.optString("url").trim());
                if (!text.contains("#EXTINF")) return new JSONObject().put("ok", false).put("msg", "Keine gültige M3U-Liste (#EXTINF fehlt)").toString();
                JSONObject r = M3u.importText(c, text, in.optBoolean("replace", true));
                SetupActivity.changed = true;
                return r.toString();
            }
            case "m3u_clear": M3u.clear(c); SetupActivity.changed = true; return "{\"ok\":true}";
            case "apps": return apps(c);
            case "apps_save": {
                JSONArray a = in.getJSONArray("favs");
                List<String> l = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
                HomeActivity.saveFavs(c, l);
                SetupActivity.changed = true;
                return "{\"ok\":true}";
            }
            case "settings": return settings(c);
            case "settings_save": {
                SharedPreferences.Editor ed = pr.edit();
                for (Iterator<String> it = in.keys(); it.hasNext(); ) {
                    String k = it.next();
                    Object[] def = find(k);
                    if (def == null) continue;
                    String t = (String) def[3];
                    Object v = in.get(k);
                    if (t.equals("bool") || t.equals("inv")) ed.putBoolean(k, t.equals("inv") ? !in.getBoolean(k) : in.getBoolean(k));
                    else if (t.equals("int")) ed.putInt(k, (int) Math.round(in.getDouble(k)));
                    else if (t.equals("float")) ed.putFloat(k, (float) in.getDouble(k));
                    else if (t.equals("ichoice")) ed.putInt(k, Integer.parseInt(String.valueOf(v)));
                    else ed.putString(k, String.valueOf(v));
                }
                ed.apply();
                SetupActivity.changed = true;
                ui(new Runnable() { public void run() { try { c.startService(new Intent(c, KinoService.class)); } catch (Exception ignored) {} } });
                return "{\"ok\":true}";
            }
        }
        return null;
    }

    static String fetch(String url) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setInstanceFollowRedirects(true); h.setConnectTimeout(10000); h.setReadTimeout(30000);
        h.setRequestProperty("User-Agent", "VLC/3.0 midnox TV");
        if (h.getResponseCode() / 100 != 2) throw new IOException("HTTP " + h.getResponseCode());
        return Style.read(h.getInputStream());
    }

    // ------------------------------------------------------------ Apps
    static String apps(Context c) throws JSONException {
        PackageManager pm = c.getPackageManager();
        Map<String, ResolveInfo> apps = new TreeMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = ri.activityInfo.packageName;
                if (!p.equals(c.getPackageName()) && !apps.containsKey(p)) apps.put(p, ri);
            }
        }
        List<String> favs = HomeActivity.favs(c);
        JSONArray a = new JSONArray();
        for (Map.Entry<String, ResolveInfo> e : apps.entrySet()) {
            String p = e.getKey();
            JSONObject o = new JSONObject().put("pkg", p).put("label", HomeActivity.label(pm, p)).put("pinned", favs.indexOf(p));
            try { o.put("icon", icon(e.getValue().loadIcon(pm))); } catch (Exception ignored) {}
            a.put(o);
        }
        return new JSONObject().put("apps", a).toString();
    }
    static String icon(Drawable d) {
        Bitmap b = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b); d.setBounds(0, 0, 72, 72); d.draw(cv);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    // ------------------------------------------------------------ Einstellungen
    /** {Rubrik, Schluessel, Bezeichnung, Typ, Standard, Werte, Namen}. Typen: bool, inv (umgekehrt gespeichert), int, float, choice, ichoice, string. */
    static Object[][] schema() {
        String[] tvIds = new String[TvRows.TV_APPS.length + 2], tvNames = new String[TvRows.TV_APPS.length + 2];
        tvIds[0] = ""; tvNames[0] = "automatisch";
        for (int i = 0; i < TvRows.TV_APPS.length; i++) { tvIds[i + 1] = TvRows.TV_APPS[i][0]; tvNames[i + 1] = TvRows.TV_APPS[i][1]; }
        tvIds[tvIds.length - 1] = "-"; tvNames[tvNames.length - 1] = "keine";
        return new Object[][]{
            {"Darstellung", "theme", "Thema", "choice", "auto", Theme.IDS, Theme.NAMES},
            {"Darstellung", "fx_level", "Animierte Deko", "ichoice", 2, new String[]{"0", "1", "2"}, new String[]{"Aus", "Dezent", "Voll"}},
            {"Darstellung", "hero_text", "Beschreibung im Kopfbereich", "bool", true, null, null},
            {"Darstellung", "hide_radio", "midnox Radio auf der Startseite", "inv", false, null, null},
            {"Reihen auf der Startseite", "hide_resume", "Weiterschauen", "inv", false, null, null},
            {"Reihen auf der Startseite", "hide_home", "Zuhause (Home Assistant)", "inv", false, null, null},
            {"Reihen auf der Startseite", "hide_tv", "Jetzt im TV", "inv", false, null, null},
            {"Jetzt im TV", "tv_app", "TV-App", "choice", "", tvIds, tvNames},
            {"Jetzt im TV", "tv_rtlplus", "RTL-Sender als Livestream in RTL+ öffnen", "bool", true, null, null},
            {"Jetzt im TV", "tv_rtlmax", "RTL+ Max-Sender zeigen (Crime, Passion, Living, GEO)", "bool", false, null, null},
            {"Wetter", "weather_on", "Wetter unter der Uhr", "bool", true, null, null},
            {"Wetter", "weather_entity", "Wetter-Entität (leer = automatisch)", "string", "", null, null},
            {"Guten Morgen", "morning_on", "Morgens beim Einschalten anzeigen", "bool", true, null, null},
            {"Guten Morgen", "morning_from", "Ab (Uhr)", "int", 4, null, null},
            {"Guten Morgen", "morning_to", "Bis (Uhr)", "int", 11, null, null},
            {"Guten Morgen", "morning_news", "Nachrichten", "ichoice", 2, new String[]{"0", "1", "2"}, new String[]{"Aus", "Deutschland (tagesschau)", "Deutschland + Schleswig-Holstein"}},
            {"Guten Morgen", "morning_name", "Name in der Begrüßung (leer = aus Home Assistant)", "string", "", null, null},
            {"Dienstplan", "shift_on", "Vor dem Dienst ans Schlafen erinnern", "bool", true, null, null},
            {"Dienstplan", "shift_lead", "Aufstehen vor Dienstbeginn (Minuten)", "int", 75, null, null},
            {"Dienstplan", "shift_sleep", "Gewünschter Schlaf (Stunden)", "float", 7.5f, null, null},
            {"Anruf", "call_on", "Film und Radio pausieren, wenn das Handy klingelt", "bool", true, null, null},
            {"Anruf", "call_entity", "Telefon-Sensor (leer = automatisch)", "string", "", null, null},
            {"Einschlaf-Timer", "sleep_fade", "Lautstärke langsam ausblenden", "bool", true, null, null},
            {"Einschlaf-Timer", "sleep_lights", "Licht mit ausschalten", "bool", true, null, null},
            {"Einschlaf-Timer", "sleep_tv", "Fernseher / Steckdose (Entität)", "string", "", null, null},
            {"Bildschirmschoner", "ss_idle", "Starten nach", "ichoice", 4, new String[]{"0", "2", "4", "5", "10", "15", "30"}, new String[]{"Aus", "2 Min.", "4 Min.", "5 Min.", "10 Min.", "15 Min.", "30 Min."}},
            {"Bildschirmschoner", "ss_midnox_only", "Nur midnox-Motive", "bool", false, null, null},
            {"Bildschirmschoner", "wish_show", "Musikwünsche einblenden", "bool", true, null, null},
            {"Kino-Modus", "kino_on", "Licht automatisch dimmen, wenn Emby abspielt", "bool", true, null, null},
            {"Kino-Modus", "kino_dark", "Nur wenn es draußen dunkel ist", "bool", true, null, null},
            {"Kino-Modus", "kino_amb", "Ambient-Helligkeit bei Wiedergabe (%)", "int", 20, null, null},
            {"Kino-Modus", "kino_pause", "Helligkeit bei Pause (%)", "int", 25, null, null},
            {"Kino-Modus", "kino_color", "Lichtfarbe", "choice", "none", KinoService.COLOR_IDS, KinoService.COLOR_NAMES},
        };
    }
    static Object[] find(String k) { for (Object[] s : schema()) if (s[1].equals(k)) return s; return null; }

    static String settings(Context c) throws JSONException {
        SharedPreferences pr = Style.prefs(c);
        JSONArray a = new JSONArray();
        for (Object[] s : schema()) {
            String k = (String) s[1], t = (String) s[3];
            JSONObject o = new JSONObject().put("section", s[0]).put("key", k).put("label", s[2]).put("type", t);
            if (t.equals("bool")) o.put("value", pr.getBoolean(k, (Boolean) s[4]));
            else if (t.equals("inv")) o.put("value", !pr.getBoolean(k, (Boolean) s[4]));
            else if (t.equals("int")) o.put("value", pr.getInt(k, (Integer) s[4]));
            else if (t.equals("float")) o.put("value", pr.getFloat(k, (Float) s[4]));
            else if (t.equals("ichoice")) o.put("value", String.valueOf(pr.getInt(k, (Integer) s[4])));
            else o.put("value", pr.getString(k, (String) s[4]));
            if (s[5] != null) {
                JSONArray opts = new JSONArray();
                String[] ids = (String[]) s[5], names = (String[]) s[6];
                for (int i = 0; i < ids.length; i++) opts.put(new JSONObject().put("v", ids[i]).put("n", names[i]));
                o.put("options", opts);
            }
            a.put(o);
        }
        return new JSONObject().put("settings", a).toString();
    }
}
