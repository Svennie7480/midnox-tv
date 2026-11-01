package de.midnox.tv;

import android.content.Context;
import android.graphics.Bitmap;
import org.json.*;
import java.util.*;

/**
 * Spieltag-Modus fuer deine Vereine (Fussball: Tor-Jubel- bzw. Tor-Alarm-Vereine, Handball: Handball-Verein):
 * Erinnerung vor dem Anpfiff (mit "Wo laeuft das?"), auf Wunsch Stadion-Licht in Vereinsfarben und Radio/TV starten,
 * nach dem Spiel Endstand und Tabellenplatz.
 */
final class Matchday {
    static final class Ev { String code; Sport.Match m; int day; boolean reminded, lightOn, summarized; long lastCheck; }
    static final List<Ev> evs = new ArrayList<>();
    static volatile long nextSchedule;
    static volatile boolean busy;

    static boolean on(Context c, String k, boolean d) { return Style.prefs(c).getBoolean(k, d); }

    static void tick(final Context c) {
        long now = System.currentTimeMillis();
        if (now > nextSchedule && !busy) {
            busy = true; nextSchedule = now + 3 * 3600000L;
            new Thread(new Runnable() { public void run() { try { schedule(c); } catch (Exception e) { nextSchedule = System.currentTimeMillis() + 15 * 60000L; } finally { busy = false; } } }).start();
            return;
        }
        if (!on(c, "md_on", true)) return;
        int before = Math.max(1, Style.prefs(c).getInt("md_before", 15));
        List<Ev> copy; synchronized (evs) { copy = new ArrayList<>(evs); }
        for (final Ev e : copy) {
            long ko = e.m.kickoff;
            if (!e.reminded && now > ko - before * 60000L && now < ko + 10 * 60000L) {
                e.reminded = true;
                if (Daily.screenOn(c)) new Thread(new Runnable() { public void run() { remind(c, e); } }).start();
            }
            boolean hb = e.code.equals("hb2");
            if (!e.summarized && now > ko + (hb ? 95 : 105) * 60000L && now - e.lastCheck > 3 * 60000L && !busy) {
                e.lastCheck = now;
                new Thread(new Runnable() { public void run() { try { after(c, e); } catch (Exception ignored) {} } }).start();
            }
            if (!e.summarized && now > ko + 4 * 3600000L) { e.summarized = true; if (e.lightOn) light(c, e, false); }
        }
    }

    /** Spiele deiner Vereine der naechsten Tage sammeln. */
    static void schedule(Context c) throws Exception {
        List<Ev> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (!GoalAlert.allClubs(c).isEmpty()) for (String code : new String[]{"bl1", "bl2"}) {
            try {
                Sport.League l = Sport.load(code, 0);
                List<Sport.League> ls = new ArrayList<>(); ls.add(l);
                if (l.day < l.maxDay) try { ls.add(Sport.load(code, l.day + 1)); } catch (Exception ignored) {}
                for (Sport.League x : ls) for (Sport.Match m : x.matches)
                    if (m.kickoff + 4 * 3600000L > now && m.kickoff - 8 * 86400000L < now && (mine(c, m.t1) || mine(c, m.t2))) out.add(ev(code, m, x.day));
            } catch (Exception ignored) {}
        }
        try {
            Sport.League l = Sport.loadHb(0);
            List<Sport.League> ls = new ArrayList<>(); ls.add(l);
            if (l.day < l.maxDay) try { ls.add(Sport.loadHb(l.day + 1)); } catch (Exception ignored) {}
            for (Sport.League x : ls) for (Sport.Match m : x.matches)
                if (m.kickoff > 0 && m.kickoff + 4 * 3600000L > now && m.kickoff - 8 * 86400000L < now && (Sport.isClub(c, m.t1) || Sport.isClub(c, m.t2))) out.add(ev("hb2", m, x.day));
        } catch (Exception ignored) {}
        synchronized (evs) {
            // Zustand bereits bekannter Spiele uebernehmen
            for (Ev n : out) for (Ev o : evs) if (o.code.equals(n.code) && o.m.t1.name.equals(n.m.t1.name) && o.m.kickoff == n.m.kickoff) {
                n.reminded = o.reminded; n.lightOn = o.lightOn; n.summarized = o.summarized;
            }
            evs.clear(); evs.addAll(out);
        }
        // naechste Planung rechtzeitig vor dem naechsten Anpfiff
        for (Ev e : out) if (e.m.kickoff - 30 * 60000L > now) nextSchedule = Math.min(nextSchedule, e.m.kickoff - 30 * 60000L);
    }
    static Ev ev(String code, Sport.Match m, int day) { Ev e = new Ev(); e.code = code; e.m = m; e.day = day; return e; }
    static boolean mine(Context c, Sport.Team t) { return GoalAlert.clubSelected(c, t.name) || GoalAlert.clubSelected(c, t.shortName); }

