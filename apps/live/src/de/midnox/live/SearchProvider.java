package de.midnox.live;

import android.content.*;
import android.database.*;
import android.net.Uri;

/**
 * Schnittstelle fuer die globale Suche von midnox TV:
 * content://de.midnox.live.search/vod?q=Avatar  ->  Treffer (ohne Zugangsdaten); Oeffnen per Intent mit Extra "vod".
 */
public class SearchProvider extends ContentProvider {
    static final String[] COLS = {"key", "title", "year", "poster", "series", "sources", "source"};
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        MatrixCursor m = new MatrixCursor(COLS);
        String q = uri.getQueryParameter("q");
        if (q == null && args != null && args.length > 0) q = args[0];
        if (q == null || q.trim().length() < 2) return m;
        Context c = getContext();
        U.init(c);
        for (VodSearch.Hit h : VodSearch.search(c, q.trim())) {
            Model.Source s = Lib.source(c, h.best().src);
            m.addRow(new Object[]{h.key, h.title, h.year, h.poster, h.series ? 1 : 0, h.sources.size(), s == null ? "" : s.name});
        }
        return m;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.midnox.vod"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
