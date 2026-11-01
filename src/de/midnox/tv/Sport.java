package de.midnox.tv;

import android.content.Context;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Sportdaten: Bundesliga 1/2 (OpenLigaDB, live mit Torschuetzen), Formel 1 (Jolpica-Kalender/Wertung,
 * OpenF1-Liveposition) und die Live-Reportagen der ARD-Sportschau.
 */
final class Sport {
    // ------------------------------------------------------------ Fussball
    static final class Team { String name, shortName, icon; }
    static final class Goal { int minute, s1, s2; String scorer; boolean team1, penalty, own; }
    static final class Match {
        int id; long kickoff; Team t1, t2; int s1 = -1, s2 = -1; boolean finished;
        final List<Goal> goals = new ArrayList<>();
        String radio;   // Sportschau-Stream fuer genau dieses Spiel (wenn ermittelt)
        boolean live(long now) { return !finished && now >= kickoff && now < kickoff + 125 * 60000L; }
        boolean started(long now) { return now >= kickoff; }
        /** "LIVE 63'", "Halbzeit", "Ende", "Sa 15:30". */
        String status(long now) {
            if (finished) return "Ende";
            if (!started(now)) {
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(kickoff);
                Calendar t = Calendar.getInstance();
                boolean today = c.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == t.get(Calendar.YEAR);
                return new SimpleDateFormat(today ? "'heute' HH:mm" : "EE HH:mm", Locale.GERMANY).format(new Date(kickoff));
            }
            long m = (now - kickoff) / 60000 + 1;
            if (m <= 46) return "LIVE " + Math.min(m, 45) + (m > 45 ? "+" : "") + "'";
            if (m <= 62) return "Halbzeit";
            long s = m - 17;
            return s > 90 ? "LIVE 90+'" : "LIVE " + s + "'";
        }
    }
    static final class Row { int pos, played, won, draw, lost, goals, against, diff, points; Team team; }
    static final class League { String code, name; int season, day, maxDay; final List<Match> matches = new ArrayList<>(); final List<Row> table = new ArrayList<>(); long loaded; }

    static int season() { Calendar c = Calendar.getInstance(); return c.get(Calendar.MONTH) >= Calendar.JULY ? c.get(Calendar.YEAR) : c.get(Calendar.YEAR) - 1; }

    /** SVG-Wappen von Wikimedia als PNG-Vorschau laden (Android kann kein SVG). */
    static String icon(String u) {
        if (u == null || u.isEmpty()) return null;
        if (u.startsWith("http://")) u = "https://" + u.substring(7);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^https://upload\\.wikimedia\\.org/wikipedia/(commons|de|en)/([0-9a-f])/([0-9a-f]{2})/(.+\\.svg)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(u);
        if (m.find()) return "https://upload.wikimedia.org/wikipedia/" + m.group(1) + "/thumb/" + m.group(2) + "/" + m.group(3) + "/" + m.group(4) + "/120px-" + m.group(4) + ".png";
        return u;
    }
    static Team team(JSONObject o) {
        Team t = new Team();
        t.name = o.optString("teamName"); t.shortName = o.optString("shortName", t.name);
        if (t.shortName.isEmpty()) t.shortName = t.name;
        t.icon = icon(o.optString("teamIconUrl"));
        return t;
    }

    static int currentDay(String code) throws Exception {
        return new JSONObject(Style.httpGet("https://api.openligadb.de/getcurrentgroup/" + code, null)).optInt("groupOrderID", 1);
    }

