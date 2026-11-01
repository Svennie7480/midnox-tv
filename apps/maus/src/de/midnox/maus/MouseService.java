package de.midnox.maus;

import android.accessibilityservice.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;

/**
 * Bedienungshilfe-Dienst: blendet den Mauszeiger ueber jeder App ein und fuehrt
 * Tippen, langes Druecken und Wischen an der Zeigerposition aus.
 */
public class MouseService extends AccessibilityService {
    static volatile MouseService inst;
    final Handler h = new Handler(Looper.getMainLooper());
    WindowManager wm;
    Pointer pointer;
    WindowManager.LayoutParams lp;
    float x, y; int sw, sh;
    float scrollPending; boolean scrolling;
    Server server;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Point p = new Point(); wm.getDefaultDisplay().getRealSize(p);
        sw = p.x; sh = p.y; x = sw / 2f; y = sh / 2f;
        pointer = new Pointer(this);
        int size = (int) (34 * getResources().getDisplayMetrics().density);
        lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = (int) x; lp.y = (int) y;
        try { wm.addView(pointer, lp); } catch (Exception ignored) {}
        pointer.setAlpha(0f);
        server = new Server(this);
        server.start();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        inst = null;
        if (server != null) server.stop();
        try { wm.removeView(pointer); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // ------------------------------------------------------------ Befehle (vom Handy)
    void move(final float dx, final float dy) {
        h.post(new Runnable() { public void run() {
            x = Math.max(0, Math.min(sw - 2, x + dx));
            y = Math.max(0, Math.min(sh - 2, y + dy));
            lp.x = (int) x; lp.y = (int) y;
            try { wm.updateViewLayout(pointer, lp); } catch (Exception ignored) {}
            show();
        }});
    }

    void show() {
        h.removeCallbacks(hide);
        pointer.animate().cancel();
        pointer.setAlpha(1f);
        h.postDelayed(hide, 6000);
    }
    final Runnable hide = new Runnable() { public void run() { pointer.animate().alpha(0f).setDuration(500).start(); } };

    void tap(final long dur) {
        h.post(new Runnable() { public void run() {
            show();
            pointer.pulse();
            Path p = new Path(); p.moveTo(x, y); p.lineTo(x + 0.5f, y + 0.5f);
            try { dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p, 0, dur)).build(), null, null); } catch (Exception ignored) {}
        }});
    }

    /** Scrollen wie mit dem Finger: dx/dy in Bildschirmpunkten (Fingerrichtung). */
    void scroll(final float dy, final float dx) {
        h.post(new Runnable() { public void run() {
            scrollPending += dy;
            if (Math.abs(dx) > Math.abs(dy) * 2 && Math.abs(dx) > 40) { swipe(dx, 0); scrollPending = 0; return; }
            if (!scrolling) nextScroll();
        }});
    }
    void nextScroll() {
        if (Math.abs(scrollPending) < 6) { scrolling = false; return; }
        float d = Math.max(-sh * 0.45f, Math.min(sh * 0.45f, scrollPending));
        scrollPending -= d;
        scrolling = true;
        swipe(0, d);
    }
    void swipe(float dx, float dy) {
        float sx = Math.max(20, Math.min(sw - 20, x)), sy = Math.max(20, Math.min(sh - 20, y));
        if (sy + dy < 10) sy = 10 - dy; if (sy + dy > sh - 10) sy = sh - 10 - dy;
        if (sx + dx < 10) sx = 10 - dx; if (sx + dx > sw - 10) sx = sw - 10 - dx;
        Path p = new Path(); p.moveTo(sx, sy); p.lineTo(sx + dx, sy + dy);
        try {
            dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p, 0, 140)).build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) { h.postDelayed(new Runnable() { public void run() { nextScroll(); } }, 20); }
                @Override public void onCancelled(GestureDescription g) { scrolling = false; }
            }, null);
        } catch (Exception e) { scrolling = false; }
    }

    void global(int action) { performGlobalAction(action); }

    /** Der Zeiger: Pfeil im midnox-Verlauf mit Schatten, kurzer Ring beim Klicken. */
    static final class Pointer extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        long pulseAt;
        Pointer(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); }
        void pulse() { pulseAt = System.currentTimeMillis(); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float s = getWidth();
            long age = System.currentTimeMillis() - pulseAt;
            if (age < 350) {
                float q = age / 350f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * 0.06f); p.setShader(null);
                p.setColor(Color.argb((int) (200 * (1 - q)), 6, 182, 212));
                c.drawCircle(s * 0.08f, s * 0.08f, s * (0.12f + 0.38f * q), p);
                postInvalidateDelayed(16);
            }
            Path a = new Path();
            a.moveTo(s * 0.06f, s * 0.04f); a.lineTo(s * 0.06f, s * 0.78f); a.lineTo(s * 0.26f, s * 0.6f);
            a.lineTo(s * 0.4f, s * 0.92f); a.lineTo(s * 0.53f, s * 0.86f); a.lineTo(s * 0.39f, s * 0.55f); a.lineTo(s * 0.66f, s * 0.55f); a.close();
            p.setStyle(Paint.Style.FILL); p.setShader(null);
            p.setShadowLayer(s * 0.08f, 0, s * 0.03f, 0x99000000);
            p.setColor(Color.WHITE); c.drawPath(a, p);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * 0.055f); p.setStrokeJoin(Paint.Join.ROUND);
            p.setShader(new LinearGradient(0, 0, s, s, new int[]{0xFF7C3AED, 0xFF3B5BF0, 0xFF06B6D4}, null, Shader.TileMode.CLAMP));
            c.drawPath(a, p);
            p.setShader(null);
        }
    }
}
