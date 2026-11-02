package de.midnox.live;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import org.json.JSONObject;
import java.util.*;

/** Mediathek: Filme und Serien mit Postern, Kategorien, Detailseite, Staffeln/Folgen und Weiterschauen. */
final class Library extends View {
    interface Cb { void playVod(Vod.Item it, Vod.Episode ep, List<Vod.Episode> next, long startMs); void close(); }

    final Cb cb;
    final Handler ui = new Handler(Looper.getMainLooper());
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    static final String CONTINUE = "#continue";

    int tab, area = 1;                      // area: 0 Reiter, 1 Kategorien, 2 Raster, 3 Detail
    final List<Vod.Cat> cats = new ArrayList<>();
    int ci, catTop; String status = "";
    final List<Vod.Item> items = new ArrayList<>();
    int gi, rowTop, page; boolean more, loadingItems; int gen;
    // Detail
    Vod.Item d; TreeMap<Integer, List<Vod.Episode>> eps; List<Integer> seasons = new ArrayList<>();
    int dFocus, dBtn, si, ei, epTop; String dStatus = "";
    final List<Object[]> hits = new ArrayList<>();   // {RectF, Runnable} fuer Touch

    Library(Context c, Cb cb) { super(c); this.cb = cb; }

    void open() {
        area = cats.isEmpty() ? 1 : area == 3 ? 3 : area;
        if (cats.isEmpty()) loadCats();
        setVisibility(VISIBLE); invalidate();
    }

