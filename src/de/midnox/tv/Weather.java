package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.*;
import java.util.*;

/** Wetter aus Home Assistant (weather.*) + gezeichnetes Wettersymbol im midnox-Verlauf. */
public class Weather {
    public static class Now { public String entity, cond = "", name = ""; public double temp = Double.NaN, hi = Double.NaN, lo = Double.NaN; public int rain = -1; }

    static final String[][] NAMES = {
        {"clear-night", "Klar"}, {"cloudy", "Bewölkt"}, {"fog", "Nebel"}, {"hail", "Hagel"},
        {"lightning", "Gewitter"}, {"lightning-rainy", "Gewitter"}, {"partlycloudy", "Teils bewölkt"},
        {"pouring", "Starkregen"}, {"rainy", "Regen"}, {"snowy", "Schnee"}, {"snowy-rainy", "Schneeregen"},
        {"sunny", "Sonnig"}, {"windy", "Windig"}, {"windy-variant", "Windig"}, {"exceptional", "Unwetter"},
    };
    public static String name(String c) { for (String[] n : NAMES) if (n[0].equals(c)) return n[1]; return c; }

    /** Alle Wetter-Entitäten: {entity_id, Anzeigename}. */
    public static List<String[]> entities(Context ctx) throws Exception {
        JSONObject cfg = Style.loadConfig(ctx);
        String base = cfg.getString("url").replaceAll("/+$", "");
        JSONArray a = new JSONArray(Style.httpGet(base + "/api/states", cfg.getString("token")));
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String id = o.optString("entity_id");
            if (id.startsWith("weather.")) out.add(new String[]{id, o.optJSONObject("attributes").optString("friendly_name", id)});
        }
        return out;
    }

    public static Now fetch(Context ctx) {
        try {
            JSONObject cfg = Style.loadConfig(ctx);
            if (cfg == null) return null;
            String base = cfg.getString("url").replaceAll("/+$", ""), tok = cfg.getString("token");
            String ent = Style.prefs(ctx).getString("weather_entity", "");
            if (ent.isEmpty()) { List<String[]> l = entities(ctx); if (l.isEmpty()) return null; ent = l.get(0)[0]; }
            JSONObject st = new JSONObject(Style.httpGet(base + "/api/states/" + ent, tok));
            JSONObject at = st.optJSONObject("attributes");
            Now n = new Now();
            n.entity = ent; n.cond = st.optString("state"); n.name = at.optString("friendly_name");
            n.temp = at.optDouble("temperature", Double.NaN);
            try {   // Tagesvorhersage (neuere HA-Versionen)
                JSONObject r = new JSONObject(Style.httpPost(base + "/api/services/weather/get_forecasts?return_response", tok,
                        new JSONObject().put("entity_id", ent).put("type", "daily").toString()));
                JSONArray f = r.getJSONObject("service_response").getJSONObject(ent).getJSONArray("forecast");
                if (f.length() > 0) {
                    JSONObject d = f.getJSONObject(0);
                    n.hi = d.optDouble("temperature", Double.NaN);
                    n.lo = d.optDouble("templow", Double.NaN);
                    n.rain = d.has("precipitation_probability") ? d.optInt("precipitation_probability") : -1;
                }
            } catch (Exception ignored) {}
            return n;
        } catch (Exception e) { return null; }
    }

    static String deg(double v) { return Math.round(v) + "°"; }

    /** "Bewölkt  ·  11°   14° / 7°" */
    public static String line(Now n) {
        StringBuilder sb = new StringBuilder(name(n.cond));
        if (!Double.isNaN(n.temp)) sb.append("  ·  ").append(deg(n.temp));
        if (!Double.isNaN(n.hi) && !Double.isNaN(n.lo)) sb.append("   ").append(deg(n.hi)).append(" / ").append(deg(n.lo));
        if (n.rain >= 30) sb.append("   Regen ").append(n.rain).append(" %");
        return sb.toString();
    }

    /** Wettersymbol als Linienzeichnung im Verlauf. */
    public static class Icon extends View {
        String cond = "";
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public Icon(Context c) { super(c); }
        public void set(String c) { cond = c == null ? "" : c; invalidate(); }
        @Override protected void onDraw(Canvas c) { draw(c, cond, 0, 0, Math.min(getWidth(), getHeight()), p); }

        public static void draw(Canvas c, String cond, float x, float y, float s, Paint p) {
            p.setShader(Style.brand(x, x + s));
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Math.max(1.5f, s * 0.08f));
            boolean precip = cond.contains("rain") || cond.equals("pouring") || cond.contains("snow") || cond.equals("hail") || cond.startsWith("lightning");
            switch (cond) {
                case "sunny": sun(c, x + s / 2, y + s / 2, s * 0.2f, s * 0.36f, p); break;
                case "clear-night": moon(c, x + s / 2, y + s / 2, s * 0.3f, p); break;
                case "partlycloudy":
                    sun(c, x + s * 0.36f, y + s * 0.34f, s * 0.13f, s * 0.24f, p);
                    cloud(c, x + s * 0.18f, y + s * 0.38f, s * 0.8f, p); break;
                case "fog":
                    for (int i = 0; i < 3; i++) c.drawLine(x + s * (0.15f + 0.05f * i), y + s * (0.32f + 0.18f * i), x + s * (0.85f - 0.05f * i), y + s * (0.32f + 0.18f * i), p);
                    break;
                case "windy": case "windy-variant":
                    for (int i = 0; i < 3; i++) {
                        Path w = new Path(); float yy = y + s * (0.3f + 0.2f * i);
                        w.moveTo(x + s * 0.12f, yy); w.cubicTo(x + s * 0.4f, yy - s * 0.1f, x + s * 0.6f, yy + s * 0.1f, x + s * (0.88f - 0.12f * i), yy);
                        c.drawPath(w, p);
                    }
                    break;
                default:
                    cloud(c, x + s * 0.1f, y + (precip ? s * 0.08f : s * 0.2f), s * 0.8f, p);
                    float top = y + s * 0.68f;
                    if (cond.startsWith("lightning")) {
                        Path b = new Path();
                        b.moveTo(x + s * 0.55f, top - s * 0.04f); b.lineTo(x + s * 0.44f, top + s * 0.14f); b.lineTo(x + s * 0.56f, top + s * 0.14f); b.lineTo(x + s * 0.45f, top + s * 0.3f);
                        c.drawPath(b, p);
                    } else if (cond.contains("snow") || cond.equals("hail")) {
                        p.setStyle(Paint.Style.FILL);
                        for (int i = 0; i < 3; i++) c.drawCircle(x + s * (0.32f + 0.18f * i), top + s * (i % 2 == 0 ? 0.08f : 0.2f), s * 0.05f, p);
                        p.setStyle(Paint.Style.STROKE);
                    } else if (precip) {
                        int n = cond.equals("pouring") ? 4 : 3;
                        for (int i = 0; i < n; i++) { float xx = x + s * (0.3f + 0.4f / (n - 1) * i); c.drawLine(xx, top, xx - s * 0.08f, top + s * 0.22f, p); }
                    }
            }
            p.setShader(null);
        }
        static void sun(Canvas c, float cx, float cy, float r, float ray, Paint p) {
            c.drawCircle(cx, cy, r, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                c.drawLine(cx + (float) Math.cos(a) * (r + ray * 0.28f), cy + (float) Math.sin(a) * (r + ray * 0.28f),
                           cx + (float) Math.cos(a) * ray, cy + (float) Math.sin(a) * ray, p);
            }
        }
        static void moon(Canvas c, float cx, float cy, float r, Paint p) {
            Path a = new Path(); a.addCircle(cx, cy, r, Path.Direction.CW);
            Path b = new Path(); b.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
            a.op(b, Path.Op.DIFFERENCE);
            c.drawPath(a, p);
        }
        static void cloud(Canvas c, float x, float y, float w, Paint p) {
            float h = w * 0.55f;
            Path cl = new Path();
            cl.addRoundRect(new RectF(x, y + h * 0.45f, x + w, y + h), h * 0.28f, h * 0.28f, Path.Direction.CW);
            Path b1 = new Path(); b1.addCircle(x + w * 0.36f, y + h * 0.48f, h * 0.32f, Path.Direction.CW);
            Path b2 = new Path(); b2.addCircle(x + w * 0.62f, y + h * 0.42f, h * 0.4f, Path.Direction.CW);
            cl.op(b1, Path.Op.UNION); cl.op(b2, Path.Op.UNION);
            c.drawPath(cl, p);
        }
    }
}
