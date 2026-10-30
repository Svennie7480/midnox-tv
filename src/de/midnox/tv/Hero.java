package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Grosses Hintergrundbild oben rechts (wie bei Netflix) plus Info-Block links.
 * Liegt hinter der Seite; blendet weich ein und aus.
 */
public class Hero {
    /** Hintergrundbild, nach links und unten in den Hintergrund auslaufend. */
    static class Backdrop extends View {
        Bitmap bmp;
        float alpha = 0f, target = 0f;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Backdrop(Context c) { super(c); setFocusable(false); }
        void set(Bitmap b) { if (b != null) bmp = b; target = b == null ? 0f : 1f; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            alpha += (target - alpha) * 0.18f;
            if (Math.abs(target - alpha) > 0.01f) postInvalidateOnAnimation(); else alpha = target;
            if (bmp == null || alpha <= 0.01f) return;
            float w = getWidth(), h = getHeight();
            float bw = w * 0.66f, bh = bw * 9f / 16f;
            RectF r = new RectF(w - bw, 0, w, bh);
            int save = c.saveLayer(r, null);
            float s = Math.max(bw / bmp.getWidth(), bh / bmp.getHeight());
            float iw = bmp.getWidth() * s, ih = bmp.getHeight() * s;
            p.setAlpha((int) (255 * alpha * 0.6f));
            c.drawBitmap(bmp, null, new RectF(r.centerX() - iw / 2, r.centerY() - ih / 2, r.centerX() + iw / 2, r.centerY() + ih / 2), p);
            // Ausblenden nach links und unten (Maske)
            Paint m = new Paint();
            m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            m.setShader(new LinearGradient(r.left, 0, r.left + bw * 0.55f, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            c.drawRect(r, m);
            m.setShader(new LinearGradient(0, bh * 0.3f, 0, bh * 0.85f, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(r, m);
            c.restoreToCount(save);
        }
    }

    /** Abdunklung: links kraeftig, nach rechts und unten weich auslaufend. */
    static class Scrim extends android.graphics.drawable.Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), m = new Paint();
        Scrim() { m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN)); }
        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            float w = r.width(), h = r.height();
            int save = c.saveLayer(r.left, r.top, r.right, r.bottom, null);
            p.setShader(new LinearGradient(r.left, 0, r.left + w * 0.62f, 0, new int[]{0xB8050409, 0x8C050409, 0x00050409}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            m.setShader(new LinearGradient(0, r.top + h * 0.7f, 0, r.bottom, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(r, m);
            c.restoreToCount(save);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    final HomeActivity act;
    final Backdrop backdrop;
    final LinearLayout info;
    final TextView kind, title, meta, overview;
    final ImageView logo;
    final View normal;              // Logo-Kopf (wird beim Medien-Fokus ausgeblendet)
    final Handler h = new Handler(Looper.getMainLooper());
    final LinkedHashMap<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 12; }
    };
    Emby.Entry current;
    Runnable pending;

    Hero(HomeActivity a, View normalHeader) {
        act = a;
        normal = normalHeader;
        backdrop = new Backdrop(a);
        info = new LinearLayout(a);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Style.px(64), Style.px(24), 0, 0);
        kind = Style.text(a, "", 11, Style.MUTED);
        kind.setLetterSpacing(0.3f);
        title = Style.text(a, "", 28, Color.WHITE);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Style.px(4), 0, Style.px(4));
        meta = Style.text(a, "", 12, 0xFFB4B8CC);
        overview = Style.text(a, "", 12, 0xCCFFFFFF);
        overview.setMaxLines(2); overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(Style.px(3), 1f);
        overview.setPadding(0, Style.px(6), 0, 0);
        logo = new ImageView(a);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setMaxHeight(Style.px(64));
        logo.setMaxWidth((int) (a.getResources().getDisplayMetrics().widthPixels * 0.36f));
        logo.setPadding(0, Style.px(6), 0, Style.px(6));
        logo.setVisibility(View.GONE);
        info.addView(kind); info.addView(logo); info.addView(title); info.addView(meta); info.addView(overview);
        info.setAlpha(0f);
        info.setBackground(new Scrim());   // dezente Abdunklung hinter Titel und Beschreibung
        int maxW = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.5f);
        title.setMaxWidth(maxW); overview.setMaxWidth(maxW); meta.setMaxWidth(maxW);
    }

    void showLogo(Bitmap b) {
        if (b == null) { logo.setVisibility(View.GONE); title.setVisibility(View.VISIBLE); return; }
        logo.setImageBitmap(b);
        logo.setVisibility(View.VISIBLE);
        title.setVisibility(View.GONE);
    }

    /** null = normaler Kopf (Logo, Gruss); sonst Infos zum fokussierten Titel. */
    void show(final Emby.Entry e) {
        if (pending != null) h.removeCallbacks(pending);
        if (e == null) {
            current = null;
            backdrop.set(null);
            info.animate().alpha(0f).setDuration(180).start();
            normal.animate().alpha(1f).setDuration(220).start();
            return;
        }
        current = e;
        kind.setText(e.kind == null ? "" : e.kind);
        title.setText(e.title);
        final Bitmap lc = e.heroLogo == null ? null : cache.get(e.heroLogo);
        showLogo(lc);
        meta.setText(e.meta == null ? "" : e.meta);
        overview.setText(e.overview == null ? "" : e.overview);
        overview.setVisibility(Style.prefs(act).getBoolean("hero_text", true) ? View.VISIBLE : View.GONE);
        normal.animate().alpha(0f).setDuration(160).start();
        info.animate().alpha(1f).setDuration(220).start();
        // Bild leicht verzoegert laden, damit schnelles Durchscrollen fluessig bleibt
        pending = new Runnable() { public void run() {
            if (e.heroLogo != null && cache.get(e.heroLogo) == null) {
                new Thread(new Runnable() { public void run() {
                    final Bitmap lb = Emby.fetchAlpha(e.heroLogo);
                    h.post(new Runnable() { public void run() { if (lb != null) cache.put(e.heroLogo, lb); if (current == e) showLogo(lb); } });
                }}).start();
            }
            final String url = e.backdrop;
            Bitmap b = url == null ? null : cache.get(url);
            if (b != null || url == null) { if (current == e) backdrop.set(b); return; }
            new Thread(new Runnable() { public void run() {
                final Bitmap nb = Emby.fetch(url);
                h.post(new Runnable() { public void run() {
                    if (nb != null) cache.put(url, nb);
                    if (current == e) backdrop.set(nb);
                }});
            }}).start();
        }};
        h.postDelayed(pending, 250);
    }
}
