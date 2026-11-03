package de.midnox.live;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

/**
 * Selbst-Updater: prueft live-update.json auf GitHub und installiert neue Versionen
 * ueber den Fire-OS-Installer. So brauchen Nutzer ohne midnox-TV-Launcher keinen Downloader-Code.
 * live-update.json: {"versionCode":17,"versionName":"2.7","apk":"https://…/midnox-live.apk","notes":"…"}
 */
final class Update {
    static final String URL = "https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/live-update.json";
    interface Cb { void result(JSONObject update); }   // null = kein Update

    static int ownCode(Context c) { try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; } }

    /** Alle 24 h automatisch im Hintergrund pruefen. */
    static boolean due(Context c) { return System.currentTimeMillis() - U.prefs(c).getLong("upd_checked", 0) > 24 * 3600000L; }

    static void check(final Context c, final Cb cb) {
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() { public void run() {
            JSONObject res = null;
            try {
                JSONObject u = new JSONObject(U.get(URL + "?t=" + System.currentTimeMillis() / 3600000L));
                U.prefs(c).edit().putLong("upd_checked", System.currentTimeMillis()).apply();
                if (u.optInt("versionCode") > ownCode(c) && !u.optString("apk").isEmpty()) res = u;
            } catch (Exception ignored) {}
            final JSONObject r = res;
            h.post(new Runnable() { public void run() { cb.result(r); } });
        } }, "live-update").start();
    }

    /** APK laden und Installer oeffnen (einmal mit OK bestaetigen). */
    static void install(final Activity a, final JSONObject u, final Runnable onFail) {
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() { public void run() {
            try {
                File f = UpdateProvider.apk(a); f.getParentFile().mkdirs();
                HttpURLConnection c = (HttpURLConnection) new URL(u.getString("apk")).openConnection();
                c.setInstanceFollowRedirects(true); c.setConnectTimeout(8000); c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "midnoxLive");
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                try (InputStream in = c.getInputStream(); OutputStream out = new BufferedOutputStream(new FileOutputStream(f), 65536)) {
                    byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                if (f.length() < 100000) throw new IOException("Download unvollständig");
                h.post(new Runnable() { public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        i.setData(Uri.parse("content://" + UpdateProvider.AUTH + "/midnox-live.apk"));
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        i.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                        a.startActivity(i);
                    } catch (Exception e) { if (onFail != null) onFail.run(); }
                }});
            } catch (Exception e) { h.post(new Runnable() { public void run() { if (onFail != null) onFail.run(); } }); }
        } }, "live-update-dl").start();
    }
}
