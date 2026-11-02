package de.midnox.quiz;

import android.content.*;
import android.database.*;
import android.net.Uri;
import org.json.JSONObject;

/** Kurzstatus fuer den midnox-TV-Launcher: heute gespielt?, Tagesserie, Platz, Punkte. Nur lesend. */
public class StatusProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        Context c = getContext();
        MatrixCursor m = new MatrixCursor(new String[]{"played", "streak", "rank", "score", "name"});
        int played = 0, rank = 0, score = 0;
        try {
            String s = U.prefs(c).getString("daily_" + Scores.today(), "");
            if (!s.isEmpty()) { JSONObject o = new JSONObject(s); played = 1; rank = o.optInt("rank"); score = o.optInt("s"); }
        } catch (Exception ignored) {}
        m.addRow(new Object[]{played, Stats.streak(c), rank, score, U.prefs(c).getString("name", "")});
        return m;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.midnox.quiz"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
