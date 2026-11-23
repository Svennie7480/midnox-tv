package de.midnox.rezepte;

import android.content.*;

public class Boot extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { Store.init(c.getApplicationContext()); if (Store.bg()) NetService.sync(c); }
}
