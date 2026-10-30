package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.TextView;

/** Fokussierbarer Knopf: im Fokus mit Verlauf gefuellt, sonst feine Kontur. */
public class Pill extends TextView {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public boolean active; // z. B. gewaehlte Staffel

    public Pill(Context c, String s, final Runnable action) {
        super(c);
        setText(s);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        setTypeface(Style.POP);
        setLetterSpacing(0.04f);
        setSingleLine(true);
        setGravity(Gravity.CENTER);
        setPadding(Style.px(24), Style.px(10), Style.px(24), Style.px(10));
        setFocusable(true); setClickable(true);
        setOnClickListener(new OnClickListener() { public void onClick(View v) { if (action != null) action.run(); } });
        setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
            animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
            invalidate();
        }});
    }

    @Override protected void onDraw(Canvas c) {
        float dp = Style.dp;
        RectF r = new RectF(1.5f * dp, 1.5f * dp, getWidth() - 1.5f * dp, getHeight() - 1.5f * dp);
        float rad = r.height() / 2;
        if (isFocused()) {
            p.setStyle(Paint.Style.FILL); p.setShader(Style.brand(r.left, r.right));
            c.drawRoundRect(r, rad, rad, p);
            setTextColor(Color.WHITE);
        } else {
            p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(0x9915141F);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f * dp);
            if (active) p.setShader(Style.brand(r.left, r.right)); else { p.setShader(null); p.setColor(0x33FFFFFF); }
            c.drawRoundRect(r, rad, rad, p);
            setTextColor(active ? Color.WHITE : 0xFFB4B8CC);
        }
        p.setShader(null);
        super.onDraw(c);
    }
}
