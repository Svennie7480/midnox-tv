package de.midnox.tv;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import java.util.*;

/** "Wo laeuft das?": Sender je Spiel (Rechtelage 2026/27) und Highlights auf YouTube. */
final class Watch {
    /** Sender fuer ein Spiel. Rechte 2026/27: 1. Liga Fr/Sa Sky, Sa 15:30 Konferenz DAZN, So DAZN; 2. Liga alles Sky, Sa 20:30 auch RTL; Handball Dyn. */
    static List<String> who(String code, Sport.Match m, int day) {
        List<String> out = new ArrayList<>();
        Calendar k = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
        k.setTimeInMillis(m.kickoff);
        int wd = k.get(Calendar.DAY_OF_WEEK), hm = k.get(Calendar.HOUR_OF_DAY) * 100 + k.get(Calendar.MINUTE);
        if (code.equals("hb2") || m.hb) { out.add("Dyn"); return out; }
        if (code.equals("bl1")) {
            if (day == 1 && wd == Calendar.FRIDAY) out.add("Sat.1");
            if (wd == Calendar.SUNDAY) out.add("DAZN");
            else if (wd == Calendar.SATURDAY && hm == 1530) { out.add("Sky"); out.add("DAZN Konferenz"); }
            else if (wd == Calendar.FRIDAY || wd == Calendar.SATURDAY) out.add("Sky");
            else { out.add("Sky"); out.add("DAZN"); }   // englische Woche: aufgeteilt
        } else if (code.equals("bl2")) {
            if (day == 1 && wd == Calendar.FRIDAY) out.add("Sat.1");
            if (wd == Calendar.SATURDAY && hm >= 2000) out.add("RTL");
            out.add(wd == Calendar.SATURDAY && hm == 1300 ? "Sky (auch Konferenz)" : "Sky");
        }
        return out;
    }
    static String label(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(" · "); sb.append(s); }
        return sb.toString();
    }

    /** Oeffnet den Sender bzw. die passende App. Liefert einen Hinweistext. */
    static String open(Context c, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("rtl")) { TvRows.openTv(c, "RTL.de"); return "RTL wird geöffnet"; }
        if (n.startsWith("sat.1")) { TvRows.openTv(c, "SAT.1.de"); return "Sat.1 wird geöffnet"; }
        if (n.startsWith("dyn")) return Handball.openDyn(c);
        if (n.startsWith("dazn")) return launch(c, "DAZN", new String[]{"com.dazn", "com.dazn.firetv"}, new String[]{"dazn"});
        if (n.startsWith("sky")) return launch(c, "Sky", new String[]{"de.sky.online", "de.sky.wow", "de.sky.skygo", "com.sky.skygo.de"}, new String[]{"wow", "sky go", "sky"});
        return name;
    }

    /** App per Paketname oder Name finden und starten; sonst Amazon Appstore mit Suche. */
    static String launch(Context c, String title, String[] pkgs, String[] labels) {
        PackageManager pm = c.getPackageManager();
        for (String p : pkgs) {
            Intent i = Style.launchIntent(pm, p);
            if (i != null) { Style.start(c, i); return title + " wird geöffnet"; }
        }
        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
            String l = String.valueOf(pm.getApplicationLabel(a)).toLowerCase(Locale.ROOT);
            for (String x : labels) if (l.equals(x) || l.startsWith(x + " ")) {
                Intent i = Style.launchIntent(pm, a.packageName);
                if (i != null) { Style.start(c, i); return title + " wird geöffnet"; }
            }
        }
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?s=" + Uri.encode(title))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return title + " ist nicht installiert – Appstore geöffnet"; }
        catch (Exception e) { return title + " ist nicht installiert"; }
    }

    /** Highlights: YouTube-Suche in SmartTube (sonst YouTube). */
    static String highlights(Context c, String t1, String t2, boolean handball) {
        String q = t1 + " " + t2 + (handball ? " Handball" : "") + " Highlights";
        Uri u = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q));
        PackageManager pm = c.getPackageManager();
        for (String p : new String[]{"com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "com.amazon.firetv.youtube", "com.google.android.youtube.tv"}) {
            if (Style.launchIntent(pm, p) == null) continue;
            try { c.startActivity(new Intent(Intent.ACTION_VIEW, u).setPackage(p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Highlights werden gesucht …"; } catch (Exception ignored) {}
        }
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return "Highlights werden gesucht …"; }
        catch (Exception e) { return "Keine YouTube-App gefunden (SmartTube gibt es im midnox Store)"; }
    }
}
