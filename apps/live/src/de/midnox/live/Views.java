package de.midnox.live;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

/** Gezeichnete Oberflaechen (schnell auch bei tausenden Sendern): Senderliste, Infoleiste, TV-Guide, Menue. */
final class Views {
    static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.GERMANY);
    static String hm(long t) { return HM.format(new Date(t)); }
    static String ell(TextPaint t, String s, float w) { return TextUtils.ellipsize(s == null ? "" : s, t, Math.max(0, w), TextUtils.TruncateAt.END).toString(); }

    /** Logo in eine Kachel zeichnen (oder Monogramm). */
    static void logo(Canvas c, Paint p, TextPaint t, Model.Channel ch, RectF r, View v) {
        Bitmap b = Img.get(ch.logo, v == null ? null : new Inval(v));
        float rad = 8 * U.dp;
        if (b != null) {
            p.setShader(null); p.setColor(0x26FFFFFF); c.drawRoundRect(r, rad, rad, p);
            float s = Math.min((r.width() - 6 * U.dp) / b.getWidth(), (r.height() - 6 * U.dp) / b.getHeight());
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            c.drawBitmap(b, null, new RectF(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2), p);
        } else {
            p.setShader(null); p.setColor(U.hashColor(ch.name)); c.drawRoundRect(r, rad, rad, p);
            t.setColor(Color.WHITE); t.setTextAlign(Paint.Align.CENTER); t.setTypeface(U.BOLD); t.setTextSize(r.height() * 0.36f);
            String m = ch.name.replaceAll("[^A-Za-z0-9]", "");
            c.drawText(m.length() > 3 ? m.substring(0, 3).toUpperCase(Locale.ROOT) : m.toUpperCase(Locale.ROOT), r.centerX(), r.centerY() + r.height() * 0.13f, t);
            t.setTextAlign(Paint.Align.LEFT);
        }
    }
    static final class Inval implements Runnable { final View v; Inval(View v) { this.v = v; } public void run() { v.invalidate(); } }

    static void bar(Canvas c, Paint p, float x, float y, float w, float h, float f) {
        p.setShader(null); p.setColor(0x33FFFFFF); c.drawRoundRect(new RectF(x, y, x + w, y + h), h / 2, h / 2, p);
        if (f > 0) { p.setShader(U.brand(x, x + w)); c.drawRoundRect(new RectF(x, y, x + w * f, y + h), h / 2, h / 2, p); p.setShader(null); }
    }

    // ============================================================ Senderliste
    interface ListCb { void play(Model.Channel ch); void guide(Model.Channel ch); void fav(Model.Channel ch); void settings(); }

    static final class ListPanel extends View {
        final ListCb cb;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        List<String> groups = new ArrayList<>();
        List<Model.Channel> chans = new ArrayList<>();
        int gi, ci, gTop, cTop, col = 1;
        String playingId;

        ListPanel(Context c, ListCb cb) { super(c); this.cb = cb; }

        void open(String group, String playingId) {
            this.playingId = playingId;
            groups = Lib.groups;
            gi = Math.max(0, groups.indexOf(group));
            if (gi >= groups.size()) gi = 0;
            loadGroup();
            // wenn der laufende Sender nicht in der Gruppe ist: "Alle Sender"
            int idx = indexOf(playingId);
            if (idx < 0 && !chans.isEmpty() && playingId != null) { gi = Math.max(0, groups.indexOf(Lib.ALL)); loadGroup(); idx = indexOf(playingId); }
            ci = Math.max(0, idx); col = chans.isEmpty() ? 0 : 1;
            cTop = Math.max(0, ci - 4); gTop = Math.max(0, gi - 4);
            invalidate();
        }
        String group() { return groups.isEmpty() ? Lib.ALL : groups.get(gi); }
        void loadGroup() { chans = Lib.inGroup(getContext(), group()); ci = 0; cTop = 0; }
        int indexOf(String id) { if (id == null) return -1; for (int i = 0; i < chans.size(); i++) if (chans.get(i).id.equals(id)) return i; return -1; }
        Model.Channel selected() { return chans.isEmpty() ? null : chans.get(Math.min(ci, chans.size() - 1)); }

        boolean key(int k, boolean repeat) {
            int step = repeat ? 1 : 1;
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_CHANNEL_UP) { move(-step); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_CHANNEL_DOWN) { move(step); return true; }
            if (k == KeyEvent.KEYCODE_PAGE_UP || k == KeyEvent.KEYCODE_MEDIA_REWIND) { move(-8); return true; }
            if (k == KeyEvent.KEYCODE_PAGE_DOWN || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) { move(8); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { if (col == 1) { col = 0; invalidate(); return true; } return false; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (col == 0) { if (!chans.isEmpty()) col = 1; invalidate(); return true; }
                Model.Channel ch = selected(); if (ch != null) cb.guide(ch); return true;
            }
            if ((k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_RIGHT) && col == 0 && group().equals(Lib.SETTINGS)) { cb.settings(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                if (col == 0) { if (!chans.isEmpty()) col = 1; invalidate(); return true; }
                Model.Channel ch = selected(); if (ch != null) cb.play(ch); return true;
            }
            if (k == KeyEvent.KEYCODE_MENU) { Model.Channel ch = selected(); if (ch != null && col == 1) { cb.fav(ch); if (group().equals(Lib.FAV)) { loadGroup(); } invalidate(); } return true; }
            return false;
        }
        void move(int d) {
            if (col == 0) {
                if (groups.isEmpty()) return;
                gi = Math.max(0, Math.min(groups.size() - 1, gi + d));
                loadGroup();
            } else {
                if (chans.isEmpty()) return;
                ci = Math.max(0, Math.min(chans.size() - 1, ci + d));
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float dp = U.dp, w = getWidth(), h = getHeight();
            float gw = 250 * dp, cw = Math.min(w - gw - 40 * dp, 620 * dp);
            p.setShader(new LinearGradient(0, 0, gw + cw + 140 * dp, 0, new int[]{0xF20B0A14, 0xEB0B0A14, 0x000B0A14}, new float[]{0, 0.82f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, gw + cw + 140 * dp, h, p); p.setShader(null);
            long now = System.currentTimeMillis();
            // Kopf
            t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(U.MUTED); t.setTextAlign(Paint.Align.LEFT); t.setLetterSpacing(0.2f);
            c.drawText("GRUPPEN", 28 * dp, 40 * dp, t);
            c.drawText(ell(t, group().toUpperCase(Locale.GERMANY) + "  ·  " + chans.size(), cw - 20 * dp), gw + 24 * dp, 40 * dp, t);
            t.setLetterSpacing(0);
            t.setTextAlign(Paint.Align.RIGHT); t.setTextSize(13 * dp); t.setColor(U.MUTED);
            c.drawText(hm(now), gw + cw, 40 * dp, t); t.setTextAlign(Paint.Align.LEFT);
            // Gruppen
            float gh = 44 * dp, top = 58 * dp;
            int gv = (int) ((h - top - 20 * dp) / gh);
            if (gi < gTop) gTop = gi; if (gi >= gTop + gv) gTop = gi - gv + 1;
            for (int i = gTop; i < groups.size() && i < gTop + gv; i++) {
                float y = top + (i - gTop) * gh;
                boolean sel = i == gi;
                if (sel) {
                    RectF r = new RectF(14 * dp, y + 3 * dp, gw - 10 * dp, y + gh - 3 * dp);
                    p.setColor(col == 0 ? 0xFF26243A : 0x1FFFFFFF); c.drawRoundRect(r, 10 * dp, 10 * dp, p);
                    if (col == 0) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 10 * dp, 10 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
                }
                t.setTextSize(15 * dp); t.setTypeface(sel ? U.BOLD : U.POP); t.setColor(sel ? Color.WHITE : 0xFFB4B8CC);
                c.drawText(ell(t, groups.get(i), gw - 50 * dp), 28 * dp, y + gh / 2 + 5 * dp, t);
            }
            // Sender
            float rh = 64 * dp, x0 = gw + 8 * dp;
            int cv = (int) ((h - top - 20 * dp) / rh);
            if (ci < cTop) cTop = ci; if (ci >= cTop + cv) cTop = ci - cv + 1;
            if (chans.isEmpty()) {
                t.setTypeface(U.POP); t.setTextSize(16 * dp); t.setColor(U.MUTED);
                c.drawText(group().equals(Lib.SETTINGS) ? "OK: Quellen einrichten – zweiter Anbieter, MACs, EPG (per Handy oder PC)" : group().equals(Lib.FAV) ? "Noch keine Favoriten – MENU auf einem Sender drückt den Stern." : "Keine Sender in dieser Gruppe", x0 + 16 * dp, top + 40 * dp, t);
            }
            for (int i = cTop; i < chans.size() && i < cTop + cv; i++) {
                Model.Channel ch = chans.get(i);
                float y = top + (i - cTop) * rh;
                boolean sel = i == ci, playing = ch.id.equals(playingId);
                RectF r = new RectF(x0, y + 3 * dp, x0 + cw, y + rh - 3 * dp);
                if (sel) {
                    p.setColor(col == 1 ? 0xFF26243A : 0x14FFFFFF); c.drawRoundRect(r, 12 * dp, 12 * dp, p);
                    if (col == 1) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 12 * dp, 12 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
                }
                if (playing) { p.setColor(U.LIVE); c.drawRoundRect(new RectF(x0 + 2 * dp, y + 16 * dp, x0 + 6 * dp, y + rh - 16 * dp), 2 * dp, 2 * dp, p); }
                t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(U.MUTED); t.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.valueOf(ch.num), x0 + 46 * dp, y + rh / 2 + 5 * dp, t); t.setTextAlign(Paint.Align.LEFT);
                logo(c, p, t, ch, new RectF(x0 + 56 * dp, y + 10 * dp, x0 + 56 * dp + 64 * dp, y + rh - 10 * dp), this);
                float tx = x0 + 132 * dp, tw = cw - 140 * dp;
                Model.Prog pr = Epg.now(ch);
                t.setTypeface(sel ? U.BOLD : U.POP); t.setTextSize(16 * dp); t.setColor(Color.WHITE);
                String nm = (Lib.isFav(getContext(), ch.id) ? "★ " : "") + ch.name;
                c.drawText(ell(t, nm, tw), tx, y + (pr == null ? rh / 2 + 6 * dp : 26 * dp), t);
                if (pr != null) {
                    t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(0xFFB4B8CC);
                    c.drawText(ell(t, pr.title, tw - 90 * dp), tx, y + 44 * dp, t);
                    t.setTextAlign(Paint.Align.RIGHT); t.setColor(U.MUTED); t.setTextSize(12 * dp);
                    c.drawText(hm(pr.start) + "–" + hm(pr.stop), tx + tw, y + 44 * dp, t); t.setTextAlign(Paint.Align.LEFT);
                    bar(c, p, tx, y + rh - 12 * dp, tw, 3 * dp, pr.progress(now));
                }
            }
            // Beschreibung rechts unten fuer den gewaehlten Sender
            Model.Channel s = selected();
            if (s != null && col == 1 && w - (gw + cw) > 300 * dp) {
                Model.Prog pr = Epg.now(s), nx = Epg.next(s);
                float dx = gw + cw + 40 * dp, dw = w - dx - 40 * dp, dy = h - 200 * dp;
                p.setColor(0xD90B0A14); c.drawRoundRect(new RectF(dx - 16 * dp, dy - 34 * dp, dx + dw + 16 * dp, h - 30 * dp), 16 * dp, 16 * dp, p);
                t.setTypeface(U.BOLD); t.setTextSize(17 * dp); t.setColor(Color.WHITE);
                c.drawText(ell(t, pr == null ? s.name : pr.title, dw), dx, dy, t);
                t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(0xFFB4B8CC);
                if (pr != null) multi(c, t, pr.desc, dx, dy + 24 * dp, dw, 4);
                if (nx != null) { t.setColor(U.MUTED); c.drawText(ell(t, "Danach " + hm(nx.start) + "  " + nx.title, dw), dx, h - 46 * dp, t); }
            }
            // Hilfe
            t.setTypeface(U.POP); t.setTextSize(12 * dp); t.setColor(U.MUTED);
            c.drawText(col == 0 ? "\u2630 Einstellungen   OK / ▶ Sender   ▲▼ Gruppe" : "OK abspielen   ▶ TV-Guide   MENU Favorit   ◀ Gruppen", x0 + 16 * dp, h - 12 * dp, t);
        }
    }

    /** Text auf mehrere Zeilen umbrechen. */
    static float multi(Canvas c, TextPaint t, String s, float x, float y, float w, int maxLines) {
        if (s == null || s.isEmpty()) return y;
        String[] words = s.replace('\n', ' ').split(" ");
        StringBuilder line = new StringBuilder(); int lines = 0; float lh = t.getTextSize() * 1.35f;
        for (int i = 0; i < words.length; i++) {
            String test = line.length() == 0 ? words[i] : line + " " + words[i];
            if (t.measureText(test) > w && line.length() > 0) {
                lines++;
                if (lines == maxLines) { c.drawText(ell(t, line + " " + words[i] + "…", w), x, y, t); return y + lh; }
                c.drawText(line.toString(), x, y, t); y += lh; line = new StringBuilder(words[i]);
            } else line = new StringBuilder(test);
        }
        if (line.length() > 0) { c.drawText(line.toString(), x, y, t); y += lh; }
        return y;
    }

    // ============================================================ Infoleiste
    static final class Banner extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Model.Channel ch; String badge = "", msg = "";
        Banner(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            if (ch == null) return;
            float dp = U.dp, w = getWidth(), h = getHeight();
            long now = System.currentTimeMillis();
            p.setShader(new LinearGradient(0, h - 230 * dp, 0, h, 0x000B0A14, 0xF20B0A14, Shader.TileMode.CLAMP));
            c.drawRect(0, h - 230 * dp, w, h, p); p.setShader(null);
            float x = 60 * dp, by = h - 150 * dp;
            logo(c, p, t, ch, new RectF(x, by, x + 150 * dp, by + 90 * dp), this);
            float tx = x + 176 * dp, tw = w - tx - 60 * dp;
            t.setTypeface(U.POP); t.setTextSize(15 * dp); t.setColor(U.MUTED);
            c.drawText(ch.num + "  " + ch.name + (badge.isEmpty() ? "" : "") + (ch.group.isEmpty() ? "" : "   ·   " + ch.group), tx, by + 6 * dp, t);
            if (!badge.isEmpty()) {
                t.setTypeface(U.BOLD); t.setTextSize(11 * dp);
                float bw = t.measureText(badge) + 14 * dp, bx = w - 60 * dp - bw;
                p.setColor(0x33FFFFFF); c.drawRoundRect(new RectF(bx, by - 10 * dp, bx + bw, by + 8 * dp), 5 * dp, 5 * dp, p);
                t.setColor(Color.WHITE); c.drawText(badge, bx + 7 * dp, by + 3 * dp, t);
            }
            t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(U.MUTED); t.setTextAlign(Paint.Align.RIGHT);
            c.drawText(hm(now), w - 60 * dp, by - 22 * dp, t); t.setTextAlign(Paint.Align.LEFT);
            Model.Prog pr = Epg.now(ch), nx = Epg.next(ch);
            t.setTypeface(U.BOLD); t.setTextSize(26 * dp); t.setColor(Color.WHITE);
            c.drawText(ell(t, msg.isEmpty() ? (pr == null ? ch.name : pr.title) : msg, tw), tx, by + 42 * dp, t);
            if (pr != null) {
                t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(0xFFB4B8CC);
                c.drawText(hm(pr.start), tx, by + 70 * dp, t);
                t.setTextAlign(Paint.Align.RIGHT); c.drawText(hm(pr.stop) + "   (noch " + Math.max(0, (pr.stop - now) / 60000) + " Min.)", tx + tw, by + 70 * dp, t); t.setTextAlign(Paint.Align.LEFT);
                bar(c, p, tx + 50 * dp, by + 65 * dp, tw - 210 * dp, 4 * dp, pr.progress(now));
            }
            if (nx != null) { t.setTypeface(U.POP); t.setTextSize(14 * dp); t.setColor(U.MUTED); c.drawText(ell(t, "Danach  " + hm(nx.start) + "   " + nx.title, tw), tx, by + 96 * dp, t); }
            else if (pr == null) { t.setTypeface(U.POP); t.setTextSize(14 * dp); t.setColor(U.MUTED); c.drawText("Kein Programm verfügbar", tx, by + 70 * dp, t); }
        }
    }

    // ============================================================ TV-Guide
    static final class Guide extends View {
        final ListCb cb;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        List<Model.Channel> chans = new ArrayList<>();
        int row, top; long cursor, winStart; String title = "";
        static final long SPAN = 150 * 60000L;
        Guide(Context c, ListCb cb) { super(c); this.cb = cb; }

        void open(List<Model.Channel> l, Model.Channel focus, String title) {
            chans = l; this.title = title;
            row = 0;
            for (int i = 0; i < l.size(); i++) if (focus != null && l.get(i).id.equals(focus.id)) row = i;
            top = Math.max(0, row - 3);
            cursor = System.currentTimeMillis();
            winStart = cursor - cursor % (30 * 60000L);
            for (Model.Channel ch : l.subList(Math.max(0, row - 8), Math.min(l.size(), row + 12))) Epg.ensureShort(getContext(), ch, new Inval(this));
            invalidate();
        }
        Model.Prog sel() { return chans.isEmpty() ? null : Epg.at(chans.get(row), cursor); }

        boolean key(int k) {
            if (chans.isEmpty()) return false;
            Model.Channel ch = chans.get(row);
            if (k == KeyEvent.KEYCODE_DPAD_UP) { row = Math.max(0, row - 1); Epg.ensureShort(getContext(), chans.get(row), new Inval(this)); invalidate(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) { row = Math.min(chans.size() - 1, row + 1); Epg.ensureShort(getContext(), chans.get(row), new Inval(this)); invalidate(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                Model.Prog s = sel();
                long nt = s != null ? s.stop + 1000 : cursor + 30 * 60000L;
                if (nt > System.currentTimeMillis() + 28 * 3600000L) return true;
                cursor = nt; scroll(); invalidate(); return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                Model.Prog s = sel();
                long nt = s != null ? s.start - 1000 : cursor - 30 * 60000L;
                if (nt < System.currentTimeMillis() - 3 * 3600000L) return true;
                cursor = nt; scroll(); invalidate(); return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { cb.play(ch); return true; }
            if (k == KeyEvent.KEYCODE_MENU) { cb.fav(ch); invalidate(); return true; }
            if (k == KeyEvent.KEYCODE_PAGE_DOWN || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) { row = Math.min(chans.size() - 1, row + 6); invalidate(); return true; }
            if (k == KeyEvent.KEYCODE_PAGE_UP || k == KeyEvent.KEYCODE_MEDIA_REWIND) { row = Math.max(0, row - 6); invalidate(); return true; }
            return false;
        }
        void scroll() {
            if (cursor < winStart + 10 * 60000L) winStart = Math.max(System.currentTimeMillis() - 3 * 3600000L, winStart - 60 * 60000L);
            if (cursor > winStart + SPAN - 15 * 60000L) winStart = winStart + 60 * 60000L;
            winStart -= winStart % (30 * 60000L);
        }

        @Override protected void onDraw(Canvas c) {
            float dp = U.dp, w = getWidth(), h = getHeight();
            long now = System.currentTimeMillis();
            c.drawColor(0xF50B0A14);
            p.setShader(new RadialGradient(w * 0.1f, 0, w * 0.6f, 0x337C3AED, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, p); p.setShader(null);
            float lx = 40 * dp, cw = 270 * dp, gx = lx + cw, gw = w - gx - 40 * dp;
            // Kopf: gewaehlte Sendung
            Model.Prog s = sel();
            Model.Channel ch = chans.isEmpty() ? null : chans.get(row);
            t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(U.MUTED); t.setLetterSpacing(0.2f);
            c.drawText("TV-GUIDE  ·  " + title.toUpperCase(Locale.GERMANY), lx, 42 * dp, t); t.setLetterSpacing(0);
            t.setTextAlign(Paint.Align.RIGHT); c.drawText(new SimpleDateFormat("EEEE, d. MMMM  HH:mm", Locale.GERMANY).format(new Date(now)), w - 40 * dp, 42 * dp, t); t.setTextAlign(Paint.Align.LEFT);
            if (ch != null) {
                t.setTypeface(U.BOLD); t.setTextSize(24 * dp); t.setColor(Color.WHITE);
                c.drawText(ell(t, s == null ? ch.name : s.title, w - 2 * lx), lx, 80 * dp, t);
                t.setTypeface(U.POP); t.setTextSize(14 * dp); t.setColor(0xFF9EC5FF);
                String when = s == null ? "Kein Programm" : new SimpleDateFormat("EE HH:mm", Locale.GERMANY).format(new Date(s.start)) + " – " + hm(s.stop) + "   ·   " + ch.name + (s.now(now) ? "   ·   läuft gerade" : "");
                c.drawText(when, lx, 104 * dp, t);
                t.setColor(0xFFB4B8CC); t.setTextSize(14 * dp);
                if (s != null) multi(c, t, s.desc, lx, 128 * dp, w * 0.7f, 2);
            }
            // Zeitleiste
            float ty = 172 * dp;
            t.setTypeface(U.POP); t.setTextSize(12 * dp);
            for (long tt = winStart; tt <= winStart + SPAN; tt += 30 * 60000L) {
                float x = gx + (tt - winStart) * gw / SPAN;
                t.setColor(U.MUTED); c.drawText(hm(tt), x + 4 * dp, ty, t);
                p.setColor(0x14FFFFFF); c.drawRect(x, ty + 6 * dp, x + 1, h - 30 * dp, p);
            }
            // Zeilen
            float rh = 58 * dp, y0 = ty + 12 * dp;
            int vis = (int) ((h - y0 - 30 * dp) / rh);
            if (row < top) top = row; if (row >= top + vis) top = row - vis + 1;
            c.save(); c.clipRect(0, y0, w, h - 26 * dp);
            for (int i = top; i < chans.size() && i < top + vis; i++) {
                Model.Channel r = chans.get(i);
                float y = y0 + (i - top) * rh;
                boolean selRow = i == row;
                p.setColor(selRow ? 0x22FFFFFF : 0x0DFFFFFF); c.drawRoundRect(new RectF(lx, y + 3 * dp, gx - 6 * dp, y + rh - 3 * dp), 10 * dp, 10 * dp, p);
                logo(c, p, t, r, new RectF(lx + 8 * dp, y + 10 * dp, lx + 68 * dp, y + rh - 10 * dp), this);
                t.setTypeface(selRow ? U.BOLD : U.POP); t.setTextSize(14 * dp); t.setColor(Color.WHITE);
                c.drawText(ell(t, r.num + "  " + r.name, cw - 90 * dp), lx + 78 * dp, y + rh / 2 + 5 * dp, t);
                List<Model.Prog> ps = Epg.list(r);
                boolean any = false;
                for (Model.Prog pr : ps) {
                    if (pr.stop <= winStart || pr.start >= winStart + SPAN) continue;
                    any = true;
                    float x1 = gx + Math.max(0, pr.start - winStart) * gw / SPAN, x2 = gx + Math.min(SPAN, pr.stop - winStart) * gw / SPAN;
                    boolean on = selRow && pr.now(cursor), live = pr.now(now);
                    RectF rr = new RectF(x1 + 2 * dp, y + 3 * dp, x2 - 2 * dp, y + rh - 3 * dp);
                    p.setColor(on ? 0xFF2E2B4A : live ? 0xFF1C1B2E : 0xFF15141F); c.drawRoundRect(rr, 8 * dp, 8 * dp, p);
                    if (on) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp); p.setShader(U.brand(rr.left, rr.right)); c.drawRoundRect(rr, 8 * dp, 8 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
                    t.setTypeface(on ? U.BOLD : U.POP); t.setTextSize(13 * dp); t.setColor(live || on ? Color.WHITE : 0xFFB4B8CC);
                    c.drawText(ell(t, pr.title, rr.width() - 14 * dp), rr.left + 8 * dp, y + rh / 2 - 1 * dp, t);
                    t.setTypeface(U.POP); t.setTextSize(11 * dp); t.setColor(U.MUTED);
                    c.drawText(ell(t, hm(pr.start), rr.width() - 14 * dp), rr.left + 8 * dp, y + rh / 2 + 15 * dp, t);
                }
                if (!any) {
                    RectF rr = new RectF(gx + 2 * dp, y + 3 * dp, gx + gw - 2 * dp, y + rh - 3 * dp);
                    p.setColor(selRow ? 0xFF2E2B4A : 0xFF15141F); c.drawRoundRect(rr, 8 * dp, 8 * dp, p);
                    t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(U.MUTED);
                    c.drawText(r.name + " – kein Programm verfügbar", rr.left + 10 * dp, y + rh / 2 + 5 * dp, t);
                }
            }
            c.restore();
            // Jetzt-Linie
            if (now > winStart && now < winStart + SPAN) {
                float x = gx + (now - winStart) * gw / SPAN;
                p.setColor(U.LIVE); c.drawRect(x - 1 * dp, ty + 6 * dp, x + 1 * dp, h - 30 * dp, p);
                c.drawCircle(x, ty + 6 * dp, 4 * dp, p);
            }
            t.setTypeface(U.POP); t.setTextSize(12 * dp); t.setColor(U.MUTED);
            c.drawText("OK Sender einschalten   ◀▶ Sendungen   ▲▼ Sender   MENU Favorit   ZURÜCK schließen", lx, h - 10 * dp, t);
        }
    }

    // ============================================================ Menue
    static final class Menu extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final List<String> items = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        String title = ""; int sel;
        Menu(Context c) { super(c); }
        void set(String title, List<String> l, List<Runnable> a) { this.title = title; items.clear(); items.addAll(l); acts.clear(); acts.addAll(a); sel = 0; invalidate(); }
        boolean key(int k) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) { sel = Math.max(0, sel - 1); invalidate(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) { sel = Math.min(items.size() - 1, sel + 1); invalidate(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { if (sel < acts.size()) acts.get(sel).run(); return true; }
            return k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        @Override protected void onDraw(Canvas c) {
            float dp = U.dp, w = getWidth(), h = getHeight(), mw = 420 * dp, x = w - mw;
            p.setShader(new LinearGradient(x - 80 * dp, 0, w, 0, new int[]{0x000B0A14, 0xF00B0A14, 0xF70B0A14}, new float[]{0, 0.2f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(x - 80 * dp, 0, w, h, p); p.setShader(null);
            t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(U.MUTED); t.setLetterSpacing(0.2f);
            c.drawText(ell(t, title.toUpperCase(Locale.GERMANY), mw - 40 * dp), x + 20 * dp, 54 * dp, t); t.setLetterSpacing(0);
            float rh = 50 * dp, y = 76 * dp;
            for (int i = 0; i < items.size(); i++) {
                RectF r = new RectF(x + 10 * dp, y + i * rh + 3 * dp, w - 24 * dp, y + (i + 1) * rh - 3 * dp);
                if (i == sel) {
                    p.setColor(0xFF26243A); c.drawRoundRect(r, 10 * dp, 10 * dp, p);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 10 * dp, 10 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL);
                }
                t.setTypeface(i == sel ? U.BOLD : U.POP); t.setTextSize(16 * dp); t.setColor(i == sel ? Color.WHITE : 0xFFC9CCE0);
                c.drawText(ell(t, items.get(i), r.width() - 30 * dp), r.left + 16 * dp, r.centerY() + 6 * dp, t);
            }
        }
    }
}
