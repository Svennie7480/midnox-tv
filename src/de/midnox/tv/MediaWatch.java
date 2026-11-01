package de.midnox.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * "Der Launcher weiss, was laeuft": liest per ADB ("dumpsys media_session"), was jede App gerade abspielt
 * (Titel, Folge, Stand, Play/Pause). Daraus entstehen: Weiterschauen ueber alle Apps, Kino-Modus fuer jede App,
 * "Laeuft gerade" auf dem Handy und das Fernseh-Tagebuch.
 */
final class MediaWatch {
    static final int NONE = 0, PLAYING = 3, PAUSED = 2;

    static final class Session { String pkg, title = "", sub = "", desc = ""; int state; long pos, dur; boolean active; }

    static volatile Session current;                 // was gerade laeuft (oder pausiert ist)
    static volatile List<Session> lastSeen = new ArrayList<>();   // alle Sitzungen der letzten Abfrage (fuer die Pruefansicht)
    static volatile String lastError = "";
    static volatile long lastPoll, lastTick;
    static volatile boolean polling;

    /** Apps, die nicht als "Film/Serie" zaehlen (Musik, Radio, System). */
    static boolean audioOnly(String pkg) {
        return pkg.equals("de.midnox.tv") || pkg.startsWith("de.midnox.") || pkg.contains("spotify") || pkg.contains("music") || pkg.contains("mp3")
            || pkg.contains("radio") || pkg.contains("tunein") || pkg.contains("audible") || pkg.contains("podcast") || pkg.contains("deezer")
            || pkg.startsWith("com.amazon.tv.") || pkg.startsWith("com.amazon.alexa") || pkg.equals("android");
    }
    static boolean liveTv(String pkg) { for (String[] a : TvRows.TV_APPS) if (a[0].equals(pkg)) return true; return false; }

