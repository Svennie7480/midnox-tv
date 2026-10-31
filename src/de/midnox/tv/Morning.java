package de.midnox.tv;

import android.content.*;
import android.util.Xml;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import java.io.StringReader;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * "Guten Morgen": sammelt die wichtigen Infos des Tages aus Home Assistant
 * (Warnungen, Dienst, Termine, Geburtstage, Muell, Auto, Wetter) und Nachrichten
 * (tagesschau + NDR Schleswig-Holstein). Wird morgens beim ersten Einschalten gezeigt.
 */
public class Morning {
    static final int WARN = 0, SHIFT = 1, CAL = 2, BDAY = 3, TRASH = 4, CAR = 5, INFO = 6;
    static class Line { int kind; String title, sub; Line(int k, String t, String s) { kind = k; title = t; sub = s == null ? "" : s; } }
    static class News { String title, desc, img, src; }
    static class Day {
        String name = "", holiday = "", sun = "";
        Weather.Now weather;
        final List<Line> lines = new ArrayList<>();
        final List<News> news = new ArrayList<>();
    }

    static final String[][] FEEDS = {
        {"tagesschau", "https://www.tagesschau.de/index~rss2.xml"},
        {"NDR Schleswig-Holstein", "https://www.ndr.de/nachrichten/schleswig-holstein/index~rss2.xml"},
    };

    // ------------------------------------------------------------ wann zeigen?
    static String today() { return new SimpleDateFormat("yyyyMMdd", Locale.GERMANY).format(new Date()); }

    /** Einmal pro Tag im Zeitfenster (Standard 4–11 Uhr) beim Einschalten. */
    static void maybe(Context c) {
        try {
            SharedPreferences pr = Style.prefs(c);
            if (!pr.getBoolean("morning_on", true)) return;
            if (pr.getBoolean("sleep_mode", false) || Sleep.at(c) > 0) return;
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (h < pr.getInt("morning_from", 4) || h >= pr.getInt("morning_to", 11)) return;
            String t = today();
            if (t.equals(pr.getString("morning_day", ""))) return;
            pr.edit().putString("morning_day", t).apply();
            show(c);
        } catch (Exception ignored) {}
    }
    static void show(Context c) {
        Intent i = new Intent(c, MorningActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        c.startActivity(i);
    }

    static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 4 ? "Gute Nacht" : h < 11 ? "Guten Morgen" : h < 17 ? "Hallo" : "Guten Abend";
    }

    // ------------------------------------------------------------ Daten sammeln
    static Day load(Context c) {
        Day d = new Day();
        SharedPreferences pr = Style.prefs(c);
        d.name = pr.getString("morning_name", "");
        JSONObject cfg = Style.loadConfig(c);
        if (cfg != null && cfg.has("url")) {
            try { ha(c, cfg, d); } catch (Exception ignored) {}
            d.weather = Weather.fetch(c);
        }
        int mode = pr.getInt("morning_news", 2);
        if (mode >= 1) feed(d, FEEDS[0], 5);
        if (mode >= 2) feed(d, FEEDS[1], 4);
        return d;
    }

