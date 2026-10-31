package de.midnox.tv;

import android.graphics.*;
import java.util.Random;

/**
 * Halloween-Kulisse: Nachthimmel, Vollmond mit ziehenden Wolken, knorrige Baeume an den Raendern,
 * Grabsteine, flackernde Kuerbislaternen, leuchtende Augen, ein Spinnennetz und eine Spinne am Faden,
 * ab und zu ein ferner Blitz. Statisches wird einmal in ein Bild gezeichnet, nur das Bewegte pro Bild.
 */
public class HalloweenScene {
    Bitmap layer; int lw, lh;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    float mx, my, mr;
    float[][] pumpkins;   // {x, groundY, size, phase}
    float[][] eyes;       // {x, y, phase}
    float[] ground;
    long nextFlash = 0, flashAt = 0;
    final Random rnd = new Random();

    static int a(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    float groundY(float x, float w) {
        int n = ground.length - 1;
        float f = Math.max(0, Math.min(n - 0.001f, x / w * n));
        int i = (int) f;
        return ground[i] + (ground[i + 1] - ground[i]) * (f - i);
    }

    void build(int w, int h) {
        lw = w; lh = h;
        float dp = Style.dp;
        layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(layer);
        Random r = new Random(7);
        // Himmel
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF07040C, 0xFF140A1C, 0xFF1C0D14, 0xFF0D0606}, new float[]{0, 0.45f, 0.78f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        for (int i = 0; i < 80; i++) { p.setColor(a(0xFFFFFF, 40 + r.nextInt(120))); c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.55f, r.nextFloat() * 1.1f * dp + 0.3f, p); }
        // Mond
        mx = w * 0.585f; my = h * 0.135f; mr = 44 * dp;
        p.setShader(new RadialGradient(mx, my, mr * 5, new int[]{a(0xFF9A3C, 70), a(0xFF7A00, 25), a(0xFF7A00, 0)}, new float[]{0, 0.35f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, mr * 5, p);
        p.setShader(new RadialGradient(mx - mr * 0.35f, my - mr * 0.35f, mr * 1.5f, new int[]{0xFFFFF4DC, 0xFFF3D9A8, 0xFFD9B47A}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, mr, p);
        p.setShader(null);
        float[][] craters = {{-0.3f, -0.1f, 0.18f, 40}, {0.25f, 0.2f, 0.22f, 35}, {0.05f, -0.35f, 0.1f, 30}, {0.35f, -0.25f, 0.08f, 30}, {-0.2f, 0.35f, 0.12f, 28}};
        for (float[] k : craters) { p.setColor(a(0xB08A55, (int) k[3])); c.drawCircle(mx + k[0] * mr, my + k[1] * mr, k[2] * mr, p); }
        // Huegel
        hill(c, w, h, 0.86f, 0.07f, 0xFF160A17, 3);
        ground = hill(c, w, h, 0.93f, 0.05f, 0xFF0A0509, 5);
        // Baeume an den Raendern
        tree(c, w * 0.02f, groundY(w * 0.02f, w) + 4 * dp, 150 * dp, 0.18f, 11);
        tree(c, w * 0.985f, groundY(w * 0.985f, w) + 4 * dp, 140 * dp, -0.2f, 23);
        // Grabsteine
        p.setColor(0xFF07040A);
        float[][] graves = {{0.13f, 18, 26, 0}, {0.9f, 16, 24, 0}, {0.86f, 12, 18, 1}};
        for (float[] g : graves) {
            float x = w * g[0], y = groundY(x, w) + 3 * dp, gw = g[1] * dp, gh = g[2] * dp;
            if (g[3] > 0) { c.drawRect(x - gw * 0.15f, y - gh, x + gw * 0.15f, y, p); c.drawRect(x - gw * 0.5f, y - gh * 0.75f, x + gw * 0.5f, y - gh * 0.55f, p); }
            else c.drawRoundRect(new RectF(x - gw / 2, y - gh, x + gw / 2, y + 4 * dp), gw / 2, gw / 2, p);
        }
        // Kuerbis-Koerper (Gesichter flackern spaeter)
        pumpkins = new float[][]{{w * 0.035f, 0, 34 * dp, 0}, {w * 0.965f, 0, 30 * dp, 2}, {w * 0.085f, 0, 20 * dp, 4}};
        for (float[] k : pumpkins) { k[1] = groundY(k[0], w) + 2 * dp; pumpkinBody(c, k[0], k[1], k[2]); }
        eyes = new float[][]{{w * 0.03f, h * 0.62f, 0}, {w * 0.975f, h * 0.58f, 3}};
        // Spinnennetz oben rechts (hinter der Uhr, sehr fein)
        web(c, w, 120 * dp);
    }

    float[] hill(Canvas c, int w, int h, float base, float amp, int color, int seed) {
        Random r = new Random(seed);
        int steps = 24;
        float[] ys = new float[steps + 1];
        Path path = new Path();
        path.moveTo(0, h);
        for (int i = 0; i <= steps; i++) ys[i] = h * base - amp * h * (0.5f + 0.5f * (float) Math.sin(i * 0.7f + seed)) * (0.6f + 0.4f * r.nextFloat());
        path.lineTo(0, ys[0]);
        for (int i = 1; i <= steps; i++) {
            float x0 = w * (i - 1) / (float) steps, x1 = w * i / (float) steps;
            path.quadTo(x0, ys[i - 1], (x0 + x1) / 2, (ys[i - 1] + ys[i]) / 2);
        }
        path.lineTo(w, h); path.close();
        p.setShader(null); p.setColor(color);
        c.drawPath(path, p);
        return ys;
    }

    void tree(Canvas c, float x, float y, float len, float lean, int seed) {
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setColor(0xFF040205); b.setStyle(Paint.Style.STROKE); b.setStrokeCap(Paint.Cap.ROUND);
        branch(c, b, x, y, (float) (-Math.PI / 2 + lean), len, 10 * Style.dp * len / (120 * Style.dp), 8, new Random(seed));
    }
    void branch(Canvas c, Paint b, float x, float y, float ang, float len, float width, int depth, Random r) {
        if (depth == 0 || len < 3 * Style.dp) return;
        float x2 = x + (float) Math.cos(ang) * len, y2 = y + (float) Math.sin(ang) * len;
        float ca = ang + (r.nextFloat() - 0.5f);
        float cx = x + (float) Math.cos(ca) * len * 0.5f, cy = y + (float) Math.sin(ca) * len * 0.5f;
        b.setStrokeWidth(width);
        Path pa = new Path(); pa.moveTo(x, y); pa.quadTo(cx, cy, x2, y2);
        c.drawPath(pa, b);
        int n = depth > 2 ? 2 : 1 + r.nextInt(3);
        for (int k = 0; k < n; k++) branch(c, b, x2, y2, ang + (r.nextFloat() * 1.5f - 0.75f), len * (0.62f + r.nextFloat() * 0.18f), width * 0.66f, depth - 1, r);
    }

    void pumpkinBody(Canvas c, float x, float gy, float size) {
        float w = size * 1.25f, h = size, cy = gy - h * 0.5f;
        float[][] ribs = {{-0.32f, 0.55f}, {0.32f, 0.55f}, {-0.14f, 0.55f}, {0.14f, 0.55f}, {0, 0.5f}};
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE); st.setStrokeWidth(1.2f * Style.dp); st.setColor(a(0x6E2A04, 140));
        for (float[] rb : ribs) {
            RectF o = new RectF(x + rb[0] * w - rb[1] * w / 2, cy - h / 2, x + rb[0] * w + rb[1] * w / 2, cy + h / 2);
            p.setShader(new RadialGradient(x - w * 0.15f, cy - h * 0.3f, w * 0.9f, new int[]{0xFFFF9A2E, 0xFFE06A0A, 0xFF8A3304}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
            c.drawOval(o, p);
            c.drawOval(o, st);
        }
        p.setShader(null); p.setColor(0xFF3D3A14);
        Path s = new Path();
        s.moveTo(x - size * 0.06f, cy - h * 0.45f); s.quadTo(x - size * 0.02f, cy - h * 0.75f, x + size * 0.12f, cy - h * 0.78f); s.lineTo(x + size * 0.08f, cy - h * 0.45f); s.close();
        c.drawPath(s, p);
    }

    void pumpkinFace(Canvas c, float x, float gy, float size, float flick) {
        float w = size * 1.25f, h = size, cy = gy - h * 0.5f;
        p.setShader(new RadialGradient(x, cy, size * 2.4f, new int[]{a(0xFF8A1E, (int) (90 * flick)), a(0xFF6A00, 0)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, cy, size * 2.4f, p);
        p.setShader(new RadialGradient(x, cy, size * 0.7f, new int[]{0xFFFFF2A0, 0xFFFFB020, 0xFFFF7A00}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        p.setAlpha((int) (255 * (0.7f + 0.3f * flick)));
        Path f = new Path();
        tri(f, x - w * 0.24f, cy - h * 0.02f, x - w * 0.08f, cy - h * 0.02f, x - w * 0.16f, cy - h * 0.24f);
        tri(f, x + w * 0.08f, cy - h * 0.02f, x + w * 0.24f, cy - h * 0.02f, x + w * 0.16f, cy - h * 0.24f);
        tri(f, x - w * 0.04f, cy + h * 0.08f, x + w * 0.04f, cy + h * 0.08f, x, cy - h * 0.02f);
        f.moveTo(x - w * 0.3f, cy + h * 0.14f);
        for (int i = 1; i <= 6; i++) {
            float t = i / 6f, mxx = x - w * 0.3f + w * 0.6f * t;
            f.lineTo(mxx - w * 0.05f, cy + h * (i % 2 == 1 ? 0.24f : 0.16f));
            f.lineTo(mxx, cy + h * (i % 2 == 0 ? 0.14f : 0.22f));
        }
        f.lineTo(x + w * 0.3f, cy + h * 0.14f);
        f.quadTo(x, cy + h * 0.42f, x - w * 0.3f, cy + h * 0.14f);
        f.close();
        c.drawPath(f, p);
        p.setAlpha(255); p.setShader(null);
    }
    static void tri(Path f, float ax, float ay, float bx, float by, float cx, float cy) { f.moveTo(ax, ay); f.lineTo(bx, by); f.lineTo(cx, cy); f.close(); }

    void web(Canvas c, int w, float size) {
        Paint l = new Paint(Paint.ANTI_ALIAS_FLAG);
        l.setStyle(Paint.Style.STROKE); l.setStrokeWidth(0.8f * Style.dp); l.setColor(a(0xFFFFFF, 34));
        float ox = w, oy = 0;
        int spokes = 7;
        for (int i = 0; i <= spokes; i++) {
            double ang = Math.PI / 2 + Math.PI / 2 * i / spokes;
            c.drawLine(ox, oy, ox + (float) Math.cos(ang) * size, oy + (float) Math.sin(ang) * size, l);
        }
        for (int ring = 1; ring <= 5; ring++) {
            float rr = size * ring / 5.5f;
            Path pa = new Path();
            for (int i = 0; i <= spokes; i++) {
                double ang = Math.PI / 2 + Math.PI / 2 * i / spokes;
                float x = ox + (float) Math.cos(ang) * rr, y = oy + (float) Math.sin(ang) * rr;
                if (i == 0) pa.moveTo(x, y);
                else {   // leicht durchhaengende Faeden
                    double am = Math.PI / 2 + Math.PI / 2 * (i - 0.5) / spokes;
                    pa.quadTo(ox + (float) Math.cos(am) * rr * 0.9f, oy + (float) Math.sin(am) * rr * 0.9f, x, y);
                }
            }
            c.drawPath(pa, l);
        }
    }

    /** Pro Bild: Kulisse + Bewegtes. t = Sekunden seit Start. */
    void draw(Canvas c, int w, int h, float t) {
        if (layer == null || lw != w || lh != h) build(w, h);
        float dp = Style.dp;
        c.drawBitmap(layer, 0, 0, null);
        // Wolken ziehen vor dem Mond vorbei (weiche Ellipsen)
        float[][] clouds = {{0.30f, 2.8f, 0.30f, 200, 0.012f}, {0.65f, 2.4f, 0.24f, 170, 0.008f}, {-0.6f, 1.8f, 0.18f, 120, 0.015f}};
        for (int i = 0; i < clouds.length; i++) {
            float[] k = clouds[i];
            float span = w * 0.6f;
            float x = mx - span / 2 + ((t * k[4] * w + i * span / 3) % span);
            float y = my + k[0] * mr, rw = k[1] * mr, rh = k[2] * mr;
            c.save();
            c.scale(1, rh / rw, x, y);
            p.setShader(new RadialGradient(x, y, rw / 2, new int[]{a(0x0C0710, (int) k[3]), a(0x0C0710, 0)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rw / 2, p);
            c.restore();
        }
        p.setShader(null);
        // Kuerbisgesichter flackern
        for (float[] k : pumpkins) {
            float fl = 0.75f + 0.25f * (float) (Math.sin(t * 7.3 + k[3]) * 0.5 + Math.sin(t * 13.1 + k[3] * 2) * 0.5);
            pumpkinFace(c, k[0], k[1], k[2], fl);
        }
        // Augen im Dunkeln, blinzeln ab und zu
        for (float[] e : eyes) {
            float cyc = (t + e[2]) % 5.5f;
            if (cyc > 5.3f) continue;
            for (int d = -1; d <= 1; d += 2) {
                float x = e[0] + d * 4 * dp;
                p.setShader(new RadialGradient(x, e[1], 5 * dp, new int[]{0xFFFFF07A, a(0xFFB000, 120), a(0xFF8000, 0)}, null, Shader.TileMode.CLAMP));
                c.drawCircle(x, e[1], 5 * dp, p);
            }
        }
        p.setShader(null);
        // Spinne am Faden (links neben dem Mond)
        float sx = w * 0.43f, sy = h * 0.10f + (float) Math.sin(t * 0.6f) * 18 * dp + 26 * dp;
        p.setColor(a(0xFFFFFF, 50)); p.setStrokeWidth(0.8f * dp);
        c.drawLine(sx, 0, sx, sy, p);
        p.setColor(0xFF0A0610);
        c.drawCircle(sx, sy + 4 * dp, 4.5f * dp, p);
        c.drawCircle(sx, sy - 1 * dp, 2.6f * dp, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.1f * dp); p.setStrokeCap(Paint.Cap.ROUND);
        for (int d = -1; d <= 1; d += 2) for (int i = 0; i < 4; i++) {
            float ly = sy + (i - 1) * 2.5f * dp + 3 * dp, wig = (float) Math.sin(t * 3 + i) * 1.2f * dp;
            Path leg = new Path(); leg.moveTo(sx, ly); leg.quadTo(sx + d * 6 * dp, ly - 4 * dp + wig, sx + d * 9 * dp, ly + 3 * dp);
            c.drawPath(leg, p);
        }
        p.setStyle(Paint.Style.FILL);
        // ferner Blitz, selten und dezent
        long now = System.currentTimeMillis();
        if (nextFlash == 0) nextFlash = now + 20000 + rnd.nextInt(30000);
        if (now > nextFlash) { flashAt = now; nextFlash = now + 25000 + rnd.nextInt(35000); }
        long df = now - flashAt;
        if (df < 450) {
            float alpha = df < 90 ? 0.10f : df < 160 ? 0.02f : df < 260 ? 0.07f : 0.07f * (450 - df) / 190f;
            p.setColor(a(0xD8D0FF, (int) (255 * alpha)));
            c.drawRect(0, 0, w, h * 0.7f, p);
        }
        // Bodennebel
        float[][] fog = {{0.2f, 0.97f, 0.5f, 60}, {0.6f, 0.99f, 0.6f, 50}, {0.9f, 0.95f, 0.4f, 45}};
        for (int i = 0; i < fog.length; i++) {
            float[] f = fog[i];
            float cx = w * f[0] + (float) Math.sin(t * 0.04f + i * 1.9f) * w * 0.1f, cy = h * f[1], rx = w * f[2];
            c.save(); c.scale(1f, 0.22f, cx, cy);
            p.setShader(new RadialGradient(cx, cy, rx, new int[]{a(0x8A6A9A, (int) f[3]), a(0x8A6A9A, 0)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rx, p);
            c.restore();
        }
        p.setShader(null);
    }
}
