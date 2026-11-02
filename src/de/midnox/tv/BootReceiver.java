package de.midnox.tv;

import android.content.*;

/** Nach einem Neustart (z. B. nach einem Fire-OS-Update): Startseite pruefen und Hintergrunddienst starten. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context c, Intent i) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() { public void run() {
            try {
                Thread.sleep(8000);   // ADB-Dienst braucht nach dem Start einen Moment
                Guard.heal(c.getApplicationContext(), true, true);
                if (Style.prefs(c).getBoolean("guard_ota", false)) Guard.set(c.getApplicationContext(), Guard.OTA, true); // falls ein Update sie wieder eingeschaltet hat
            } catch (Exception ignored) {}
            try { c.startService(new Intent(c, KinoService.class)); } catch (Exception ignored) {}
            pr.finish();
        }}).start();
    }
}