    static void ha(Context c, JSONObject cfg, Day d) throws Exception {
        String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
        JSONArray states = new JSONArray(Style.httpGet(base + "/api/states", tok));
        Map<String, JSONObject> byId = new HashMap<>();
        for (int i = 0; i < states.length(); i++) { JSONObject s = states.getJSONObject(i); byId.put(s.getString("entity_id"), s); }

        // Name: einzige Person in Home Assistant (wenn nicht eingestellt)
        if (d.name.isEmpty()) {
            List<String> persons = new ArrayList<>();
            for (String id : byId.keySet()) if (id.startsWith("person.")) persons.add(id);
            JSONObject p = persons.size() == 1 ? byId.get(persons.get(0)) : byId.get("person.sven");
            if (p == null) for (String id : persons) if ("home".equals(byId.get(id).optString("state"))) { p = byId.get(id); break; }
            if (p != null) d.name = attrs(p).optString("friendly_name");
        }

        // Unwetter- und Katastrophenwarnungen (NINA / DWD)
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, JSONObject> e : byId.entrySet()) {
            String id = e.getKey(); JSONObject s = e.getValue(), a = attrs(s);
            boolean nina = id.startsWith("binary_sensor.warning_") && "on".equals(s.optString("state"));
            boolean dwd = id.startsWith("sensor.") && id.contains("warn") && a.has("warning_count") && a.optInt("warning_count") > 0;
            if (nina) {
                String head = a.optString("headline", a.optString("friendly_name"));
                if (!seen.add(head)) continue;
                String place = a.optString("friendly_name").replaceAll("\\s*Warnung \\d+$", "").replaceAll(",.*", "");
                d.lines.add(new Line(WARN, head, place + (a.has("expires") ? " · bis " + hhmm(a.optString("expires")) : "")));
            } else if (dwd) {
                for (int i = 1; i <= a.optInt("warning_count"); i++) {
                    String head = a.optString("warning_" + i + "_headline", a.optString("warning_" + i + "_name"));
                    if (head.isEmpty() || !seen.add(head)) continue;
                    d.lines.add(new Line(WARN, head, "Deutscher Wetterdienst"));
                }
            }
        }

        // Dienst
        shift(c, byId, d);

