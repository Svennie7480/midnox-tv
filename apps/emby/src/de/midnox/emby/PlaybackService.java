package de.midnox.emby;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** Hält die App am Leben, solange auf dem Handy Musik im Hintergrund läuft (Benachrichtigung mit Pause/Stopp). */
public class PlaybackService extends Service {
    static final String CH = "playback";
    static MainActivity act;
    static PlaybackService inst;

    static void show(MainActivity a, String title, String sub, boolean playing) {
        act = a;
        if (inst != null) { try { inst.post(title, sub, playing); return; } catch (Exception ignored) {} }   // läuft schon: nur Benachrichtigung aktualisieren
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            try { a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7); } catch (Exception ignored) {}
        Intent i = new Intent(a, PlaybackService.class).putExtra("t", title).putExtra("s", sub).putExtra("p", playing);
        try { if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(i); else a.startService(i); } catch (Exception ignored) {}
    }
    static void hide(MainActivity a) { try { a.stopService(new Intent(a, PlaybackService.class)); } catch (Exception ignored) {} }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        String act0 = in != null ? in.getAction() : null;
        if ("toggle".equals(act0) && act != null) { act.remoteKey("playpause"); return START_NOT_STICKY; }
        if ("stop".equals(act0)) { if (act != null) act.remoteKey("stop"); stopSelf(); return START_NOT_STICKY; }
        String t = in != null ? in.getStringExtra("t") : "midnox Media", s = in != null ? in.getStringExtra("s") : "";
        boolean playing = in == null || in.getBooleanExtra("p", true);
        inst = this;
        post(t, s, playing);
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() { if (inst == this) inst = null; super.onDestroy(); }

    void post(String t, String s, boolean playing) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null)
            nm.createNotificationChannel(new NotificationChannel(CH, "Wiedergabe", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), pf());
        PendingIntent tog = PendingIntent.getService(this, 1, new Intent(this, PlaybackService.class).setAction("toggle"), pf());
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, PlaybackService.class).setAction("stop"), pf());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(t).setContentText(s).setContentIntent(open).setOngoing(true).setShowWhen(false)
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pause" : "Weiter", tog)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stopp", stop);
        if (Build.VERSION.SDK_INT >= 21) b.setVisibility(Notification.VISIBILITY_PUBLIC).setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1));
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, b.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(7, b.build());
    }
    static int pf() { return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT; }
    @Override public IBinder onBind(Intent i) { return null; }
}
