package de.midnox.live;

import android.content.*;
import android.database.*;
import android.net.Uri;

/** Gibt die Einstellungen (Quellen, MACs, Favoriten, Zuordnungen) an midnox Media auf demselben Gerät weiter.
 *  Nur Apps mit derselben Signatur (de.midnox.permission.SHARE) dürfen lesen. */
public class ExportProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        try {
            MatrixCursor c = new MatrixCursor(new String[]{"json"});
            c.addRow(new Object[]{ Web.export(getContext()).toString() });
            return c;
        } catch (Exception e) { return null; }
    }
    @Override public String getType(Uri u) { return "application/json"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
