package de.midnox.live;

import android.content.*;
import android.graphics.*;
import android.util.TypedValue;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Farben, Masse und Netz-Helfer im midnox-Design. */
final class U {
    static final int C1 = 0xFF7C3AED, C2 = 0xFF3B5BF0, C3 = 0xFF06B6D4, BG = 0xFF0B0A14, CARD = 0xFF15141F, MUTED = 0xFF8A8FA8, TEXT = 0xFFE6E8F2, LIVE = 0xFFEF4444;
    static final String UA = "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36 midnoxLive";
    static float dp = 2f;
    static Typeface POP, BOLD;   // in init() gesetzt
    static void init(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        POP = Typeface.DEFAULT; BOLD = Typeface.DEFAULT_BOLD;
        try { POP = Typeface.create("sans-serif-light", Typeface.NORMAL); BOLD = Typeface.create("sans-serif", Typeface.BOLD); } catch (Exception ignored) {}
    }
    static int px(double v) { return (int) Math.round(v * dp); }
    static Shader brand(float x0, float x1) { return new LinearGradient(x0, 0, x1, 0, new int[]{C1, C2, C3}, null, Shader.TileMode.CLAMP); }
    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color); t.setTypeface(POP);
        t.setIncludeFontPadding(false);
        return t;
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("live", Context.MODE_PRIVATE); }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[32768]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close(); return o.toString("UTF-8");
    }
    static String readFile(File f) throws IOException { return read(new FileInputStream(f)); }
    static void writeFile(File f, String s) throws IOException {
        File t = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(t)) { o.write(s.getBytes("UTF-8")); }
        if (!t.renameTo(f)) { f.delete(); t.renameTo(f); }
    }

    /** HTTP-Verbindung mit Umleitungen (auch http<->https) und optionalen Kopfzeilen. */
    static HttpURLConnection open(String url, Map<String, String> headers, int timeout) throws IOException {
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(timeout);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Encoding", "gzip");
            if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            int code = c.getResponseCode();
            if (code / 100 == 3) { String loc = c.getHeaderField("Location"); c.disconnect(); if (loc == null) throw new IOException("Umleitung ohne Ziel"); url = new URL(new URL(url), loc).toString(); continue; }
            if (code != 200) { c.disconnect(); throw new IOException("HTTP " + code); }
            return c;
        }
        throw new IOException("zu viele Umleitungen");
    }
    /** Antwort als Strom, gzip automatisch (Kopfzeile oder .gz). */
    static InputStream stream(String url, Map<String, String> headers, int timeout) throws IOException {
        HttpURLConnection c = open(url, headers, timeout);
        InputStream in = new BufferedInputStream(c.getInputStream(), 65536);
        in.mark(4);
        int b1 = in.read(), b2 = in.read();
        in.reset();
        if (b1 == 0x1f && b2 == 0x8b) return new GZIPInputStream(in, 65536);
        return in;
    }
    static String get(String url) throws IOException { return read(stream(url, null, 20000)); }
    static String get(String url, Map<String, String> headers) throws IOException { return read(stream(url, headers, 20000)); }

    static int hashColor(String s) { float[] hsv = {Math.abs(s.hashCode() % 360), 0.55f, 0.62f}; return Color.HSVToColor(hsv); }
    static String norm(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\([^)]*\\)|\\[[^]]*\\]", " ").replaceAll("(?i)\\b\\d{3,4}p\\b", " ").replaceAll("^\\s*[A-Z]{2,3}\\s*[:|]\\s*", "");
        s = s.toLowerCase(Locale.GERMANY).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = s.replaceAll("\\b(fhd|uhd|hd|sd|4k|hevc|h265|de|ger|german|deutschland|\\|de\\|)\\b", " ");
        return s.replaceAll("[^a-z0-9+]", "");
    }

    static String ownIp() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
        } catch (Exception ignored) {}
        return "?";
    }
}
