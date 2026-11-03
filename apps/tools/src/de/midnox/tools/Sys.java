package de.midnox.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.*;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.view.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Geraete-, Speicher-, Netzwerk- und App-Informationen. Was ADB braucht, ist als solches gekennzeichnet. */
final class Sys {
    // ------------------------------------------------------------ Geraet
    static final Map<String, String> MODELS = new HashMap<>();
    static {
        MODELS.put("AFTGAZL", "Fire TV Cube (3. Gen.)"); MODELS.put("AFTR", "Fire TV Cube (2. Gen.)"); MODELS.put("AFTA", "Fire TV Cube (1. Gen.)");
        MODELS.put("AFTKA", "Fire TV Stick 4K Max"); MODELS.put("AFTKM", "Fire TV Stick 4K Max (2. Gen.)"); MODELS.put("AFTKRT", "Fire TV Stick 4K (2. Gen.)");
        MODELS.put("AFTMM", "Fire TV Stick 4K"); MODELS.put("AFTSSS", "Fire TV Stick (3. Gen.)"); MODELS.put("AFTSS", "Fire TV Stick Lite");
        MODELS.put("AFTT", "Fire TV Stick (2. Gen.)"); MODELS.put("AFTN", "Fire TV (3. Gen.)"); MODELS.put("AFTS", "Fire TV (2. Gen.)");
    }
    static String model() { String m = Build.MODEL; return MODELS.containsKey(m) ? MODELS.get(m) + " · " + m : Build.MANUFACTURER + " " + m; }
    static boolean amazon() { return "Amazon".equalsIgnoreCase(Build.MANUFACTURER); }
    static String android() { return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"; }
    static String cpu() {
        String hw = "";
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/cpuinfo"))) { String l; while ((l = r.readLine()) != null) if (l.startsWith("Hardware")) { hw = l.replaceAll(".*:\\s*", ""); break; } } catch (Exception ignored) {}
        return (hw.isEmpty() ? Build.HARDWARE : hw) + " · " + Runtime.getRuntime().availableProcessors() + " Kerne";
    }
    static long[] ram(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return new long[]{mi.totalMem, mi.availMem};
    }
    static long[] storage() { StatFs s = new StatFs(Environment.getDataDirectory().getPath()); return new long[]{s.getTotalBytes(), s.getAvailableBytes()}; }
    static String display(Context c) {
        Display d = ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        StringBuilder sb = new StringBuilder();
        if (Build.VERSION.SDK_INT >= 23) { Display.Mode m = d.getMode(); sb.append(m.getPhysicalWidth()).append(" × ").append(m.getPhysicalHeight()).append(" · ").append(Math.round(m.getRefreshRate())).append(" Hz"); }
        else { android.graphics.Point p = new android.graphics.Point(); d.getRealSize(p); sb.append(p.x).append(" × ").append(p.y); }
        if (Build.VERSION.SDK_INT >= 24) {
            Display.HdrCapabilities h = d.getHdrCapabilities();
            if (h != null && h.getSupportedHdrTypes().length > 0) {
                List<String> l = new ArrayList<>();
                for (int t : h.getSupportedHdrTypes()) l.add(t == 1 ? "Dolby Vision" : t == 2 ? "HDR10" : t == 3 ? "HLG" : t == 4 ? "HDR10+" : "HDR");
                sb.append(" · ").append(android.text.TextUtils.join(", ", l));
            }
        }
        return sb.toString();
    }
    static String uptime() { return U.dur(SystemClock.elapsedRealtime()); }

    // ------------------------------------------------------------ Netzwerk
    static final class Net { String type = "–", ssid = "", ip = "–", gateway = "–", dns = "–", mac = ""; int rssi, signal = -1, linkMbps, freq; }
    static Net net(Context c) {
        Net n = new Net();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            n.type = ni == null || !ni.isConnected() ? "nicht verbunden" : ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "LAN-Kabel" : ni.getType() == ConnectivityManager.TYPE_WIFI ? "WLAN" : ni.getTypeName();
            if (Build.VERSION.SDK_INT >= 23) {
                Network an = cm.getActiveNetwork();
                LinkProperties lp = an == null ? null : cm.getLinkProperties(an);
                if (lp != null) {
                    for (LinkAddress la : lp.getLinkAddresses()) if (la.getAddress() instanceof Inet4Address) n.ip = la.getAddress().getHostAddress();
                    for (RouteInfo r : lp.getRoutes()) if (r.isDefaultRoute() && r.getGateway() instanceof Inet4Address) n.gateway = r.getGateway().getHostAddress();
                    List<String> d = new ArrayList<>(); for (InetAddress a : lp.getDnsServers()) d.add(a.getHostAddress());
                    if (!d.isEmpty()) n.dns = android.text.TextUtils.join(", ", d);
                }
            }
            if (ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null) {
                    n.rssi = wi.getRssi(); n.signal = WifiManager.calculateSignalLevel(n.rssi, 101); n.linkMbps = wi.getLinkSpeed();
                    if (Build.VERSION.SDK_INT >= 21) n.freq = wi.getFrequency();
                    String s = wi.getSSID(); if (s != null && !s.contains("unknown")) n.ssid = s.replace("\"", "");
                }
            }
        } catch (Exception ignored) {}
        return n;
    }
    /** WLAN-Name per ADB (ohne Standortberechtigung liefert Android ihn nicht). */
    static String ssidAdb(Context c) {
        try {
            String r = Adb.shell(c, "dumpsys wifi | grep -m1 'mWifiInfo'");
            Matcher m = Pattern.compile("SSID: \"?([^\",]+)\"?,").matcher(r);
            if (m.find() && !m.group(1).contains("unknown")) return m.group(1);
        } catch (Exception ignored) {}
        return "";
    }
    /** Oeffentliche IP, Anbieter und Ort. */
    static JSONObject publicInfo() {
        try { return new JSONObject(U.get("https://ipinfo.io/json")); } catch (Exception ignored) {}
        try {
            JSONObject o = new JSONObject();
            for (String l : U.get("https://www.cloudflare.com/cdn-cgi/trace").split("\n")) { String[] p = l.split("=", 2); if (p.length == 2) o.put(p[0], p[1]); }
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }
    /** Verbindungsaufbau-Zeit (TCP) in ms, -1 = nicht erreichbar. */
    static long tcpPing(String host, int port) {
        long best = -1;
        for (int i = 0; i < 3; i++) {
            Socket s = new Socket();
            long t0 = System.nanoTime();
            try { s.connect(new InetSocketAddress(host, port), 2000); long ms = (System.nanoTime() - t0) / 1000000; if (best < 0 || ms < best) best = ms; }
            catch (Exception ignored) {} finally { try { s.close(); } catch (Exception ignored) {} }
        }
        return best;
    }

    // ------------------------------------------------------------ Apps und Speicher (per ADB)
    static final class App { String pkg, name; long app = -1, data = -1, cache = -1; boolean system; long total() { return Math.max(0, app) + Math.max(0, data) + Math.max(0, cache); } }
    static List<App> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> map = new LinkedHashMap<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (pm.getLaunchIntentForPackage(ai.packageName) == null && pm.getLeanbackLaunchIntentForPackage(ai.packageName) == null) continue;
            App a = new App(); a.pkg = ai.packageName; a.name = String.valueOf(pm.getApplicationLabel(ai));
            a.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (!a.system) try { a.app = new File(ai.sourceDir).length(); } catch (Exception ignored) {}
            map.put(a.pkg, a);
        }
        try { diskstats(Adb.shell(c, "dumpsys diskstats"), map); } catch (Exception ignored) {}
        List<App> l = new ArrayList<>(map.values());
        Collections.sort(l, new Comparator<App>() { public int compare(App x, App y) { return Long.compare(y.total(), x.total()); } });
        return l;
    }
    /** „dumpsys diskstats“: Package Names / App Sizes / App Data Sizes / Cache Sizes als Listen. */
    static long[] totals = new long[3];   // Apps, Daten, Cache (alle Pakete, auch ohne Startsymbol)
    static void diskstats(String r, Map<String, App> map) {
        List<String> names = list(r, "Package Names"); List<String> as = list(r, "App Sizes"), ds = list(r, "App Data Sizes"), cs = list(r, "Cache Sizes");
        long ta = 0, td = 0, tc = 0;
        for (int i = 0; i < names.size(); i++) {
            String p = names.get(i).replace("\"", "");
            long a = i < as.size() ? lng(as.get(i)) : -1, d = i < ds.size() ? lng(ds.get(i)) : -1, cc = i < cs.size() ? lng(cs.get(i)) : -1;
            ta += Math.max(0, a); td += Math.max(0, d); tc += Math.max(0, cc);
            App x = map.get(p); if (x == null) continue;
            x.app = a; x.data = d; x.cache = cc;
        }
        totals = new long[]{ta, td, tc};
    }
    static List<String> list(String r, String key) {
        Matcher m = Pattern.compile(Pattern.quote(key) + ":\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(r);
        if (!m.find()) return new ArrayList<>();
        List<String> l = new ArrayList<>(); for (String s : m.group(1).split(",")) if (!s.trim().isEmpty()) l.add(s.trim());
        return l;
    }
    static long lng(String s) { try { return Long.parseLong(s.trim()); } catch (Exception e) { return -1; } }

    /** Alle App-Caches leeren lassen (Android raeumt Caches, bis so viel frei ist wie verlangt). Liefert freigegebene Bytes. */
    static long trimCaches(Context c) throws Exception {
        long before = storage()[1];
        Adb.shellLong(c, "pm trim-caches 999G", 60000);
        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
        return Math.max(0, storage()[1] - before);
    }
    /** Hintergrund-Apps beenden. Liefert freigewordenen Arbeitsspeicher. */
    static long freeRam(Context c) throws Exception {
        long before = ram(c)[1];
        Adb.shell(c, "am kill-all");
        try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
        return Math.max(0, ram(c)[1] - before);
    }
    static String fireOs(Context c) {
        try { String v = Adb.shell(c, "getprop ro.build.version.name").trim(); if (!v.isEmpty()) return v.replaceAll("\\s*\\(.*", ""); } catch (Exception ignored) {}
        return amazon() ? "Fire OS" : "";
    }
    /** Temperatur des Chips (°C) per ADB, -1 = unbekannt. */
    static float temperature(Context c) {
        try {
            String r = Adb.shell(c, "cat /sys/class/thermal/thermal_zone0/temp /sys/class/thermal/thermal_zone1/temp 2>/dev/null");
            float best = -1;
            for (String l : r.split("\\s+")) { float v = lng(l); if (v > 1000) v /= 1000f; if (v > 10 && v < 130 && v > best) best = v; }
            return best;
        } catch (Exception e) { return -1; }
    }
}
