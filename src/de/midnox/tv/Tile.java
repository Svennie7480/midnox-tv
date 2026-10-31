package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.HorizontalScrollView;

/** App-Kachel mit Banner/Icon oder gezeichnetem Symbol; leuchtet beim Fokus im Verlauf. */
public class Tile extends View {
    public static final int GLYPH_NONE = 0, GLYPH_GRID = 1, GLYPH_GEAR = 2, GLYPH_UPDATE = 3, GLYPH_HOME = 4, GLYPH_SEARCH = 5;
    final String label; final Drawable banner, icon; final int glyph;
    final Runnable action, longAction;
    HorizontalScrollView scroller;
    boolean pinned;
    String tag;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            glow = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), g = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean focused;
    final float dp = Style.dp;

    public Tile(Context c, String label, Drawable banner, Drawable icon, int glyph, Runnable action, Runnable longAction) {
        super(c);
        this.label = label; this.banner = banner; this.icon = icon; this.glyph = glyph;
        this.action = action; this.longAction = longAction;
        setFocusable(true); setFocusableInTouchMode(true); setClickable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        fill.setColor(Style.CARD);
        stroke.setStyle(Paint.Style.STROKE);
        txt.setTextSize(14 * dp); txt.setTextAlign(Paint.Align.CENTER); txt.setTypeface(Style.POP);
        setOnClickListener(new OnClickListener() { public void onClick(View v) { if (Tile.this.action != null) Tile.this.action.run(); } });
        if (longAction != null) setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { Tile.this.longAction.run(); return true; } });
        setOnFocusChangeListener(new OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                focused = has;
                animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
                invalidate();
                if (has && scroller != null) {
                    int target = v.getLeft() + v.getWidth() / 2 - scroller.getWidth() / 2;
                    scroller.smoothScrollTo(Math.max(0, target), 0);
                }
                if (has && getContext() instanceof HomeActivity) { ((HomeActivity) getContext()).reveal(v); ((HomeActivity) getContext()).showHero(null); }
            }
        });
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU && longAction != null) { longAction.run(); return true; }
        return super.onKeyDown(keyCode, e);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), pad = 10 * dp, rad = 14 * dp;
        float cardH = getHeight() - pad - 34 * dp;
        RectF card = new RectF(pad, pad, w - pad, pad + cardH);
        if (focused) {
            glow.setShader(Style.brand(card.left, card.right));
            glow.setMaskFilter(new BlurMaskFilter(14 * dp, BlurMaskFilter.Blur.NORMAL));
            glow.setAlpha(150);
            c.drawRoundRect(card, rad, rad, glow);
        }
        fill.setShader(new LinearGradient(0, card.top, 0, card.bottom, focused ? 0xFF262439 : 0xFF1A1928, focused ? 0xFF1A1928 : 0xFF121120, Shader.TileMode.CLAMP));
        c.drawRoundRect(card, rad, rad, fill);
        Drawable d = banner != null ? banner : icon;
        if (d != null) {
            int save = c.save();
            Path clip = new Path(); clip.addRoundRect(card, rad, rad, Path.Direction.CW);
            c.clipPath(clip);
            if (banner != null) {   // Banner randlos ueber die ganze Karte
                d.setBounds((int) card.left, (int) card.top, (int) card.right, (int) card.bottom);
            } else {                // nur Icon: gross und mittig
                int sz = (int) (card.height() * 0.78f); int cx = (int) card.centerX(), cy = (int) card.centerY();
                d.setBounds(cx - sz / 2, cy - sz / 2, cx + sz / 2, cy + sz / 2);
            }
            d.draw(c);
            c.restoreToCount(save);
        } else if (glyph != GLYPH_NONE) {
            drawGlyph(c, card);
        }
        if (focused) {
            stroke.setShader(Style.brand(card.left, card.right)); stroke.setStrokeWidth(3 * dp);
        } else {
            stroke.setShader(null); stroke.setColor(banner != null ? 0x00000000 : 0x14FFFFFF); stroke.setStrokeWidth(1 * dp);
        }
        c.drawRoundRect(card, rad, rad, stroke);
        if (pinned) { // kleiner Punkt: liegt auf der Startseite
            g.setShader(Style.brand(card.right - 22 * dp, card.right - 10 * dp));
            c.drawCircle(card.right - 14 * dp, card.top + 14 * dp, 6 * dp, g);
        }
        if (!focused) return;   // Name nur beim ausgewaehlten Element
        txt.setColor(Color.WHITE);
        String l = label;
        float maxW = w - 8 * dp;
        if (txt.measureText(l) > maxW) l = android.text.TextUtils.ellipsize(l, new android.text.TextPaint(txt), maxW, android.text.TextUtils.TruncateAt.END).toString();
        c.drawText(l, w / 2, card.bottom + 25 * dp, txt);
    }

    void drawGlyph(Canvas c, RectF card) {
        g.setShader(Style.brand(card.centerX() - 24 * dp, card.centerX() + 24 * dp));
        float cx = card.centerX(), cy = card.centerY();
        if (glyph == GLYPH_GRID) {
            float s = 11 * dp, gap = 6 * dp, start = -(s * 1.5f + gap);
            g.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                float x = cx + start + i * (s + gap), y = cy + start + j * (s + gap);
                c.drawRoundRect(new RectF(x, y, x + s, y + s), 3 * dp, 3 * dp, g);
            }
        } else if (glyph == GLYPH_UPDATE) { // Pfeil nach unten in einen Strich
            g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(5 * dp); g.setStrokeCap(Paint.Cap.ROUND); g.setStrokeJoin(Paint.Join.ROUND);
            c.drawLine(cx, cy - 18 * dp, cx, cy + 8 * dp, g);
            Path a = new Path(); a.moveTo(cx - 11 * dp, cy - 3 * dp); a.lineTo(cx, cy + 8 * dp); a.lineTo(cx + 11 * dp, cy - 3 * dp);
            c.drawPath(a, g);
            c.drawLine(cx - 16 * dp, cy + 18 * dp, cx + 16 * dp, cy + 18 * dp, g);
            g.setStyle(Paint.Style.FILL);
        } else if (glyph == GLYPH_SEARCH) {   // Lupe
            g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(5 * dp); g.setStrokeCap(Paint.Cap.ROUND);
            c.drawCircle(cx - 5 * dp, cy - 5 * dp, 14 * dp, g);
            c.drawLine(cx + 6 * dp, cy + 6 * dp, cx + 18 * dp, cy + 18 * dp, g);
            g.setStyle(Paint.Style.FILL);
        } else if (glyph == GLYPH_HOME) {   // Haus mit Licht-Punkt
            g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(5 * dp); g.setStrokeCap(Paint.Cap.ROUND); g.setStrokeJoin(Paint.Join.ROUND);
            Path house = new Path();
            house.moveTo(cx - 22 * dp, cy - 2 * dp); house.lineTo(cx, cy - 22 * dp); house.lineTo(cx + 22 * dp, cy - 2 * dp);
            house.moveTo(cx - 16 * dp, cy - 7 * dp); house.lineTo(cx - 16 * dp, cy + 20 * dp); house.lineTo(cx + 16 * dp, cy + 20 * dp); house.lineTo(cx + 16 * dp, cy - 7 * dp);
            c.drawPath(house, g);
            g.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy + 6 * dp, 5 * dp, g);
        } else if (glyph == GLYPH_GEAR) {
            g.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45f, cx, cy);
                c.drawRoundRect(new RectF(cx - 4 * dp, cy - 22 * dp, cx + 4 * dp, cy - 12 * dp), 2 * dp, 2 * dp, g);
                c.restore();
            }
            g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(6 * dp);
            c.drawCircle(cx, cy, 12 * dp, g);
            g.setStyle(Paint.Style.FILL);
        }
    }
}
