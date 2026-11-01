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
    static boolean isApp(String name) { return name.toLowerCase(Locale.ROOT).matches(".*\\.(apk|apks|xapk)$"); }

    static String receive(Context c, InputStream in, long len, String name, boolean unzip) throws Exception {
        if (!isApp(name)) return saveFile(c, in, len, name, unzip).toString();
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

    // ------------------------------------------------------------ beliebige Dateien -> Download-Ordner
    static JSONObject saveFile(final Context c, InputStream in, long len, String name, boolean unzip) throws Exception {
        if (len <= 0) return fail("Keine Datei empfangen");
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty()) name = "datei";
        toast(c, "Empfange " + name + " …");
        File dl = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        boolean canWrite = c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!canWrite) {   // Recht einmalig ueber ADB holen
            try { Adb.shellLong(c, "pm grant " + c.getPackageName() + " android.permission.WRITE_EXTERNAL_STORAGE", 20000); } catch (Exception ignored) {}
            canWrite = c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        File dir = canWrite ? dl : new File(dir(c), "Empfangen");
        dir.mkdirs();
        File f = unique(dir, name);
        OutputStream out = new BufferedOutputStream(new FileOutputStream(f), 65536);
        byte[] buf = new byte[65536]; long left = len; int n;
        try { while (left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0) { out.write(buf, 0, n); left -= n; } }
        finally { out.close(); }
        if (left > 0) { f.delete(); return fail("Übertragung abgebrochen"); }
        String where = (canWrite ? "Download/" : "midnox-Ordner/") + f.getName();
        if (!canWrite) {   // ohne Schreibrecht: ueber ADB in den Download-Ordner verschieben
            try {
                Adb.shellLong(c, "mkdir -p /sdcard/Download && mv '" + f.getPath() + "' '/sdcard/Download/" + f.getName().replace("'", "") + "'", 120000);
                f = new File(dl, f.getName().replace("'", "")); where = "Download/" + f.getName();
            } catch (Exception ignored) {}
        }
        String extra = "";
        if (unzip && f.getName().toLowerCase(Locale.ROOT).endsWith(".zip") && f.canRead() && f.getParentFile().canWrite()) {
            File to = unique(f.getParentFile(), f.getName().replaceAll("(?i)\\.zip$", ""));
            int cnt = 0;
            try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(f)))) {
                ZipEntry e;
                String base = to.getCanonicalPath() + File.separator;
                while ((e = z.getNextEntry()) != null) {
                    File o = new File(to, e.getName());
                    if (!o.getCanonicalPath().startsWith(base)) continue;   // Schutz vor ../-Pfaden
                    if (e.isDirectory()) { o.mkdirs(); continue; }
                    o.getParentFile().mkdirs();
                    try (OutputStream w = new BufferedOutputStream(new FileOutputStream(o), 65536)) { while ((n = z.read(buf)) > 0) w.write(buf, 0, n); }
                    cnt++;
                }
            }
            extra = " · " + cnt + " Dateien entpackt nach " + to.getName() + "/";
            scan(c, to);
        }
        scan(c, f);
        final String msg = "\u2713 " + f.getName() + " gespeichert in " + where.replaceAll("/[^/]*$", "/") + extra;
        toast(c, msg);
        return new JSONObject().put("ok", true).put("msg", msg).put("path", f.getPath());
    }
    static File unique(File dir, String name) {
        File f = new File(dir, name);
        String base = name.replaceAll("\\.[^.]*$", ""), ext = name.length() > base.length() ? name.substring(base.length()) : "";
        for (int i = 2; f.exists() && i < 1000; i++) f = new File(dir, base + " (" + i + ")" + ext);
        return f;
    }
    static void scan(Context c, File f) {
        try {
            List<String> paths = new ArrayList<>();
            if (f.isDirectory()) { File[] l = f.listFiles(); if (l != null) for (File x : l) if (x.isFile()) paths.add(x.getPath()); }
            else paths.add(f.getPath());
            android.media.MediaScannerConnection.scanFile(c, paths.toArray(new String[0]), null, null);
        } catch (Exception ignored) {}
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
