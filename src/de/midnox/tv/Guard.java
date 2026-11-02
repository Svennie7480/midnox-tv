package de.midnox.tv;

import android.content.*;
import java.util.*;

/**
 * Schutz gegen Fire-OS-Updates:
 *  - prueft regelmaessig (Start, Bildschirm an, Neustart), ob midnox noch Startseite ist, und setzt sich sonst per ADB neu,
 *  - schaltet auf Wunsch die Systemupdates ab (nur deaktivieren, jederzeit umkehrbar),
 *  - Amazon-Telemetrie an/aus (Liste aus dem Einrichtungsassistenten).
 */
public class Guard {
    static final String[] OTA = {
        "com.amazon.device.software.ota", "com.amazon.device.software.ota.override", "com.amazon.tv.forcedotaupdater.v2"
    };
    static long lastHeal;
    static String lastResult = "";

    // ------------------------------------------------------------ Startseite reparieren
    static boolean adb(Context c) { return Style.prefs(c).getBoolean("adb_ok", false); }

    /** Im Hintergrund aufrufen. force = Zeitsperre ignorieren. */
    static void heal(Context c, boolean force, boolean goHome) {
        if (!adb(c) || !Style.prefs(c).getBoolean("guard_home", true)) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastHeal < 10 * 60000L) return;
        lastHeal = now;
        try {
            String r = Adb.shell(c, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME");
            if (r.contains(c.getPackageName() + "/")) { lastResult = "ok"; return; }
            Adb.shell(c, "cmd package set-home-activity " + c.getPackageName() + "/.HomeActivity");
            Style.prefs(c).edit().putLong("guard_fixed", now).putInt("guard_fixes", Style.prefs(c).getInt("guard_fixes", 0) + 1).apply();
            lastResult = "repariert";
            if (goHome) Adb.shell(c, "input keyevent 3");
        } catch (Exception e) { lastResult = "kein ADB"; }
    }
    static void healAsync(final Context c, final boolean force, final boolean goHome) {
        new Thread(new Runnable() { public void run() { heal(c.getApplicationContext(), force, goHome); } }).start();
    }

    // ------------------------------------------------------------ Pakete an/aus
    static Set<String> disabled(Context c) {
        Set<String> s = new HashSet<>();
        try { for (String l : Adb.shell(c, "pm list packages -d").split("\n")) if (l.startsWith("package:")) s.add(l.substring(8).trim()); } catch (Exception ignored) {}
        return s;
    }
    static Set<String> installed(Context c) {
        Set<String> s = new HashSet<>();
        try { for (String l : Adb.shell(c, "pm list packages com.amazon").split("\n")) if (l.startsWith("package:")) s.add(l.substring(8).trim()); } catch (Exception ignored) {}
        return s;
    }
    /** Ergebnis: Anzahl erfolgreich geaenderter Pakete, -1 = Fire OS hat abgelehnt. */
    static int set(Context c, String[] pkgs, boolean off) {
        Set<String> inst = installed(c);
        int ok = 0, tried = 0;
        for (String p : pkgs) {
            if (!inst.contains(p)) continue;
            tried++;
            try {
                String r = Adb.shell(c, (off ? "pm disable-user --user 0 " : "pm enable ") + p);
                if (r.contains("new state")) ok++;
            } catch (Exception ignored) {}
        }
        return tried > 0 && ok == 0 ? -1 : ok;
    }
    /** Zustand: 0 = an, 1 = aus, 2 = teilweise, -1 = unbekannt/nicht vorhanden. */
    static int state(Context c, String[] pkgs) {
        Set<String> inst = installed(c), dis = disabled(c);
        int n = 0, d = 0;
        for (String p : pkgs) if (inst.contains(p)) { n++; if (dis.contains(p)) d++; }
        if (n == 0) return -1;
        return d == 0 ? 0 : d == n ? 1 : 2;
    }

    static String updatesOff(Context c, boolean off) {
        int r = set(c, OTA, off);
        Style.prefs(c).edit().putBoolean("guard_ota", off && r != -1).apply();
        if (r == -1) return "Fire OS lässt das Abschalten der Updates auf diesem Gerät nicht zu";
        if (r == 0) return off ? "Keine Update-Dienste gefunden" : "Nichts zu tun";
        return off ? "Systemupdates gesperrt" : "Systemupdates wieder an";
    }
    static String telemetryOff(Context c, boolean off) {
        int r = set(c, WizardActivity.BLOAT, off);
        Style.prefs(c).edit().putBoolean("wiz_cleaned", off && r > 0).apply();
        if (r == -1) return "Fire OS hat abgelehnt";
        return off ? r + " Amazon-Dienste abgeschaltet" : r + " Amazon-Dienste wieder an";
    }

    /** Fuer die Pi-hole-Blockliste (zusaetzliche Ebene, ersetzt die ADB-Sperre nicht). */
    static final String PIHOLE_LIST = "https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/firetv/pihole-amazon.txt";
}