        // Kalender: heute + morgen
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.GERMANY);
        Calendar from = Calendar.getInstance();
        from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0); from.set(Calendar.SECOND, 0); from.set(Calendar.MILLISECOND, 0);
        Calendar to = (Calendar) from.clone(); to.add(Calendar.DAY_OF_YEAR, 2);
        String sToday = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(from.getTime());
        Calendar tm = (Calendar) from.clone(); tm.add(Calendar.DAY_OF_YEAR, 1);
        String sTomorrow = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(tm.getTime());
        String range = "?start=" + URLEncoder.encode(iso.format(from.getTime()), "UTF-8") + "&end=" + URLEncoder.encode(iso.format(to.getTime()), "UTF-8");
        Pattern skip = Pattern.compile(Style.prefs(c).getString("morning_cal_skip", "f1|formel|llm|dienstplan|streaming"), Pattern.CASE_INSENSITIVE);

        List<String> trashToday = new ArrayList<>(), trashTomorrow = new ArrayList<>(), holidays = new ArrayList<>();
        List<Line> today = new ArrayList<>(), tomorrow = new ArrayList<>(), bdays = new ArrayList<>();
        Set<String> dup = new HashSet<>();
        JSONArray cals = new JSONArray(Style.httpGet(base + "/api/calendars", tok));
        for (int i = 0; i < cals.length(); i++) {
            JSONObject cal = cals.getJSONObject(i);
            String id = cal.optString("entity_id"), name = cal.optString("name");
            String key = (id + " " + name).toLowerCase(Locale.GERMANY);
            if (skip.matcher(key).find()) continue;
            if ("unavailable".equals(byId.containsKey(id) ? byId.get(id).optString("state") : "")) continue;
            boolean trash = key.matches(".*(m[uü]ll|abfall|entsorg|tonne).*"), bday = key.contains("geburtstag") || key.contains("birthday"), hol = key.contains("feiertag") || key.contains("holiday");
            JSONArray ev;
            try { ev = new JSONArray(Style.httpGet(base + "/api/calendars/" + id + range, tok)); } catch (Exception e) { continue; }
            for (int j = 0; j < ev.length(); j++) {
                JSONObject e = ev.getJSONObject(j);
                String sum = e.optString("summary").trim();
                JSONObject st = e.optJSONObject("start");
                if (st == null || sum.isEmpty()) continue;
                boolean allDay = st.has("date") && !st.has("dateTime");
                String when = allDay ? st.optString("date") : st.optString("dateTime");
                String day = when.length() >= 10 ? when.substring(0, 10) : "";
                boolean isToday = day.equals(sToday), isTomorrow = day.equals(sTomorrow);
                if (allDay && !isToday && !isTomorrow && day.compareTo(sToday) < 0) {   // mehrtaegig, laeuft noch
                    JSONObject en = e.optJSONObject("end");
                    if (en != null && en.optString("date").compareTo(sToday) > 0) isToday = true;
                }
                if (!isToday && !isTomorrow) continue;
                if (!dup.add(day + sum + (allDay ? "" : when))) continue;
                String time = allDay ? "" : when.substring(11, 16);
                if (trash) (isToday ? trashToday : trashTomorrow).add(sum.replaceAll("(?i)^restm[uü]lltonne$", "Restmüll").trim());
                else if (hol) { if (isToday) holidays.add(sum.replaceAll("\\s*\\(.*\\)", "")); }
                else if (bday) { if (isToday) bdays.add(new Line(BDAY, sum.toLowerCase(Locale.GERMANY).contains("geburtstag") ? sum : "Geburtstag: " + sum, "Heute")); }
                else if (isToday) today.add(new Line(CAL, (time.isEmpty() ? "" : time + "  ") + sum, allDay ? "Heute ganztägig" : "Heute"));
                else tomorrow.add(new Line(CAL, (time.isEmpty() ? "" : time + "  ") + sum, "Morgen"));
            }
        }
        Comparator<Line> byTime = new Comparator<Line>() { public int compare(Line a, Line b) { return a.title.compareTo(b.title); } };
        Collections.sort(today, byTime); Collections.sort(tomorrow, byTime);
        d.lines.addAll(bdays);
        d.lines.addAll(today);
        if (!trashToday.isEmpty()) d.lines.add(new Line(TRASH, "Heute Müllabfuhr: " + join(trashToday), "Tonnen sollten schon draußen stehen"));
        if (!trashTomorrow.isEmpty()) d.lines.add(new Line(TRASH, "Morgen Müllabfuhr: " + join(trashTomorrow), "Heute Abend rausstellen"));
        for (int i = 0; i < Math.min(2, tomorrow.size()); i++) d.lines.add(tomorrow.get(i));
        d.holiday = join(holidays);

        // Auto (z. B. BYD): Ladezustand + Reichweite
        for (String id : byId.keySet()) {
            if (!id.startsWith("sensor.") || !(id.endsWith("_ladezustand") || id.endsWith("_state_of_charge"))) continue;
            JSONObject s = byId.get(id);
            double soc;
            try { soc = Double.parseDouble(s.optString("state")); } catch (Exception e) { continue; }
            String pre = id.replaceAll("_(ladezustand|state_of_charge)$", "");
            JSONObject r = byId.containsKey(pre + "_reichweite") ? byId.get(pre + "_reichweite") : byId.get(pre + "_range");
            String name = attrs(s).optString("friendly_name").replaceAll("(?i)\\s*(ladezustand|state of charge)$", "").trim();
            if (name.equals(name.toUpperCase(Locale.ROOT))) name = cap(name);
            String range2 = r == null ? "" : " · " + Math.round(r.optDouble("state", 0)) + " km Reichweite";
            JSONObject ch = byId.get(pre.replace("sensor.", "binary_sensor.") + "_laden");
            boolean charging = ch != null && "on".equals(ch.optString("state"));
            d.lines.add(new Line(CAR, name + "  " + Math.round(soc) + " %" + range2, soc < 25 ? "Akku fast leer – heute laden" : charging ? "Lädt gerade" : "Bereit"));
        }

        // Sonne
        JSONObject sun = byId.get("sun.sun");
        if (sun != null) {
            String up = hhmm(attrs(sun).optString("next_rising")), down = hhmm(attrs(sun).optString("next_setting"));
            if (!up.isEmpty() && !down.isEmpty()) d.sun = "Sonnenaufgang " + up + "  ·  Sonnenuntergang " + down;
        }
    }

    static void shift(Context c, Map<String, JSONObject> byId, Day d) {
        try {
            Object[] n = Daily.nextShift(c);
            JSONObject det = byId.get("sensor.dienstdetail_heute");
            String ort = det == null ? "" : attrs(det).optString("start_ort");
            JSONArray lin = det == null ? null : attrs(det).optJSONArray("linien");
            if (n == null) return;
            Date start = (Date) n[0];
            Calendar sc = Calendar.getInstance(); sc.setTime(start);
            JSONObject ns = null;
            for (JSONObject s : byId.values()) if (s.optJSONObject("attributes") != null && s.optJSONObject("attributes").optString("start").equals(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).format(start))) { ns = s; break; }
            String bis = ns == null ? "" : attrs(ns).optString("end_time");
            String hm = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(start);
            String nr = String.valueOf(n[1]);
            if (HaPanel.same(sc, Calendar.getInstance())) {
                StringBuilder sub = new StringBuilder();
                if (!ort.isEmpty()) sub.append("Start ").append(ort);
                if (lin != null && lin.length() > 0) {
                    List<String> l = new ArrayList<>();
                    for (int i = 0; i < lin.length(); i++) l.add(lin.optString(i));
                    sub.append(sub.length() > 0 ? " · " : "").append("Linie ").append(join(l));
                }
                if (start.getTime() < System.currentTimeMillis()) sub.insert(0, "Läuft schon · ");
                d.lines.add(0, new Line(SHIFT, "Dienst " + nr + "  ·  " + hm + (bis.isEmpty() ? "" : "–" + bis), sub.toString()));
            } else {
                Calendar tm = Calendar.getInstance(); tm.add(Calendar.DAY_OF_YEAR, 1);
                String day = HaPanel.same(sc, tm) ? "morgen" : new SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(start);
                d.lines.add(0, new Line(SHIFT, "Heute dienstfrei", "Nächster Dienst " + day + " um " + hm + (nr.isEmpty() ? "" : " (" + nr + ")")));
            }
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ Nachrichten
    static void feed(Day d, String[] f, int max) {
        try {
            String xml;
            try { xml = Style.httpGet(f[1], null); } catch (Exception e) { xml = RadioNet.get(f[1]); }
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(new StringReader(xml));
            News cur = null; String tag = null; int n = 0;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT && n < max; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("item".equals(tag)) { cur = new News(); cur.src = f[0]; }
                } else if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) {
                    if (cur == null || tag == null) continue;
                    String t = p.getText();
                    if ("title".equals(tag)) cur.title = (cur.title == null ? "" : cur.title) + t;
                    else if ("description".equals(tag)) cur.desc = (cur.desc == null ? "" : cur.desc) + t;
                    else if ("content:encoded".equals(tag) && cur.img == null) {
                        Matcher m = Pattern.compile("<img[^>]+src=\"([^\"]+)\"").matcher(t);
                        if (m.find()) cur.img = m.group(1).replace("&amp;", "&").replaceAll("width=\\d+", "width=640");
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("item".equals(p.getName()) && cur != null) {
                        String t = cur.title == null ? "" : cur.title.trim();
                        if (!t.isEmpty() && !t.startsWith("Livestream") && !t.startsWith("tagesschau in 100") && !t.contains("Wetter:")) {
                            cur.title = t; cur.desc = cur.desc == null ? "" : cur.desc.replaceAll("<[^>]+>", "").trim();
                            d.news.add(cur); n++;
                        }
                        cur = null;
                    }
                    tag = null;
                }
            }
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ Helfer
    static JSONObject attrs(JSONObject s) { JSONObject a = s.optJSONObject("attributes"); return a == null ? new JSONObject() : a; }
    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i == 0 ? "" : i == l.size() - 1 ? " und " : ", ").append(l.get(i));
        return sb.toString();
    }
    static String cap(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.toLowerCase(Locale.GERMANY).split(" ")) if (!w.isEmpty()) sb.append(sb.length() == 0 ? "" : " ").append(w.length() <= 3 ? w.toUpperCase(Locale.GERMANY) : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        return sb.toString();
    }
    /** ISO-Zeit (auch UTC) -> lokale "HH:mm". */
    static String hhmm(String iso) {
        if (iso == null || iso.length() < 16) return "";
        try {
            String s = iso.replaceAll("\\.\\d+", "").replace("Z", "+00:00");
            Date dt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.GERMANY).parse(s);
            return new SimpleDateFormat("HH:mm", Locale.GERMANY).format(dt);
        } catch (Exception e) { return iso.substring(11, 16); }
    }
}
