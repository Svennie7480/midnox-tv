package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONObject;
import java.util.*;

/** Saison- und Feiertags-Themen im midnox-Verlaufsstil, automatisch nach Datum. */
public class Theme {
    // Formen
    static final int ORB = 1, FLAKE = 2, BAT = 3, HEART = 4, STAR = 5, PETAL = 6, CONFETTI = 7, DOT = 8;
    // Bewegungen
    static final int FALL = 1, RISE = 2, FLY = 3, TWINKLE = 4, DRIFT = 5;

    /** Eine Deko-Ebene: Form, Bewegung, Anzahl, Groesse (dp). */
    static class Layer {
        final int shape, motion, count; final float minSize, maxSize, speed;
        Layer(int shape, int motion, int count, float minSize, float maxSize, float speed) {
            this.shape = shape; this.motion = motion; this.count = count; this.minSize = minSize; this.maxSize = maxSize; this.speed = speed;
        }
    }

    final String id, greeting;
    final int c1, c2, c3, bg1, bg2, glow1, glow2;
    final boolean fireworks;
    final Layer[] layers;
    int[] bands;
    // Atmosphaere (grossflaechig, ruhig) hinter den Partikeln
    static final int AMB_NONE = 0, AMB_MOON_FOG = 1, AMB_BOKEH = 2, AMB_FROST = 3, AMB_GLOW = 4, AMB_HALLOWEEN = 5;
    int amb = AMB_GLOW;
    Theme amb(int a) { amb = a; return this; }

    Theme(String id, String greeting, int c1, int c2, int c3, int bg1, int bg2, int glow1, int glow2, boolean fireworks, Layer... layers) {
        this.id = id; this.greeting = greeting; this.c1 = c1; this.c2 = c2; this.c3 = c3;
        this.bg1 = bg1; this.bg2 = bg2; this.glow1 = glow1; this.glow2 = glow2;
        this.fireworks = fireworks; this.layers = layers;
    }

    static Layer L(int shape, int motion, int count, float min, float max, float speed) { return new Layer(shape, motion, count, min, max, speed); }