    /** Laeuft gerade (oder gleich) ein Spiel, das fuer die Live-Leiste zaehlt? */
    static boolean liveWindow(Context c, long now) {
        synchronized (evs) { for (Ev e : evs) if (now > e.m.kickoff - 2 * 60000L && now < e.m.kickoff + 130 * 60000L) return true; }
        if (GoalAlert.allClubs(c).isEmpty()) synchronized (GoalAlert.kickoffs) {   // keine Vereine gewaehlt: alle Spiele der Ligen
            for (long[] k : GoalAlert.kickoffs) if ((k[0] == 1 ? on(c, "sa_bl1", true) : on(c, "sa_bl2", true)) && now > k[1] - 120000 && now < k[1] + 125 * 60000L) return true;
        }
        Sport.Match w = Handball.watch;
        return w != null && now > w.kickoff - 2 * 60000L && now < w.kickoff + 130 * 60000L;
    }

    // ------------------------------------------------------------ vor dem Spiel
    static void remind(Context c, Ev e) {
        boolean hb = e.code.equals("hb2");
        List<String> tv = Watch.who(e.code, e.m, e.day);
        String when = new java.text.SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(e.m.kickoff));
        Bitmap c1 = GoalAlert.crest(e.m.t1.icon), c2 = GoalAlert.crest(e.m.t2.icon);
        String auto = Style.prefs(c).getString("md_auto", "none");
        String sub = "Läuft bei: " + Watch.label(tv) + (auto.equals("radio") ? "  ·  Radio startet" : auto.equals("tv") ? "  ·  " + tv.get(0) + " öffnet" : "");
        GoalAlert.show(c, "md", "ANPFIFF UM " + when, e.m.t1.shortName, e.m.t2.shortName, "–", sub, "", c1, c2);
        if (on(c, "md_light", false)) light(c, e, true);
        if (auto.equals("radio")) radio(c, e);
        else if (auto.equals("tv") && !tv.isEmpty()) {
            final String first = tv.get(0);
            final Context cc = c;
            GoalAlert.ui.postDelayed(new Runnable() { public void run() { Watch.open(cc, first); } }, 8000);
        }
    }
    static void radio(final Context c, Ev e) {
        if (e.code.equals("hb2")) {
            final String u = Style.prefs(c).getString("hb_radio", "").trim();
            if (!u.isEmpty()) GoalAlert.ui.post(new Runnable() { public void run() { RadioService.playCustom(c, "sport_hb", Sport.hbClub(c) + " live", 0xFF0067B1, u); } });
            return;
        }
        try {
            Sport.League l = Sport.load(e.code, e.day);
            Sport.mapRadio(l);
            String url = null, name = null;
            for (Sport.Match m : l.matches) if (m.id == e.m.id && m.radio != null) { url = m.radio; name = m.t1.shortName + " – " + m.t2.shortName + " (Sportschau)"; }
            if (url == null) { url = Sport.stream(e.code, "konferenz"); name = "Sportschau " + l.name + " – Konferenz"; }
            final String u = url, n = name;
            GoalAlert.ui.post(new Runnable() { public void run() { RadioService.playCustom(c, "sport_md", n, 0xFF0067B1, u); } });
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ nach dem Spiel
    static void after(Context c, Ev e) throws Exception {
        boolean hb = e.code.equals("hb2");
        Sport.League l = hb ? Sport.loadHb(e.day) : Sport.load(e.code, e.day);
        Sport.Match m = null;
        for (Sport.Match x : l.matches) if (x.t1.name.equals(e.m.t1.name) && x.t2.name.equals(e.m.t2.name)) m = x;
        if (m == null || !m.finished) return;
        e.summarized = true;
        if (e.lightOn) light(c, e, false);
        if (hb || !on(c, "md_after", true) || !Daily.screenOn(c)) return;   // Handball meldet den Endstand selbst
        boolean home = mine(c, m.t1);
        int my = home ? m.s1 : m.s2, other = home ? m.s2 : m.s1;
        String club = home ? m.t1.shortName : m.t2.shortName;
        String res = my > other ? "Sieg für " + club + "!" : my < other ? "Niederlage für " + club : "Unentschieden";
        // Tabellenplatz (OpenLigaDB rechnet live mit)
        String place = "";
        for (Sport.Row r : l.table) if (r.team.name.equals(home ? m.t1.name : m.t2.name)) place = "  ·  jetzt Platz " + r.pos + " (" + r.points + " Pkt.)";
        GoalAlert.show(c, "md", "ENDSTAND", m.t1.shortName, m.t2.shortName, m.s1 + " : " + m.s2, res + place, "", GoalAlert.crest(m.t1.icon), GoalAlert.crest(m.t2.icon));
    }

    // ------------------------------------------------------------ Stadion-Licht
    static void light(final Context c, final Ev e, final boolean on) {
        e.lightOn = on;
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject cfg = Style.loadConfig(c);
                String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
                if (!on) { Style.httpPost(base + "/api/services/scene/turn_on", tok, new JSONObject().put("entity_id", "scene.midnox_stadion_vorher").put("transition", 2).toString()); return; }
                JSONArray ids = GoalAlert.lights(c);
                if (ids.length() == 0) return;
                String club = mine(c, e.m.t1) || Sport.isClub(c, e.m.t1) ? e.m.t1.name : e.m.t2.name;
                int[] col = GoalAlert.colors(club);
                if (col == null) col = new int[]{0x2D68C4, 0xFFFFFF};   // Handball/unbekannt: Blau
                col = GoalAlert.custom(c, col);
                Style.httpPost(base + "/api/services/scene/create", tok, new JSONObject().put("scene_id", "midnox_stadion_vorher").put("snapshot_entities", ids).toString());
                // abwechselnd Farbe 1 und 2 auf die Lampen verteilen
                for (int i = 0; i < ids.length(); i++) {
                    int cc = col[i % 2];
                    JSONObject d = new JSONObject().put("entity_id", ids.getString(i)).put("brightness_pct", 70).put("transition", 2)
                        .put("rgb_color", new JSONArray().put((cc >> 16) & 0xFF).put((cc >> 8) & 0xFF).put(cc & 0xFF));
                    try { Style.httpPost(base + "/api/services/light/turn_on", tok, d.toString()); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }}).start();
    }

    static void test(Context c) {
        Sport.Match m = new Sport.Match(); m.t1 = new Sport.Team(); m.t2 = new Sport.Team();
        m.t1.name = m.t1.shortName = "Hamburger SV"; m.t2.name = m.t2.shortName = "FC St. Pauli";
        m.kickoff = System.currentTimeMillis() + 15 * 60000L;
        Ev e = ev("bl1", m, 10);
        GoalAlert.show(c, "md", "ANPFIFF UM " + new java.text.SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(m.kickoff)), "HSV", "St. Pauli", "–", "Test · Läuft bei: " + Watch.label(Watch.who("bl1", m, 10)), "", null, null);
    }
}