    /** Alle paar Sekunden aus dem Hintergrunddienst. */
    static void tick(final Context c) {
        if (!Style.prefs(c).getBoolean("mw_on", true)) { current = null; return; }
        long now = System.currentTimeMillis();
        if (polling || now - lastPoll < 5000) return;
        if (!Daily.screenOn(c)) { current = null; return; }
        polling = true; lastPoll = now;
        try { poll(c); } catch (Exception e) { lastError = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()); }
        finally { polling = false; }
    }

    static void poll(Context c) throws Exception {
        String out = Adb.shellLong(c, "dumpsys media_session", 6000);
        List<Session> all = parse(out);
        lastSeen = all;
        lastError = "";
        Session best = null;
        for (Session s : all) {
            if (s.pkg == null || s.pkg.equals(c.getPackageName())) continue;
            if (s.state == PLAYING && (best == null || best.state != PLAYING)) best = s;
            else if (s.state == PAUSED && s.active && best == null) best = s;
        }
        long now = System.currentTimeMillis();
        long dt = lastTick == 0 ? 0 : Math.min(15000, now - lastTick);
        lastTick = now;
        current = best;
        if (best != null) record(c, best, best.state == PLAYING ? dt : 0);
        breakHint(c, best, dt);
    }

    /** "dumpsys media_session" in Sitzungen zerlegen. */
    static List<Session> parse(String out) {
        List<Session> l = new ArrayList<>();
        Session s = null;
        Pattern st = Pattern.compile("state=PlaybackState \\{state=(\\d+), position=(-?\\d+)");
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("package=")) { s = new Session(); s.pkg = line.substring(8).trim(); l.add(s); continue; }
            if (s == null) continue;
            if (line.startsWith("active=")) s.active = line.contains("true");
            Matcher m = st.matcher(line);
            if (m.find()) { s.state = Integer.parseInt(m.group(1)); try { s.pos = Long.parseLong(m.group(2)); } catch (Exception ignored) {} }
            if (line.startsWith("metadata:")) {
                int d = line.indexOf("description=");
                if (d >= 0) {
                    String[] parts = line.substring(d + 12).split(", ", 3);
                    s.title = clean(parts.length > 0 ? parts[0] : "");
                    s.sub = clean(parts.length > 1 ? parts[1] : "");
                    s.desc = clean(parts.length > 2 ? parts[2] : "");
                }
            }
        }
        // Nur die neueste Sitzung je App behalten (Apps registrieren manchmal mehrere)
        Map<String, Session> by = new LinkedHashMap<>();
        for (Session x : l) {
            Session o = by.get(x.pkg);
            if (o == null || rank(x) > rank(o)) by.put(x.pkg, x);
        }
        return new ArrayList<>(by.values());
    }
    static int rank(Session s) { return (s.state == PLAYING ? 4 : s.state == PAUSED ? 2 : 0) + (s.active ? 1 : 0) + (s.title.isEmpty() ? 0 : 1); }
    static String clean(String s) { s = s.trim(); return s.equals("null") ? "" : s; }

    // ------------------------------------------------------------ Verlauf und Statistik (watch.json)
    static JSONObject data;
    static synchronized JSONObject load(Context c) {
        if (data != null) return data;
        try { data = new JSONObject(Style.read(new FileInputStream(new File(c.getFilesDir(), "watch.json")))); }
        catch (Exception e) { data = new JSONObject(); }
        return data;
    }
    static long lastSave;
    static synchronized void save(Context c, boolean force) {
        if (!force && System.currentTimeMillis() - lastSave < 60000) return;
        lastSave = System.currentTimeMillis();
        try { FileOutputStream f = new FileOutputStream(new File(c.getFilesDir(), "watch.json")); f.write(data.toString().getBytes("UTF-8")); f.close(); } catch (Exception ignored) {}
    }
    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()); }

    static synchronized void record(Context c, Session s, long dt) {
        try {
            JSONObject d = load(c);
            // Zeit je App und Tag
            if (dt > 0) {
                JSONObject days = d.optJSONObject("days"); if (days == null) { days = new JSONObject(); d.put("days", days); }
                JSONObject day = days.optJSONObject(today()); if (day == null) { day = new JSONObject(); days.put(today(), day); }
                day.put(s.pkg, day.optLong(s.pkg) + dt);
                // aeltere Tage als 60 Tage entfernen
                if (days.length() > 62) { List<String> ks = new ArrayList<>(); for (Iterator<String> it = days.keys(); it.hasNext(); ) ks.add(it.next()); Collections.sort(ks); for (int i = 0; i < ks.size() - 60; i++) days.remove(ks.get(i)); }
            }
            // Verlauf (nur mit Titel)
            if (!s.title.isEmpty() && !audioOnly(s.pkg)) {
                JSONArray items = d.optJSONArray("items"); if (items == null) { items = new JSONArray(); d.put("items", items); }
                String key = s.pkg + "|" + series(s);
                JSONArray n = new JSONArray();
                JSONObject it = null;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject x = items.getJSONObject(i);
                    if (key.equals(x.optString("key"))) it = x; else n.put(x);
                }
                if (it == null) it = new JSONObject().put("key", key).put("pkg", s.pkg).put("first", System.currentTimeMillis()).put("ms", 0L);
                it.put("title", s.title).put("sub", s.sub).put("pos", s.pos).put("last", System.currentTimeMillis()).put("ms", it.optLong("ms") + dt);
                JSONArray out = new JSONArray().put(it);
                for (int i = 0; i < n.length() && out.length() < 40; i++) out.put(n.get(i));
                d.put("items", out);
            }
            save(c, false);
        } catch (Exception ignored) {}
    }
    /** Serien zusammenfassen: "Stranger Things" statt jede Folge einzeln (Titel ohne "Folge …"/"S1:E2"). */
    static String series(Session s) {
        String t = s.title;
        // Viele Apps: Titel = Serie, Untertitel = Folge. Wenn der Titel nach einer Folge aussieht, Untertitel als Serie nehmen.
        if (t.matches("(?i).*(folge|episode|staffel|season|\\bs\\d+\\s*:?\\s*e\\d+).*") && !s.sub.isEmpty()) t = s.sub;
        return t.toLowerCase(Locale.GERMANY).replaceAll("[^a-z0-9äöüß]+", " ").trim();
    }

    static String appName(Context c, String pkg) {
        try { PackageManager pm = c.getPackageManager(); return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception e) { return pkg; }
    }

    /** Zuletzt Geschautes (fuer die Startseite), ohne Emby (hat eine eigene Reihe). */
    static List<JSONObject> recent(Context c, int max) {
        List<JSONObject> l = new ArrayList<>();
        JSONArray items = load(c).optJSONArray("items");
        for (int i = 0; items != null && i < items.length() && l.size() < max; i++) {
            JSONObject x = items.optJSONObject(i);
            if (x == null || x.optString("pkg").contains("emby") || x.optLong("ms") < 60000) continue;   // mind. 1 Minute geschaut
            if (Style.launchIntent(c.getPackageManager(), x.optString("pkg")) == null) continue;
            l.add(x);
        }
        return l;
    }

    // ------------------------------------------------------------ Kino-Modus
    /** Zustand fuer den Kino-Modus: nur Video-Apps; Live-TV-Apps nur, wenn eingeschaltet. */
    static int kinoState(Context c) {
        Session s = current;
        if (s == null || audioOnly(s.pkg) || s.pkg.contains("emby")) return KinoService.IDLE;
        if (liveTv(s.pkg) && !Style.prefs(c).getBoolean("kino_livetv", false)) return KinoService.IDLE;
        if (System.currentTimeMillis() - lastPoll > 30000) return KinoService.IDLE;
        return s.state == PLAYING ? KinoService.PLAYING : s.state == PAUSED ? KinoService.PAUSED : KinoService.IDLE;
    }

    // ------------------------------------------------------------ Pausen-Hinweis
    static long watchedInRow, hinted;
    static void breakHint(Context c, Session s, long dt) {
        int h = Style.prefs(c).getInt("mw_break_h", 0);
        if (s != null && s.state == PLAYING && !audioOnly(s.pkg)) watchedInRow += dt; else if (s == null) watchedInRow = Math.max(0, watchedInRow - dt * 4);
        if (h <= 0 || watchedInRow < h * 3600000L || System.currentTimeMillis() - hinted < 3600000L) return;
        hinted = System.currentTimeMillis();
        final Context cc = c; final int hh = h;
        GoalAlert.ui.post(new Runnable() { public void run() { Style.toast(cc, "⏸  Du schaust seit " + hh + " Stunde" + (hh == 1 ? "" : "n") + " am Stück – Zeit für eine kurze Pause?"); } });
    }

    // ------------------------------------------------------------ fuer Handy und PC
    static JSONObject now(Context c) throws JSONException {
        Session s = current;
        if (s == null) return null;
        return new JSONObject().put("pkg", s.pkg).put("app", appName(c, s.pkg)).put("title", s.title).put("sub", s.sub)
            .put("state", s.state == PLAYING ? "play" : s.state == PAUSED ? "pause" : "").put("pos", s.pos);
    }
    /** Pruefansicht und Tagebuch fuer die PC-Verwaltung. */
    static JSONObject report(Context c) throws JSONException {
        JSONObject o = new JSONObject();
        JSONArray seen = new JSONArray();
        for (Session s : lastSeen) seen.put(new JSONObject().put("pkg", s.pkg).put("app", appName(c, s.pkg)).put("title", s.title).put("sub", s.sub)
            .put("state", s.state == PLAYING ? "spielt" : s.state == PAUSED ? "pausiert" : s.state == 1 ? "gestoppt" : s.state == 0 ? "–" : "Status " + s.state).put("active", s.active));
        o.put("sessions", seen).put("error", lastError).put("polled", lastPoll).put("now", now(c));
        JSONObject d = load(c);
        // Statistik: heute, 7 Tage, 30 Tage je App
        JSONObject days = d.optJSONObject("days");
        Map<String, long[]> per = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        JSONArray chart = new JSONArray();
        for (int i = 0; i < 30; i++) {
            String day = f.format(cal.getTime());
            JSONObject dd = days == null ? null : days.optJSONObject(day);
            long sum = 0;
            if (dd != null) for (Iterator<String> it = dd.keys(); it.hasNext(); ) {
                String p = it.next(); long ms = dd.optLong(p);
                if (audioOnly(p)) continue;
                long[] a = per.get(p); if (a == null) { a = new long[3]; per.put(p, a); }
                if (i == 0) a[0] += ms;
                if (i < 7) a[1] += ms;
                a[2] += ms; sum += ms;
            }
            if (i < 14) chart.put(new JSONObject().put("day", day).put("ms", sum));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        JSONArray apps = new JSONArray();
        List<Map.Entry<String, long[]>> es = new ArrayList<>(per.entrySet());
        Collections.sort(es, new Comparator<Map.Entry<String, long[]>>() { public int compare(Map.Entry<String, long[]> a, Map.Entry<String, long[]> b) { return Long.compare(b.getValue()[2], a.getValue()[2]); } });
        for (Map.Entry<String, long[]> e : es) apps.put(new JSONObject().put("pkg", e.getKey()).put("app", appName(c, e.getKey())).put("today", e.getValue()[0]).put("week", e.getValue()[1]).put("month", e.getValue()[2]));
        o.put("apps", apps).put("chart", chart);
        JSONArray items = d.optJSONArray("items");
        JSONArray hist = new JSONArray();
        for (int i = 0; items != null && i < items.length() && i < 25; i++) {
            JSONObject x = items.getJSONObject(i);
            hist.put(new JSONObject().put("app", appName(c, x.optString("pkg"))).put("title", x.optString("title")).put("sub", x.optString("sub")).put("last", x.optLong("last")).put("ms", x.optLong("ms")));
        }
        o.put("history", hist);
        return o;
    }
    static synchronized void clear(Context c) { data = new JSONObject(); save(c, true); }

    /** Weiterschauen: App oeffnen (bei YouTube/SmartTube direkt nach dem Titel suchen). */
    static void open(Context c, JSONObject x) {
        String pkg = x.optString("pkg");
        if (pkg.contains("youtube") || pkg.contains("smarttube") || pkg.contains("videomanager")) {
            try {
                c.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(x.optString("title"))))
                    .setPackage(pkg).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
        Style.start(c, Style.launchIntent(c.getPackageManager(), pkg));
    }
}
