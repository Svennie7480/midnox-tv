package de.midnox.tv;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

/**
 * Apps vom Handy installieren: APK (oder Split-Bundle .apks/.xapk) wird hochgeladen
 * bzw. per Link geladen und still ueber ADB installiert; sonst Installer-Dialog am TV.
 */
public class Installer {
    static volatile boolean busy;

    static File dir(Context c) {
        File d = c.getExternalFilesDir("install");
        if (d == null) d = new File(c.getFilesDir(), "install");
        d.mkdirs();
        return d;
    }
    static File file(Context c) { return new File(dir(c), "upload.apk"); }

    /** Upload vom Handy (roher Datenstrom). */
    static String receive(Context c, InputStream in, long len, String name) throws Exception {
        if (busy) return err("Es läuft gerade schon eine Installation");
        busy = true;
        try {
            if (len <= 0) return err("Keine Datei empfangen");
            toast(c, "Empfange " + name + " …");
            File f = file(c);
            OutputStream out = new BufferedOutputStream(new FileOutputStream(f), 65536);
            byte[] buf = new byte[65536]; long left = len; int n;
            while (left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0) { out.write(buf, 0, n); left -= n; }
            out.close();
            if (left > 0) return err("Upload unvollständig");
            return install(c, f, name).toString();
        } finally { busy = false; }
    }

