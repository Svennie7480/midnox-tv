package de.midnox.tv;

import android.service.dreams.DreamService;

/** Systemweiter Bildschirmschoner (wenn am Fire TV als Bildschirmschoner gesetzt). */
public class MidnoxDream extends DreamService {
    Screensaver view;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Style.init(this);
        Theme.current(this).apply();
        setInteractive(false);
        setFullscreen(true);
        view = new Screensaver(this);
        setContentView(view);
    }
    @Override public void onDreamingStarted() { super.onDreamingStarted(); view.start(); }
    @Override public void onDreamingStopped() { super.onDreamingStopped(); view.stop(); }
}
