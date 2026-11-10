package de.midnox.party;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.util.*;

final class U {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("party", Context.MODE_PRIVATE); }

    static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[65536]; int r;
        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        return bo.toByteArray();
    }
    static byte[] asset(Context c, String path) throws IOException {
        InputStream in = c.getAssets().open(path);
        try { return readStream(in); } finally { in.close(); }
    }
    static String mime(String p) {
        String n = p.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
    static String ownIp() {
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress ad = a.nextElement();
                    if (ad instanceof Inet4Address && !ad.isLoopbackAddress()) return ad.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
    static String q(String path, String key) {
        int i = path.indexOf('?');
        if (i < 0) return null;
        for (String kv : path.substring(i + 1).split("&")) {
            int e = kv.indexOf('=');
            String k = e < 0 ? kv : kv.substring(0, e);
            if (k.equals(key)) {
                String v = e < 0 ? "" : kv.substring(e + 1);
                try { return URLDecoder.decode(v, "UTF-8"); } catch (Exception ex) { return v; }
            }
        }
        return null;
    }
}
