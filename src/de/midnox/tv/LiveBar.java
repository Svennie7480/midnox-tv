package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.TextPaint;
import android.view.*;
import java.util.*;

/**
 * Live-Leiste: kleine Spielstand-Leiste ueber jeder App (Fussball 1./2. Liga und Handball).
 * Erscheint automatisch, solange ein Spiel deiner Vereine laeuft (ohne Vereinsauswahl: alle laufenden Spiele).
 * Braucht die Berechtigung "ueber anderen Apps anzeigen" – die holt sich der Launcher selbst per ADB.
 */
final class LiveBar {
    static final Handler ui = new Handler(Looper.getMainLooper());
    static Bar view;
    static volatile List<Item> items = new ArrayList<>();
    static volatile long lastLoad, nextCheck;
    static volatile boolean loading;
    static volatile boolean hiddenByUser;    // per Handy/PC/Einstellungen ausgeblendet, bis kein Spiel mehr laeuft
    static volatile boolean forced;          // manuell eingeblendet

    static final class Item { String kind, t1, t2, score, status; boolean mine; }

    static boolean on(Context c) { return Style.prefs(c).getBoolean("bar_on", true); }

    /** Aus Daily.tick. */
    static void tick(final Context c) {
        long now = System.currentTimeMillis();
        boolean screen = Daily.screenOn(c) && !Style.prefs(c).getBoolean("sleep_mode", false);
        if (!on(c) || !screen) { hide(c); return; }
        boolean anyWindow = Matchday.liveWindow(c, now) || forced;
        if (!anyWindow) { items = new ArrayList<>(); hiddenByUser = false; hide(c); return; }
        if (!loading && now - lastLoad > 30000) {
            loading = true; lastLoad = now;
            new Thread(new Runnable() { public void run() {
                try { items = load(c); } catch (Exception ignored) {} finally { loading = false; }
                ui.post(new Runnable() { public void run() { refresh(c); } });
            }}).start();
        }
    }

