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
            case "sounds": return new JSONObject().put("sounds", GoalSound.list(c)).toString();
            case "sound_test": GoalSound.test(c, in.optString("slot")); return "{\"ok\":true}";
            case "sound_stop": GoalSound.stop(); return "{\"ok\":true}";
            case "sound_delete": GoalSound.delete(c, in.optString("slot")); return "{\"ok\":true}";
            case "hb_test": ui(new Runnable() { public void run() { Handball.test(c); } }); return "{\"ok\":true}";
            case "goal_light_test": GoalAlert.jubelTest(c); return "{\"ok\":true}";
            case "goal_test": ui(new Runnable() { public void run() { GoalAlert.test(c); } }); return "{\"ok\":true}";
            case "ha": return ha(c);
            case "ha_save": {
                SharedPreferences.Editor ed = pr.edit();
                if (in.has("kino")) ed.putString("kino_lights", in.getJSONObject("kino").toString());
                if (in.has("sleep")) ed.putString("sleep_light_ids", in.isNull("sleep") ? "" : in.getJSONArray("sleep").toString());
                if (in.has("morning")) ed.putString("morning_light_ids", in.getJSONArray("morning").toString());
                if (in.has("goal")) ed.putString("goal_light_ids", in.getJSONArray("goal").toString());
                if (in.has("morning_pct")) ed.putInt("morning_light_pct", in.getInt("morning_pct"));
                for (String k : new String[]{"weather_entity", "sleep_tv", "call_entity"}) if (in.has(k)) ed.putString(k, in.getString(k));
                ed.apply();
                SetupActivity.changed = true;
                return "{\"ok\":true}";
            }
            case "ha_light": {   // Lampe zum Erkennen kurz schalten
                JSONObject cfg = Style.loadConfig(c);
                String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
                Style.httpPost(base + "/api/services/light/toggle", tok, new JSONObject().put("entity_id", in.getString("id")).toString());
                return "{\"ok\":true}";
            }
            case "tiles": {
                JSONObject cfg = Style.loadConfig(c);
                String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
                JSONArray st = new JSONArray(Style.httpGet(base + "/api/states", tok)), sensors = new JSONArray();
                for (int i = 0; i < st.length(); i++) {
                    JSONObject o = st.getJSONObject(i);
                    String id = o.getString("entity_id");
                    if (!(id.startsWith("sensor.") || id.startsWith("binary_sensor.") || id.startsWith("input_number.") || id.startsWith("weather."))) continue;
                    if ("unavailable".equals(o.optString("state"))) continue;
                    JSONObject a = o.optJSONObject("attributes");
                    sensors.put(new JSONObject().put("id", id).put("name", a == null ? id : a.optString("friendly_name", id))
                        .put("unit", a == null ? "" : a.optString("unit_of_measurement", "")).put("state", o.optString("state"))
                        .put("dienst", a != null && a.has("start_time") && a.has("end_time")));
                }
                JSONArray t = cfg.optJSONArray("tiles");
                if (t == null || t.length() == 0) t = HaPanel.autoTiles(st);
                return new JSONObject().put("tiles", t).put("sensors", sensors).toString();
            }
            case "tiles_save": {
                JSONObject cfg = Style.loadConfig(c);
                cfg.put("tiles", in.getJSONArray("tiles"));
                Style.saveConfig(c, cfg);
                SetupActivity.changed = true;
                return "{\"ok\":true}";
            }
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
            {"Tor-Alarm (über jeder App)", "sa_on", "Tore und Karten live einblenden", "bool", true, null, null},
            {"Tor-Alarm (über jeder App)", "sa_bl1", "1. Bundesliga", "bool", true, null, null},
            {"Tor-Alarm (über jeder App)", "sa_bl2", "2. Bundesliga", "bool", true, null, null},
            {"Tor-Alarm (über jeder App)", "sa_goal", "Tore", "bool", true, null, null},
            {"Tor-Alarm (über jeder App)", "sa_yellow", "Gelbe Karten", "bool", false, null, null},
            {"Tor-Alarm (über jeder App)", "sa_red", "Rote und Gelb-Rote Karten", "bool", true, null, null},
            {"Tor-Alarm (über jeder App)", "sa_teams", "Nur diese Vereine (nichts gewählt = alle)", "multi", "", null, null},
            {"Tor-Jubel mit Licht", "gl_on", "Lampen leuchten in den Vereinsfarben, wenn dein Verein trifft", "bool", false, null, null},
            {"Tor-Jubel mit Licht", "gl_teams", "Mein Verein (nichts gewählt = Vereine vom Tor-Alarm)", "multi", "", null, null},
            {"Tor-Jubel mit Licht", "gl_c1", "Farbe 1", "color", "", null, null},
            {"Tor-Jubel mit Licht", "gl_c2", "Farbe 2", "color", "", null, null},
            {"Handball", "hb_team", "Mein Verein (Teil des Namens, z. B. Lübeck-Schwartau)", "text", "L\u00fcbeck-Schwartau", null, null},
            {"Handball", "hb_alert", "Spielstand meines Vereins über jeder App einblenden", "bool", true, null, null},
            {"Handball", "hb_start", "Anpfiff melden", "bool", true, null, null},
            {"Handball", "hb_end", "Endstand melden", "bool", true, null, null},
            {"Handball", "hb_every", "Zwischenstand höchstens alle … Minuten (0 = aus)", "int", 10, null, null},
            {"Handball", "hb_radio", "Eigener Radio-Link (leer = Knopf öffnet Dyn)", "text", "", null, null},
            {"Handball", "hb_test", "Einblendung testen", "action", "hb_test", null, null},
            {"Tor-Sounds", "gs_on", "Eigene Sounds bei Toren und Karten abspielen", "bool", true, null, null},
            {"Tor-Sounds", "gs_vol", "Lautstärke in % (relativ zur TV-Lautstärke)", "int", 80, null, null},
            {"Tor-Sounds", "gs_files", "", "sounds", "", null, null},
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
    // ------------------------------------------------------------ Home Assistant
    static String ha(Context c) throws Exception {
        JSONObject cfg = Style.loadConfig(c);
        if (cfg == null || !cfg.has("url")) return new JSONObject().put("error", "Home Assistant ist noch nicht verbunden (Einstellungen \u203a Mit Handy einrichten)").toString();
        String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
        SharedPreferences pr = Style.prefs(c);
        JSONArray lights = new JSONArray();
        for (JSONArray l : KinoService.allLights(base, tok)) lights.put(l);
        JSONArray st = new JSONArray(Style.httpGet(base + "/api/states", tok));
        JSONArray weather = new JSONArray(), tv = new JSONArray(), phone = new JSONArray();
        JSONObject lightState = new JSONObject();
        for (int i = 0; i < st.length(); i++) {
            JSONObject o = st.getJSONObject(i);
            String id = o.getString("entity_id");
            JSONObject a = o.optJSONObject("attributes");
            String name = a == null ? id : a.optString("friendly_name", id);
            JSONArray e = new JSONArray().put(id).put(name);
            if (id.startsWith("light.")) lightState.put(id, o.optString("state"));
            if (id.startsWith("weather.")) weather.put(e);
            else if (id.startsWith("switch.") || id.startsWith("media_player.")) tv.put(e);
            else if (id.startsWith("sensor.") && id.contains("phone_state")) phone.put(e);
        }
        JSONObject kino; try { kino = new JSONObject(pr.getString("kino_lights", "{}")); } catch (Exception e) { kino = new JSONObject(); }
        Object sleep; String sl = pr.getString("sleep_light_ids", "");
        try { sleep = sl.isEmpty() ? JSONObject.NULL : new JSONArray(sl); } catch (Exception e) { sleep = JSONObject.NULL; }
        JSONArray morning; try { morning = new JSONArray(pr.getString("morning_light_ids", "[]")); } catch (Exception e) { morning = new JSONArray(); }
        JSONArray goal; try { goal = new JSONArray(pr.getString("goal_light_ids", "[]")); } catch (Exception e) { goal = new JSONArray(); }
        return new JSONObject().put("lights", lights).put("state", lightState).put("kino", kino).put("sleep", sleep).put("morning", morning).put("goal", goal)
            .put("morning_pct", pr.getInt("morning_light_pct", 60))
            .put("weather", weather).put("tv", tv).put("phone", phone)
            .put("weather_entity", pr.getString("weather_entity", "")).put("sleep_tv", pr.getString("sleep_tv", "")).put("call_entity", pr.getString("call_entity", ""))
            .toString();
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
            if (t.equals("color")) o.put("auto", GoalAlert.hex(GoalAlert.autoColors(c)[k.endsWith("2") ? 1 : 0]));
            if (t.equals("multi")) {
                JSONArray opts = new JSONArray();
                for (String lg : new String[]{"bl1", "bl2"}) try {
                    JSONArray tb = new JSONArray(Style.httpGet("https://api.openligadb.de/getbltable/" + lg + "/" + Sport.season(), null));
                    for (int i = 0; i < tb.length(); i++) { String n = tb.getJSONObject(i).optString("shortName"); opts.put(new JSONObject().put("v", n).put("n", n).put("g", lg.equals("bl1") ? "1. Liga" : "2. Liga")); }
                } catch (Exception ignored) {}
                o.put("options", opts);
            }
            if (s[5] != null) {
                JSONArray opts = new JSONArray();
                String[] ids = (String[]) s[5], names = (String[]) s[6];
                for (int i = 0; i < ids.length; i++) opts.put(new JSONObject().put("v", ids[i]).put("n", names[i]));
                o.put("options", opts);
            }
            if (k.equals("tv_app")) {   // jede App nur einmal; installierte Variante bevorzugen, nicht installierte kennzeichnen
                android.content.pm.PackageManager pm = c.getPackageManager();
                String cur = pr.getString(k, "");
                JSONArray opts = new JSONArray().put(new JSONObject().put("v", "").put("n", "automatisch (erste installierte)"));
                Set<String> done = new HashSet<>();
                for (String[] x : TvRows.TV_APPS) {
                    if (done.contains(x[1])) continue;
                    String pick = null;
                    for (String[] y : TvRows.TV_APPS) if (y[1].equals(x[1]) && (Style.launchIntent(pm, y[0]) != null || y[0].equals(cur))) { pick = y[0]; if (Style.launchIntent(pm, y[0]) != null) break; }
                    boolean inst = pick != null && Style.launchIntent(pm, pick) != null;
                    if (pick == null) pick = x[0];
                    done.add(x[1]);
                    String hint = x[0].equals(TvRows.TV_APP) ? " \u2013 schaltet direkt auf den Sender" : " \u2013 \u00f6ffnet nur die App";
                    opts.put(new JSONObject().put("v", pick).put("n", x[1] + (inst ? "" : " (nicht installiert)") + hint));
                }
                opts.put(new JSONObject().put("v", "-").put("n", "keine"));
                o.put("options", opts);
            }
            a.put(o);
        }
        return new JSONObject().put("settings", a).toString();
    }
}
