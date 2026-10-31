package de.midnox.tv;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Liest die Live-TV-Programmdatenbank des Fire TV (Sender + laufende Sendungen, z. B. von MagentaTV). */
public class TvGuide {
    static final Uri CHANNELS = Uri.parse("content://android.media.tv/channel");
    static final Uri PROGRAMS = Uri.parse("content://android.media.tv/program");

    /** Diagnose: was ist lesbar? Ergebnis auch in files/tv_status.txt. */
    static String diagnose(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("TV-Programm-Test ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(new Date())).append('\n');
        Map<String, Integer> perPkg = new TreeMap<>();
        List<String> sample = new ArrayList<>();
        int channels = 0;
        try (Cursor k = c.getContentResolver().query(CHANNELS, new String[]{"_id", "package_name", "display_number", "display_name", "input_id", "browsable"}, null, null, null)) {
            if (k == null) sb.append("Sender: keine Antwort (null)\n");
            else while (k.moveToNext()) {
                channels++;
                String pkg = k.getString(1);
                perPkg.put(pkg, perPkg.containsKey(pkg) ? perPkg.get(pkg) + 1 : 1);
                if (sample.size() < 15) sample.add(k.getLong(0) + " | " + k.getString(2) + " | " + k.getString(3) + " | " + pkg + " | " + k.getString(4) + " | browsable=" + k.getInt(5));
            }
        } catch (Exception e) { sb.append("Sender-Fehler: ").append(e).append('\n'); }
        sb.append("Sender gesamt: ").append(channels).append('\n');
        for (Map.Entry<String, Integer> e : perPkg.entrySet()) sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        for (String s : sample) sb.append("    ").append(s).append('\n');
        long now = System.currentTimeMillis();
        int progs = 0;
        try (Cursor p = c.getContentResolver().query(PROGRAMS, new String[]{"channel_id", "title", "start_time_utc_millis", "end_time_utc_millis"},
                "start_time_utc_millis<=? AND end_time_utc_millis>?", new String[]{String.valueOf(now), String.valueOf(now)}, null)) {
            if (p == null) sb.append("Sendungen: keine Antwort (null)\n");
            else {
                SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.GERMANY);
                while (p.moveToNext()) {
                    if (progs < 15) sb.append("    jetzt: Sender ").append(p.getLong(0)).append(" | ").append(p.getString(1))
                            .append(" | ").append(f.format(new Date(p.getLong(2)))).append("–").append(f.format(new Date(p.getLong(3)))).append('\n');
                    progs++;
                }
            }
        } catch (Exception e) { sb.append("Sendungen-Fehler: ").append(e).append('\n'); }
        sb.append("Laufende Sendungen: ").append(progs).append('\n');
        try (FileWriter w = new FileWriter(new File(c.getExternalFilesDir(null), "tv_status.txt"))) { w.write(sb.toString()); } catch (Exception ignored) {}
        return channels + " Sender, " + progs + " laufende Sendungen";
    }
}
