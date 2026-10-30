package de.midnox.tv;

import android.app.Activity;
import android.os.*;
import android.view.*;

/** Bildschirmschoner als Vollbild (Test aus den Einstellungen und nach Leerlauf auf der Startseite). */
public class ScreensaverActivity extends Activity {
    Screensaver view;
    final Handler h = new Handler(Looper.getMainLooper());
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        view = new Screensaver(this);
        setContentView(view);
        // nach 2 Stunden darf das Geraet wieder in den Ruhemodus
        h.postDelayed(new Runnable() { public void run() { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } }, 2 * 3600 * 1000L);
    }
    @Override protected void onResume() { super.onResume(); view.start(); }
    @Override protected void onPause() { super.onPause(); view.stop(); finish(); }
    @Override public boolean dispatchKeyEvent(KeyEvent e) { if (e.getAction() == KeyEvent.ACTION_DOWN) finish(); return true; }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) { finish(); return true; }
}
