package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.View;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * midnox.de-Bildschirmschoner: grosse Bilder aus Emby mit langsamem Kamerafahrt-Effekt,
 * Titel-Logo, Uhr und Wetter. Ohne Emby (oder auf Wunsch) nur der midnox-Verlauf.
 */
public class Screensaver extends View {
    static class Slide { Bitmap img, logo; String title, meta; }

    final Handler h = new Handler(Looper.getMainLooper());
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    final List<Slide> slides = Collections.synchronizedList(new ArrayList<Slide>());
    volatile Weather.Now weather;
    Slide cur, prev;
    long slideStart, fadeStart, startedAt;
    int index = -1;
    boolean running;
    float driftX, driftY;   // Einbrenn-Schutz
    static final long SLIDE_MS = 14000, FADE_MS = 1800;

    public Screensaver(Context c) { super(c); setBackgroundColor(Color.BLACK); }

    boolean midnoxOnly() { return Style.prefs(getContext()).getBoolean("ss_midnox_only", false); }

    public void start() {
        running = true;
        startedAt = SystemClock.uptimeMillis();
        new Thread(new Runnable() { public void run() { load(); } }).start();
        h.post(frame);
    }
    public void stop() { running = false; h.removeCallbacksAndMessages(null); }

    final Runnable frame = new Runnable() { public void run() {
        if (!running) return;
        long now = SystemClock.uptimeMillis();
        if (!slides.isEmpty() && (cur == null || now - slideStart > SLIDE_MS)) next(now);
        long min = (System.currentTimeMillis() / 60000) % 8;   // alle paar Minuten leicht verschieben
        driftX = (float) Math.sin(min) * 10 * Style.dp; driftY = (float) Math.cos(min * 1.3) * 8 * Style.dp;
        invalidate();
        h.postDelayed(this, 33);
    }};

    void next(long now) {
        prev = cur;
        index = (index + 1) % slides.size();
        cur = slides.get(index);
        slideStart = now; fadeStart = now;
    }

