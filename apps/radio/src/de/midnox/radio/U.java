package de.midnox.radio;

import android.content.*;
import android.graphics.*;
import android.util.TypedValue;
import android.widget.TextView;
import java.io.*;
import java.net.*;

/** Farben, Masse und kleine Helfer im midnox-Design. */
final class U {
    static final int C1 = 0xFF7C3AED, C2 = 0xFF3B5BF0, C3 = 0xFF06B6D4, BG = 0xFF0B0A14, CARD = 0xFF15141F, MUTED = 0xFF8A8FA8, TEXT = 0xFFE6E8F2;
    static float dp = 2f;
    static Typeface POP = Typeface.DEFAULT;
    static void init(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        try { POP = Typeface.create("sans-serif-light", Typeface.NORMAL); } catch (Exception ignored) {}
    }
    static int px(double v) { return (int) Math.round(v * dp); }
    static Shader brand(float x0, float x1) { return new LinearGradient(x0, 0, x1, 0, new int[]{C1, C2, C3}, null, Shader.TileMode.CLAMP); }
    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color); t.setTypeface(POP);
        t.setIncludeFontPadding(false);
        return t;
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("radio", Context.MODE_PRIVATE); }
    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close(); return o.toString("UTF-8");
    }
    static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) >= 0) { if (c == '\n') break; if (c != '\r') sb.append((char) c); }
        return c < 0 && sb.length() == 0 ? null : sb.toString();
    }
    /** GET mit normalem HTTP-Stack, bei DNS-Sperre ueber Net (DoH). */
    static String get(String url) throws IOException {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", Net.UA);
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return read(c.getInputStream());
        } catch (UnknownHostException e) { return Net.get(url); }
    }
    static int color(String hex, int def) { try { return hex == null || hex.isEmpty() ? def : Color.parseColor(hex); } catch (Exception e) { return def; } }
    /** Stabile Farbe aus einem Namen (fuer Sender ohne Logo). */
    static int hashColor(String s) {
        float[] hsv = {Math.abs(s.hashCode() % 360), 0.62f, 0.78f};
        return Color.HSVToColor(hsv);
    }
}
