package de.midnox.tv;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/** Status aus der midnox-Quiz-App (Tages-Duell, Serie) fuer Kachel und Hinweis auf der Startseite. */
final class QuizStatus {
    static final String PKG = "de.midnox.quiz";
    int played = -1, streak, rank, score;

    static QuizStatus read(Context c) {
        QuizStatus s = new QuizStatus();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Uri.parse("content://de.midnox.quiz.status"), null, null, null, null);
            if (cur != null && cur.moveToFirst()) { s.played = cur.getInt(0); s.streak = cur.getInt(1); s.rank = cur.getInt(2); s.score = cur.getInt(3); }
        } catch (Exception ignored) {
        } finally { if (cur != null) cur.close(); }
        return s;
    }
    boolean installed() { return played >= 0; }
    /** Kurzer Text auf der Quiz-Kachel. */
    String badge() {
        if (!installed()) return null;
        if (played == 0) return streak > 0 ? "🔥 " + streak + " · Duell wartet" : "Tages-Duell wartet";
        return rank > 0 ? "✓ Platz " + rank + (streak > 1 ? " · 🔥 " + streak : "") : streak > 1 ? "✓ 🔥 " + streak : null;
    }
    /** Hinweis oben (nur wenn das Quiz nicht als Kachel auf der Startseite liegt). */
    String alert() {
        if (!installed() || played != 0) return null;
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (h < 9) return null;
        return streak > 0 ? "🧠 Tages-Duell wartet – halte deine Serie von " + streak + (streak == 1 ? " Tag" : " Tagen") + "!" : "🧠 Das heutige Tages-Duell im midnox Quiz wartet";
    }
}