    // ------------------------------------------------------------ Laden
    void loadCats() {
        final int g = ++gen;
        cats.clear(); items.clear(); ci = 0; catTop = 0; gi = 0; rowTop = 0;
        Vod.Cat cont = new Vod.Cat(); cont.id = CONTINUE; cont.name = "▶  Weiterschauen"; cont.kind = tab; cats.add(cont);
        status = "Lade Kategorien …"; invalidate();
        new Thread(new Runnable() { public void run() {
            final List<Vod.Cat> l = Vod.categories(getContext(), tab);
            ui.post(new Runnable() { public void run() {
                if (g != gen) return;
                cats.addAll(l);
                status = l.isEmpty() ? (tab == Vod.MOVIE ? "Deine Quellen bieten keine Filme an" : "Deine Quellen bieten keine Serien an – bei manchen Portalen stecken sie unter „Filme“") : "";
                if (cats.size() > 1 && continueItems().isEmpty()) ci = 1;
                loadItems(true);
            }});
        }}).start();
    }
    List<Vod.Item> continueItems() {
        List<Vod.Item> l = new ArrayList<>();
        for (Vod.Item it : Vod.continueList(getContext())) if ((it.series ? Vod.SERIES : Vod.MOVIE) == tab || (tab == Vod.MOVIE && !it.series)) l.add(it);
        return l;
    }
    void loadItems(boolean reset) {
        if (cats.isEmpty()) return;
        final Vod.Cat cat = cats.get(ci);
        if (reset) { items.clear(); gi = 0; rowTop = 0; page = 0; more = false; gen++; }
        if (cat.id.equals(CONTINUE)) { items.addAll(continueItems()); more = false; invalidate(); return; }
        if (loadingItems && !reset) return;
        final int g = gen, pg = page + 1;
        loadingItems = true; invalidate();
        new Thread(new Runnable() { public void run() {
            Vod.Page r; String err = null;
            try { r = Vod.items(getContext(), cat, pg); } catch (Exception e) { r = new Vod.Page(); err = e.getMessage(); }
            final Vod.Page rr = r; final String er = err;
            ui.post(new Runnable() { public void run() {
                if (g != gen) return;
                loadingItems = false;
                items.addAll(rr.items); more = rr.more; page = pg;
                if (er != null && items.isEmpty()) status = "Fehler: " + er; else if (items.isEmpty()) status = "Diese Kategorie ist leer"; else status = "";
                invalidate();
            }});
        }}).start();
    }
    void openDetail(final Vod.Item it) {
        d = it; eps = null; seasons.clear(); si = 0; ei = 0; epTop = 0; dFocus = 0; dBtn = 0; dStatus = it.series ? "Lade Folgen …" : "";
        area = 3; invalidate();
        final int g = ++gen;
        new Thread(new Runnable() { public void run() {
            TreeMap<Integer, List<Vod.Episode>> e = null; String err = null;
            if (it.series) { try { e = Vod.episodes(getContext(), it); } catch (Exception x) { err = x.getMessage(); } }
            else Vod.details(getContext(), it);
            final TreeMap<Integer, List<Vod.Episode>> ee = e; final String er = err;
            ui.post(new Runnable() { public void run() {
                if (g != gen || d != it) return;
                if (it.series) {
                    eps = ee; seasons.clear();
                    if (ee != null) seasons.addAll(ee.keySet());
                    dStatus = er != null ? "Fehler: " + er : seasons.isEmpty() ? "Keine Folgen gefunden" : "";
                    // zur zuletzt gesehenen Folge springen
                    JSONObject last = lastEpisode(it);
                    if (last != null && !seasons.isEmpty()) { int s = seasons.indexOf(last.optInt("s")); if (s >= 0) { si = s; List<Vod.Episode> l = eps.get(seasons.get(si)); for (int k = 0; k < l.size(); k++) if (l.get(k).num == last.optInt("e")) ei = last.optBoolean("done") && k + 1 < l.size() ? k + 1 : k; } }
                }
                invalidate();
            }});
        }}).start();
    }
    JSONObject lastEpisode(Vod.Item it) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(U.prefs(getContext()).getString("vod_resume", "[]"));
            for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); if (o.optString("k").startsWith(it.key() + "|")) return o; }
        } catch (Exception ignored) {}
        return null;
    }
    List<Vod.Episode> curEps() { return eps == null || seasons.isEmpty() ? Collections.<Vod.Episode>emptyList() : eps.get(seasons.get(si)); }

    // ------------------------------------------------------------ Tasten
    int cols() { float dp = U.dp; return Math.max(1, (int) ((getWidth() - 330 * dp - 30 * dp) / (138 * dp))); }

    boolean key(int k) {
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        if (k == KeyEvent.KEYCODE_BACK) {
            if (area == 3) { area = items.isEmpty() ? 1 : 2; d = null; invalidate(); return true; }
            cb.close(); return true;
        }
        switch (area) {
            case 0:
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) { int nt = k == KeyEvent.KEYCODE_DPAD_LEFT ? 0 : 1; if (nt != tab) { tab = nt; loadCats(); } invalidate(); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN || ok) { area = 1; invalidate(); return true; }
                return true;
            case 1:
                if (k == KeyEvent.KEYCODE_DPAD_UP) { if (ci == 0) area = 0; else { ci--; loadItems(true); } invalidate(); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) { if (ci < cats.size() - 1) { ci++; loadItems(true); } invalidate(); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_RIGHT || ok) { if (!items.isEmpty()) { area = 2; gi = 0; } invalidate(); return true; }
                return true;
            case 2: {
                int c = cols();
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) { if (gi % c == 0) area = 1; else gi--; }
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { if (gi + 1 < items.size()) gi++; }
                else if (k == KeyEvent.KEYCODE_DPAD_UP) { if (gi < c) area = 0; else gi -= c; }
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN) { gi = Math.min(items.size() - 1, gi + c); }
                else if (ok && gi < items.size()) { openDetail(items.get(gi)); return true; }
                if (more && !loadingItems && gi >= items.size() - c * 2) loadItems(false);
                invalidate(); return true;
            }
            case 3: return detailKey(k, ok);
        }
        return false;
    }
    boolean detailKey(int k, boolean ok) {
        if (d == null) return true;
        if (!d.series) {
            JSONObject r = Vod.resume(getContext(), d.key());
            int nb = r != null && r.optLong("pos") > 60000 ? 2 : 1;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) dBtn = Math.max(0, dBtn - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) dBtn = Math.min(nb - 1, dBtn + 1);
            else if (ok) cb.playVod(d, null, null, nb == 2 && dBtn == 0 ? r.optLong("pos") : 0);
            invalidate(); return true;
        }
        List<Vod.Episode> l = curEps();
        if (k == KeyEvent.KEYCODE_DPAD_UP) { if (dFocus == 2 && ei > 0) ei--; else if (dFocus > 0) dFocus--; }
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN) { if (dFocus < 2) { if (dFocus == 0 && seasons.size() <= 1) dFocus = 2; else dFocus++; } else if (ei < l.size() - 1) ei++; }
        else if (k == KeyEvent.KEYCODE_DPAD_LEFT && dFocus == 1) { if (si > 0) { si--; ei = 0; epTop = 0; } }
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && dFocus == 1) { if (si < seasons.size() - 1) { si++; ei = 0; epTop = 0; } }
        else if (ok && !l.isEmpty()) {
            Vod.Episode ep = l.get(Math.min(ei, l.size() - 1));
            JSONObject r = Vod.resume(getContext(), ep.key(d));
            cb.playVod(d, ep, l.subList(Math.min(ei, l.size() - 1) + 1, l.size()), r != null && !r.optBoolean("done") ? r.optLong("pos") : 0);
        }
        invalidate(); return true;
    }
    void scrollRows(int n) {
        if (area == 3) { List<Vod.Episode> l = curEps(); ei = Math.max(0, Math.min(l.size() - 1, ei + n)); dFocus = 2; }
        else if (area == 2 || !items.isEmpty()) { area = 2; gi = Math.max(0, Math.min(items.size() - 1, gi + n * cols())); if (more && !loadingItems && gi >= items.size() - cols() * 2) loadItems(false); }
        else { ci = Math.max(0, Math.min(cats.size() - 1, ci + n)); loadItems(true); }
        invalidate();
    }
    boolean tap(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) { Object[] h = hits.get(i); if (((RectF) h[0]).contains(x, y)) { ((Runnable) h[1]).run(); invalidate(); return true; } }
        return true;
    }
    void hit(RectF r, Runnable a) { hits.add(new Object[]{r, a}); }

    // ------------------------------------------------------------ Zeichnen
    @Override protected void onDraw(Canvas c) {
        hits.clear();
        float dp = U.dp, w = getWidth(), h = getHeight();
        c.drawColor(0xFF0B0A14);
        p.setShader(new RadialGradient(w * 0.15f, 0, w * 0.6f, 0x337C3AED, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, p); p.setShader(null);
        if (area == 3 && d != null) { drawDetail(c, w, h); return; }
        // Reiter
        float x = 40 * dp;
        t.setTypeface(U.POP); t.setTextSize(22 * dp); t.setColor(Color.WHITE); t.setTextAlign(Paint.Align.LEFT);
        c.drawText("Mediathek", x, 44 * dp, t);
        float tx = x + t.measureText("Mediathek") + 30 * dp;
        String[] tabs = {"🎬  Filme", "📺  Serien"};
        for (int i = 0; i < 2; i++) {
            t.setTextSize(15 * dp); t.setTypeface(i == tab ? U.BOLD : U.POP);
            float tw = t.measureText(tabs[i]) + 34 * dp;
            RectF r = new RectF(tx, 20 * dp, tx + tw, 54 * dp);
            if (i == tab) { p.setShader(area == 0 ? U.brand(r.left, r.right) : null); p.setColor(0xFF26243A); c.drawRoundRect(r, 17 * dp, 17 * dp, p); p.setShader(null); }
            else if (area == 0) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * dp); p.setColor(0x55FFFFFF); c.drawRoundRect(r, 17 * dp, 17 * dp, p); p.setStyle(Paint.Style.FILL); }
            t.setColor(i == tab ? Color.WHITE : 0xFFB4B8CC); c.drawText(tabs[i], tx + 17 * dp, 43 * dp, t);
            final int ti = i; hit(r, new Runnable() { public void run() { if (tab != ti) { tab = ti; loadCats(); } area = 1; } });
            tx += tw + 10 * dp;
        }
        t.setTypeface(U.POP); t.setTextSize(12 * dp); t.setColor(U.MUTED); t.setTextAlign(Paint.Align.RIGHT);
        c.drawText("ZURÜCK = Live-TV", w - 40 * dp, 42 * dp, t); t.setTextAlign(Paint.Align.LEFT);
        // Kategorien
        float top = 76 * dp, rh = 40 * dp, cw = 280 * dp;
        int vis = (int) ((h - top - 20 * dp) / rh);
        if (ci < catTop) catTop = ci; if (ci >= catTop + vis) catTop = ci - vis + 1;
        Set<String> srcs = new HashSet<>(); for (Vod.Cat k : cats) if (k.src != null) srcs.add(k.src);
        for (int i = catTop; i < cats.size() && i < catTop + vis; i++) {
            float y = top + (i - catTop) * rh;
            RectF r = new RectF(x - 10 * dp, y + 2 * dp, x + cw, y + rh - 2 * dp);
            boolean sel = i == ci;
            if (sel) {
                p.setColor(area == 1 ? 0xFF26243A : 0x1FFFFFFF); c.drawRoundRect(r, 10 * dp, 10 * dp, p);
                if (area == 1) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 10 * dp, 10 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
            }
            Vod.Cat k = cats.get(i);
            String nm = k.name + (srcs.size() > 1 && k.src != null && Lib.srcNames.containsKey(k.src) ? "  ·  " + Lib.srcNames.get(k.src) : "");
            t.setTypeface(sel ? U.BOLD : U.POP); t.setTextSize(14 * dp); t.setColor(sel ? Color.WHITE : 0xFFC9CCE0);
            c.drawText(Views.ell(t, nm, cw - 16 * dp), x + 4 * dp, y + rh / 2 + 5 * dp, t);
            final int idx = i; hit(r, new Runnable() { public void run() { ci = idx; area = 1; loadItems(true); } });
        }
        // Raster
        float gx = x + cw + 30 * dp, gw = w - gx - 30 * dp;
        int cols = cols();
        float cellW = 138 * dp, pw = 124 * dp, ph = 186 * dp, cellH = ph + 46 * dp;
        float gy = top + 64 * dp;
        // Info zum gewaehlten Titel
        Vod.Item sel = area == 2 && gi < items.size() ? items.get(gi) : null;
        if (sel != null) {
            t.setTypeface(U.BOLD); t.setTextSize(19 * dp); t.setColor(Color.WHITE);
            c.drawText(Views.ell(t, sel.name, gw), gx, top + 20 * dp, t);
            t.setTypeface(U.POP); t.setTextSize(12.5f * dp); t.setColor(0xFF9EC5FF);
            c.drawText(Views.ell(t, meta(sel), gw), gx, top + 42 * dp, t);
        } else if (!status.isEmpty() || loadingItems) {
            t.setTypeface(U.POP); t.setTextSize(15 * dp); t.setColor(U.MUTED);
            c.drawText(loadingItems && items.isEmpty() ? "Lade …" : status, gx, top + 30 * dp, t);
        } else {
            t.setTypeface(U.POP); t.setTextSize(13 * dp); t.setColor(U.MUTED);
            c.drawText(cats.isEmpty() ? "" : items.size() + (more ? "+" : "") + " Titel  ·  ▶ ins Raster wechseln", gx, top + 30 * dp, t);
        }
        int visRows = Math.max(1, (int) ((h - gy) / cellH));
        int row = gi / cols;
        if (row < rowTop) rowTop = row; if (row >= rowTop + visRows) rowTop = row - visRows + 1;
        c.save(); c.clipRect(gx - 10 * dp, gy - 10 * dp, w, h);
        for (int i = rowTop * cols; i < items.size() && i < (rowTop + visRows + 1) * cols; i++) {
            Vod.Item it = items.get(i);
            float cx = gx + (i % cols) * cellW, cy = gy + (i / cols - rowTop) * cellH;
            boolean s = area == 2 && i == gi;
            RectF r = new RectF(cx, cy, cx + pw, cy + ph);
            if (s) { float g = 5 * dp; r = new RectF(r.left - g, r.top - g, r.right + g, r.bottom + g); }
            poster(c, it, r);
            if (s) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 10 * dp, 10 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); }
            JSONObject res = it.series ? null : Vod.resume(getContext(), it.key());
            if (res != null && res.optLong("dur") > 0 && res.optLong("pos") > 0) Views.bar(c, p, r.left + 8 * dp, r.bottom - 10 * dp, r.width() - 16 * dp, 4 * dp, res.optLong("pos") / (float) res.optLong("dur"));
            t.setTypeface(s ? U.BOLD : U.POP); t.setTextSize(11.5f * dp); t.setColor(s ? Color.WHITE : 0xFFC9CCE0);
            c.drawText(Views.ell(t, it.name, pw), cx, cy + ph + 18 * dp, t);
            t.setTextSize(10.5f * dp); t.setColor(U.MUTED); t.setTypeface(U.POP);
            c.drawText(Views.ell(t, (it.year.isEmpty() ? "" : it.year) + (it.rating.isEmpty() ? "" : "  ★ " + it.rating) + (it.series ? "  · Serie" : ""), pw), cx, cy + ph + 34 * dp, t);
            final Vod.Item fi = it; final int idx = i;
            hit(new RectF(cx, cy, cx + pw, cy + cellH), new Runnable() { public void run() { gi = idx; area = 2; openDetail(fi); } });
        }
        c.restore();
        if (loadingItems && !items.isEmpty()) { t.setTextSize(12 * dp); t.setColor(U.MUTED); t.setTextAlign(Paint.Align.RIGHT); c.drawText("lade weitere …", w - 30 * dp, h - 12 * dp, t); t.setTextAlign(Paint.Align.LEFT); }
    }
    String meta(Vod.Item it) {
        StringBuilder sb = new StringBuilder();
        if (!it.year.isEmpty()) sb.append(it.year);
        if (!it.rating.isEmpty()) sb.append(sb.length() > 0 ? "   " : "").append("★ ").append(it.rating);
        if (!it.duration.isEmpty()) sb.append(sb.length() > 0 ? "   " : "").append(it.duration);
        if (!it.genre.isEmpty()) sb.append(sb.length() > 0 ? "   " : "").append(it.genre);
        if (it.series) sb.append(sb.length() > 0 ? "   " : "").append("Serie");
        return sb.toString();
    }
    void poster(Canvas c, Vod.Item it, RectF r) {
        float rad = 10 * U.dp;
        Bitmap b = it.poster.isEmpty() ? null : Img.get(it.poster, 360, new Views.Inval(this));
        c.save();
        Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.clipPath(clip);
        if (b != null) {
            float s = Math.max(r.width() / b.getWidth(), r.height() / b.getHeight());
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            c.drawBitmap(b, null, new RectF(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2), p);
        } else {
            p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, U.hashColor(it.name), 0xFF15141F, Shader.TileMode.CLAMP)); c.drawRect(r, p); p.setShader(null);
            t.setTypeface(U.BOLD); t.setTextSize(13 * U.dp); t.setColor(Color.WHITE);
            Views.multi(c, t, it.name, r.left + 10 * U.dp, r.top + 28 * U.dp, r.width() - 20 * U.dp, 5);
        }
        c.restore();
    }

    void drawDetail(Canvas c, float w, float h) {
        float dp = U.dp;
        // Hintergrund: Poster unscharf
        Bitmap b = d.poster.isEmpty() ? null : Img.get(d.poster, 360, new Views.Inval(this));
        if (b != null) {
            Bitmap soft = Bitmap.createScaledBitmap(b, 12, 18, true);
            p.setAlpha(80); c.drawBitmap(soft, null, new RectF(0, -h * 0.2f, w, h * 1.2f), p); p.setAlpha(255);
            p.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xF20B0A14, 0xCC0B0A14, 0xF20B0A14}, null, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, p); p.setShader(null);
        }
        float x = 50 * dp, top = 40 * dp, pw = 230 * dp, ph = 345 * dp;
        poster(c, d, new RectF(x, top, x + pw, top + ph));
        float tx = x + pw + 40 * dp, tw = w - tx - 50 * dp, y = top + 30 * dp;
        t.setTypeface(U.BOLD); t.setTextSize(28 * dp); t.setColor(Color.WHITE);
        c.drawText(Views.ell(t, d.name, tw), tx, y, t);
        y += 28 * dp;
        t.setTypeface(U.POP); t.setTextSize(14 * dp); t.setColor(0xFF9EC5FF);
        c.drawText(Views.ell(t, meta(d), tw), tx, y, t);
        y += 30 * dp;
        t.setColor(0xFFD6D8E6); t.setTextSize(14.5f * dp);
        y = Views.multi(c, t, d.plot.isEmpty() ? "Keine Beschreibung vorhanden." : d.plot, tx, y, tw, d.series ? 3 : 7);
        t.setColor(U.MUTED); t.setTextSize(12.5f * dp);
        if (!d.director.isEmpty()) { y += 6 * dp; c.drawText(Views.ell(t, "Regie: " + d.director, tw), tx, y, t); y += 18 * dp; }
        if (!d.cast.isEmpty()) { c.drawText(Views.ell(t, "Mit: " + d.cast, tw), tx, y, t); y += 18 * dp; }
        y += 14 * dp;
        if (!d.series) {
            JSONObject r = Vod.resume(getContext(), d.key());
            List<String> btn = new ArrayList<>();
            if (r != null && r.optLong("pos") > 60000) { btn.add("▶  Fortsetzen ab " + time(r.optLong("pos"))); btn.add("↺  Von vorn"); }
            else btn.add("▶  Abspielen");
            float bx = tx;
            for (int i = 0; i < btn.size(); i++) {
                t.setTypeface(U.BOLD); t.setTextSize(15 * dp);
                float bw = t.measureText(btn.get(i)) + 40 * dp;
                RectF br = new RectF(bx, y, bx + bw, y + 44 * dp);
                if (i == dBtn) { p.setShader(U.brand(br.left, br.right)); c.drawRoundRect(br, 22 * dp, 22 * dp, p); p.setShader(null); }
                else { p.setColor(0xFF26243A); c.drawRoundRect(br, 22 * dp, 22 * dp, p); }
                t.setColor(Color.WHITE); c.drawText(btn.get(i), bx + 20 * dp, y + 28 * dp, t);
                final int bi = i; final JSONObject rr = r;
                hit(br, new Runnable() { public void run() { dBtn = bi; cb.playVod(d, null, null, rr != null && rr.optLong("pos") > 60000 && bi == 0 ? rr.optLong("pos") : 0); } });
                bx += bw + 14 * dp;
            }
            if (r != null && r.optLong("dur") > 0) { t.setTypeface(U.POP); t.setTextSize(12 * dp); t.setColor(U.MUTED); c.drawText("Gesehen: " + time(r.optLong("pos")) + " von " + time(r.optLong("dur")), tx, y + 70 * dp, t); }
            return;
        }
        // Serie: Staffeln + Folgen
        if (!dStatus.isEmpty()) { t.setTypeface(U.POP); t.setTextSize(15 * dp); t.setColor(U.MUTED); c.drawText(dStatus, tx, y + 10 * dp, t); return; }
        float sx = tx;
        for (int i = 0; i < seasons.size(); i++) {
            String s = "Staffel " + seasons.get(i);
            t.setTypeface(i == si ? U.BOLD : U.POP); t.setTextSize(13.5f * dp);
            float sw = t.measureText(s) + 28 * dp;
            if (sx + sw > tx + tw) break;
            RectF sr = new RectF(sx, y, sx + sw, y + 32 * dp);
            if (i == si) { p.setShader(dFocus == 1 ? U.brand(sr.left, sr.right) : null); p.setColor(0xFF2E2B4A); c.drawRoundRect(sr, 16 * dp, 16 * dp, p); p.setShader(null); }
            else { p.setColor(0x1FFFFFFF); c.drawRoundRect(sr, 16 * dp, 16 * dp, p); }
            t.setColor(Color.WHITE); c.drawText(s, sx + 14 * dp, y + 21 * dp, t);
            final int idx = i; hit(sr, new Runnable() { public void run() { si = idx; ei = 0; epTop = 0; dFocus = 1; } });
            sx += sw + 8 * dp;
        }
        y += 44 * dp;
        List<Vod.Episode> l = curEps();
        float rh = 46 * dp;
        int vis = Math.max(1, (int) ((h - y - 20 * dp) / rh));
        if (ei < epTop) epTop = ei; if (ei >= epTop + vis) epTop = ei - vis + 1;
        for (int i = epTop; i < l.size() && i < epTop + vis; i++) {
            Vod.Episode ep = l.get(i);
            float ry = y + (i - epTop) * rh;
            RectF r = new RectF(tx - 8 * dp, ry, tx + tw, ry + rh - 4 * dp);
            boolean s = i == ei;
            if (s) { p.setColor(dFocus == 2 ? 0xFF26243A : 0x14FFFFFF); c.drawRoundRect(r, 10 * dp, 10 * dp, p);
                if (dFocus == 2) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 10 * dp, 10 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL); } }
            JSONObject res = Vod.resume(getContext(), ep.key(d));
            t.setTypeface(s ? U.BOLD : U.POP); t.setTextSize(14 * dp); t.setColor(Color.WHITE);
            String title = ep.num + ".  " + (ep.title.isEmpty() || ep.title.equals("Folge " + ep.num) ? "Folge " + ep.num : ep.title);
            c.drawText(Views.ell(t, title, tw - 170 * dp), tx + 6 * dp, ry + 27 * dp, t);
            t.setTypeface(U.POP); t.setTextSize(12 * dp); t.setTextAlign(Paint.Align.RIGHT);
            if (res != null && res.optBoolean("done")) { t.setColor(0xFF34D399); c.drawText("✓ gesehen", tx + tw - 10 * dp, ry + 27 * dp, t); }
            else if (res != null && res.optLong("pos") > 0) { t.setColor(0xFF9EC5FF); c.drawText("▶ ab " + time(res.optLong("pos")), tx + tw - 10 * dp, ry + 27 * dp, t); }
            else if (!ep.duration.isEmpty()) { t.setColor(U.MUTED); c.drawText(ep.duration, tx + tw - 10 * dp, ry + 27 * dp, t); }
            t.setTextAlign(Paint.Align.LEFT);
            final int idx = i;
            hit(r, new Runnable() { public void run() { ei = idx; dFocus = 2; detailKey(KeyEvent.KEYCODE_DPAD_CENTER, true); } });
        }
    }
    static String time(long ms) {
        long s = ms / 1000, hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
        return hh > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hh, mm, ss) : String.format(Locale.ROOT, "%d:%02d", mm, ss);
    }
}
