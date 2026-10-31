package de.midnox.tv;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Stellt die heruntergeladene APK dem Fire-OS-Installer bereit (statt FileProvider). */
public class ApkProvider extends ContentProvider {
    public static final String AUTH = "de.midnox.tv.apk";

    File file() { return new File(new File(getContext().getCacheDir(), "update"), "midnox-tv.apk"); }
    File file(Uri u) { return "upload.apk".equals(u.getLastPathSegment()) ? Installer.file(getContext()) : file(); }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{uri.getLastPathSegment(), file(uri).length()});
        return c;
    }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
