package de.midnox.rezepte;

import android.app.*;
import android.content.*;
import android.os.*;

/** Hält den Rezept-Server im Heimnetz am Laufen, auch wenn die App geschlossen ist (Einstellung „Immer im Netzwerk“). */
public class NetService extends Service {
    static void sync(Context c) {
        Intent i = new Intent(c, NetService.class);
        try {
            if (Store.bg()) { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i); }
            else c.stopService(i);
        } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        Srv.start(this);
        if (!Store.bg()) { stopSelf(); return START_NOT_STICKY; }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("net", "Rezepte im Netzwerk", NotificationManager.IMPORTANCE_MIN));
            b = new Notification.Builder(this, "net");
        } else b = new Notification.Builder(this);
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        b.setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle("midnox Rezepte im Netzwerk")
         .setContentText(Srv.url()).setContentIntent(pi).setOngoing(true);
        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, n);
        } catch (Exception e) { stopSelf(); }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