    void load() {
        weather = Weather.fetch(getContext());
        if (midnoxOnly()) return;
        Emby e = Emby.from(getContext());
        if (e == null) return;
        try {
            JSONArray a = e.getObj("/emby/Users/" + e.user + "/Items?IncludeItemTypes=Movie,Series&Recursive=true&SortBy=Random&Limit=24&ImageTypes=Backdrop" + Emby.FIELDS).optJSONArray("Items");
            for (Emby.Entry en : e.entries(a, 24)) {
                if (!running) return;
                if (en.backdrop == null) continue;
                Slide s = new Slide();
                s.img = Emby.fetch(en.backdrop.replace("maxWidth=1280", "maxWidth=1920"));
                if (s.img == null) continue;
                s.logo = en.heroLogo == null ? null : Emby.fetchAlpha(en.heroLogo);
                s.title = en.title; s.meta = en.meta;
                slides.add(s);
                while (slides.size() > 12) slides.remove(0);
            }
        } catch (Exception ignored) {}
        // Wetter alle 15 Minuten neu
        h.postDelayed(new Runnable() { public void run() {
            if (running) new Thread(new Runnable() { public void run() { weather = Weather.fetch(getContext()); } }).start();
            if (running) h.postDelayed(this, 15 * 60000);
        }}, 15 * 60000);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), hh = getHeight(), dp = Style.dp;
        long now = SystemClock.uptimeMillis();
        boolean pictures = cur != null;
        if (pictures) {
            float fade = Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            if (prev != null && fade < 1f) drawSlide(c, prev, now - slideStart + SLIDE_MS, 1f);
            drawSlide(c, cur, now - slideStart, fade);
            // Abdunklung fuer Schrift
            p.setAlpha(255);
            p.setShader(new LinearGradient(0, hh * 0.45f, 0, hh, 0x00000000, 0xD9000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
            p.setShader(new LinearGradient(0, 0, w * 0.6f, 0, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
            p.setShader(null);
            // Titel / Logo unten links
            Slide s = cur;
            float x = 64 * dp + driftX, base = hh - 64 * dp + driftY;
            int a = (int) (255 * fade);
            if (s.meta != null && !s.meta.isEmpty()) {
                t.setTypeface(Style.POP); t.setTextSize(13 * dp); t.setColor(0xB3FFFFFF); t.setAlpha((int) (a * 0.7f));
                c.drawText(TextUtils.ellipsize(s.meta, t, w * 0.5f, TextUtils.TruncateAt.END).toString(), x, base, t);
                base -= 22 * dp;
            }
            if (s.logo != null) {
                float sc = Math.min(w * 0.3f / s.logo.getWidth(), 110 * dp / s.logo.getHeight());
                float lw = s.logo.getWidth() * sc, lh = s.logo.getHeight() * sc;
                p.setAlpha(a);
                c.drawBitmap(s.logo, null, new RectF(x, base - lh, x + lw, base), p);
                p.setAlpha(255);
            } else {
                t.setTypeface(Style.POP); t.setTextSize(36 * dp); t.setColor(Color.WHITE); t.setAlpha(a);
                c.drawText(TextUtils.ellipsize(s.title, t, w * 0.5f, TextUtils.TruncateAt.END).toString(), x, base, t);
            }
        } else {
            drawMidnox(c, w, hh, now);
        }
        // midnox.de oben links
        t.setTypeface(Style.POP); t.setTextSize((pictures ? 20 : 0) * dp); t.setAlpha(255);
        if (pictures) {
            t.setShader(Style.brand(56 * dp, 56 * dp + t.measureText("midnox.de")));
            c.drawText("midnox.de", 56 * dp + driftX, 56 * dp + driftY, t);
            t.setShader(null);
        }
        // Uhr + Datum + Wetter unten rechts (bzw. mittig im midnox-Modus)
        Date d = new Date();
        String clock = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(d);
        String date = new SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(d);
        Weather.Now wn = weather;
        String wl = wn == null ? null : Weather.line(wn);
        if (pictures) {
            float rx = w - 64 * dp + driftX, by = hh - 64 * dp + driftY;
            t.setTextAlign(Paint.Align.RIGHT);
            if (wl != null) {
                t.setTypeface(Style.POP); t.setTextSize(14 * dp); t.setColor(0xCCFFFFFF);
                c.drawText(wl, rx, by, t);
                Weather.Icon.draw(c, wn.cond, rx - t.measureText(wl) - 30 * dp, by - 18 * dp, 22 * dp, p);
                by -= 28 * dp;
            }
            t.setTypeface(Style.POP); t.setTextSize(15 * dp); t.setColor(0xB3FFFFFF);
            c.drawText(date, rx, by, t);
            t.setTypeface(thin); t.setTextSize(84 * dp); t.setColor(Color.WHITE);
            c.drawText(clock, rx + 4 * dp, by - 26 * dp, t);
            t.setTextAlign(Paint.Align.LEFT);
        } else {
            float cx = w / 2 + driftX * 3, cy = hh / 2 + driftY * 3;
            t.setTextAlign(Paint.Align.CENTER);
            t.setTypeface(Style.POP); t.setTextSize(72 * dp);
            float tw = t.measureText("midnox.de");
            t.setShader(Style.brand(cx - tw / 2, cx + tw / 2));
            c.drawText("midnox.de", cx, cy - 30 * dp, t);
            t.setShader(null);
            t.setTypeface(thin); t.setTextSize(56 * dp); t.setColor(Color.WHITE);
            c.drawText(clock, cx, cy + 50 * dp, t);
            t.setTypeface(Style.POP); t.setTextSize(14 * dp); t.setColor(0xB3FFFFFF);
            c.drawText(date + (wl == null ? "" : "   ·   " + wl), cx, cy + 84 * dp, t);
            t.setTextAlign(Paint.Align.LEFT);
        }
    }

    /** Bild mit langsamer Fahrt (Ken-Burns). */
    void drawSlide(Canvas c, Slide s, long age, float alpha) {
        float w = getWidth(), hh = getHeight();
        float prog = Math.min(1f, age / (float) (SLIDE_MS + FADE_MS));
        float zoom = 1.04f + 0.08f * prog;
        float sc = Math.max(w / s.img.getWidth(), hh / s.img.getHeight()) * zoom;
        float iw = s.img.getWidth() * sc, ih = s.img.getHeight() * sc;
        boolean dir = (s.hashCode() & 1) == 0;
        float ox = (iw - w) * (dir ? prog : 1 - prog), oy = (ih - hh) * 0.5f;
        p.setShader(null); p.setAlpha((int) (255 * alpha));
        c.drawBitmap(s.img, null, new RectF(-ox, -oy, -ox + iw, -oy + ih), p);
        p.setAlpha(255);
    }

    /** Ohne Bilder: wandernde Lichter im midnox-Verlauf. */
    void drawMidnox(Canvas c, float w, float hh, long now) {
        float tt = (now - startedAt) / 1000f;
        p.setShader(new LinearGradient(0, 0, 0, hh, 0xFF0B0A14, 0xFF06050C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hh, p);
        int[] cols = {Style.C1, Style.C2, Style.C3};
        for (int i = 0; i < 3; i++) {
            float cx = w * (0.5f + 0.38f * (float) Math.sin(tt * 0.05f + i * 2.1f));
            float cy = hh * (0.5f + 0.35f * (float) Math.cos(tt * 0.04f + i * 1.7f));
            int col = (cols[i] & 0x00FFFFFF) | 0x55000000;
            p.setShader(new RadialGradient(cx, cy, w * 0.42f, col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hh, p);
        }
        p.setShader(null);
    }
}
