package de.midnox.radio;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.TextView;

/** Gemeinsame Zeichenbausteine: Hintergrund, Senderlogo-Kachel, Verlaufsschrift, Knopf. */
final class Ui {
    /** Dunkler Hintergrund mit midnox-Schimmer; optional getoent (Senderfarbe). */
    static final class Bg extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int tint = U.C1; Bitmap art;
        @Override public void draw(Canvas c) {
            Rect r = getBounds(); float w = r.width(), h = r.height();
            c.drawColor(U.BG);
            if (art != null) {
                p.setShader(null); p.setAlpha(110); p.setFilterBitmap(true);
                float s = Math.max(w / art.getWidth(), h / art.getHeight());
                RectF d = new RectF(w / 2 - art.getWidth() * s / 2, h / 2 - art.getHeight() * s / 2, w / 2 + art.getWidth() * s / 2, h / 2 + art.getHeight() * s / 2);
                c.drawBitmap(art, null, d, p);
                p.setAlpha(255);
                p.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xF20B0A14, 0xB00B0A14, 0xE00B0A14}, null, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
            }
            p.setShader(new RadialGradient(w * 0.12f, h * 0.05f, w * 0.55f, (tint & 0xFFFFFF) | 0x38000000, 0, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.95f, h * 1.05f, w * 0.5f, 0x2606B6D4, 0, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** Weich gezeichnetes (unscharfes) Bild fuer Hintergruende. */
    static Bitmap soft(Bitmap b) {
        if (b == null) return null;
        Bitmap s = Bitmap.createScaledBitmap(b, 16, 16, true);
        return Bitmap.createScaledBitmap(s, 96, 96, true);
    }

    static final Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    static final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /** Senderlogo in einer abgerundeten Kachel (oder Monogramm, wenn kein Logo). */
    static void logo(Canvas c, RectF r, Station s, Bitmap b, float radius) {
        lp.setShader(null);
        if (b != null) {
            boolean light = isLight(b);
            lp.setColor(light ? 0xFF1A1928 : 0xFFF4F4F8);
            if (b.hasAlpha()) c.drawRoundRect(r, radius, radius, lp); else { lp.setColor(0xFF000000); c.drawRoundRect(r, radius, radius, lp); }
            c.save();
            Path clip = new Path(); clip.addRoundRect(r, radius, radius, Path.Direction.CW);
            c.clipPath(clip);
            float pad = b.hasAlpha() ? r.width() * 0.1f : 0;
            RectF in = new RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad);
            float sc = Math.min(in.width() / b.getWidth(), in.height() / b.getHeight());
            float bw = b.getWidth() * sc, bh = b.getHeight() * sc;
            if (!b.hasAlpha()) { sc = Math.max(r.width() / b.getWidth(), r.height() / b.getHeight()); bw = b.getWidth() * sc; bh = b.getHeight() * sc; }
            c.drawBitmap(b, null, new RectF(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2), lp);
            c.restore();
        } else {
            int col = s.color;
            lp.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, col, darker(col), Shader.TileMode.CLAMP));
            c.drawRoundRect(r, radius, radius, lp);
            lp.setShader(null);
            tp.setColor(0xFFFFFFFF); tp.setTextAlign(Paint.Align.CENTER); tp.setFakeBoldText(true);
            String m = mono(s.name);
            tp.setTextSize(r.width() * (m.length() > 2 ? 0.26f : 0.36f));
            c.drawText(m, r.centerX(), r.centerY() + tp.getTextSize() * 0.36f, tp);
            tp.setFakeBoldText(false);
        }
    }

    static String mono(String n) {
        String x = n.replaceAll("(?i)^(radio|the|die|das|der)\\s+", "").trim();
        if (x.isEmpty()) x = n;
        if (x.matches("^[0-9.,]+.*")) return x.replaceAll("^([0-9.,]+).*", "$1");
        String[] w = x.split("\\s+");
        String m = w.length > 1 && Character.isLetterOrDigit(w[1].charAt(0)) ? "" + w[0].charAt(0) + w[1].charAt(0) : x.substring(0, Math.min(2, x.length()));
        return m.toUpperCase();
    }
    static int darker(int c) { float[] h = new float[3]; Color.colorToHSV(c, h); h[2] *= 0.55f; h[0] = (h[0] + 25) % 360; return Color.HSVToColor(h); }
    /** Logo mit transparentem Rand und dunkler Schrift? -> heller Hintergrund. */
    static final java.util.WeakHashMap<Bitmap, Boolean> lightCache = new java.util.WeakHashMap<>();
    static boolean isLight(Bitmap b) {
        if (!b.hasAlpha()) return false;
        Boolean cached = lightCache.get(b);
        if (cached != null) return cached;
        boolean r = calcLight(b); lightCache.put(b, r); return r;
    }
    static boolean calcLight(Bitmap b) {
        long sum = 0; int n = 0;
        int sw = b.getWidth(), sh = b.getHeight();
        for (int y = 0; y < sh; y += Math.max(1, sh / 24)) for (int x = 0; x < sw; x += Math.max(1, sw / 24)) {
            int p = b.getPixel(x, y);
            if ((p >>> 24) < 128) continue;
            sum += (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100; n++;
        }
        return n > 0 && sum / n > 150;   // helle Logos auf dunklem Grund
    }

    static String ell(String s, TextPaint p, float w) { return TextUtils.ellipsize(s, p, w, TextUtils.TruncateAt.END).toString(); }

    /** Text im midnox-Verlauf. */
    static TextView gradient(Context c, String s, float sp) {
        final TextView t = U.text(c, s, sp, 0xFFFFFFFF);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { public void onLayoutChange(View v, int l, int tp, int r, int b, int ol, int ot, int or, int ob) {
            float w = t.getPaint().measureText(t.getText().toString());
            t.getPaint().setShader(U.brand(0, Math.max(w, 1)));
        }});
        return t;
    }

    /** Fokussierbarer Knopf (Pille). */
    static final class Pill extends TextView {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Pill(Context c, String s, final Runnable a) {
            super(c);
            setText(s); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); setTextColor(U.TEXT); setTypeface(U.POP);
            setGravity(Gravity.CENTER); setSingleLine(true);
            setPadding(U.px(22), U.px(10), U.px(22), U.px(10));
            setFocusable(true); setClickable(true);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { if (a != null) a.run(); } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean f) {
                animate().scaleX(f ? 1.07f : 1f).scaleY(f ? 1.07f : 1f).setDuration(120).start(); invalidate(); } });
        }
        @Override protected void onDraw(Canvas c) {
            RectF r = new RectF(U.dp, U.dp, getWidth() - U.dp, getHeight() - U.dp);
            float rad = r.height() / 2;
            if (isFocused()) { p.setStyle(Paint.Style.FILL); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, rad, rad, p); setTextColor(0xFFFFFFFF); }
            else { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(0x9915141F); c.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f * U.dp); p.setColor(0x44FFFFFF); c.drawRoundRect(r, rad, rad, p); setTextColor(U.TEXT); }
            super.onDraw(c);
        }
    }
}