    static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        boolean anyFav = !GoalAlert.allClubs(c).isEmpty();
        for (String code : new String[]{"bl1", "bl2"}) {
            if (!Style.prefs(c).getBoolean(code.equals("bl1") ? "sa_bl1" : "sa_bl2", true)) continue;
            try {
                Sport.League l = Sport.load(code, 0);
                for (Sport.Match m : l.matches) {
                    if (!m.live(now)) continue;
                    boolean mine = GoalAlert.clubSelected(c, m.t1.name) || GoalAlert.clubSelected(c, m.t2.name)
                        || GoalAlert.clubSelected(c, m.t1.shortName) || GoalAlert.clubSelected(c, m.t2.shortName);
                    if (anyFav && !mine) continue;
                    Item it = new Item(); it.kind = "fb"; it.mine = mine;
                    it.t1 = m.t1.shortName; it.t2 = m.t2.shortName;
                    it.score = (m.s1 < 0 ? 0 : m.s1) + ":" + (m.s2 < 0 ? 0 : m.s2);
                    it.status = m.status(now).replace("LIVE ", "");
                    out.add(it);
                }
            } catch (Exception ignored) {}
        }
        if (Style.prefs(c).getBoolean("hb_alert", true)) try {
            Sport.League l = Sport.loadHb(0);
            for (Sport.Match m : l.matches) {
                if (!m.live(now) || !(Sport.isClub(c, m.t1) || Sport.isClub(c, m.t2))) continue;
                Item it = new Item(); it.kind = "hb"; it.mine = true;
                it.t1 = m.t1.shortName; it.t2 = m.t2.shortName;
                it.score = (m.s1 < 0 ? 0 : m.s1) + ":" + (m.s2 < 0 ? 0 : m.s2);
                it.status = m.status(now).replace("LIVE ", "");
                out.add(it);
            }
        } catch (Exception ignored) {}
        // eigene Vereine zuerst
        Collections.sort(out, new Comparator<Item>() { public int compare(Item a, Item b) { return (b.mine ? 1 : 0) - (a.mine ? 1 : 0); } });
        return out;
    }

    static void refresh(Context c) {
        if (items.isEmpty() || hiddenByUser) { hide(c); return; }
        if (!permitted(c)) { grant(c); return; }
        if (view == null) show(c);
        if (view != null) { view.requestLayout(); view.invalidate(); }
    }

    // ------------------------------------------------------------ Fenster
    static boolean permitted(Context c) {
        return Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(c);
    }
    static long lastGrant;
    static void grant(final Context c) {
        if (System.currentTimeMillis() - lastGrant < 10 * 60000L) return;
        lastGrant = System.currentTimeMillis();
        new Thread(new Runnable() { public void run() {
            try { Adb.shell(c, "appops set " + c.getPackageName() + " SYSTEM_ALERT_WINDOW allow"); } catch (Exception ignored) {}
            ui.postDelayed(new Runnable() { public void run() { if (permitted(c)) refresh(c); } }, 1500);
        }}).start();
    }

    static void show(Context c) {
        try {
            Context app = c.getApplicationContext();
            Style.init(app);
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            boolean top = "top".equals(Style.prefs(c).getString("bar_pos", "bottom"));
            lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
            lp.y = Style.px(top ? 18 : 22);
            view = new Bar(app);
            wm.addView(view, lp);
        } catch (Exception e) { view = null; }
    }

    static void hide(Context c) {
        if (view == null) return;
        ui.post(new Runnable() { public void run() {
            if (view == null) return;
            try { ((WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(view); } catch (Exception ignored) {}
            view = null;
        }});
    }

    /** Ein/aus per Handy, PC oder Einstellungen. Liefert den neuen Zustand als Text. */
    static String toggle(final Context c) {
        if (view != null) { hiddenByUser = true; forced = false; hide(c); return "Live-Leiste ausgeblendet"; }
        hiddenByUser = false; forced = true; lastLoad = 0;
        if (!permitted(c)) { lastGrant = 0; grant(c); }
        tick(c);
        return items.isEmpty() ? "Live-Leiste an – sie erscheint, sobald ein Spiel läuft" : "Live-Leiste wird eingeblendet";
    }

    // ------------------------------------------------------------ Darstellung
    static final class Bar extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        float offset;   // Laufschrift, wenn zu breit
        Bar(Context c) { super(c); t.setTypeface(Style.POP); }

        float chipW(Item it) {
            float dp = Style.dp;
            t.setTextSize(13 * dp); t.setFakeBoldText(false);
            float w = t.measureText(it.t1 + "  " + it.t2) ;
            t.setFakeBoldText(true); w += t.measureText(it.score);
            t.setTextSize(10.5f * dp); t.setFakeBoldText(false); w += t.measureText(it.status);
            return w + 64 * dp;
        }
        float total() { float w = 0; for (Item it : items) w += chipW(it); return w + 40 * Style.dp; }
        float maxW() { return getResources().getDisplayMetrics().widthPixels * 0.86f; }

        @Override protected void onMeasure(int a, int b) {
            setMeasuredDimension((int) Math.min(total(), maxW()), Style.px(38));
        }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth(), h = getHeight();
            List<Item> list = items;
            RectF r = new RectF(0, 0, w, h);
            p.setShader(null); p.setColor(0xE8121120); c.drawRoundRect(r, h / 2, h / 2, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * dp); p.setShader(Style.brand(0, w));
            c.drawRoundRect(new RectF(dp, dp, w - dp, h - dp), h / 2, h / 2, p);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            // LIVE-Punkt
            float pulse = 0.55f + 0.45f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 600.0));
            p.setColor(Color.argb((int) (255 * pulse), 239, 68, 68));
            c.drawCircle(18 * dp, h / 2, 4.5f * dp, p);
            float tot = total(), x = 32 * dp;
            boolean scroll = tot > w;
            c.save(); c.clipRect(28 * dp, 0, w - 10 * dp, h);
            if (scroll) { offset += 1.2f * dp; if (offset > tot) offset = 0; x -= offset; }
            for (int pass = 0; pass < (scroll ? 2 : 1); pass++) {
                for (Item it : list) {
                    x = drawChip(c, it, x, h);
                }
                x += 40 * dp;
            }
            c.restore();
            postInvalidateDelayed(scroll ? 33 : 300);
        }
        float drawChip(Canvas c, Item it, float x, float h) {
            float dp = Style.dp, cy = h / 2 + 4.5f * dp;
            // Sportart
            p.setColor(it.kind.equals("hb") ? 0xFF06B6D4 : 0xFF7C3AED);
            c.drawCircle(x + 5 * dp, h / 2, 3 * dp, p);
            x += 14 * dp;
            t.setTextAlign(Paint.Align.LEFT); t.setTextSize(13 * dp); t.setFakeBoldText(it.mine); t.setColor(Color.WHITE);
            c.drawText(it.t1, x, cy, t); x += t.measureText(it.t1) + 8 * dp;
            t.setFakeBoldText(true); t.setColor(0xFFFCA5A5);
            c.drawText(it.score, x, cy, t); x += t.measureText(it.score) + 8 * dp;
            t.setFakeBoldText(it.mine); t.setColor(Color.WHITE);
            c.drawText(it.t2, x, cy, t); x += t.measureText(it.t2) + 8 * dp;
            t.setFakeBoldText(false); t.setTextSize(10.5f * dp); t.setColor(0xFF9EA2B8);
            c.drawText(it.status, x, cy - 0.5f * dp, t); x += t.measureText(it.status) + 22 * dp;
            return x;
        }
    }
}
