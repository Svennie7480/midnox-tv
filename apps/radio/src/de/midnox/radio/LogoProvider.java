package de.midnox.radio;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Liefert Senderlogos als content://-Adresse (Android Auto laedt Bilder nur so zuverlaessig). */
public class LogoProvider extends ContentProvider {
    static final String AUTH = "de.midnox.radio.logo";
    static Uri uri(String url) { return new Uri.Builder().scheme("content").authority(AUTH).appendPath(Img.md5(url)).appendQueryParameter("u", url).build(); }

    @Override public boolean onCreate() { Img.init(getContext()); return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        String url = u.getQueryParameter("u");
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) throw new FileNotFoundException();
        File f = new File(Img.dir, Img.md5(url));
        if (!f.exists()) Img.fetch(url, 256);          // laedt und legt die Datei im Cache ab
        if (!f.exists()) throw new FileNotFoundException(url);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri u) { return "image/*"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
