package de.midnox.emby;

import android.content.*;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.util.*;

/** Kleine Helfer. */
final class U {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("emby", Context.MODE_PRIVATE); }
    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[32768]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close(); return o.toString("UTF-8");
    }
    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "midnoxEmby");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        return read(c.getInputStream());
    }
    static String deviceId(Context c) {
        SharedPreferences p = prefs(c); String id = p.getString("deviceId", null);
        if (id == null) { id = "midnox-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); p.edit().putString("deviceId", id).apply(); }
        return id;
    }
    static String deviceName() {
        String m = Build.MODEL == null ? "" : Build.MODEL;
        if (m.startsWith("AFT")) return "Fire TV";
        return m.isEmpty() ? "Android TV" : m;
    }
    static String versionName(Context c) { try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "1.0"; } }
    static String ownIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
