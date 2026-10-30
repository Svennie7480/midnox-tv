package de.midnox.tv;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

/**
 * Prueft update.json (Standard: GitHub) und installiert neue Versionen.
 * update.json: {"versionCode": 13, "versionName": "4.1", "apk": "https://.../midnox-tv.apk", "notes": "..."}
 */
public class Updater {
    public static final String DEFAULT_URL = "https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/update.json";

    public interface Callback { void result(JSONObject update); }

    /** Letztes Ergebnis der Suche (fuer Einstellungen + Protokoll). */
    public static volatile String lastStatus = "";

    static void log(Context c, String msg) {
        lastStatus = msg;
        try {
            FileOutputStream o = new FileOutputStream(new File(c.getExternalFilesDir(null), "updater_status.txt"));
            o.write((new java.util.Date() + "  " + msg).getBytes("UTF-8"));
            o.close();
        } catch (Exception ignored) {}
    }

    static String url(Context c) {
        JSONObject cfg = Style.loadConfig(c);
        String u = cfg == null ? "" : cfg.optString("update_url", "");
        return u.isEmpty() ? DEFAULT_URL : u;
    }

    static int ownCode(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    /** Liefert das Update-Objekt, wenn eine neuere Version vorliegt, sonst null (im UI-Thread). */
    public static void check(final Context c, final Callback cb) {
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() { public void run() {
            JSONObject res = null;
            String target = url(c) + "?t=" + System.currentTimeMillis();
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(target).openConnection();
                con.setConnectTimeout(8000); con.setReadTimeout(10000);
                con.setInstanceFollowRedirects(true);
                int code = con.getResponseCode();
                if (code != 200) throw new IOException("HTTP " + code);
                JSONObject u = new JSONObject(Style.read(con.getInputStream()));
                int remote = u.optInt("versionCode", 0), own = ownCode(c);
                if (remote > own && !u.optString("apk").isEmpty()) { res = u; log(c, "Update gefunden: " + u.optString("versionName") + " (" + remote + " > " + own + ")"); }
                else log(c, "Aktuell: online " + u.optString("versionName") + " (" + remote + "), installiert " + own);
            } catch (Exception e) {
                log(c, "Fehler bei " + target + " -> " + e);
            }
            final JSONObject r = res;
            h.post(new Runnable() { public void run() { cb.result(r); } });
        }}, "update-check").start();
    }

    /** APK laden und den Installer von Fire OS oeffnen (einmal mit OK bestaetigen). */
    public static void install(final Activity a, final JSONObject u) {
        final Handler h = new Handler(Looper.getMainLooper());
        Toast.makeText(a, "Lade Update " + u.optString("versionName") + " …", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { public void run() {
            try {
                File dir = new File(a.getCacheDir(), "update");
                dir.mkdirs();
                final File f = new File(dir, "midnox-tv.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(u.getString("apk")).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(8000); c.setReadTimeout(30000);
                InputStream in = c.getInputStream();
                OutputStream out = new FileOutputStream(f);
                byte[] buf = new byte[16384]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close(); in.close();
                h.post(new Runnable() { public void run() {
                    try {
                        Uri uri = Uri.parse("content://" + ApkProvider.AUTH + "/midnox-tv.apk");
                        Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        i.setData(uri);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        a.startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(a, "Installer konnte nicht geöffnet werden", Toast.LENGTH_LONG).show();
                    }
                }});
            } catch (final Exception e) {
                h.post(new Runnable() { public void run() { Toast.makeText(a, "Update fehlgeschlagen: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show(); } });
            }
        }}, "update-download").start();
    }
}
