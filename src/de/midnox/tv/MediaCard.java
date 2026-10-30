package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.widget.HorizontalScrollView;

/** Vorschaukarte (16:9) im midnox-Stil mit Fokus-Leuchten und Fortschrittsbalken. */
public class MediaCard extends View {
    public interface Listener {
        void onFocus(MediaCard card);
        void onClick(MediaCard card);
        void onLongClick(MediaCard card);
    }

    public final Emby.Entry e;
    final HorizontalScrollView sv;
    final Listener l;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    boolean focused;
    public boolean selected; // z. B. aktuelle Folge

    public MediaCard(Context c, Emby.Entry e, HorizontalScrollView sv, Listener l) {
        super(c);
        this.e = e; this.sv = sv; this.l = l;
        setFocusable(true); setClickable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        t.setTypeface(Style.POP);
        setOnClickListener(new OnClickListener() { public void onClick(View v) { MediaCard.this.l.onClick(MediaCard.this); } });
        setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { MediaCard.this.l.onLongClick(MediaCard.this); return true; } });
        setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
            focused = has;
            animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
            invalidate();
            if (has) {
                if (MediaCard.this.sv != null) MediaCard.this.sv.smoothScrollTo(Math.max(0, v.getLeft() + v.getWidth() / 2 - MediaCard.this.sv.getWidth() / 2), 0);
                MediaCard.this.l.onFocus(MediaCard.this);
            }
        }});
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { l.onLongClick(this); return true; }
        return super.onKeyDown(keyCode, ev);
    }

    @Override protected void onDraw(Canvas c) {
        float dp = Style.dp, w = getWidth(), h = getHeight(), rad = 12 * dp;
        RectF r = new RectF(0, 0, w, h);
        if (focused) {
            glow.setShader(Style.brand(0, w));
            glow.setMaskFilter(new BlurMaskFilter(12 * dp, BlurMaskFilter.Blur.NORMAL));
            glow.setAlpha(150);
            c.drawRoundRect(r, rad, rad, glow);
        }
        int save = c.save();
        Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        p.setShader(null); p.setColor(Style.CARD); c.drawRect(r, p);
        if (e.bmp != null) {
            float s = Math.max(w / e.bmp.getWidth(), h / e.bmp.getHeight());
            float bw = e.bmp.getWidth() * s, bh = e.bmp.getHeight() * s;
            c.drawBitmap(e.bmp, null, new RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), p);
        }
        p.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x00000000, 0xE6050508, Shader.TileMode.CLAMP));
        c.drawRect(r, p);
        p.setShader(null);
        t.setColor(Color.WHITE); t.setTextSize(12 * dp);
        String sub = e.sub == null ? "" : e.sub;
        c.drawText(TextUtils.ellipsize(e.title, t, w - 18 * dp, TextUtils.TruncateAt.END).toString(), 9 * dp, h - (sub.isEmpty() ? 10 : 22) * dp, t);
        if (!sub.isEmpty()) {
            t.setColor(0xFFB4B8CC); t.setTextSize(9 * dp);
            c.drawText(TextUtils.ellipsize(sub, t, w - 18 * dp, TextUtils.TruncateAt.END).toString(), 9 * dp, h - 9 * dp, t);
        }
        if (e.progress > 0) {
            p.setColor(0x55FFFFFF);
            c.drawRect(0, h - 3 * dp, w, h, p);
            p.setShader(Style.brand(0, w));
            c.drawRect(0, h - 3 * dp, (float) (w * Math.min(100, e.progress) / 100.0), h, p);
            p.setShader(null);
        }
        c.restoreToCount(save);
        p.setStyle(Paint.Style.STROKE);
        if (focused) { p.setShader(Style.brand(0, w)); p.setStrokeWidth(3 * dp); }
        else if (selected) { p.setShader(Style.brand(0, w)); p.setStrokeWidth(1.5f * dp); }
        else { p.setShader(null); p.setColor(0x22FFFFFF); p.setStrokeWidth(1 * dp); }
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL); p.setShader(null);
    }
}
