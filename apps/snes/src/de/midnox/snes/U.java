package de.midnox.snes;

import android.content.*;
import java.io.*;
import java.net.*;
import java.util.*;

final class U {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("snes", Context.MODE_PRIVATE); }

    static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toByteArray();
    }
    static byte[] asset(Context c, String path) throws IOException {
        InputStream in = c.getAssets().open(path);
        try { return readStream(in); } finally { in.close(); }
    }
    static String mime(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
    /** Lokale WLAN-IP (für die Handy-Gamepad-Adresse). */
    static String ownIp() {
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                NetworkInterface ni = e.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
    static String q(String path, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]" + key + "=([^&]*)").matcher(path);
        try { return m.find() ? URLDecoder.decode(m.group(1), "UTF-8") : null; } catch (Exception e) { return m.find() ? m.group(1) : null; }
    }
}
