package de.midnox.tv;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** "Gute Nacht": kurzer Countdown mit Verlaengern/Abbrechen, danach dunkler Bildschirm. */
public class SleepActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    TextView count, sub;
    LinearLayout btns;
    int left = 20;
    boolean done;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xF2050409);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(new Style.GradientText(this, "Gute Nacht", 52));
        count = Style.text(this, "", 16, 0xCCFFFFFF);
        count.setPadding(0, Style.px(10), 0, Style.px(26));
        col.addView(count);
        btns = new LinearLayout(this);
        Pill more = new Pill(this, "+15 Minuten", new Runnable() { public void run() { Sleep.set(SleepActivity.this, 15); finish(); } });
        Pill now = new Pill(this, "Jetzt schlafen", new Runnable() { public void run() { sleepNow(); } });
        Pill cancel = new Pill(this, "Abbrechen", new Runnable() { public void run() { Sleep.set(SleepActivity.this, 0); finish(); } });
        for (Pill p : new Pill[]{more, now, cancel}) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(Style.px(8), 0, Style.px(8), 0); btns.addView(p, lp); }
        col.addView(btns);
        sub = Style.text(this, "", 12, Style.MUTED);
        sub.setPadding(0, Style.px(24), 0, 0);
        col.addView(sub);
        root.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        more.requestFocus();
        // Radio leise ausblenden
        h.post(tick);
    }

    final Runnable tick = new Runnable() { public void run() {
        if (done) return;
        count.setText("Alles geht in " + left + " Sekunden aus");
        sub.setText("Radio und Filme stoppen" + (Style.prefs(SleepActivity.this).getBoolean("sleep_lights", true) ? ", Licht geht aus" : "")
                + (Style.prefs(SleepActivity.this).getString("sleep_tv", "").isEmpty() ? "" : ", Fernseher schaltet ab"));
        if (RadioService.inst != null) RadioService.inst.volume(Math.max(0.05f, left / 20f));
        if (left-- <= 0) { sleepNow(); return; }
        h.postDelayed(this, 1000);
    }};

    void sleepNow() {
        if (done) return;
        done = true;
        btns.setVisibility(View.GONE);
        count.setText("Schlaf gut.");
        sub.setText("");
        new Thread(new Runnable() { public void run() { Sleep.execute(SleepActivity.this); } }).start();
        // Bildschirm dunkel und nicht mehr wachhalten
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        h.postDelayed(new Runnable() { public void run() {
            View dark = new View(SleepActivity.this);
            dark.setBackgroundColor(Color.BLACK);
            addContentView(dark, new FrameLayout.LayoutParams(-1, -1));
            dark.setAlpha(0f); dark.animate().alpha(1f).setDuration(2500).start();
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 0f;
            getWindow().setAttributes(lp);
        }}, 2500);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (done && e.getAction() == KeyEvent.ACTION_DOWN) {   // aufwachen
            Style.prefs(this).edit().putBoolean("sleep_mode", false).apply();
            finish();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
