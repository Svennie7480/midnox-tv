package de.midnox.quiz;

import android.content.*;
import android.graphics.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Farben, Masse und Netz-Helfer im midnox-Design. */
final class U {
    static final int C1 = 0xFF7C3AED, C2 = 0xFF3B5BF0, C3 = 0xFF06B6D4, BG = 0xFF0B0A14, CARD = 0xFF15141F, CARD2 = 0xFF1E1C2C, MUTED = 0xFF8A8FA8, TEXT = 0xFFE6E8F2,
        OK = 0xFF22C55E, BAD = 0xFFEF4444, GOLD = 0xFFFACC15, LOCK = 0xFFF59E0B;
    /** Antwortfarben A–D (Party und Solo). */
    static final int[] ANS = {0xFFE11D48, 0xFF2563EB, 0xFFD97706, 0xFF16A34A};
    static final String[] SHAPE = {"▲", "◆", "●", "■"};
    static float dp = 2f;
    static Typeface POP = Typeface.DEFAULT, BOLD = Typeface.DEFAULT_BOLD;
    static void init(Context c) {
        dp = c.getResources().getDisplayMetrics().density;
        try { POP = Typeface.create("sans-serif-light", Typeface.NORMAL); BOLD = Typeface.create("sans-serif", Typeface.BOLD); } catch (Exception ignored) {}
    }
    static int px(double v) { return (int) Math.round(v * dp); }
    static Shader brand(float x0, float x1) { return new LinearGradient(x0, 0, x1, 0, new int[]{C1, C2, C3}, null, Shader.TileMode.CLAMP); }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("quiz", Context.MODE_PRIVATE); }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close(); return o.toString("UTF-8");
    }
    static String readFile(File f) throws IOException { return read(new FileInputStream(f)); }
    static void writeFile(File f, String s) throws IOException {
        File t = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(t)) { o.write(s.getBytes("UTF-8")); }
        if (!t.renameTo(f)) { f.delete(); t.renameTo(f); }
    }
    static String http(String url, String post) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "midnoxQuiz/1");
        if (post != null) {
            c.setDoOutput(true); c.setRequestMethod("POST"); c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(post.getBytes("UTF-8")); }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) throw new IOException("HTTP " + code);
        return read(in);
    }
    static String ownIp() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
        } catch (Exception ignored) {}
        return "?";
    }
    static String money(int v) { return String.format(Locale.GERMANY, "%,d €", v); }
    static String num(int v) { return String.format(Locale.GERMANY, "%,d", v); }
    static String dur(long sec) { return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60); }
    /** Text umbrechen und zeichnen; liefert die Hoehe. */
    static float multi(Canvas c, Paint t, String s, float x, float y, float w, int maxLines, boolean center) {
        List<String> lines = wrap(t, s, w);
        if (lines.size() > maxLines) { lines = new ArrayList<>(lines.subList(0, maxLines)); }
        float lh = t.getTextSize() * 1.28f;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            float lx = center ? x + (w - t.measureText(l)) / 2 : x;
            c.drawText(l, lx, y + i * lh, t);
        }
        return lines.size() * lh;
    }
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
    /** Schriftgroesse so lange verkleinern, bis der Text in maxLines Zeilen passt. */
    static void fit(Paint t, String s, float w, int maxLines, float start, float min) {
        float sz = start; t.setTextSize(sz);
        while (sz > min && wrap(t, s, w).size() > maxLines) { sz -= 1; t.setTextSize(sz); }
    }
}