    /** APK von einem Link laden. */
    static String fromUrl(Context c, String url) throws Exception {
        if (busy) return err("Es läuft gerade schon eine Installation");
        busy = true;
        try {
            if (!url.matches("(?i)https?://.+")) return err("Bitte einen Link mit http:// oder https:// angeben");
            String name = url.replaceAll("[?#].*", "").replaceAll(".*/", "");
            if (name.isEmpty()) name = "app.apk";
            toast(c, "Lade " + name + " …");
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setInstanceFollowRedirects(true);
            h.setConnectTimeout(10000); h.setReadTimeout(30000);
            h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) midnox TV");
            int code = h.getResponseCode();
            for (int i = 0; i < 5 && code / 100 == 3; i++) {   // Weiterleitung http <-> https
                String loc = h.getHeaderField("Location");
                h = (HttpURLConnection) new URL(new URL(url), loc).openConnection();
                h.setConnectTimeout(10000); h.setReadTimeout(30000);
                code = h.getResponseCode();
            }
            if (code != 200) return err("Download fehlgeschlagen (HTTP " + code + ")");
            String cd = h.getHeaderField("Content-Disposition");
            if (cd != null && cd.contains("filename=")) name = cd.replaceAll(".*filename\\*?=(UTF-8'')?\"?", "").replaceAll("[\";].*", "");
            File f = file(c);
            InputStream in = h.getInputStream();
            OutputStream out = new BufferedOutputStream(new FileOutputStream(f), 65536);
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close(); in.close();
            return install(c, f, name).toString();
        } finally { busy = false; }
    }

    static JSONObject install(final Context c, File f, String name) throws Exception {
        PackageManager pm = c.getPackageManager();
        // Einzelne APK oder Bundle mit mehreren APKs?
        List<File> parts = new ArrayList<>();
        File base = f;
        try (ZipFile z = new ZipFile(f)) {
            if (z.getEntry("AndroidManifest.xml") == null) {
                File pd = new File(dir(c), "parts");
                deleteAll(pd); pd.mkdirs();
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (e.isDirectory() || !n.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                    File out = new File(pd, n.replaceAll(".*/", "").replaceAll("[^A-Za-z0-9._-]", "_"));
                    copy(z.getInputStream(e), out);
                    parts.add(out);
                }
                if (parts.isEmpty()) return fail("Das ist keine App-Datei (APK)");
                base = parts.get(0);
                for (File p : parts) if (p.getName().equalsIgnoreCase("base.apk") || !p.getName().toLowerCase(Locale.ROOT).startsWith("split") && !p.getName().contains("config.")) { base = p; break; }
            }
        } catch (ZipException e) { return fail("Die Datei ist beschädigt oder keine APK"); }

        PackageInfo pi = pm.getPackageArchiveInfo(base.getPath(), 0);
        if (pi == null) return fail("Die APK konnte nicht gelesen werden");
        pi.applicationInfo.sourceDir = pi.applicationInfo.publicSourceDir = base.getPath();
        String label = String.valueOf(pi.applicationInfo.loadLabel(pm));
        final String pkg = pi.packageName;
        String old = null;
        try { old = pm.getPackageInfo(pkg, 0).versionName; } catch (Exception ignored) {}
        if (pkg.equals(c.getPackageName())) return fail("midnox TV aktualisiert sich selbst über Einstellungen → Updates");
        String what = label + " " + (pi.versionName == null ? "" : pi.versionName);
        toast(c, (old == null ? "Installiere " : "Aktualisiere ") + what.trim() + " …");

        // 1) still ueber ADB
        String out = null;
        try {
            if (parts.isEmpty()) out = Adb.shellLong(c, "pm install -r -g '" + f.getPath() + "'", 240000);
            else {
                String cr = Adb.shellLong(c, "pm install-create -r -g", 30000);
                String sid = cr.replaceAll("(?s).*\\[(\\d+)\\].*", "$1");
                if (!sid.matches("\\d+")) throw new IOException(cr);
                for (File p : parts) {
                    String w = Adb.shellLong(c, "pm install-write -S " + p.length() + " " + sid + " '" + p.getName() + "' '" + p.getPath() + "'", 240000);
                    if (!w.contains("Success")) { Adb.shellLong(c, "pm install-abandon " + sid, 20000); out = w; break; }
                }
                if (out == null) out = Adb.shellLong(c, "pm install-commit " + sid, 240000);
            }
            Style.prefs(c).edit().putBoolean("adb_ok", true).apply();
        } catch (Exception e) { out = null; }

        if (out != null && out.contains("Success")) {
            f.delete(); deleteAll(new File(dir(c), "parts"));
            final String msg = label + (old == null ? " ist installiert" : " ist aktualisiert (" + old + " → " + pi.versionName + ")");
            ui(new Runnable() { public void run() {
                Bundle b = new Bundle(); b.putString("pkg", pkg);
                PromptActivity.show(c, "installed", "App installiert", msg, b);
            }});
            return new JSONObject().put("ok", true).put("msg", "✓ " + msg).put("pkg", pkg);
        }
        if (out != null && out.contains("Failure")) {
            String reason = out.replaceAll("(?s).*Failure \\[([^\\]]*)\\].*", "$1").trim();
            String nice = reason.contains("UPDATE_INCOMPATIBLE") ? "Andere Signatur als die installierte Version – bitte die alte App erst deinstallieren"
                    : reason.contains("OLDER_SDK") ? "Die App braucht eine neuere Android-Version als der Fire TV hat"
                    : reason.contains("NO_MATCHING_ABIS") ? "Die App passt nicht zum Prozessor des Fire TV (andere APK-Variante wählen, meist armeabi-v7a oder arm64)"
                    : reason.contains("VERSION_DOWNGRADE") ? "Auf dem Fire TV ist schon eine neuere Version installiert"
                    : reason.contains("INSUFFICIENT_STORAGE") ? "Nicht genug Speicherplatz auf dem Fire TV"
                    : "Installation abgelehnt: " + reason;
            toast(c, nice);
            return fail(nice);
        }

        // 2) ohne ADB: normaler Installer-Dialog am Fernseher (nur einzelne APKs)
        if (!parts.isEmpty()) return fail("App-Bundles gehen nur mit Netzwerk-Debugging (ADB) – bitte am Fire TV einschalten und einmal zulassen");
        ui(new Runnable() { public void run() {
            try {
                Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                i.setData(Uri.parse("content://" + ApkProvider.AUTH + "/upload.apk"));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception e) { Style.toast(c, "Installer konnte nicht geöffnet werden"); }
        }});
        return new JSONObject().put("ok", true).put("msg", "Bitte am Fernseher „Installieren“ bestätigen (" + what.trim() + ")");
    }

    // ------------------------------------------------------------ Helfer
    static void copy(InputStream in, File out) throws IOException {
        OutputStream o = new BufferedOutputStream(new FileOutputStream(out), 65536);
        byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        o.close(); in.close();
    }
    static void deleteAll(File d) { File[] l = d.listFiles(); if (l != null) for (File x : l) x.delete(); d.delete(); }
    static void ui(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
    static void toast(final Context c, final String s) { ui(new Runnable() { public void run() { Style.toast(c, s); } }); }
    static String err(String m) { try { return fail(m).toString(); } catch (Exception e) { return "{\"ok\":false}"; } }
    static JSONObject fail(String m) throws JSONException { return new JSONObject().put("ok", false).put("msg", m); }
}
