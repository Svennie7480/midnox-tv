package de.midnox.tv;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Gibt den ADB-Schlüssel des Launchers an die übrigen midnox-Apps weiter (Tools, Maus …),
 * damit die ADB-Freigabe am Fernseher nur EINMAL bestätigt werden muss und danach für alle gilt.
 * Zugriff nur für Apps mit derselben Signatur (midnox.jks) – abgesichert über eine signature-Permission.
 */
public class KeyProvider extends ContentProvider {
    public static final String AUTH = "de.midnox.tv.keys";

    @Override public boolean onCreate() { return true; }

    /** call("get") -> Bundle mit "pub"/"priv" (Base64, ADB-RSA-Schlüsselpaar des Launchers). */
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"get".equals(method)) return null;
        try {
            Adb.keys(getContext());   // stellt sicher, dass ein Schlüssel existiert
            android.content.SharedPreferences pr = Style.prefs(getContext());
            Bundle b = new Bundle();
            b.putString("pub", pr.getString("adb_pub", ""));
            b.putString("priv", pr.getString("adb_priv", ""));
            return b;
        } catch (Exception e) { return null; }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
