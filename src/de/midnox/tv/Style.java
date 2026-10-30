package de.midnox.tv;

import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

/** Gemeinsame Farben, Schrift und Helfer im midnox-Design. */
public final class Style {
    // Farben werden vom aktuellen Saison-Thema gesetzt (Theme.apply)
    public static int C1 = 0xFF7C3AED, C2 = 0xFF3B5BF0, C3 = 0xFF06B6D4;
    public static int BG1 = 0xFF0E0D1A, BG2 = 0xFF050508, GLOW1 = 0x337C3AED, GLOW2 = 0x2606B6D4;
    /** Optional: waagerechte Farbbaender von oben nach unten (z. B. Flagge) statt Eck-Schimmer. */
    public static int[] BANDS = null;
    public static final int CARD = 0xFF15141F, MUTED = 0xFF6B6F86;
    public static float dp = 2f;
    public static Typeface POP = Typeface.DEFAULT;

    public static void init(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        try { POP = Typeface.createFromAsset(c.getAssets(), "poppins_light.ttf"); }
        catch (Exception e) { POP = Typeface.create("sans-serif-light", Typeface.NORMAL); }
    }

    public static int px(double v) { return (int) Math.round(v * dp); }

    public static Shader brand(float x0, float x1) {
        return new LinearGradient(x0, 0, x1, 0, new int[]{C1, C2, C3}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(POP);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = null;
        try { i = pm.getLeanbackLaunchIntentForPackage(pkg); } catch (Throwable ignored) {}
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    public static void start(Context c, Intent i) {
        try { c.startActivity(i); } catch (Exception e) { Toast.makeText(c, "App konnte nicht gestartet werden", Toast.LENGTH_SHORT).show(); }
    }

    /** Konfiguration aus <externalFiles>/ha.json (Home Assistant + Emby). */
    public static JSONObject loadConfig(Context c) {
        try {
            File f = new File(c.getExternalFilesDir(null), "ha.json");
            if (!f.exists()) return null;
            return new JSONObject(read(new FileInputStream(f)));
        } catch (Exception e) { return null; }
    }

    /** Einstellungsdatei schreiben (dieselbe Datei, die das PC-Skript ablegt). */
    public static void saveConfig(Context c, JSONObject cfg) throws IOException {
        File f = new File(c.getExternalFilesDir(null), "ha.json");
        OutputStream o = new FileOutputStream(f);
        o.write(cfg.toString().getBytes("UTF-8"));
        o.close();
    }

    public static String read(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    public static String httpGet(String url, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(10000);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setRequestProperty("Accept", "application/json");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        return read(c.getInputStream());
    }

    public static String httpPost(String url, String bearer, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(10000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream o = c.getOutputStream();
        o.write(body.getBytes("UTF-8"));
        o.close();
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        return read(c.getInputStream());
    }

    public static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("midnox", Context.MODE_PRIVATE);
    }

    /** Dunkler Verlauf mit zwei Farbschimmern und Linie unten. */
    public static class Background extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            if (BANDS != null) {
                p.setShader(new LinearGradient(0, 0, 0, r.height(), BANDS, null, Shader.TileMode.CLAMP));
                c.drawRect(r, p);
                // leichte Abdunklung zur Mitte, damit Kacheln ruhig bleiben
                p.setShader(new RadialGradient(r.width() / 2f, r.height() / 2f, r.width() * 0.6f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
                c.drawRect(r, p);
                p.setShader(brand(r.width() * 0.3f, r.width() * 0.7f));
                p.setAlpha(140);
                c.drawRect(r.width() * 0.3f, r.height() - 3 * dp, r.width() * 0.7f, r.height() - 1 * dp, p);
                p.setAlpha(255);
                return;
            }
            p.setShader(new LinearGradient(0, 0, 0, r.height(), BG1, BG2, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(new RadialGradient(r.width() * 0.08f, r.height() * 0.05f, r.width() * 0.45f, GLOW1, GLOW1 & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(new RadialGradient(r.width() * 0.95f, r.height() * 1.0f, r.width() * 0.5f, GLOW2, GLOW2 & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            p.setShader(brand(r.width() * 0.3f, r.width() * 0.7f));
            p.setAlpha(140);
            c.drawRect(r.width() * 0.3f, r.height() - 3 * dp, r.width() * 0.7f, r.height() - 1 * dp, p);
            p.setAlpha(255);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** Text im midnox-Verlauf. */
    public static class GradientText extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String s;
        public GradientText(Context c, String s, float sizeDp) {
            super(c);
            this.s = s;
            p.setTextSize(sizeDp * dp);
            p.setTypeface(POP);
        }
        @Override protected void onMeasure(int w, int h) {
            Paint.FontMetrics fm = p.getFontMetrics();
            setMeasuredDimension((int) Math.ceil(p.measureText(s)) + 4 + getPaddingLeft() + getPaddingRight(), (int) Math.ceil(fm.descent - fm.ascent) + getPaddingTop() + getPaddingBottom());
        }
        @Override protected void onDraw(Canvas c) {
            p.setShader(brand(getPaddingLeft(), getWidth() - getPaddingRight()));
            c.drawText(s, getPaddingLeft(), getPaddingTop() - p.getFontMetrics().ascent, p);
        }
    }

    /** Misst den Inhalt ohne Breitenlimit und skaliert ihn bei Bedarf herunter. */
    public static class Fit extends FrameLayout {
        float scale = 1f;
        public Fit(Context c) { super(c); }
        @Override protected void onMeasure(int ws, int hs) {
            int maxW = MeasureSpec.getSize(ws);
            View ch = getChildAt(0);
            ch.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
            scale = (MeasureSpec.getMode(ws) != MeasureSpec.UNSPECIFIED && cw > maxW && maxW > 0) ? maxW / (float) cw : 1f;
            setMeasuredDimension(Math.round(cw * scale), Math.round(chh * scale));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            View ch = getChildAt(0);
            ch.layout(0, 0, ch.getMeasuredWidth(), ch.getMeasuredHeight());
            ch.setPivotX(0); ch.setPivotY(0);
            ch.setScaleX(scale); ch.setScaleY(scale);
        }
    }
}
