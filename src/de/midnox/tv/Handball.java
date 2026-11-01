package de.midnox.tv;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import java.util.*;

/** Handball (2. HBL): Spielstand-Einblendungen fuer den eigenen Verein, Dyn oeffnen, eigenes Radio. */
final class Handball {
    static long nextSchedule, lastPoll;
    static Sport.Match watch;          // naechstes/aktuelles Spiel des Vereins
    static String lastScore = null;    // zuletzt gesehener Stand
    static long lastChange, lastShown;
    static boolean announcedStart, announcedEnd;

    static boolean on(Context c, String k, boolean d) { return Style.prefs(c).getBoolean(k, d); }

    /** Wird aus Daily.tick (ca. alle 20 s) aufgerufen; nur rund um Spiele des Vereins wird abgefragt. */
    static void tick(final Context c) {
        if (!on(c, "hb_alert", true)) return;
        long now = System.currentTimeMillis();
        if (now > nextSchedule) {
            nextSchedule = now + 3 * 3600000L;
            new Thread(new Runnable() { public void run() { try { schedule(c); } catch (Exception e) { nextSchedule = System.currentTimeMillis() + 15 * 60000L; } } }).start();
            return;
        }
        Sport.Match w = watch;
        if (w == null || now < w.kickoff - 5 * 60000L || now > w.kickoff + 150 * 60000L || announcedEnd) return;
        if (now - lastPoll < 60000L) return;
        lastPoll = now;
        new Thread(new Runnable() { public void run() { try { poll(c); } catch (Exception ignored) {} } }).start();
    }

    static void schedule(Context c) throws Exception {
        Sport.League l = Sport.loadHb(0);
        Sport.Match best = null;
        long now = System.currentTimeMillis();
        List<Sport.League> ls = new ArrayList<>(); ls.add(l);
        if (l.day < l.maxDay) try { ls.add(Sport.loadHb(l.day + 1)); } catch (Exception ignored) {}
        for (Sport.League x : ls) for (Sport.Match m : x.matches)
            if ((Sport.isClub(c, m.t1) || Sport.isClub(c, m.t2)) && m.kickoff + 150 * 60000L > now && (best == null || m.kickoff < best.kickoff)) best = m;
        if (best != null && (watch == null || watch.id != best.id || watch.kickoff != best.kickoff)) {
            watch = best; lastScore = null; announcedStart = false; announcedEnd = false; lastShown = 0;
        }
        // naechste Abfrage rechtzeitig vor dem Anpfiff
        if (best != null && best.kickoff - 10 * 60000L > now) nextSchedule = Math.min(nextSchedule, best.kickoff - 10 * 60000L);
    }

    static void poll(Context c) throws Exception {
        Sport.Match w = watch;
        if (w == null) return;
        Sport.League l = Sport.loadHb(0);
        Sport.Match m = null;
        for (Sport.Match x : l.matches) if (x.id == w.id) m = x;
        if (m == null) return;
        long now = System.currentTimeMillis();
        String sc = m.s1 < 0 ? null : m.s1 + " : " + m.s2;
        boolean changed = sc != null && !sc.equals(lastScore);
        if (changed) { lastScore = sc; lastChange = now; }
        int every = Style.prefs(c).getInt("hb_every", 10);
        if (sc != null && !announcedStart) {
            announcedStart = true; lastShown = now;
            if (on(c, "hb_start", true)) show(c, m, "ANPFIFF", sc, "Das Spiel läuft");
            return;
        }
        // Ende: laut HBL beendet, sonst Stand seit 8 Min. unveraendert und Spiel laeuft schon > 95 Min.
        if (sc != null && (m.hbState == 2 || m.hbState == 0 && now > m.kickoff + 95 * 60000L && now - lastChange > 8 * 60000L)) {
            announcedEnd = true;
            String place = "";
            for (Sport.Row r : l.table) if (Sport.isClub(c, r.team)) place = "  ·  Platz " + r.pos;
            if (on(c, "hb_end", true)) show(c, m, "ENDSTAND", sc, verdict(c, m) + place);
            return;
        }
        if (sc != null && every > 0 && now - lastShown >= every * 60000L && changed) {
            lastShown = now;
            show(c, m, "ZWISCHENSTAND", sc, lead(c, m));
        }
    }

    static String verdict(Context c, Sport.Match m) {
        boolean home = Sport.isClub(c, m.t1);
        int my = home ? m.s1 : m.s2, other = home ? m.s2 : m.s1;
        String club = home ? m.t1.shortName : m.t2.shortName;
        return my > other ? "Sieg für " + club + "! 🎉" : my < other ? "Niederlage für " + club : "Unentschieden";
    }
    static String lead(Context c, Sport.Match m) {
        boolean home = Sport.isClub(c, m.t1);
        int d = (home ? m.s1 - m.s2 : m.s2 - m.s1);
        String club = home ? m.t1.shortName : m.t2.shortName;
        return d > 0 ? club + " führt mit " + d : d < 0 ? club + " liegt " + (-d) + " zurück" : "Gleichstand";
    }
    static void show(Context c, Sport.Match m, String title, String score, String sub) {
        GoalAlert.show(c, "hb", title, m.t1.shortName, m.t2.shortName, score, sub, "", null, null);
    }
    static void test(Context c) {
        Sport.Match m = new Sport.Match(); m.t1 = new Sport.Team(); m.t2 = new Sport.Team();
        m.t1.shortName = "Lübeck-Schwartau"; m.t2.shortName = "Eintracht Hagen"; m.t1.name = "VfL Lübeck-Schwartau"; m.t2.name = "Eintracht Hagen";
        show(c, m, "ZWISCHENSTAND", "17 : 14", "Test: so sieht die Einblendung aus");
    }

    // ------------------------------------------------------------ Zuhören / Zusehen
    /** Eigener Radio-Link (z. B. Vereins-Webradio) oder Dyn. Liefert Text fuer den Hinweis. */
    static String listen(Context c) {
        String u = Style.prefs(c).getString("hb_radio", "").trim();
        if (!u.isEmpty()) {
            if (RadioService.isPlaying(u)) { RadioService.start(c, "stop"); return "Radio aus"; }
            RadioService.playCustom(c, "sport_hb", Sport.hbClub(c) + " live", 0xFF0067B1, u);
            return "Live-Radio läuft";
        }
        return openDyn(c);
    }
    static String openDyn(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String p : new String[]{"sport.dyn.app.androidtv.prod", "sport.dyn.app.firetv.prod", "sport.dyn.app.android.prod"}) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(p);
            if (i == null) i = pm.getLaunchIntentForPackage(p);
            if (i != null) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Dyn wird geöffnet"; }
        }
        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
            String label = String.valueOf(pm.getApplicationLabel(a));
            if (a.packageName.contains(".dyn.") || label.equalsIgnoreCase("Dyn") || label.toLowerCase(Locale.ROOT).startsWith("dyn ")) {
                Intent i = pm.getLeanbackLaunchIntentForPackage(a.packageName);
                if (i == null) i = pm.getLaunchIntentForPackage(a.packageName);
                if (i != null) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Dyn wird geöffnet"; }
            }
        }
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?s=" + Uri.encode("Dyn Sport"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Dyn ist nicht installiert – Amazon Appstore geöffnet"; }
        catch (Exception e) { return "Dyn ist nicht installiert"; }
    }
}