    static League load(String code, int day) throws Exception {
        League l = new League();
        l.code = code; l.name = code.equals("bl1") ? "1. Bundesliga" : "2. Bundesliga";
        l.season = season(); l.maxDay = 34;
        if (day <= 0) day = currentDay(code);
        l.day = day;
        JSONArray a = new JSONArray(Style.httpGet("https://api.openligadb.de/getmatchdata/" + code + "/" + l.season + "/" + day, null));
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Match m = new Match();
            m.id = o.optInt("matchID");
            try { m.kickoff = utc.parse(o.optString("matchDateTimeUTC")).getTime(); } catch (Exception e) { m.kickoff = 0; }
            m.t1 = team(o.getJSONObject("team1")); m.t2 = team(o.getJSONObject("team2"));
            m.finished = o.optBoolean("matchIsFinished");
            JSONArray res = o.optJSONArray("matchResults");
            for (int k = 0; res != null && k < res.length(); k++) {
                JSONObject r = res.getJSONObject(k);
                if (r.optInt("resultTypeID") == 2 || (m.s1 < 0 && r.optInt("resultTypeID") == 1)) { m.s1 = r.optInt("pointsTeam1"); m.s2 = r.optInt("pointsTeam2"); }
            }
            JSONArray gs = o.optJSONArray("goals");
            int t1id = o.getJSONObject("team1").optInt("teamId");
            int p1 = 0, p2 = 0;
            for (int k = 0; gs != null && k < gs.length(); k++) {
                JSONObject g = gs.getJSONObject(k);
                Goal x = new Goal();
                x.minute = g.optInt("matchMinute"); x.s1 = g.optInt("scoreTeam1"); x.s2 = g.optInt("scoreTeam2");
                x.scorer = g.optString("goalGetterName", "").replace("null", "").trim();
                x.penalty = g.optBoolean("isPenalty"); x.own = g.optBoolean("isOwnGoal");
                x.team1 = g.has("scoringTeamId") && !g.isNull("scoringTeamId") ? g.optInt("scoringTeamId") == t1id : (x.s1 > p1);
                p1 = x.s1; p2 = x.s2;
                m.goals.add(x);
            }
            if (!m.goals.isEmpty() && !m.finished) { Goal last = m.goals.get(m.goals.size() - 1); m.s1 = last.s1; m.s2 = last.s2; }
            if (m.s1 < 0 && m.started(System.currentTimeMillis())) { m.s1 = 0; m.s2 = 0; }
            l.matches.add(m);
        }
        Collections.sort(l.matches, new Comparator<Match>() { public int compare(Match x, Match y) { return x.kickoff != y.kickoff ? Long.compare(x.kickoff, y.kickoff) : Integer.compare(x.id, y.id); } });
        try {
            JSONArray t = new JSONArray(Style.httpGet("https://api.openligadb.de/getbltable/" + code + "/" + l.season, null));
            for (int i = 0; i < t.length(); i++) {
                JSONObject o = t.getJSONObject(i);
                Row r = new Row();
                r.pos = i + 1; r.team = team(o);
                r.played = o.optInt("matches"); r.won = o.optInt("won"); r.draw = o.optInt("draw"); r.lost = o.optInt("lost");
                r.goals = o.optInt("goals"); r.against = o.optInt("opponentGoals"); r.diff = o.optInt("goalDiff"); r.points = o.optInt("points");
                l.table.add(r);
            }
        } catch (Exception ignored) {}
        emblems(l);
        l.loaded = System.currentTimeMillis();
        return l;
    }

    // ------------------------------------------------------------ Radio (ARD Sportschau)
    static String stream(String code, String which) {   // which: "konferenz" oder "spiel1".."spiel9"
        return "https://dispatcher.rndfnk.com/wdr/sportschau/liga" + (code.equals("bl1") ? "1" : "2") + which + "/mp3/128/stream.mp3";
    }

    static final Map<String, Map<String, String>> EMBLEMS = new HashMap<>();   // Liga -> Vereinsname -> PNG
    static String pageUrl(String code) { return "https://www.sportschau.de/live-und-ergebnisse/fussball/" + (code.equals("bl1") ? "deutschland-bundesliga/" : "deutschland-2-bundesliga/"); }
    /** Vereinswappen als PNG von der Sportschau (Wikimedia-SVGs kann Android nicht anzeigen). */
    static void emblems(League l) {
        Map<String, String> em = EMBLEMS.get(l.code);
        if (em == null) {
            em = new HashMap<>();
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("<img[^>]*src=\"(https://s\\.hs-data\\.com/gfx/emblem/[^\"]+\\.png)\"[^>]*alt=\"([^\"]+)\"").matcher(RadioNet.get(pageUrl(l.code)));
                while (m.find()) em.put(m.group(2), m.group(1).replace("/80x80/", "/80x80/"));
                if (!em.isEmpty()) EMBLEMS.put(l.code, em);
            } catch (Exception ignored) {}
        }
        List<Team> teams = new ArrayList<>();
        for (Match m : l.matches) { teams.add(m.t1); teams.add(m.t2); }
        for (Row r : l.table) teams.add(r.team);
        for (Team t : teams) for (Map.Entry<String, String> e : em.entrySet()) if (matches(e.getKey(), t)) { t.icon = e.getValue(); break; }
    }

    /** Zuordnung Spiel -> Sportschau-Reportage aus der Ergebnisseite der Sportschau (Anstosszeit + Mannschaften). */
    static void mapRadio(League l) {
        try {
            String page = RadioNet.get(pageUrl(l.code));
            SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            utc.setTimeZone(TimeZone.getTimeZone("UTC"));
            String[] blocks = page.split("<li data-match_id=");
            for (Match m : l.matches) m.radio = null;
            for (int i = 1; i < blocks.length; i++) {
                String bl = blocks[i];
                java.util.regex.Matcher dt = java.util.regex.Pattern.compile("data-datetime=\"([^\"]+)\"").matcher(bl);
                java.util.regex.Matcher au = java.util.regex.Pattern.compile("data-audiolivestream=\"([^\"]+)\"").matcher(bl);
                if (!dt.find() || !au.find()) continue;
                long at; try { at = utc.parse(dt.group(1)).getTime(); } catch (Exception e) { continue; }
                String stream = au.group(1).replace("&amp;", "&");
                StringBuilder names = new StringBuilder();
                java.util.regex.Matcher nm = java.util.regex.Pattern.compile("(?:team-name[^\"]*\">|alt=\")([^<\"]+)").matcher(bl);
                while (nm.find()) names.append(nm.group(1)).append(' ');
                for (Match m : l.matches) {
                    if (Math.abs(m.kickoff - at) > 5 * 60000L) continue;
                    if (matches(names.toString(), m.t1) || matches(names.toString(), m.t2)) { m.radio = stream; break; }
                }
            }
        } catch (Exception ignored) {}
    }
    static boolean matches(String title, Team t) {
        String x = norm(title);
        for (String w : (t.shortName + " " + t.name).split("[\\s.-]+")) {
            String n = norm(w);
            if (n.length() >= 4 && !n.matches("(borussia|eintracht|union|werder|fortuna|arminia|holstein|hertha|fussball|sport|verein|club|klub|1899|1860|1846|1904|1909|1910)") && x.contains(n)) return true;
        }
        return false;
    }
    static String norm(String s) { return s.toLowerCase(Locale.GERMANY).replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss").replaceAll("[^a-z0-9]", ""); }

    static String icyTitle(String url) {
        RadioNet.Resp r = null;
        try {
            r = RadioNet.open(url, true);
            if (r.code != 200 || r.metaint <= 0) return null;
            r.s.setSoTimeout(6000);
            long skip = r.metaint;
            byte[] buf = new byte[8192];
            while (skip > 0) { int n = r.in.read(buf, 0, (int) Math.min(buf.length, skip)); if (n < 0) return null; skip -= n; }
            int len = r.in.read() * 16;
            if (len <= 0) return "";
            byte[] m = new byte[len]; int got = 0;
            while (got < len) { int n = r.in.read(m, got, len - got); if (n < 0) break; got += n; }
            String s = new String(m, 0, got, "UTF-8");
            int a = s.indexOf("StreamTitle='");
            if (a < 0) return "";
            int b = s.indexOf("';", a + 13);
            return s.substring(a + 13, b < 0 ? s.length() : b);
        } catch (Exception e) { return null; }
        finally { if (r != null) r.close(); }
    }

    // ------------------------------------------------------------ Formel 1
    static final class Session { String name; long at; }
    static final class Race { String name, circuit, place; int round; long at; final List<Session> sessions = new ArrayList<>(); }
    static final class Pos { int pos; String code, name, team, gap; int color; double points; }
    static final class F1 {
        Race next, last; final List<Pos> lastResult = new ArrayList<>(), drivers = new ArrayList<>(), teams = new ArrayList<>();
        String liveSession; final List<Pos> live = new ArrayList<>();
    }
    static long parse(String date, String time) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(date + "T" + (time == null || time.isEmpty() ? "12:00:00Z" : time)).getTime();
        } catch (Exception e) { return 0; }
    }
    static Race race(JSONObject o) {
        Race r = new Race();
        r.name = o.optString("raceName").replace("Grand Prix", "GP"); r.round = o.optInt("round");
        JSONObject c = o.optJSONObject("Circuit");
        if (c != null) { r.circuit = c.optString("circuitName"); JSONObject loc = c.optJSONObject("Location"); r.place = loc == null ? "" : loc.optString("locality") + ", " + loc.optString("country"); }
        r.at = parse(o.optString("date"), o.optString("time"));
        String[][] ss = {{"FirstPractice", "1. Training"}, {"SecondPractice", "2. Training"}, {"ThirdPractice", "3. Training"}, {"SprintQualifying", "Sprint-Qualifying"}, {"Sprint", "Sprint"}, {"Qualifying", "Qualifying"}};
        for (String[] s : ss) {
            JSONObject x = o.optJSONObject(s[0]);
            if (x == null) continue;
            Session se = new Session(); se.name = s[1]; se.at = parse(x.optString("date"), x.optString("time"));
            r.sessions.add(se);
        }
        Session rc = new Session(); rc.name = "Rennen"; rc.at = r.at; r.sessions.add(rc);
        Collections.sort(r.sessions, new Comparator<Session>() { public int compare(Session a, Session b) { return Long.compare(a.at, b.at); } });
        return r;
    }

    static F1 loadF1() throws Exception {
        F1 f = new F1();
        long now = System.currentTimeMillis();
        JSONArray races = new JSONObject(Style.httpGet("https://api.jolpi.ca/ergast/f1/current.json", null)).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
        for (int i = 0; i < races.length(); i++) {
            Race r = race(races.getJSONObject(i));
            if (r.at + 3 * 3600000L > now) { f.next = r; break; }
        }
        try {
            JSONArray lr = new JSONObject(Style.httpGet("https://api.jolpi.ca/ergast/f1/current/last/results.json", null)).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            if (lr.length() > 0) {
                f.last = race(lr.getJSONObject(0));
                JSONArray res = lr.getJSONObject(0).getJSONArray("Results");
                for (int i = 0; i < res.length(); i++) {
                    JSONObject x = res.getJSONObject(i); Pos p = new Pos();
                    p.pos = x.optInt("position"); JSONObject d = x.getJSONObject("Driver");
                    p.code = d.optString("code"); p.name = d.optString("givenName") + " " + d.optString("familyName");
                    p.team = x.getJSONObject("Constructor").optString("name"); p.color = teamColor(p.team);
                    JSONObject t = x.optJSONObject("Time");
                    p.gap = t != null ? t.optString("time") : x.optString("status");
                    p.points = x.optDouble("points", 0);
                    f.lastResult.add(p);
                }
            }
        } catch (Exception ignored) {}
        try {
            JSONArray ds = new JSONObject(Style.httpGet("https://api.jolpi.ca/ergast/f1/current/driverStandings.json", null)).getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0).getJSONArray("DriverStandings");
            for (int i = 0; i < ds.length(); i++) {
                JSONObject x = ds.getJSONObject(i); Pos p = new Pos();
                p.pos = x.optInt("position", i + 1); JSONObject d = x.getJSONObject("Driver");
                p.code = d.optString("code"); p.name = d.optString("givenName") + " " + d.optString("familyName");
                JSONArray cs = x.optJSONArray("Constructors"); p.team = cs != null && cs.length() > 0 ? cs.getJSONObject(0).optString("name") : "";
                p.color = teamColor(p.team); p.points = x.optDouble("points", 0);
                f.drivers.add(p);
            }
        } catch (Exception ignored) {}
        try {
            JSONArray cs = new JSONObject(Style.httpGet("https://api.jolpi.ca/ergast/f1/current/constructorStandings.json", null)).getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0).getJSONArray("ConstructorStandings");
            for (int i = 0; i < cs.length(); i++) {
                JSONObject x = cs.getJSONObject(i); Pos p = new Pos();
                p.pos = x.optInt("position", i + 1); p.team = p.name = x.getJSONObject("Constructor").optString("name");
                p.color = teamColor(p.team); p.points = x.optDouble("points", 0);
                f.teams.add(p);
            }
        } catch (Exception ignored) {}
        loadLive(f);
        return f;
    }

    /** Laufende Session: Positionen und Abstaende von OpenF1. */
    static void loadLive(F1 f) {
        f.live.clear(); f.liveSession = null;
        try {
            JSONObject s = new JSONArray(Style.httpGet("https://api.openf1.org/v1/sessions?session_key=latest", null)).getJSONObject(0);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
            long a = iso.parse(s.optString("date_start")).getTime(), b = iso.parse(s.optString("date_end")).getTime(), now = System.currentTimeMillis();
            if (now < a - 10 * 60000L || now > b + 20 * 60000L) return;
            String key = s.optString("session_key");
            f.liveSession = s.optString("session_name");
            Map<Integer, Pos> by = new HashMap<>();
            JSONArray dr = new JSONArray(Style.httpGet("https://api.openf1.org/v1/drivers?session_key=" + key, null));
            for (int i = 0; i < dr.length(); i++) {
                JSONObject d = dr.getJSONObject(i); Pos p = new Pos();
                p.code = d.optString("name_acronym"); p.name = d.optString("full_name"); p.team = d.optString("team_name");
                try { p.color = 0xFF000000 | Integer.parseInt(d.optString("team_colour", "888888"), 16); } catch (Exception e) { p.color = 0xFF888888; }
                p.gap = ""; by.put(d.optInt("driver_number"), p);
            }
            String since = iso.format(new Date(Math.max(a, now - 4 * 60000L))).replace("+", "%2B");
            JSONArray ps = new JSONArray(Style.httpGet("https://api.openf1.org/v1/position?session_key=" + key + (now < b ? "&date>" + since : ""), null));
            for (int i = 0; i < ps.length(); i++) { JSONObject x = ps.getJSONObject(i); Pos p = by.get(x.optInt("driver_number")); if (p != null) p.pos = x.optInt("position"); }
            try {
                JSONArray iv = new JSONArray(Style.httpGet("https://api.openf1.org/v1/intervals?session_key=" + key + (now < b ? "&date>" + since : ""), null));
                for (int i = 0; i < iv.length(); i++) {
                    JSONObject x = iv.getJSONObject(i); Pos p = by.get(x.optInt("driver_number"));
                    if (p == null) continue;
                    Object g = x.opt("gap_to_leader");
                    p.gap = g == null || g == JSONObject.NULL ? "" : g instanceof Number ? (((Number) g).doubleValue() == 0 ? "" : "+" + String.format(Locale.GERMANY, "%.3f", ((Number) g).doubleValue())) : String.valueOf(g);
                }
            } catch (Exception ignored) {}
            for (Pos p : by.values()) if (p.pos > 0) f.live.add(p);
            Collections.sort(f.live, new Comparator<Pos>() { public int compare(Pos x, Pos y) { return x.pos - y.pos; } });
        } catch (Exception ignored) {}
    }

    static int teamColor(String t) {
        String n = t.toLowerCase(Locale.ROOT);
        if (n.contains("mclaren")) return 0xFFFF8000; if (n.contains("ferrari")) return 0xFFE8002D; if (n.contains("red bull")) return 0xFF3671C6;
        if (n.contains("mercedes")) return 0xFF27F4D2; if (n.contains("aston")) return 0xFF229971; if (n.contains("alpine")) return 0xFFFF87BC;
        if (n.contains("williams")) return 0xFF64C4FF; if (n.contains("haas")) return 0xFFB6BABD; if (n.contains("sauber") || n.contains("audi")) return 0xFF52E252;
        if (n.contains("rb") || n.contains("racing bulls")) return 0xFF6692FF; if (n.contains("cadillac")) return 0xFFC0A060;
        return 0xFF8A8FA8;
    }
}
