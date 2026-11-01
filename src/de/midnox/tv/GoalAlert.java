package de.midnox.tv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.widget.Toast;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * Tor-Alarm: Tore und Karten der Bundesliga live ueber jeder App einblenden (Film, Live-TV, Startseite).
 * Quelle: Sportschau-Liveticker. Nur waehrend Spielen wird abgefragt.
 */
public final class GoalAlert {
    static final class Ev { String id, action, kind, minute, player, team, text; }
    static volatile long nextSchedule, lastPoll;
    static volatile boolean busy;
    static final List<long[]> kickoffs = new ArrayList<>();   // {Liga 1/2, Anstoss}
    static final Map<String, Set<String>> seen = new HashMap<>();
    static final Map<String, Bitmap> crests = Collections.synchronizedMap(new HashMap<String, Bitmap>());
    static final Handler ui = new Handler(Looper.getMainLooper());

    static boolean on(Context c, String k, boolean d) { return Style.prefs(c).getBoolean(k, d); }

    /** Wird alle paar Sekunden aus dem Hintergrunddienst aufgerufen. */
    static void tick(final Context c) {
        if (!on(c, "sa_on", true) || busy) return;
        long now = System.currentTimeMillis();
        if (now > nextSchedule) {
            busy = true;
            new Thread(new Runnable() { public void run() { try { schedule(c); } finally { busy = false; } } }).start();
            return;
        }
        if (now - lastPoll < 40000) return;
        boolean live = false;
        synchronized (kickoffs) {
            for (long[] k : kickoffs) if ((k[0] == 1 ? on(c, "sa_bl1", true) : on(c, "sa_bl2", true)) && now > k[1] - 120000 && now < k[1] + 130 * 60000L) live = true;
        }
        if (!live || !Daily.screenOn(c) || Style.prefs(c).getBoolean("sleep_mode", false)) return;
        lastPoll = now; busy = true;
        new Thread(new Runnable() { public void run() { try { poll(c); } catch (Exception ignored) {} finally { busy = false; } } }).start();
    }

    static void schedule(Context c) {
        List<long[]> l = new ArrayList<>();
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        boolean ok = true;
        for (int lg = 1; lg <= 2; lg++) {
            try {
                JSONArray a = new JSONArray(Style.httpGet("https://api.openligadb.de/getmatchdata/bl" + lg, null));
                for (int i = 0; i < a.length(); i++) try { l.add(new long[]{lg, utc.parse(a.getJSONObject(i).optString("matchDateTimeUTC")).getTime()}); } catch (Exception ignored) {}
            } catch (Exception e) { ok = false; }
        }
        synchronized (kickoffs) { kickoffs.clear(); kickoffs.addAll(l); }
        nextSchedule = System.currentTimeMillis() + (ok ? 3 * 3600000L : 10 * 60000L);
    }

    static Set<String> favorites(Context c) {
        Set<String> s = new HashSet<>();
        for (String x : Style.prefs(c).getString("sa_teams", "").split(",")) if (!x.trim().isEmpty()) s.add(Sport.norm(x.trim()));
        return s;
    }
    static boolean isFav(Set<String> fav, String name) {
        if (fav.isEmpty()) return true;
        String n = Sport.norm(name);
        for (String f : fav) if (n.contains(f) || f.contains(n)) return true;
        return false;
    }

