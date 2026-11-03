package de.midnox.tools;

import android.content.*;
import android.graphics.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Farben, Masse und Helfer im midnox-Design. */
final class U {
    static final int C1 = 0xFF7C3AED, C2 = 0xFF3B5BF0, C3 = 0xFF06B6D4, BG = 0xFF0B0A14, CARD = 0xFF15141F, CARD2 = 0xFF1E1C2C, MUTED = 0xFF8A8FA8,
        OK = 0xFF22C55E, BAD = 0xFFEF4444, WARN = 0xFFF59E0B;
    static float dp = 2f;
    static Typeface POP = Typeface.DEFAULT, BOLD = Typeface.DEFAULT_BOLD;
    static void init(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        try { POP = Typeface.create("sans-serif-light", Typeface.NORMAL); BOLD = Typeface.create("sans-serif", Typeface.BOLD); } catch (Exception ignored) {}
    }
    static Shader brand(float x0, float x1) { return new LinearGradient(x0, 0, x1, 0, new int[]{C1, C2, C3}, null, Shader.TileMode.CLAMP); }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tools", Context.MODE_PRIVATE); }
    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close(); return o.toString("UTF-8");
    }
    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(8000); c.setRequestProperty("User-Agent", "midnoxTools/1");
        return read(c.getInputStream());
    }
    static String bytes(long b) {
        if (b < 0) return "–";
        double v = b; String[] u = {"B", "KB", "MB", "GB", "TB"}; int i = 0;
        while (v >= 1000 && i < u.length - 1) { v /= 1000; i++; }
        return String.format(Locale.GERMANY, i == 0 ? "%.0f %s" : v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }
    static String dur(long ms) {
        long m = ms / 60000, h = m / 60, d = h / 24;
        return d > 0 ? d + " T " + (h % 24) + " Std" : h > 0 ? h + " Std " + (m % 60) + " Min" : m + " Min";
    }
    static String num(double v, int dec) { return String.format(Locale.GERMANY, "%." + dec + "f", v); }
    static List<String> wrap(Paint t, String s, float w) {
        List<String> out = new ArrayList<>(); StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String tryS = cur.length() == 0 ? word : cur + " " + word;
            if (t.measureText(tryS) <= w || cur.length() == 0) { cur.setLength(0); cur.append(tryS); }
            else { out.add(cur.toString()); cur.setLength(0); cur.append(word); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
    static String ell(Paint t, String s, float w) { if (t.measureText(s) <= w) return s; while (s.length() > 1 && t.measureText(s + "…") > w) s = s.substring(0, s.length() - 1); return s + "…"; }
}
