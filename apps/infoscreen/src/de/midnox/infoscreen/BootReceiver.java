package de.midnox.infoscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Startet den Infoscreen nach dem Einschalten des Fernsehers/Fire TV automatisch. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!c.getSharedPreferences("mi", 0).getBoolean("autostart", true)) return;
        try {
            Intent s = new Intent(c, MainActivity.class);
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(s);
        } catch (Exception ignored) {}
    }
}