    static void poll(Context c) throws Exception {
        Set<String> fav = favorites(c);
        for (int lg = 1; lg <= 2; lg++) {
            if (!(lg == 1 ? on(c, "sa_bl1", true) : on(c, "sa_bl2", true))) continue;
            String page;
            try { page = RadioNet.get(Sport.pageUrl(lg == 1 ? "bl1" : "bl2")); } catch (Exception e) { continue; }
            for (String b : page.split("<li data-match_id=")) {
                if (!b.contains(" live match") && !b.contains("\"live ") && !b.contains(" live\"")) continue;
                Matcher mid = Pattern.compile("^\"(\\d+)\"").matcher(b);
                Matcher more = Pattern.compile("href=\"(/live-und-ergebnisse/[^\"]+/liveticker)\"").matcher(b);
                if (!mid.find() || !more.find()) continue;
                String home = grab(b, "team-name team-name-home\">([^<]+)<"), away = grab(b, "team-name team-name-away\">([^<]+)<");
                String hs = grab(b, "team-shortname team-shortname-home\">([^<]+)<"), as = grab(b, "team-shortname team-shortname-away\">([^<]+)<");
                List<String> imgs = new ArrayList<>();
                Matcher im = Pattern.compile("src=\"(https://s\\.hs-data\\.com/gfx/emblem/[^\"]+)\"").matcher(b);
                while (im.find() && imgs.size() < 2) imgs.add(im.group(1));
                if (!isFav(fav, home) && !isFav(fav, away)) continue;
                List<Ev> evs;
                try { evs = ticker(RadioNet.get("https://www.sportschau.de" + more.group(1))); } catch (Exception e) { continue; }
                String key = mid.group(1);
                Set<String> s = seen.get(key);
                boolean first = s == null;
                if (first) { s = new HashSet<>(); seen.put(key, s); }
                List<Ev> fresh = new ArrayList<>();
                for (Ev e : evs) if (s.add(e.id) && !first) fresh.add(e);
                Collections.reverse(fresh);   // Ticker ist neueste zuerst
                for (Ev e : fresh) {
                    String type = e.action.equals("goal") ? "goal" : e.action.equals("card") ? (e.kind.contains("red") && e.kind.contains("yellow") ? "yellowred" : e.kind.contains("red") ? "red" : "yellow") : null;
                    if (type == null) continue;
                    String slot = "card";
                    if (type.equals("goal")) {
                        Matcher fu = Pattern.compile("f\u00fcr ([^,]+),").matcher(e.text);
                        String scorer = fu.find() ? fu.group(1) : e.team;
                        celebrate(c, scorer);
                        if (clubSelected(c, scorer)) slot = "mygoal";
                        else if (clubSelected(c, home) || clubSelected(c, away) || clubSelected(c, hs) || clubSelected(c, as)) slot = "against";
                        else slot = "goal";
                        if (slot.equals("mygoal") && on(c, "gl_on", false)) GoalSound.play(c, slot);   // Jubel-Sound auch ohne Einblendung
                    }
                    if (type.equals("goal") && !on(c, "sa_goal", true)) continue;
                    if (type.equals("yellow") && !on(c, "sa_yellow", false)) continue;
                    if ((type.equals("red") || type.equals("yellowred")) && !on(c, "sa_red", true)) continue;
                    String score = null;
                    Matcher sc = Pattern.compile("(\\d+)\\s*:\\s*(\\d+)").matcher(e.text);
                    if (type.equals("goal") && sc.find()) score = sc.group(1) + " : " + sc.group(2);
                    if (score == null) score = grab(b, "match-result-home\"><div class=\"match-result match-result-0\">(\\d+)") + " : " + grab(b, "match-result-away\"><div class=\"match-result match-result-0\">(\\d+)");
                    Bitmap c1 = crest(imgs.size() > 0 ? imgs.get(0) : null), c2 = crest(imgs.size() > 1 ? imgs.get(1) : null);
                    if (!(slot.equals("mygoal") && on(c, "gl_on", false))) GoalSound.play(c, slot);
                    show(c, type, e.minute, hs.isEmpty() ? home : hs, as.isEmpty() ? away : as, score, e.player, e.team, c1, c2);
                    try { Thread.sleep(7500); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    // ------------------------------------------------------------ Tor-Jubel mit Licht
    /** Vereinsfarben, die als Licht gut wirken (Schwarz wird zu Weiss). {Suchbegriffe, Farbe 1, Farbe 2} */
    static final Object[][] COLORS = {
        {"bayern", 0xDC052D, 0xFFFFFF}, {"dortmund|bvb", 0xFDE100, 0xFFFFFF}, {"freiburg", 0xE2001A, 0xFFFFFF}, {"leipzig", 0xDD0741, 0xFFFFFF},
        {"bremen|werder", 0x1D9053, 0xFFFFFF}, {"leverkusen", 0xE32221, 0xFFFFFF}, {"elversberg", 0xFFFFFF, 0x2A6FDB}, {"mainz", 0xC3141E, 0xFFFFFF},
        {"augsburg", 0xBA3733, 0x46714D}, {"stuttgart", 0xE32219, 0xFFFFFF}, {"hoffenheim", 0x1961B5, 0xFFFFFF}, {"paderborn", 0x005CA9, 0xFFFFFF},
        {"hamburger|hsv", 0x0A3F86, 0xFFFFFF}, {"union", 0xEB1923, 0xFFFFFF}, {"koeln|koln", 0xED1C24, 0xFFFFFF}, {"frankfurt", 0xE1000F, 0xFFFFFF},
        {"schalke|s04", 0x004D9D, 0xFFFFFF}, {"gladbach|monchengladbach|moenchengladbach", 0x00A54F, 0xFFFFFF},
        {"bielefeld|arminia", 0x004E95, 0xFFFFFF}, {"braunschweig", 0xFFD500, 0x0050A0}, {"cottbus", 0xE30613, 0xFFFFFF}, {"bochum", 0x005CA9, 0xFFFFFF},
        {"wolfsburg", 0x65B32E, 0xFFFFFF}, {"osnabrueck|osnabruck", 0x5A2D82, 0xFFFFFF}, {"darmstadt", 0x004E9F, 0xFFFFFF}, {"magdeburg", 0x0059A3, 0xFFFFFF},
        {"kaiserslautern|fck", 0xE20612, 0xFFFFFF}, {"hertha", 0x005CA9, 0xFFFFFF}, {"karlsruhe|ksc", 0x00529F, 0xFFFFFF}, {"heidenheim", 0xE30613, 0x00489B},
        {"pauli", 0xE30613, 0xFFFFFF}, {"nuernberg|nurnberg", 0xAA1124, 0xFFFFFF}, {"hannover", 0x00A54F, 0xE30613}, {"dresden|dynamo", 0xFFD200, 0xFFFFFF},
        {"fuerth|furth", 0x00A54F, 0xFFFFFF}, {"kiel|holstein", 0x0050A0, 0xE30613}, {"hansa|rostock", 0x004A99, 0xFFFFFF}, {"duesseldorf|dusseldorf|fortuna", 0xE30613, 0xFFFFFF},
    };
    static int[] colors(String team) {
        String n = Sport.norm(team);
        for (Object[] c : COLORS) for (String k : ((String) c[0]).split("\\|")) if (n.contains(k)) return new int[]{(Integer) c[1], (Integer) c[2]};
        return null;
    }
    static boolean clubSelected(Context c, String team) {
        String sel = Style.prefs(c).getString("gl_teams", "");
        if (sel.isEmpty()) sel = Style.prefs(c).getString("sa_teams", "");
        if (sel.isEmpty()) return false;
        String n = Sport.norm(team == null ? "" : team);
        if (n.isEmpty()) return false;
        for (String x : sel.split(",")) { String f = Sport.norm(x.trim()); if (!f.isEmpty() && (n.contains(f) || f.contains(n))) return true; }
        return false;
    }
    static void celebrate(Context c, String team) {
        if (!on(c, "gl_on", false) || !clubSelected(c, team)) return;
        int[] col = colors(team);
        if (col != null) jubel(c, col);
    }
    static volatile boolean jubelt;
    /** Lampen ~9 Sekunden in den Vereinsfarben wechseln lassen, dann vorherigen Zustand wiederherstellen. */
    /** Vereinsfarben, ggf. durch eigene Farben aus den Einstellungen (gl_c1/gl_c2, "#rrggbb") ersetzt. */
    static int[] custom(Context c, int[] col) {
        int[] r = col.clone();
        String[] k = {"gl_c1", "gl_c2"};
        for (int i = 0; i < 2; i++) try {
            String v = Style.prefs(c).getString(k[i], "").trim().replace("#", "");
            if (v.length() == 6) r[i] = Integer.parseInt(v, 16);
        } catch (Exception ignored) {}
        return r;
    }
    static String hex(int v) { return String.format("#%06x", v & 0xFFFFFF); }
    /** Automatische Farben des ersten gewählten Vereins (für die PC-Seite). */
    static int[] autoColors(Context c) {
        String sel = Style.prefs(c).getString("gl_teams", "");
        if (sel.isEmpty()) sel = Style.prefs(c).getString("sa_teams", "");
        int[] col = sel.isEmpty() ? null : colors(sel.split(",")[0]);
        return col == null ? new int[]{0x7C3AED, 0x06B6D4} : col;
    }
    static void jubel(final Context c, int[] colIn) {
        final int[] col = custom(c, colIn);
        if (jubelt) return;
        jubelt = true;
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject cfg = Style.loadConfig(c);
                String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
                JSONArray ids = new JSONArray(Style.prefs(c).getString("goal_light_ids", "[]"));
                if (ids.length() == 0) {
                    JSONObject m = new JSONObject(Style.prefs(c).getString("kino_lights", "{}"));
                    for (Iterator<String> it = m.keys(); it.hasNext(); ) ids.put(it.next());
                }
                if (ids.length() == 0) return;
                Style.httpPost(base + "/api/services/scene/create", tok, new JSONObject().put("scene_id", "midnox_torjubel_vorher").put("snapshot_entities", ids).toString());
                for (int i = 0; i < 10; i++) {
                    int cc = col[i % 2];
                    JSONObject d = new JSONObject().put("entity_id", ids).put("brightness_pct", 100).put("transition", 0)
                        .put("rgb_color", new JSONArray().put((cc >> 16) & 0xFF).put((cc >> 8) & 0xFF).put(cc & 0xFF));
                    try { Style.httpPost(base + "/api/services/light/turn_on", tok, d.toString()); } catch (Exception ignored) {}
                    Thread.sleep(900);
                }
                Style.httpPost(base + "/api/services/scene/turn_on", tok, new JSONObject().put("entity_id", "scene.midnox_torjubel_vorher").put("transition", 1).toString());
            } catch (Exception ignored) {
            } finally { jubelt = false; }
        }}).start();
    }
    static void jubelTest(Context c) {
        String sel = Style.prefs(c).getString("gl_teams", "");
        if (sel.isEmpty()) sel = Style.prefs(c).getString("sa_teams", "");
        String team = sel.isEmpty() ? "HSV" : sel.split(",")[0];
        int[] col = colors(team);
        jubel(c, col == null ? new int[]{0x7C3AED, 0x06B6D4} : col);
        GoalSound.play(c, "mygoal");
        show(c, "goal", "90", team, "Gegner", "1 : 0", "Test: Tor-Jubel", team, null, null);
    }

    static String grab(String s, String re) { Matcher m = Pattern.compile(re).matcher(s); return m.find() ? android.text.Html.fromHtml(m.group(1)).toString().trim() : ""; }

    static List<Ev> ticker(String html) {
        List<Ev> out = new ArrayList<>();
        Matcher m = Pattern.compile("<li class=\"liveticker\" id=\"(\\d+)\"[^>]*data-event_action=\"([^\"]*)\" data-event_kind=\"([^\"]*)\">(.*?)</li>", Pattern.DOTALL).matcher(html);
        while (m.find()) {
            Ev e = new Ev();
            e.id = m.group(1); e.action = m.group(2); e.kind = m.group(3);
            String body = m.group(4);
            e.minute = grab(body, "liveticker-minute\">([^<]*)<").replace(".", "");
            e.player = grab(body, "person-name[^\"]*\"><a[^>]*>([^<]+)<");
            e.team = grab(body, "team-shortname-person\"><a[^>]*>([^<]+)<");
            e.text = android.text.Html.fromHtml(grab(body, "liveticker-content\">(.*?)</div>")).toString();
            out.add(e);
        }
        return out;
    }

    static Bitmap crest(String url) {
        if (url == null) return null;
        Bitmap b = crests.get(url);
        if (b == null) { b = Emby.fetchAlpha(url); if (b != null) crests.put(url, b); }
        return b;
    }

    /** Die Einblendung: zweimal hintereinander (zusammen rund 7 Sekunden), oben rechts. */
    static void show(final Context c, final String type, final String minute, final String t1, final String t2, final String score, final String player, final String team, final Bitmap c1, final Bitmap c2) {
        ui.post(new Runnable() { public void run() {
            for (int k = 0; k < 2; k++) {
                final int kk = k;
                ui.postDelayed(new Runnable() { public void run() {
                    Toast to = new Toast(c.getApplicationContext());
                    to.setView(new Card(c, type, minute, t1, t2, score, player, team, c1, c2));
                    to.setDuration(Toast.LENGTH_LONG);
                    to.setGravity(Gravity.TOP | Gravity.RIGHT, Style.px(36), Style.px(30));
                    to.show();
                }}, kk * 3400L);
            }
        }});
    }

    static void test(Context c) {
        show(c, "goal", "79", "Leverkusen", "Stuttgart", "2 : 4", "Ibrahim Maza", "Leverkusen", null, null);
    }

    static final class Card extends View {
        final String type, minute, t1, t2, score, player, team; final Bitmap c1, c2;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Card(Context c, String type, String minute, String t1, String t2, String score, String player, String team, Bitmap c1, Bitmap c2) {
            super(c); Style.init(c);
            this.type = type; this.minute = minute; this.t1 = t1; this.t2 = t2; this.score = score; this.player = player; this.team = team; this.c1 = c1; this.c2 = c2;
            t.setTypeface(Style.POP);
        }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(Style.px(520), Style.px(118)); }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth(), hh = getHeight();
            RectF r = new RectF(0, 0, w, hh);
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0xF5121120);
            c.drawRoundRect(r, 22 * dp, 22 * dp, p);
            boolean hb = type.equals("hb");
            boolean goal = type.equals("goal") || hb;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp);
            if (goal) p.setShader(Style.brand(0, w)); else p.setColor(type.equals("yellow") ? 0xFFFACC15 : 0xFFEF4444);
            c.drawRoundRect(new RectF(dp, dp, w - dp, hh - dp), 22 * dp, 22 * dp, p);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            // Symbol links
            float ix = 20 * dp, iy = hh / 2;
            if (hb) {   // Handball: Ball mit Naehten
                float bx = ix + 28 * dp;
                p.setShader(Style.brand(ix, ix + 56 * dp)); c.drawCircle(bx, iy, 28 * dp, p); p.setShader(null);
                p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * dp); c.drawCircle(bx, iy, 15 * dp, p);
                p.setStrokeWidth(2 * dp);
                c.drawArc(new RectF(bx - 26 * dp, iy - 15 * dp, bx + 4 * dp, iy + 15 * dp), -60, 120, false, p);
                c.drawArc(new RectF(bx - 4 * dp, iy - 15 * dp, bx + 26 * dp, iy + 15 * dp), 120, 120, false, p);
                c.drawLine(bx, iy - 15 * dp, bx, iy + 15 * dp, p);
                p.setStyle(Paint.Style.FILL);
            } else if (goal) {
                p.setShader(Style.brand(ix, ix + 56 * dp)); c.drawCircle(ix + 28 * dp, iy, 28 * dp, p); p.setShader(null);
                p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * dp); c.drawCircle(ix + 28 * dp, iy, 15 * dp, p);
                Path pent = new Path();
                for (int i = 0; i < 5; i++) { double a = Math.toRadians(-90 + i * 72); float px = ix + 28 * dp + (float) Math.cos(a) * 6 * dp, py = iy + (float) Math.sin(a) * 6 * dp; if (i == 0) pent.moveTo(px, py); else pent.lineTo(px, py); }
                pent.close(); p.setStyle(Paint.Style.FILL); c.drawPath(pent, p);
            } else {
                float cw = 34 * dp, ch = 46 * dp;
                if (type.equals("yellowred")) {
                    p.setColor(0xFFFACC15); c.save(); c.rotate(-10, ix + 22 * dp, iy); c.drawRoundRect(new RectF(ix + 4 * dp, iy - ch / 2, ix + 4 * dp + cw, iy + ch / 2), 5 * dp, 5 * dp, p); c.restore();
                    p.setColor(0xFFEF4444); c.save(); c.rotate(8, ix + 34 * dp, iy); c.drawRoundRect(new RectF(ix + 18 * dp, iy - ch / 2, ix + 18 * dp + cw, iy + ch / 2), 5 * dp, 5 * dp, p); c.restore();
                } else {
                    p.setColor(type.equals("yellow") ? 0xFFFACC15 : 0xFFEF4444);
                    c.save(); c.rotate(-8, ix + 28 * dp, iy); c.drawRoundRect(new RectF(ix + 11 * dp, iy - ch / 2, ix + 11 * dp + cw, iy + ch / 2), 5 * dp, 5 * dp, p); c.restore();
                }
            }
            float x = ix + 76 * dp;
            String title = hb ? "HANDBALL · " + minute : goal ? "TOR!" : type.equals("yellow") ? "GELBE KARTE" : type.equals("red") ? "ROTE KARTE" : "GELB-ROT";
            t.setTextAlign(Paint.Align.LEFT); t.setFakeBoldText(true); t.setTextSize(15 * dp);
            t.setColor(goal ? 0xFF22D3EE : type.equals("yellow") ? 0xFFFACC15 : 0xFFF87171);
            c.drawText(title + (minute.isEmpty() || hb ? "" : "   " + minute + "'"), x, 30 * dp, t);
            // Spielstand mit Wappen
            float y = 66 * dp, cs = 24 * dp;
            t.setTextSize(17 * dp); t.setColor(Color.WHITE);
            float cur = x;
            if (c1 != null) { icon(c, c1, cur, y - cs + 5 * dp, cs); cur += cs + 8 * dp; }
            String line = t1 + "  " + score + "  " + t2;
            line = TextUtils.ellipsize(line, t, w - cur - (c2 != null ? cs + 30 * dp : 20 * dp), TextUtils.TruncateAt.END).toString();
            c.drawText(line, cur, y, t);
            cur += t.measureText(line) + 8 * dp;
            if (c2 != null) icon(c, c2, cur, y - cs + 5 * dp, cs);
            t.setFakeBoldText(false); t.setTextSize(13.5f * dp); t.setColor(0xFFB4B8CC);
            String who = player.isEmpty() ? "" : player + (team.isEmpty() ? "" : "  (" + team + ")");
            c.drawText(TextUtils.ellipsize(who, t, w - x - 20 * dp, TextUtils.TruncateAt.END).toString(), x, 94 * dp, t);
        }
        void icon(Canvas c, Bitmap b, float x, float y, float s) {
            float sc = Math.min(s / b.getWidth(), s / b.getHeight()), bw = b.getWidth() * sc, bh = b.getHeight() * sc;
            c.drawBitmap(b, null, new RectF(x + (s - bw) / 2, y + (s - bh) / 2, x + (s + bw) / 2, y + (s + bh) / 2), p);
        }
    }
}