    static Theme byId(String id, Calendar now) {
        switch (id) {
            case "halloween":
                return new Theme(id, "Happy Halloween", 0xFFFF7A00, 0xFFB5179E, 0xFF7CFF4F, 0xFF120A12, 0xFF050307, 0x40FF7A00, 0x307CFF4F, false,
                        L(BAT, FLY, 3, 10, 20, 30)).amb(AMB_HALLOWEEN);
            case "einheit": {
                Theme th = new Theme(id, "Tag der Deutschen Einheit", 0xFF8A8A8A, 0xFFDD0000, 0xFFFFCE00, 0xFF0E0B0B, 0xFF050404, 0x38DD0000, 0x34FFCE00, false,
                        L(ORB, RISE, 12, 20, 60, 12), L(DOT, RISE, 22, 1.5f, 3.2f, 20));
                // Flagge von oben nach unten: Schwarz, Rot, Gold (gedaempft)
                th.bands = new int[]{0xFF050505, 0xFF0A0606, 0xFF4A0808, 0xFF560B07, 0xFF4A3404, 0xFF5A4206};
                return th;
            }
            case "advent": {
                Calendar xmas = (Calendar) now.clone();
                xmas.set(Calendar.MONTH, Calendar.DECEMBER); xmas.set(Calendar.DAY_OF_MONTH, 24);
                int days = xmas.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR);
                String g = now.get(Calendar.DAY_OF_MONTH) == 6 ? "Frohen Nikolaus" : "Noch " + days + (days == 1 ? " Tag" : " Tage") + " bis Heiligabend";
                return new Theme(id, g, 0xFFE63946, 0xFFF4C27A, 0xFF2A9D8F, 0xFF0F0A0C, 0xFF040506, 0x38E63946, 0x302A9D8F, false,
                        L(DOT, FALL, 70, 1, 3.4f, 26), L(ORB, DRIFT, 9, 26, 70, 5), L(STAR, TWINKLE, 5, 5, 10, 0)).amb(AMB_BOKEH);
            }
            case "weihnachten":
                return new Theme(id, "Frohe Weihnachten", 0xFFE63946, 0xFFF4C27A, 0xFF2A9D8F, 0xFF0F0A0C, 0xFF040506, 0x40E63946, 0x382A9D8F, false,
                        L(DOT, FALL, 90, 1, 3.6f, 26), L(ORB, DRIFT, 11, 26, 76, 5), L(STAR, TWINKLE, 7, 5, 11, 0)).amb(AMB_BOKEH);
            case "silvester":
                return new Theme(id, "Guten Rutsch", 0xFFFFD166, 0xFFE0E0F0, 0xFF4CC9F0, 0xFF080A18, 0xFF030308, 0x30FFD166, 0x304CC9F0, true,
                        L(STAR, TWINKLE, 14, 3, 8, 0)).amb(AMB_NONE);
            case "neujahr":
                return new Theme(id, "Frohes neues Jahr " + now.get(Calendar.YEAR), 0xFFFFD166, 0xFFE0E0F0, 0xFF4CC9F0, 0xFF080A18, 0xFF030308, 0x30FFD166, 0x304CC9F0, true,
                        L(STAR, TWINKLE, 14, 3, 8, 0)).amb(AMB_NONE);
            case "winter":
                return new Theme(id, "", 0xFF7DD3FC, 0xFF60A5FA, 0xFFE0F2FE, 0xFF0A1020, 0xFF04060C, 0x2660A5FA, 0x267DD3FC, false,
                        L(DOT, FALL, 70, 0.8f, 3, 22), L(FLAKE, FALL, 3, 8, 14, 14)).amb(AMB_FROST);
            case "valentin":
                return new Theme(id, "Happy Valentine's Day", 0xFFFF4D8D, 0xFFFF8FAB, 0xFFC77DFF, 0xFF16080F, 0xFF060306, 0x40FF4D8D, 0x30C77DFF, false,
                        L(HEART, RISE, 6, 8, 18, 12), L(ORB, DRIFT, 10, 26, 80, 5)).amb(AMB_BOKEH);
            case "muttertag":
                return new Theme(id, "Alles Liebe zum Muttertag", 0xFFFF8FAB, 0xFFFFB4A2, 0xFFC77DFF, 0xFF140A10, 0xFF060306, 0x38FF8FAB, 0x30C77DFF, false,
                        L(HEART, RISE, 5, 8, 16, 10), L(PETAL, FALL, 10, 7, 13, 12), L(ORB, DRIFT, 6, 26, 70, 5)).amb(AMB_BOKEH);
            case "fasching":
                return new Theme(id, "Helau & Alaaf", 0xFFFF4D6D, 0xFFFFD60A, 0xFF4CC9F0, 0xFF0C0A14, 0xFF040308, 0x34FF4D6D, 0x344CC9F0, false,
                        L(CONFETTI, FALL, 34, 3, 7, 32));
            case "ostern":
                return new Theme(id, "Frohe Ostern", 0xFFA7F3D0, 0xFFFDE68A, 0xFFF9A8D4, 0xFF0C1210, 0xFF050706, 0x30A7F3D0, 0x30F9A8D4, false,
                        L(PETAL, FALL, 14, 7, 14, 12), L(ORB, DRIFT, 8, 26, 76, 5)).amb(AMB_BOKEH);
            case "maifeiertag":
                return new Theme(id, "Schönen 1. Mai", 0xFF84CC16, 0xFF22C55E, 0xFFFDE047, 0xFF0A100A, 0xFF040604, 0x3084CC16, 0x30FDE047, false,
                        L(PETAL, FALL, 14, 7, 13, 12), L(ORB, DRIFT, 6, 26, 70, 5)).amb(AMB_BOKEH);
            case "vatertag":
                return new Theme(id, "Schönen Vatertag", 0xFF38BDF8, 0xFF6366F1, 0xFFFBBF24, 0xFF080C16, 0xFF030408, 0x3038BDF8, 0x30FBBF24, false,
                        L(ORB, RISE, 14, 18, 60, 12));
            case "pfingsten":
                return new Theme(id, "Frohe Pfingsten", 0xFFFB923C, 0xFFF43F5E, 0xFFFDE047, 0xFF120C0A, 0xFF050404, 0x30FB923C, 0x30FDE047, false,
                        L(ORB, RISE, 14, 18, 60, 12), L(DOT, RISE, 20, 1.5f, 3, 20));
            default:
                return new Theme("midnox", "", 0xFF7C3AED, 0xFF3B5BF0, 0xFF06B6D4, 0xFF0E0D1A, 0xFF050508, 0x337C3AED, 0x2606B6D4, false);
        }
    }

    /** Welches Thema gilt heute? Zum Testen: in ha.json "theme": "halloween" usw. */
    static Theme current(Context c) {
        Calendar now = Calendar.getInstance();
        String force = Style.prefs(c).getString("theme", "auto");      // Einstellungsmenue hat Vorrang
        if (force.equals("auto")) {
            JSONObject cfg = Style.loadConfig(c);
            force = cfg == null ? "" : cfg.optString("theme", "");
        }
        if (!force.isEmpty() && !force.equals("auto")) return byId(force, now);
        return byId(idFor(now), now);
    }

    static final String[] IDS = {"auto", "midnox", "halloween", "einheit", "advent", "weihnachten", "silvester", "neujahr", "winter", "fasching", "valentin", "ostern", "maifeiertag", "vatertag", "muttertag", "pfingsten"};
    static final String[] NAMES = {"Automatisch", "midnox", "Halloween", "Tag der Deutschen Einheit", "Advent", "Weihnachten", "Silvester", "Neujahr", "Winter", "Fasching", "Valentinstag", "Ostern", "1. Mai", "Vatertag", "Muttertag", "Pfingsten"};

    static String idFor(Calendar now) {
        int m = now.get(Calendar.MONTH) + 1, d = now.get(Calendar.DAY_OF_MONTH), md = m * 100 + d;
        int doy = now.get(Calendar.DAY_OF_YEAR), e = easter(now.get(Calendar.YEAR)).get(Calendar.DAY_OF_YEAR);
        // feste Feiertage
        if (md == 1003) return "einheit";
        if (md >= 1015 && md <= 1101) return "halloween";
        if (md >= 1206 && md <= 1223) return "advent";
        if (md >= 1224 && md <= 1226) return "weihnachten";
        if (md >= 1227) return "silvester";
        if (md <= 103) return "neujahr";
        if (md >= 213 && md <= 214) return "valentin";
        if (md == 501) return "maifeiertag";
        // bewegliche Feiertage (abhaengig von Ostern)
        if (doy >= e - 52 && doy <= e - 47) return "fasching";      // Weiberfastnacht bis Faschingsdienstag
        if (doy >= e - 2 && doy <= e + 1) return "ostern";          // Karfreitag bis Ostermontag
        if (doy == e + 39) return "vatertag";                       // Christi Himmelfahrt
        if (doy >= e + 49 && doy <= e + 50) return "pfingsten";     // Pfingstsonntag + -montag
        if (m == 5 && now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && d >= 8 && d <= 14) return "muttertag";
        if (md >= 104 && md <= 228) return "winter";
        return "midnox";
    }

    /** Ostersonntag (Gauss/Anonymous-Gregorian). */
    static Calendar easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        Calendar cal = Calendar.getInstance(); cal.set(y, month - 1, day);
        return cal;
    }

    void apply() {
        Style.C1 = c1; Style.C2 = c2; Style.C3 = c3;
        Style.BG1 = bg1; Style.BG2 = bg2; Style.GLOW1 = glow1; Style.GLOW2 = glow2;
        Style.BANDS = bands;
    }

    // =====================================================================
    /** Animierte Deko hinter den Kacheln: Vektorformen im Themen-Verlauf. */
    static class Overlay extends View {
        static class P { int shape, motion, col; float x, y, vx, vy, size, phase, rot, vrot, alpha, z; }
        static class Spark { float x, y, vx, vy, life; int col; }

        final Theme t;
        final Random rnd = new Random();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<P> parts = new ArrayList<>();
        final List<Spark> sparks = new ArrayList<>();
        final int[] counts;
        final Path bat = new Path(), batBody = new Path(), heart = new Path(), star = new Path();
        boolean running, seeded;
        long last;
        final long born = System.nanoTime();
        final float level;   // 0 = aus, 0.5 = dezent, 1 = voll

        Overlay(Context c, Theme t) {
            super(c);
            this.t = t;
            setFocusable(false); setClickable(false);
            counts = new int[t.layers.length];
            level = Style.prefs(c).getInt("fx_level", 2) / 2f;
            // Fledermaus (Einheitsgroesse ~ 1 breit), Fluegel werden beim Zeichnen bewegt
            bat.moveTo(0f, 0f);
            bat.cubicTo(-0.12f, -0.18f, -0.3f, -0.22f, -0.5f, -0.1f);
            bat.cubicTo(-0.42f, -0.02f, -0.38f, 0.04f, -0.36f, 0.1f);
            bat.cubicTo(-0.3f, 0.02f, -0.24f, 0.02f, -0.2f, 0.08f);
            bat.cubicTo(-0.15f, 0.02f, -0.08f, 0.02f, -0.05f, 0.08f);
            bat.lineTo(0f, 0.04f); bat.lineTo(0.05f, 0.08f);
            bat.cubicTo(0.08f, 0.02f, 0.15f, 0.02f, 0.2f, 0.08f);
            bat.cubicTo(0.24f, 0.02f, 0.3f, 0.02f, 0.36f, 0.1f);
            bat.cubicTo(0.38f, 0.04f, 0.42f, -0.02f, 0.5f, -0.1f);
            bat.cubicTo(0.3f, -0.22f, 0.12f, -0.18f, 0f, 0f);
            bat.close();
            // Koerper mit Kopf und Ohren
            batBody.addOval(new RectF(-0.07f, -0.1f, 0.07f, 0.14f), Path.Direction.CW);
            batBody.moveTo(-0.065f, -0.06f); batBody.lineTo(-0.06f, -0.2f); batBody.lineTo(-0.015f, -0.1f); batBody.close();
            batBody.moveTo(0.065f, -0.06f); batBody.lineTo(0.06f, -0.2f); batBody.lineTo(0.015f, -0.1f); batBody.close();
            heart.moveTo(0f, 0.35f);
            heart.cubicTo(-0.55f, 0f, -0.45f, -0.45f, 0f, -0.2f);
            heart.cubicTo(0.45f, -0.45f, 0.55f, 0f, 0f, 0.35f);
            heart.close();
            star.moveTo(0f, -0.5f);
            star.quadTo(0.06f, -0.06f, 0.5f, 0f);
            star.quadTo(0.06f, 0.06f, 0f, 0.5f);
            star.quadTo(-0.06f, 0.06f, -0.5f, 0f);
            star.quadTo(-0.06f, -0.06f, 0f, -0.5f);
            star.close();
        }
        void start() { running = true; last = 0; invalidate(); }
        void stop() { running = false; }

        int pal() { int r = rnd.nextInt(3); return r == 0 ? t.c1 : r == 1 ? t.c2 : t.c3; }

        P spawn(Layer l, boolean anywhere) {
            float w = getWidth(), h = getHeight(), dp = Style.dp;
            P q = new P();
            q.shape = l.shape; q.motion = l.motion; q.col = pal();
            // Tiefe: die meisten Teilchen weit hinten (klein, blass, langsam), wenige vorne
            q.z = (float) Math.pow(rnd.nextFloat(), 1.6);
            q.size = (l.minSize + q.z * (l.maxSize - l.minSize)) * dp;
            q.phase = rnd.nextFloat() * 6.28f;
            q.rot = rnd.nextFloat() * 360; q.vrot = (rnd.nextFloat() - 0.5f) * 120;
            float sp = l.speed * (0.35f + 0.65f * q.z) * (0.85f + rnd.nextFloat() * 0.3f) * dp;
            q.alpha = 0.18f + 0.5f * q.z;
            switch (l.motion) {
                case FLY:  q.x = anywhere ? rnd.nextFloat() * w : -q.size * 2; q.y = h * (0.05f + rnd.nextFloat() * 0.28f); q.vx = sp; break;
                case FALL: q.x = rnd.nextFloat() * w; q.y = anywhere ? rnd.nextFloat() * h : -q.size * 2; q.vy = sp; break;
                case RISE: q.x = rnd.nextFloat() * w; q.y = anywhere ? rnd.nextFloat() * h : h + q.size * 2; q.vy = -sp; break;
                case TWINKLE: q.x = rnd.nextFloat() * w; q.y = rnd.nextFloat() * h * 0.6f; break;
                default: q.x = rnd.nextFloat() * w; q.y = rnd.nextFloat() * h; q.vx = (rnd.nextFloat() - 0.5f) * sp * 2; q.vy = (rnd.nextFloat() - 0.5f) * sp * 2; q.alpha *= 0.5f;
            }
            return q;
        }

        @Override protected void onDraw(Canvas c) {
            if (level <= 0) return;
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            float w = getWidth(), h = getHeight(), dp = Style.dp;
            if (w <= 0) { if (running) postInvalidateOnAnimation(); return; }
            ambient(c, w, h, (now - born) / 1e9f);
            boolean init = !seeded;
            for (int i = 0; i < t.layers.length; i++) {
                while (counts[i] < Math.round(t.layers[i].count * level)) { P q = spawn(t.layers[i], init); q.phase += i * 100; parts.add(q); counts[i]++; }
            }
            seeded = true;
            Iterator<P> it = parts.iterator();
            while (it.hasNext()) {
                P q = it.next();
                q.phase += dt; q.rot += q.vrot * dt;
                float x = q.x, y = q.y;
                switch (q.motion) {
                    case FLY:  q.x += q.vx * dt; x = q.x; y = q.y + (float) Math.sin(q.phase * 2.2f) * 12 * dp; break;
                    case FALL: q.y += q.vy * dt; x = q.x + (float) Math.sin(q.phase * 1.2f) * 16 * dp; y = q.y; break;
                    case RISE: q.y += q.vy * dt; x = q.x + (float) Math.sin(q.phase * 0.9f) * 14 * dp; y = q.y; break;
                    case DRIFT: q.x += q.vx * dt; q.y += q.vy * dt; x = q.x; y = q.y; break;
                    default: break;
                }
                boolean out = x < -q.size * 3 || x > w + q.size * 3 || y < -q.size * 3 || y > h + q.size * 3;
                if (out && q.motion != TWINKLE) {
                    it.remove();
                    for (int i = 0; i < t.layers.length; i++) if (t.layers[i].shape == q.shape && t.layers[i].motion == q.motion) { counts[i]--; break; }
                    continue;
                }
                float a = q.alpha;
                if (q.motion == TWINKLE) a = q.alpha * (0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(q.phase * 1.7f)));
                drawShape(c, q, x, y, t.amb == AMB_HALLOWEEN ? Math.min(1f, a + 0.3f) : a * calm(x, y, w, h));
            }
            if (t.fireworks) fireworks(c, dt, w, h);
            if (running) postInvalidateOnAnimation();
        }

        /** Ueber den Kachelreihen (Mitte/unten) deutlich zuruecknehmen, an Raendern und oben voll. */
        float calm(float x, float y, float w, float h) {
            float top = Math.max(0f, 1f - y / (h * 0.32f));
            float edge = Math.max(0f, 1f - Math.min(x, w - x) / (w * 0.08f));
            return 0.35f + 0.65f * Math.min(1f, Math.max(top, edge));
        }

        /** Grossflaechige, ruhige Lichtstimmung je Thema. */
        HalloweenScene halloween;
        void ambient(Canvas c, float w, float h, float tt) {
            float dp = Style.dp;
            p.setStyle(Paint.Style.FILL);
            switch (t.amb) {
                case AMB_HALLOWEEN:
                    if (halloween == null) halloween = new HalloweenScene();
                    halloween.draw(c, (int) w, (int) h, tt);
                    break;
                case AMB_MOON_FOG: {
                    // Mond: weicher Halo + Scheibe im Verlauf, sehr zurueckhaltend
                    float mx = w * 0.64f, my = h * 0.14f, r = 46 * dp;
                    p.setShader(new RadialGradient(mx, my, r * 4.5f, (t.c1 & 0x00FFFFFF) | 0x2E000000, t.c1 & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    c.drawCircle(mx, my, r * 4.5f, p);
                    p.setShader(new RadialGradient(mx - r * 0.3f, my - r * 0.3f, r * 1.3f, 0x55FFE7C2, (t.c1 & 0x00FFFFFF) | 0x33000000, Shader.TileMode.CLAMP));
                    c.drawCircle(mx, my, r, p);
                    // Bodennebel: langsam ziehende, flache Schwaden
                    for (int i = 0; i < 4; i++) {
                        float cx = w * (0.1f + 0.3f * i) + (float) Math.sin(tt * 0.04f + i * 1.9f) * w * 0.12f;
                        float cy = h * (1.02f - 0.05f * (i % 2));
                        float rx = w * 0.42f;
                        int col = (i % 2 == 0 ? t.c2 : t.c1) & 0x00FFFFFF;
                        c.save(); c.scale(1f, 0.22f, cx, cy);
                        p.setShader(new RadialGradient(cx, cy, rx, col | 0x30000000, col, Shader.TileMode.CLAMP));
                        c.drawCircle(cx, cy, rx, p);
                        c.restore();
                    }
                    break;
                }
                case AMB_FROST: {
                    // Raureif in den Ecken
                    float r = w * 0.35f;
                    float[][] pts = {{0, 0}, {w, 0}, {0, h}, {w, h}};
                    for (float[] q : pts) {
                        p.setShader(new RadialGradient(q[0], q[1], r, 0x22E0F2FE, 0x00E0F2FE, Shader.TileMode.CLAMP));
                        c.drawCircle(q[0], q[1], r, p);
                    }
                    break;
                }
                case AMB_BOKEH: {
                    // warmes Licht von oben, sehr weich
                    float cx = w * (0.5f + 0.2f * (float) Math.sin(tt * 0.02f));
                    p.setShader(new RadialGradient(cx, -h * 0.1f, w * 0.6f, (t.c2 & 0x00FFFFFF) | 0x1C000000, t.c2 & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h * 0.7f, p);
                    break;
                }
                default: break;
            }
            p.setShader(null);
        }

        void drawShape(Canvas c, P q, float x, float y, float a) {
            float s = q.size, dp = Style.dp;
            p.setStyle(Paint.Style.FILL); p.setStrokeWidth(0); p.setShader(null);
            switch (q.shape) {
                case ORB: { // weicher Lichtpunkt
                    int col = q.col;
                    if (t.amb == AMB_BOKEH) {   // Bokeh: flache Scheibe mit hellerem Rand
                        p.setColor(col); p.setAlpha((int) (a * 46));
                        c.drawCircle(x, y, s * 0.6f, p);
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f * dp); p.setAlpha((int) (a * 70));
                        c.drawCircle(x, y, s * 0.6f, p);
                        p.setStyle(Paint.Style.FILL);
                    } else {
                        p.setShader(new RadialGradient(x, y, s, (col & 0x00FFFFFF) | ((int) (a * 110) << 24), col & 0x00FFFFFF, Shader.TileMode.CLAMP));
                        c.drawCircle(x, y, s, p);
                    }
                    break;
                }
                case DOT:
                    p.setColor(Color.WHITE); p.setAlpha((int) (255 * a));
                    if (t.id.equals("einheit") || t.id.equals("pfingsten")) { p.setColor(q.col); p.setAlpha((int) (255 * a)); }
                    c.drawCircle(x, y, s, p);
                    if (q.z > 0.75f) { p.setAlpha((int) (40 * a)); c.drawCircle(x, y, s * 2.4f, p); }
                    break;
                case FLAKE: { // feine Schneeflocke aus Linien
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f * dp); p.setStrokeCap(Paint.Cap.ROUND);
                    p.setShader(Style.brand(x - s, x + s)); p.setAlpha((int) (255 * Math.min(1, a + 0.25f)));
                    c.save(); c.rotate(q.rot * 0.3f, x, y);
                    for (int i = 0; i < 6; i++) {
                        c.save(); c.rotate(i * 60, x, y);
                        c.drawLine(x, y, x, y - s / 2, p);
                        c.drawLine(x, y - s * 0.3f, x - s * 0.12f, y - s * 0.4f, p);
                        c.drawLine(x, y - s * 0.3f, x + s * 0.12f, y - s * 0.4f, p);
                        c.restore();
                    }
                    c.restore();
                    break;
                }
                case BAT: {
                    // abwechselnd ein paar Fluegelschlaege und Gleiten
                    boolean glide = Math.sin(q.phase * 0.7f) > 0.2f;
                    float flap = glide ? 0.85f + 0.05f * (float) Math.sin(q.phase * 2) : 0.5f + 0.5f * (float) Math.abs(Math.sin(q.phase * 7));
                    c.save(); c.translate(x, y); c.rotate((float) Math.sin(q.phase * 1.1f) * 6); c.scale(s * 2, s * 2 * flap);
                    // dunkle Silhouette mit leichtem Farbschimmer (wirkt vor dem Mond am besten)
                    p.setShader(new LinearGradient(0, -0.2f, 0, 0.12f, 0xFF0A060C, (t.c1 & 0x00FFFFFF) | 0xFF000000, Shader.TileMode.CLAMP));
                    p.setAlpha((int) (255 * Math.min(0.9f, a + 0.35f)));
                    c.drawPath(bat, p);
                    c.restore();
                    c.save(); c.translate(x, y); c.scale(s * 2, s * 2);   // Koerper flattert nicht mit
                    c.drawPath(batBody, p);
                    c.restore();
                    break;
                }
                case HEART: {
                    c.save(); c.translate(x, y); c.rotate((float) Math.sin(q.phase) * 12); c.scale(s * 1.6f, s * 1.6f);
                    p.setShader(Style.brand(-0.5f, 0.5f)); p.setAlpha((int) (255 * Math.min(1, a + 0.15f)));
                    c.drawPath(heart, p);
                    c.restore();
                    break;
                }
                case STAR: {
                    c.save(); c.translate(x, y); c.scale(s * 2, s * 2);
                    p.setShader(Style.brand(-0.5f, 0.5f)); p.setAlpha((int) (255 * a));
                    c.drawPath(star, p);
                    c.restore();
                    break;
                }
                case PETAL: {
                    c.save(); c.translate(x, y); c.rotate(q.rot);
                    p.setShader(new LinearGradient(-s, 0, s, 0, q.col, t.c2, Shader.TileMode.CLAMP)); p.setAlpha((int) (255 * Math.min(1, a + 0.15f)));
                    c.drawOval(new RectF(-s, -s * 0.45f, s, s * 0.45f), p);
                    c.restore();
                    break;
                }
                case CONFETTI: {
                    c.save(); c.translate(x, y); c.rotate(q.rot);
                    float sq = (float) Math.abs(Math.cos(q.phase * 3)); // Drehung um die eigene Achse
                    p.setColor(q.col); p.setAlpha((int) (255 * Math.min(1, a + 0.3f)));
                    c.drawRoundRect(new RectF(-s, -s * 0.45f * sq, s, s * 0.45f * sq), 1.5f * dp, 1.5f * dp, p);
                    c.restore();
                    break;
                }
            }
            p.setShader(null);
        }

        void fireworks(Canvas c, float dt, float w, float h) {
            float dp = Style.dp;
            if (rnd.nextFloat() < dt * 0.8f) {
                float x = w * (0.15f + rnd.nextFloat() * 0.7f), y = h * (0.12f + rnd.nextFloat() * 0.3f);
                int col = pal();
                int n = 40 + rnd.nextInt(20);
                for (int i = 0; i < n; i++) {
                    double ang = (Math.PI * 2 * i) / n + rnd.nextFloat() * 0.1;
                    float v = (70 + rnd.nextFloat() * 50) * dp;
                    Spark s = new Spark();
                    s.x = x; s.y = y; s.vx = (float) Math.cos(ang) * v; s.vy = (float) Math.sin(ang) * v; s.life = 1f; s.col = col;
                    sparks.add(s);
                }
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(1.6f * dp);
            Iterator<Spark> it = sparks.iterator();
            while (it.hasNext()) {
                Spark s = it.next();
                float px = s.x, py = s.y;
                s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 35 * dp * dt; s.vx *= 0.982f; s.vy *= 0.982f;
                s.life -= dt * 0.6f;
                if (s.life <= 0) { it.remove(); continue; }
                // Leuchtspur als kurzer Verlaufsstrich
                float tx = s.x - s.vx * 0.08f, ty = s.y - s.vy * 0.08f;
                p.setShader(new LinearGradient(tx, ty, s.x, s.y, s.col & 0x00FFFFFF, (s.col & 0x00FFFFFF) | ((int) (230 * s.life) << 24), Shader.TileMode.CLAMP));
                c.drawLine(tx, ty, s.x, s.y, p);
            }
            p.setShader(null); p.setStyle(Paint.Style.FILL);
        }
    }
}
