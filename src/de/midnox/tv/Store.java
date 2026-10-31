package de.midnox.tv;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/**
 * midnox Store: Katalog (store.json im GitHub-Repo) + Aufloesen der neuesten APK
 * aus GitHub-Releases (passend zum Prozessor des Fire TV).
 */
public class Store {
    static class App {
        String id, name, pkg, desc, cat, github, match, url, version;
        // Laufzeit
        String latest, apk, error, installed, status = "";
        boolean resolving, busy;
        boolean isInstalled() { return installed != null; }
        boolean hasUpdate() { return installed != null && latest != null && newer(latest, installed); }
    }

    static String catalogUrl(Context c) { return Updater.url(c).replaceAll("update\\.json.*$", "store.json"); }

    static List<App> load(Context c) throws Exception {
        JSONObject j = new JSONObject(Style.httpGet(catalogUrl(c) + "?t=" + System.currentTimeMillis() / 600000, null));
        JSONArray a = j.getJSONArray("apps");
        List<App> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            App x = new App();
            x.id = o.optString("id"); x.name = o.optString("name"); x.pkg = o.optString("pkg");
            x.desc = o.optString("desc"); x.cat = o.optString("cat"); x.github = o.optString("github", null);
            x.match = o.optString("match", null); x.url = o.optString("url", null); x.version = o.optString("version", null);
            if (x.url != null) { x.apk = x.url.replace("{abi}", Build.SUPPORTED_ABIS[0]); x.latest = x.version; }
            out.add(x);
        }
        refreshInstalled(c, out);
        return out;
    }

    static void refreshInstalled(Context c, List<App> apps) {
        PackageManager pm = c.getPackageManager();
        for (App a : apps) {
            a.installed = null;
            for (String p : a.pkg.split(",")) {
                try { PackageInfo pi = pm.getPackageInfo(p.trim(), 0); a.installed = pi.versionName == null ? "?" : pi.versionName; a.pkg = p.trim(); break; }
                catch (Exception ignored) {}
            }
        }
    }

    /** Neueste passende APK aus den GitHub-Releases. */
    static void resolve(App a) {
        if (a.github == null || a.apk != null && a.latest != null) return;
        try {
            JSONArray rel = new JSONArray(Style.httpGet("https://api.github.com/repos/" + a.github + "/releases?per_page=15", null));
            Pattern m = a.match == null ? null : Pattern.compile(a.match, Pattern.CASE_INSENSITIVE);
            for (int i = 0; i < rel.length(); i++) {
                JSONObject r = rel.getJSONObject(i);
                if (r.optBoolean("draft")) continue;
                JSONArray as = r.optJSONArray("assets");
                String best = null; int bestScore = 0;
                for (int k = 0; as != null && k < as.length(); k++) {
                    JSONObject x = as.getJSONObject(k);
                    String n = x.optString("name");
                    if (!n.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                    if (m != null && !m.matcher(n).find()) continue;
                    int s = score(n);
                    if (s > bestScore) { bestScore = s; best = x.optString("browser_download_url"); }
                }
                if (best != null) {
                    a.apk = best;
                    a.latest = r.optString("tag_name").replaceAll("^[^0-9]*", "");
                    if (a.latest.isEmpty()) a.latest = r.optString("name");
                    a.error = null;
                    return;
                }
            }
            a.error = "Keine passende APK gefunden";
        } catch (Exception e) {
            a.error = e.getMessage() != null && e.getMessage().contains("403") ? "GitHub-Limit erreicht – später nochmal" : "GitHub nicht erreichbar";
        }
    }

    /** Wie gut passt eine APK zum Prozessor? 0 = gar nicht. */
    static int score(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        boolean x86 = n.matches(".*(x86|x64|amd64).*");
        boolean a64 = n.matches(".*(arm64|aarch64|v8a|armv8).*");
        boolean a32 = !a64 && n.matches(".*(armeabi|v7a|armv7|arm32|[-_.]arm[-_.]).*");
        List<String> abis = Arrays.asList(Build.SUPPORTED_ABIS);
        String primary = abis.isEmpty() ? "armeabi-v7a" : abis.get(0);
        if (x86 && !a64 && !a32) return primary.startsWith("x86") ? 3 : 0;
        if (a64) return primary.equals("arm64-v8a") ? 3 : 0;
        if (a32) return primary.equals("armeabi-v7a") ? 3 : abis.contains("armeabi-v7a") ? 2 : 0;
        return n.contains("universal") ? 2 : 1;   // ohne Angabe: meist universell
    }

    /** a neuer als b? Vergleicht Zahlenbloecke ("29.80" > "29.79", "1.10" > "1.9"). */
    static boolean newer(String a, String b) {
        Matcher ma = Pattern.compile("\\d+").matcher(a), mb = Pattern.compile("\\d+").matcher(b);
        List<Long> x = new ArrayList<>(), y = new ArrayList<>();
        while (ma.find() && x.size() < 4) x.add(Long.parseLong(ma.group().length() > 12 ? "0" : ma.group()));
        while (mb.find() && y.size() < 4) y.add(Long.parseLong(mb.group().length() > 12 ? "0" : mb.group()));
        if (x.isEmpty() || y.isEmpty()) return false;
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            long p = i < x.size() ? x.get(i) : 0, q = i < y.size() ? y.get(i) : 0;
            if (p != q) return p > q;
        }
        return false;
    }
}
