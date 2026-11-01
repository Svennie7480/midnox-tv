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
                    if (type.equals("goal") && !on(c, "sa_goal", true)) continue;
                    if (type.equals("yellow") && !on(c, "sa_yellow", false)) continue;
                    if ((type.equals("red") || type.equals("yellowred")) && !on(c, "sa_red", true)) continue;
                    String score = null;
                    Matcher sc = Pattern.compile("(\\d+)\\s*:\\s*(\\d+)").matcher(e.text);
                    if (type.equals("goal") && sc.find()) score = sc.group(1) + " : " + sc.group(2);
                    if (score == null) score = grab(b, "match-result-home\"><div class=\"match-result match-result-0\">(\\d+)") + " : " + grab(b, "match-result-away\"><div class=\"match-result match-result-0\">(\\d+)");
                    Bitmap c1 = crest(imgs.size() > 0 ? imgs.get(0) : null), c2 = crest(imgs.size() > 1 ? imgs.get(1) : null);
                    show(c, type, e.minute, hs.isEmpty() ? home : hs, as.isEmpty() ? away : as, score, e.player, e.team, c1, c2);
                    try { Thread.sleep(7500); } catch (InterruptedException ignored) {}
                }
            }
        }
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
            boolean goal = type.equals("goal");
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp);
            if (goal) p.setShader(Style.brand(0, w)); else p.setColor(type.equals("yellow") ? 0xFFFACC15 : 0xFFEF4444);
            c.drawRoundRect(new RectF(dp, dp, w - dp, hh - dp), 22 * dp, 22 * dp, p);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            // Symbol links
            float ix = 20 * dp, iy = hh / 2;
            if (goal) {
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
            String title = goal ? "TOR!" : type.equals("yellow") ? "GELBE KARTE" : type.equals("red") ? "ROTE KARTE" : "GELB-ROT";
            t.setTextAlign(Paint.Align.LEFT); t.setFakeBoldText(true); t.setTextSize(15 * dp);
            t.setColor(goal ? 0xFF22D3EE : type.equals("yellow") ? 0xFFFACC15 : 0xFFF87171);
            c.drawText(title + (minute.isEmpty() ? "" : "   " + minute + "'"), x, 30 * dp, t);
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
